package org.bartram.vidtag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.bartram.vidtag.config.TagFilterProperties;
import org.bartram.vidtag.exception.ExternalServiceException;
import org.bartram.vidtag.model.RaindropTag;
import org.bartram.vidtag.model.TagStrategy;
import org.bartram.vidtag.model.TagWithConfidence;
import org.bartram.vidtag.model.VideoMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for AI-powered video tagging using Spring AI with Claude.
 */
@Service
public class VideoTaggingService {

    private static final Logger log = LoggerFactory.getLogger(VideoTaggingService.class);
    private static final Pattern MARKDOWN_CODE_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*\\n?(.+?)\\n?```", Pattern.DOTALL);
    private static final int DEFAULT_MAX_TAGS = 10;
    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.0;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final TagFilterProperties tagFilterProperties;

    public VideoTaggingService(ChatClient.Builder chatClientBuilder,
                              ObjectMapper objectMapper,
                              TagFilterProperties tagFilterProperties) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.tagFilterProperties = tagFilterProperties;
    }

    /**
     * Generates tags for a video using AI analysis.
     *
     * @param video the video metadata to analyze
     * @param existingTags list of existing tags to prefer
     * @param tagStrategy strategy configuration for tag generation
     * @return list of generated tags with confidence scores, sorted by confidence descending
     */
    @CircuitBreaker(name = "claude", fallbackMethod = "generateTagsFallback")
    public List<TagWithConfidence> generateTags(VideoMetadata video, List<RaindropTag> existingTags, TagStrategy tagStrategy) {
        log.debug("Generating tags for video: {}", video.videoId());

        String prompt = buildPrompt(video, existingTags, tagStrategy);
        log.trace("Generated prompt: {}", prompt);

        String response = chatClient.prompt(prompt)
            .call()
            .chatResponse()
            .getResult()
            .getOutput()
            .getText();

        log.debug("AI response received for video {}", video.videoId());

        List<TagWithConfidence> tags = parseResponse(response);

        // Apply blocklist filter if enabled
        if (tagFilterProperties.isFilterEnabled()) {
            tags = applyBlocklistFilter(tags);
        }

        // Apply strategy filters
        int maxTags = (tagStrategy != null && tagStrategy.maxTagsPerVideo() != null)
            ? tagStrategy.maxTagsPerVideo()
            : DEFAULT_MAX_TAGS;

        double confidenceThreshold = (tagStrategy != null && tagStrategy.confidenceThreshold() != null)
            ? tagStrategy.confidenceThreshold()
            : DEFAULT_CONFIDENCE_THRESHOLD;

        List<TagWithConfidence> filteredTags = tags.stream()
            .filter(tag -> tag.confidence() >= confidenceThreshold)
            .sorted(Comparator.comparing(TagWithConfidence::confidence).reversed())
            .limit(maxTags)
            .collect(Collectors.toList());

        log.info("Generated {} tags for video {} (filtered from {})",
            filteredTags.size(), video.videoId(), tags.size());

        return filteredTags;
    }

    /**
     * Fallback method when Claude API circuit breaker is open.
     *
     * @param video the video metadata that was being processed
     * @param existingTags the existing tags
     * @param tagStrategy the tag strategy
     * @param throwable the exception that triggered the fallback
     * @throws RuntimeException always thrown with descriptive message
     */
    private List<TagWithConfidence> generateTagsFallback(VideoMetadata video, List<RaindropTag> existingTags,
                                                          TagStrategy tagStrategy, Throwable throwable) {
        log.error("Claude API circuit breaker fallback triggered for video {}: {}",
            video.videoId(), throwable.getMessage());
        throw new ExternalServiceException("claude", "AI tagging service is currently unavailable", throwable);
    }

    private String buildPrompt(VideoMetadata video, List<RaindropTag> existingTags, TagStrategy tagStrategy) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a video tagging assistant. Analyze the following video and generate relevant tags.\n\n");

        prompt.append("Video Title: ").append(video.title()).append("\n");
        if (video.description() != null && !video.description().isEmpty()) {
            prompt.append("Video Description: ").append(video.description()).append("\n");
        }
        prompt.append("\n");

        if (existingTags != null && !existingTags.isEmpty()) {
            prompt.append("Existing tags to prefer (use these when appropriate, mark isExisting=true):\n");
            existingTags.forEach(tag -> prompt.append("- ").append(tag.name()).append("\n"));
            prompt.append("\n");
        }

        prompt.append("Tag Strategy Rules:\n");
        if (tagStrategy != null) {
            if (tagStrategy.maxTagsPerVideo() != null) {
                prompt.append("- Maximum tags: ").append(tagStrategy.maxTagsPerVideo()).append("\n");
            }
            if (tagStrategy.confidenceThreshold() != null) {
                prompt.append("- Minimum confidence threshold: ").append(tagStrategy.confidenceThreshold()).append("\n");
            }
            if (tagStrategy.customInstructions() != null && !tagStrategy.customInstructions().isEmpty()) {
                prompt.append("- Custom instructions: ").append(tagStrategy.customInstructions()).append("\n");
            }
        }
        prompt.append("- Prefer existing tags when they are relevant\n");
        prompt.append("- Generate lowercase, hyphenated tags (e.g., 'spring-boot', 'machine-learning')\n");
        prompt.append("\n");

        // Add blocklist instruction if filtering is enabled
        if (tagFilterProperties.isFilterEnabled()) {
            Set<String> blockedTags = tagFilterProperties.getBlockedTagsSet();
            String blockedTagsList = String.join(", ", blockedTags);
            prompt.append("IMPORTANT: Do not suggest any of these tags: ")
                  .append(blockedTagsList)
                  .append("\nThese tags are explicitly blocked and should be avoided.\n\n");
        }

        prompt.append("Respond with ONLY a JSON array (no markdown, no explanation) in this format:\n");
        prompt.append("[{\"tag\":\"tag-name\",\"confidence\":0.0-1.0,\"isExisting\":true/false}]\n\n");
        prompt.append("Confidence should reflect how relevant the tag is to the video content.");

        return prompt.toString();
    }

    private List<TagWithConfidence> parseResponse(String response) {
        if (response == null || response.isBlank()) {
            log.warn("Received empty response from AI");
            return Collections.emptyList();
        }

        String jsonContent = extractJsonFromMarkdown(response);

        try {
            List<TagResponse> tagResponses = objectMapper.readValue(
                jsonContent,
                new TypeReference<List<TagResponse>>() {}
            );

            return tagResponses.stream()
                .map(tr -> new TagWithConfidence(tr.tag(), tr.confidence(), tr.isExisting()))
                .collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            log.error("Failed to parse AI response as JSON: {}", e.getMessage());
            log.debug("Raw response: {}", response);
            return Collections.emptyList();
        }
    }

    private String extractJsonFromMarkdown(String response) {
        Matcher matcher = MARKDOWN_CODE_BLOCK_PATTERN.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return response.trim();
    }

    private List<TagWithConfidence> applyBlocklistFilter(List<TagWithConfidence> tags) {
        try {
            Set<String> blockedTags = tagFilterProperties.getBlockedTagsSet();

            return tags.stream()
                .filter(tagWithConfidence -> {
                    String normalizedTag = tagWithConfidence.tag().toLowerCase();
                    boolean isBlocked = blockedTags.contains(normalizedTag);

                    if (isBlocked) {
                        log.debug("Blocked tag: '{}' (matched blocklist)",
                            tagWithConfidence.tag());
                    }

                    return !isBlocked;
                })
                .toList();

        } catch (Exception e) {
            log.warn("Failed to apply tag blocklist filter, continuing without filtering: {}",
                e.getMessage(), e);
            return tags;
        }
    }

    /**
     * Internal record for parsing AI response JSON.
     */
    private record TagResponse(String tag, Double confidence, Boolean isExisting) {}
}
