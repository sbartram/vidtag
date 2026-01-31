package org.bartram.vidtag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bartram.vidtag.config.TagFilterProperties;
import org.bartram.vidtag.model.RaindropTag;
import org.bartram.vidtag.model.TagStrategy;
import org.bartram.vidtag.model.TagWithConfidence;
import org.bartram.vidtag.model.VideoMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VideoTaggingService.
 */
@ExtendWith(MockitoExtension.class)
class VideoTaggingServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    private ObjectMapper objectMapper;
    private VideoTaggingService videoTaggingService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(chatClientBuilder.build()).thenReturn(chatClient);
        // Create empty filter properties for default behavior (no filtering)
        TagFilterProperties emptyFilterProperties = new TagFilterProperties();
        videoTaggingService = new VideoTaggingService(chatClientBuilder, objectMapper, emptyFilterProperties);
    }

    @Test
    void generateTags_shouldReturnTagsFromAIResponse() {
        // Given
        VideoMetadata video = new VideoMetadata(
            "video123",
            "https://youtube.com/watch?v=video123",
            "Java Spring Boot Tutorial",
            "Learn how to build REST APIs with Spring Boot",
            Instant.now(),
            1800
        );
        List<RaindropTag> existingTags = List.of(
            new RaindropTag("java"),
            new RaindropTag("spring"),
            new RaindropTag("tutorial")
        );
        TagStrategy strategy = new TagStrategy(5, 0.7, null);

        String aiResponse = """
            [
                {"tag": "java", "confidence": 0.95, "isExisting": true},
                {"tag": "spring-boot", "confidence": 0.90, "isExisting": false},
                {"tag": "rest-api", "confidence": 0.85, "isExisting": false}
            ]
            """;

        setupMockChatClient(aiResponse);

        // When
        List<TagWithConfidence> result = videoTaggingService.generateTags(video, existingTags, strategy);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).tag()).isEqualTo("java");
        assertThat(result.get(0).confidence()).isEqualTo(0.95);
        assertThat(result.get(0).isExisting()).isTrue();
    }

    @Test
    void generateTags_shouldFilterByConfidenceThreshold() {
        // Given
        VideoMetadata video = new VideoMetadata(
            "video123",
            "https://youtube.com/watch?v=video123",
            "Java Tutorial",
            "Description",
            Instant.now(),
            600
        );
        List<RaindropTag> existingTags = List.of();
        TagStrategy strategy = new TagStrategy(10, 0.8, null);

        String aiResponse = """
            [
                {"tag": "java", "confidence": 0.95, "isExisting": false},
                {"tag": "coding", "confidence": 0.75, "isExisting": false},
                {"tag": "programming", "confidence": 0.85, "isExisting": false}
            ]
            """;

        setupMockChatClient(aiResponse);

        // When
        List<TagWithConfidence> result = videoTaggingService.generateTags(video, existingTags, strategy);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(TagWithConfidence::tag)
            .containsExactly("java", "programming");
        assertThat(result).allMatch(tag -> tag.confidence() >= 0.8);
    }

    @Test
    void generateTags_shouldLimitByMaxTags() {
        // Given
        VideoMetadata video = new VideoMetadata(
            "video123",
            "https://youtube.com/watch?v=video123",
            "Java Tutorial",
            "Description",
            Instant.now(),
            600
        );
        List<RaindropTag> existingTags = List.of();
        TagStrategy strategy = new TagStrategy(2, 0.5, null);

        String aiResponse = """
            [
                {"tag": "java", "confidence": 0.95, "isExisting": false},
                {"tag": "spring", "confidence": 0.90, "isExisting": false},
                {"tag": "coding", "confidence": 0.85, "isExisting": false},
                {"tag": "programming", "confidence": 0.80, "isExisting": false}
            ]
            """;

        setupMockChatClient(aiResponse);

        // When
        List<TagWithConfidence> result = videoTaggingService.generateTags(video, existingTags, strategy);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(TagWithConfidence::tag)
            .containsExactly("java", "spring");
    }

    @Test
    void generateTags_shouldHandleMarkdownCodeBlocks() {
        // Given
        VideoMetadata video = new VideoMetadata(
            "video123",
            "https://youtube.com/watch?v=video123",
            "Java Tutorial",
            "Description",
            Instant.now(),
            600
        );
        List<RaindropTag> existingTags = List.of();
        TagStrategy strategy = new TagStrategy(5, 0.5, null);

        String aiResponseWithMarkdown = """
            ```json
            [
                {"tag": "java", "confidence": 0.95, "isExisting": false}
            ]
            ```
            """;

        setupMockChatClient(aiResponseWithMarkdown);

        // When
        List<TagWithConfidence> result = videoTaggingService.generateTags(video, existingTags, strategy);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).tag()).isEqualTo("java");
    }

    @Test
    void generateTags_shouldHandleMarkdownCodeBlocksWithoutLanguage() {
        // Given
        VideoMetadata video = new VideoMetadata(
            "video123",
            "https://youtube.com/watch?v=video123",
            "Java Tutorial",
            "Description",
            Instant.now(),
            600
        );
        List<RaindropTag> existingTags = List.of();
        TagStrategy strategy = new TagStrategy(5, 0.5, null);

        String aiResponseWithMarkdown = """
            ```
            [
                {"tag": "python", "confidence": 0.88, "isExisting": false}
            ]
            ```
            """;

        setupMockChatClient(aiResponseWithMarkdown);

        // When
        List<TagWithConfidence> result = videoTaggingService.generateTags(video, existingTags, strategy);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).tag()).isEqualTo("python");
    }

    @Test
    void generateTags_shouldIncludeCustomInstructionsInPrompt() {
        // Given
        VideoMetadata video = new VideoMetadata(
            "video123",
            "https://youtube.com/watch?v=video123",
            "Java Tutorial",
            "Description",
            Instant.now(),
            600
        );
        List<RaindropTag> existingTags = List.of();
        String customInstructions = "Focus on programming language tags only";
        TagStrategy strategy = new TagStrategy(5, 0.5, customInstructions);

        String aiResponse = """
            [{"tag": "java", "confidence": 0.95, "isExisting": false}]
            """;

        setupMockChatClient(aiResponse);

        // When
        videoTaggingService.generateTags(video, existingTags, strategy);

        // Then
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClient).prompt(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains(customInstructions);
    }

    @Test
    void generateTags_shouldIncludeExistingTagsInPrompt() {
        // Given
        VideoMetadata video = new VideoMetadata(
            "video123",
            "https://youtube.com/watch?v=video123",
            "Java Tutorial",
            "Description",
            Instant.now(),
            600
        );
        List<RaindropTag> existingTags = List.of(
            new RaindropTag("java"),
            new RaindropTag("programming")
        );
        TagStrategy strategy = new TagStrategy(5, 0.5, null);

        String aiResponse = """
            [{"tag": "java", "confidence": 0.95, "isExisting": true}]
            """;

        setupMockChatClient(aiResponse);

        // When
        videoTaggingService.generateTags(video, existingTags, strategy);

        // Then
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClient).prompt(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("java");
        assertThat(prompt).contains("programming");
    }

    @Test
    void generateTags_shouldIncludeVideoTitleAndDescriptionInPrompt() {
        // Given
        VideoMetadata video = new VideoMetadata(
            "video123",
            "https://youtube.com/watch?v=video123",
            "Advanced Spring Security Tutorial",
            "Learn OAuth2 and JWT authentication",
            Instant.now(),
            1200
        );
        List<RaindropTag> existingTags = List.of();
        TagStrategy strategy = new TagStrategy(5, 0.5, null);

        String aiResponse = """
            [{"tag": "security", "confidence": 0.95, "isExisting": false}]
            """;

        setupMockChatClient(aiResponse);

        // When
        videoTaggingService.generateTags(video, existingTags, strategy);

        // Then
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClient).prompt(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("Advanced Spring Security Tutorial");
        assertThat(prompt).contains("Learn OAuth2 and JWT authentication");
    }

    @Test
    void generateTags_shouldReturnEmptyListOnJsonParseError() {
        // Given
        VideoMetadata video = new VideoMetadata(
            "video123",
            "https://youtube.com/watch?v=video123",
            "Java Tutorial",
            "Description",
            Instant.now(),
            600
        );
        List<RaindropTag> existingTags = List.of();
        TagStrategy strategy = new TagStrategy(5, 0.5, null);

        String invalidJson = "This is not valid JSON";

        setupMockChatClient(invalidJson);

        // When
        List<TagWithConfidence> result = videoTaggingService.generateTags(video, existingTags, strategy);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void generateTags_withNullStrategy_shouldUseDefaults() {
        // Given
        VideoMetadata video = new VideoMetadata(
            "video123",
            "https://youtube.com/watch?v=video123",
            "Java Tutorial",
            "Description",
            Instant.now(),
            600
        );
        List<RaindropTag> existingTags = List.of();

        String aiResponse = """
            [
                {"tag": "java", "confidence": 0.95, "isExisting": false},
                {"tag": "tutorial", "confidence": 0.85, "isExisting": false}
            ]
            """;

        setupMockChatClient(aiResponse);

        // When
        List<TagWithConfidence> result = videoTaggingService.generateTags(video, existingTags, null);

        // Then
        assertThat(result).hasSize(2);
    }

    @Test
    void generateTags_withEmptyExistingTags_shouldWork() {
        // Given
        VideoMetadata video = new VideoMetadata(
            "video123",
            "https://youtube.com/watch?v=video123",
            "Python Tutorial",
            "Description",
            Instant.now(),
            600
        );
        List<RaindropTag> existingTags = List.of();
        TagStrategy strategy = new TagStrategy(5, 0.5, null);

        String aiResponse = """
            [{"tag": "python", "confidence": 0.95, "isExisting": false}]
            """;

        setupMockChatClient(aiResponse);

        // When
        List<TagWithConfidence> result = videoTaggingService.generateTags(video, existingTags, strategy);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).isExisting()).isFalse();
    }

    @Test
    void generateTags_shouldSortByConfidenceDescending() {
        // Given
        VideoMetadata video = new VideoMetadata(
            "video123",
            "https://youtube.com/watch?v=video123",
            "Java Tutorial",
            "Description",
            Instant.now(),
            600
        );
        List<RaindropTag> existingTags = List.of();
        TagStrategy strategy = new TagStrategy(10, 0.0, null);

        String aiResponse = """
            [
                {"tag": "low", "confidence": 0.50, "isExisting": false},
                {"tag": "high", "confidence": 0.95, "isExisting": false},
                {"tag": "medium", "confidence": 0.75, "isExisting": false}
            ]
            """;

        setupMockChatClient(aiResponse);

        // When
        List<TagWithConfidence> result = videoTaggingService.generateTags(video, existingTags, strategy);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).tag()).isEqualTo("high");
        assertThat(result.get(1).tag()).isEqualTo("medium");
        assertThat(result.get(2).tag()).isEqualTo("low");
    }

    @Test
    void shouldFilterBlockedTags() {
        // Given
        TagFilterProperties filterProperties = new TagFilterProperties();
        filterProperties.setBlockedTags("spam,promotional");

        when(chatClientBuilder.build()).thenReturn(chatClient);
        VideoTaggingService service = new VideoTaggingService(chatClientBuilder, objectMapper, filterProperties);

        VideoMetadata video = new VideoMetadata(
            "video123",
            "https://youtube.com/watch?v=video123",
            "Java Tutorial",
            "Description",
            Instant.now(),
            600
        );
        List<RaindropTag> existingTags = List.of();
        TagStrategy strategy = new TagStrategy(10, 0.0, null);

        // Mock AI response with blocked and allowed tags
        String aiResponse = """
            [
                {"tag": "tutorial", "confidence": 0.9, "isExisting": false},
                {"tag": "spam", "confidence": 0.8, "isExisting": false},
                {"tag": "programming", "confidence": 0.85, "isExisting": false},
                {"tag": "promotional", "confidence": 0.7, "isExisting": false}
            ]
            """;

        setupMockChatClient(aiResponse);

        // When
        List<TagWithConfidence> result = service.generateTags(video, existingTags, strategy);

        // Then - only non-blocked tags should remain
        assertThat(result)
            .hasSize(2)
            .extracting(TagWithConfidence::tag)
            .containsExactly("tutorial", "programming");
    }

    @Test
    void shouldAddBlocklistToPromptWhenConfigured() {
        // Setup
        TagFilterProperties filterProperties = new TagFilterProperties();
        filterProperties.setBlockedTags("spam,promotional");

        when(chatClientBuilder.build()).thenReturn(chatClient);
        VideoTaggingService service = new VideoTaggingService(chatClientBuilder, objectMapper, filterProperties);

        VideoMetadata video = new VideoMetadata(
            "video123",
            "https://youtube.com/watch?v=video123",
            "Java Tutorial",
            "Description",
            Instant.now(),
            600
        );
        List<RaindropTag> existingTags = List.of();
        TagStrategy strategy = new TagStrategy(5, 0.5, null);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

        String aiResponse = """
            [{"tag": "java", "confidence": 0.95, "isExisting": false}]
            """;

        setupMockChatClient(aiResponse);

        // Execute
        service.generateTags(video, existingTags, strategy);

        // Verify - prompt should contain blocklist instruction
        verify(chatClient).prompt(promptCaptor.capture());
        String capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt)
            .contains("Do not suggest any of these tags:")
            .contains("spam")
            .contains("promotional");
    }

    @Test
    void shouldNotAddBlocklistToPromptWhenEmpty() {
        // Setup
        TagFilterProperties filterProperties = new TagFilterProperties();
        filterProperties.setBlockedTags("");

        when(chatClientBuilder.build()).thenReturn(chatClient);
        VideoTaggingService service = new VideoTaggingService(chatClientBuilder, objectMapper, filterProperties);

        VideoMetadata video = new VideoMetadata(
            "video123",
            "https://youtube.com/watch?v=video123",
            "Java Tutorial",
            "Description",
            Instant.now(),
            600
        );
        List<RaindropTag> existingTags = List.of();
        TagStrategy strategy = new TagStrategy(5, 0.5, null);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

        String aiResponse = """
            [{"tag": "java", "confidence": 0.95, "isExisting": false}]
            """;

        setupMockChatClient(aiResponse);

        // Execute
        service.generateTags(video, existingTags, strategy);

        // Verify - prompt should NOT contain blocklist instruction
        verify(chatClient).prompt(promptCaptor.capture());
        String capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt).doesNotContain("Do not suggest any of these tags:");
    }

    @Test
    void shouldFilterCaseInsensitively() {
        TagFilterProperties filterProperties = new TagFilterProperties();
        filterProperties.setBlockedTags("spam");

        when(chatClientBuilder.build()).thenReturn(chatClient);
        VideoTaggingService service = new VideoTaggingService(chatClientBuilder, objectMapper, filterProperties);

        VideoMetadata video = new VideoMetadata(
            "video123",
            "https://youtube.com/watch?v=video123",
            "Java Tutorial",
            "Description",
            Instant.now(),
            600
        );
        List<RaindropTag> existingTags = List.of();
        TagStrategy strategy = new TagStrategy(10, 0.0, null);

        String aiResponse = """
            [
                {"tag": "Tutorial", "confidence": 0.9, "isExisting": false},
                {"tag": "SPAM", "confidence": 0.8, "isExisting": false},
                {"tag": "Spam", "confidence": 0.7, "isExisting": false}
            ]
            """;

        setupMockChatClient(aiResponse);

        // When
        List<TagWithConfidence> result = service.generateTags(video, existingTags, strategy);

        // Then
        assertThat(result)
            .hasSize(1)
            .extracting(TagWithConfidence::tag)
            .containsExactly("Tutorial");
    }

    @Test
    void shouldReturnAllTagsWhenBlocklistEmpty() {
        TagFilterProperties filterProperties = new TagFilterProperties();
        filterProperties.setBlockedTags("");

        when(chatClientBuilder.build()).thenReturn(chatClient);
        VideoTaggingService service = new VideoTaggingService(chatClientBuilder, objectMapper, filterProperties);

        VideoMetadata video = new VideoMetadata(
            "video123",
            "https://youtube.com/watch?v=video123",
            "Java Tutorial",
            "Description",
            Instant.now(),
            600
        );
        List<RaindropTag> existingTags = List.of();
        TagStrategy strategy = new TagStrategy(10, 0.0, null);

        String aiResponse = """
            [
                {"tag": "tutorial", "confidence": 0.9, "isExisting": false},
                {"tag": "spam", "confidence": 0.8, "isExisting": false}
            ]
            """;

        setupMockChatClient(aiResponse);

        // When
        List<TagWithConfidence> result = service.generateTags(video, existingTags, strategy);

        // Then
        assertThat(result)
            .hasSize(2)
            .extracting(TagWithConfidence::tag)
            .containsExactly("tutorial", "spam");
    }

    @Test
    void shouldReturnEmptyListWhenAllTagsBlocked() {
        TagFilterProperties filterProperties = new TagFilterProperties();
        filterProperties.setBlockedTags("tutorial,spam,promotional");

        when(chatClientBuilder.build()).thenReturn(chatClient);
        VideoTaggingService service = new VideoTaggingService(chatClientBuilder, objectMapper, filterProperties);

        VideoMetadata video = new VideoMetadata(
            "video123",
            "https://youtube.com/watch?v=video123",
            "Java Tutorial",
            "Description",
            Instant.now(),
            600
        );
        List<RaindropTag> existingTags = List.of();
        TagStrategy strategy = new TagStrategy(10, 0.0, null);

        String aiResponse = """
            [
                {"tag": "tutorial", "confidence": 0.9, "isExisting": false},
                {"tag": "spam", "confidence": 0.8, "isExisting": false},
                {"tag": "promotional", "confidence": 0.7, "isExisting": false}
            ]
            """;

        setupMockChatClient(aiResponse);

        // When
        List<TagWithConfidence> result = service.generateTags(video, existingTags, strategy);

        // Then
        assertThat(result).isEmpty();
    }

    private void setupMockChatClient(String responseContent) {
        AssistantMessage assistantMessage = new AssistantMessage(responseContent);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(callResponseSpec.chatResponse()).thenReturn(chatResponse);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(chatClient.prompt(anyString())).thenReturn(requestSpec);
    }
}
