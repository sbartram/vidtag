package org.bartram.vidtag.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.ValueInstantiationException;

class TagStrategyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSuggestConstantHasExpectedValues() {
        // Then
        assertEquals(5, TagStrategy.SUGGEST.maxTagsPerVideo());
        assertEquals(0.5, TagStrategy.SUGGEST.confidenceThreshold());
        assertNull(TagStrategy.SUGGEST.customInstructions());
    }

    @Test
    void testCustomStrategyCreation() {
        // Given
        Integer maxTags = 10;
        Double threshold = 0.8;
        String instructions = "Focus on technical topics";

        // When
        TagStrategy strategy = new TagStrategy(maxTags, threshold, instructions);

        // Then
        assertEquals(maxTags, strategy.maxTagsPerVideo());
        assertEquals(threshold, strategy.confidenceThreshold());
        assertEquals(instructions, strategy.customInstructions());
    }

    @Test
    void testFromStringWithSuggest() {
        // When
        TagStrategy result = TagStrategy.fromString("SUGGEST");

        // Then
        assertSame(TagStrategy.SUGGEST, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUGGEST", "suggest", "Suggest", "SuGgEsT"})
    void testFromStringIsCaseInsensitive(String input) {
        // When
        TagStrategy result = TagStrategy.fromString(input);

        // Then
        assertSame(TagStrategy.SUGGEST, result);
    }

    @Test
    void testFromStringWithUnknownValueThrowsException() {
        // When/Then
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> TagStrategy.fromString("UNKNOWN"));
        assertEquals("Unknown TagStrategy: UNKNOWN", exception.getMessage());
    }

    @Test
    void testJsonDeserializationFromString() throws Exception {
        // Given
        String json = "\"SUGGEST\"";

        // When
        TagStrategy result = objectMapper.readValue(json, TagStrategy.class);

        // Then
        assertSame(TagStrategy.SUGGEST, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"SUGGEST\"", "\"suggest\"", "\"Suggest\""})
    void testJsonDeserializationFromStringIsCaseInsensitive(String json) throws Exception {
        // When
        TagStrategy result = objectMapper.readValue(json, TagStrategy.class);

        // Then
        assertSame(TagStrategy.SUGGEST, result);
    }

    @Test
    void testJsonDeserializationFromObject() throws Exception {
        // Given
        String json = """
            {
                "maxTagsPerVideo": 10,
                "confidenceThreshold": 0.8,
                "customInstructions": "Focus on technical content"
            }
            """;

        // When
        TagStrategy result = objectMapper.readValue(json, TagStrategy.class);

        // Then
        assertEquals(10, result.maxTagsPerVideo());
        assertEquals(0.8, result.confidenceThreshold());
        assertEquals("Focus on technical content", result.customInstructions());
    }

    @Test
    void testJsonDeserializationFromObjectWithNullFields() throws Exception {
        // Given
        String json = """
            {
                "maxTagsPerVideo": 7,
                "confidenceThreshold": null,
                "customInstructions": null
            }
            """;

        // When
        TagStrategy result = objectMapper.readValue(json, TagStrategy.class);

        // Then
        assertEquals(7, result.maxTagsPerVideo());
        assertNull(result.confidenceThreshold());
        assertNull(result.customInstructions());
    }

    @Test
    void testJsonDeserializationFromUnknownStringThrowsException() {
        // Given
        String json = "\"INVALID_STRATEGY\"";

        // When/Then
        ValueInstantiationException exception =
                assertThrows(ValueInstantiationException.class, () -> objectMapper.readValue(json, TagStrategy.class));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals(
                "Unknown TagStrategy: INVALID_STRATEGY", exception.getCause().getMessage());
    }

    @Test
    void testJsonSerialization() throws Exception {
        // Given
        TagStrategy strategy = new TagStrategy(10, 0.75, "Custom instructions");

        // When
        String json = objectMapper.writeValueAsString(strategy);

        // Then
        assertTrue(json.contains("\"maxTagsPerVideo\":10"));
        assertTrue(json.contains("\"confidenceThreshold\":0.75"));
        assertTrue(json.contains("\"customInstructions\":\"Custom instructions\""));
    }

    @Test
    void testRecordEquality() {
        // Given
        TagStrategy strategy1 = new TagStrategy(5, 0.5, null);
        TagStrategy strategy2 = new TagStrategy(5, 0.5, null);

        // Then
        assertEquals(strategy1, strategy2);
        assertEquals(strategy1.hashCode(), strategy2.hashCode());
    }

    @Test
    void testRecordInequality() {
        // Given
        TagStrategy strategy1 = new TagStrategy(5, 0.5, null);
        TagStrategy strategy2 = new TagStrategy(10, 0.5, null);

        // Then
        assertNotEquals(strategy1, strategy2);
    }
}
