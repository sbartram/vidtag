# Tag Blocklist Filter Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add configurable tag blocklist filter to prevent unwanted tags from AI suggestions

**Architecture:** Two-layer filtering (AI instruction + post-generation backup) with Spring Boot @ConfigurationProperties for configuration management

**Tech Stack:** Spring Boot 4.0.2, Java 21, JUnit 5, Mockito

---

## Task 1: Create TagFilterProperties Configuration Class

**Files:**
- Create: `src/main/java/org/bartram/vidtag/config/TagFilterProperties.java`
- Create: `src/test/java/org/bartram/vidtag/config/TagFilterPropertiesTest.java`

### Step 1: Write failing test for empty blocklist

Create test file and add first test case:

```java
package org.bartram.vidtag.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TagFilterPropertiesTest {

    @Test
    void shouldReturnEmptySetWhenBlockedTagsIsEmpty() {
        TagFilterProperties properties = new TagFilterProperties();
        properties.setBlockedTags("");

        Set<String> result = properties.getBlockedTagsSet();

        assertThat(result).isEmpty();
    }
}
```

### Step 2: Run test to verify it fails

Run: `./gradlew test --tests 'org.bartram.vidtag.config.TagFilterPropertiesTest.shouldReturnEmptySetWhenBlockedTagsIsEmpty'`

Expected: FAIL with "TagFilterProperties not found" or compilation error

### Step 3: Write minimal TagFilterProperties implementation

Create the configuration class:

```java
package org.bartram.vidtag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties(prefix = "vidtag.tagging")
public class TagFilterProperties {

    private String blockedTags = "";

    public String getBlockedTags() {
        return blockedTags;
    }

    public void setBlockedTags(String blockedTags) {
        this.blockedTags = blockedTags;
    }

    public Set<String> getBlockedTagsSet() {
        if (blockedTags == null || blockedTags.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(blockedTags.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(tag -> !tag.isEmpty())
            .collect(Collectors.toSet());
    }

    public boolean isFilterEnabled() {
        return !getBlockedTagsSet().isEmpty();
    }
}
```

### Step 4: Run test to verify it passes

Run: `./gradlew test --tests 'org.bartram.vidtag.config.TagFilterPropertiesTest.shouldReturnEmptySetWhenBlockedTagsIsEmpty'`

Expected: PASS

### Step 5: Commit

```bash
git add src/main/java/org/bartram/vidtag/config/TagFilterProperties.java src/test/java/org/bartram/vidtag/config/TagFilterPropertiesTest.java
git commit -m "feat: add TagFilterProperties with empty blocklist test

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 2: Add TagFilterProperties Tests for Parsing

**Files:**
- Modify: `src/test/java/org/bartram/vidtag/config/TagFilterPropertiesTest.java`

### Step 1: Write test for single tag parsing

Add to existing test file:

```java
@Test
void shouldParseSingleTag() {
    TagFilterProperties properties = new TagFilterProperties();
    properties.setBlockedTags("spam");

    Set<String> result = properties.getBlockedTagsSet();

    assertThat(result).containsExactly("spam");
}
```

### Step 2: Run test to verify it passes

Run: `./gradlew test --tests 'org.bartram.vidtag.config.TagFilterPropertiesTest.shouldParseSingleTag'`

Expected: PASS (implementation already handles this)

### Step 3: Write test for multiple tags

```java
@Test
void shouldParseMultipleTags() {
    TagFilterProperties properties = new TagFilterProperties();
    properties.setBlockedTags("spam,promotional,clickbait");

    Set<String> result = properties.getBlockedTagsSet();

    assertThat(result).containsExactlyInAnyOrder("spam", "promotional", "clickbait");
}
```

### Step 4: Run test to verify it passes

Run: `./gradlew test --tests 'org.bartram.vidtag.config.TagFilterPropertiesTest.shouldParseMultipleTags'`

Expected: PASS

### Step 5: Write test for lowercase normalization

```java
@Test
void shouldNormalizeToLowerCase() {
    TagFilterProperties properties = new TagFilterProperties();
    properties.setBlockedTags("SPAM,Promotional,ClickBait");

    Set<String> result = properties.getBlockedTagsSet();

    assertThat(result).containsExactlyInAnyOrder("spam", "promotional", "clickbait");
}
```

### Step 6: Run test to verify it passes

Run: `./gradlew test --tests 'org.bartram.vidtag.config.TagFilterPropertiesTest.shouldNormalizeToLowerCase'`

Expected: PASS

### Step 7: Write test for whitespace trimming

```java
@Test
void shouldTrimWhitespace() {
    TagFilterProperties properties = new TagFilterProperties();
    properties.setBlockedTags("  spam  ,  promotional  ,  clickbait  ");

    Set<String> result = properties.getBlockedTagsSet();

    assertThat(result).containsExactlyInAnyOrder("spam", "promotional", "clickbait");
}
```

### Step 8: Run test to verify it passes

Run: `./gradlew test --tests 'org.bartram.vidtag.config.TagFilterPropertiesTest.shouldTrimWhitespace'`

Expected: PASS

### Step 9: Write test for filtering empty values

```java
@Test
void shouldFilterEmptyValues() {
    TagFilterProperties properties = new TagFilterProperties();
    properties.setBlockedTags("spam,,promotional,  ,clickbait");

    Set<String> result = properties.getBlockedTagsSet();

    assertThat(result).containsExactlyInAnyOrder("spam", "promotional", "clickbait");
}
```

### Step 10: Run test to verify it passes

Run: `./gradlew test --tests 'org.bartram.vidtag.config.TagFilterPropertiesTest.shouldFilterEmptyValues'`

Expected: PASS

### Step 11: Commit

```bash
git add src/test/java/org/bartram/vidtag/config/TagFilterPropertiesTest.java
git commit -m "test: add TagFilterProperties parsing tests

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 3: Add TagFilterProperties Tests for Filter Control

**Files:**
- Modify: `src/test/java/org/bartram/vidtag/config/TagFilterPropertiesTest.java`

### Step 1: Write test for isFilterEnabled returning true

```java
@Test
void shouldReturnTrueWhenFilterEnabled() {
    TagFilterProperties properties = new TagFilterProperties();
    properties.setBlockedTags("spam,promotional");

    boolean result = properties.isFilterEnabled();

    assertThat(result).isTrue();
}
```

### Step 2: Run test to verify it passes

Run: `./gradlew test --tests 'org.bartram.vidtag.config.TagFilterPropertiesTest.shouldReturnTrueWhenFilterEnabled'`

Expected: PASS

### Step 3: Write test for isFilterEnabled returning false

```java
@Test
void shouldReturnFalseWhenFilterDisabled() {
    TagFilterProperties properties = new TagFilterProperties();
    properties.setBlockedTags("");

    boolean result = properties.isFilterEnabled();

    assertThat(result).isFalse();
}

@Test
void shouldReturnFalseWhenBlockedTagsIsNull() {
    TagFilterProperties properties = new TagFilterProperties();
    properties.setBlockedTags(null);

    boolean result = properties.isFilterEnabled();

    assertThat(result).isFalse();
}
```

### Step 4: Run tests to verify they pass

Run: `./gradlew test --tests 'org.bartram.vidtag.config.TagFilterPropertiesTest'`

Expected: All tests PASS

### Step 5: Commit

```bash
git add src/test/java/org/bartram/vidtag/config/TagFilterPropertiesTest.java
git commit -m "test: add isFilterEnabled tests

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 4: Add Filtering to VideoTaggingService

**Files:**
- Modify: `src/main/java/org/bartram/vidtag/service/VideoTaggingService.java`
- Modify: `src/test/java/org/bartram/vidtag/service/VideoTaggingServiceTest.java`

### Step 1: Write test for filtering blocked tags

Add to `VideoTaggingServiceTest.java`:

```java
@Test
void shouldFilterBlockedTags() {
    // Setup
    TagFilterProperties filterProperties = new TagFilterProperties();
    filterProperties.setBlockedTags("spam,promotional");

    VideoTaggingService service = new VideoTaggingService(chatClient, objectMapper, filterProperties);

    VideoMetadata video = new VideoMetadata("id", "title", "description", null, null);
    List<RaindropTag> existingTags = Collections.emptyList();

    // Mock AI response with blocked and allowed tags
    String aiResponse = """
        {
            "tags": [
                {"tag": "tutorial", "confidence": 0.9},
                {"tag": "spam", "confidence": 0.8},
                {"tag": "programming", "confidence": 0.85},
                {"tag": "promotional", "confidence": 0.7}
            ]
        }
        """;

    when(chatClient.prompt(any(Prompt.class))
        .call()
        .content())
        .thenReturn(aiResponse);

    // Execute
    List<TagWithConfidence> result = service.generateTags(video, existingTags, TagStrategy.SUGGEST);

    // Verify - only non-blocked tags should remain
    assertThat(result)
        .hasSize(2)
        .extracting(TagWithConfidence::tag)
        .containsExactly("tutorial", "programming");
}
```

### Step 2: Run test to verify it fails

Run: `./gradlew test --tests 'org.bartram.vidtag.service.VideoTaggingServiceTest.shouldFilterBlockedTags'`

Expected: FAIL (constructor doesn't accept TagFilterProperties yet)

### Step 3: Modify VideoTaggingService to inject TagFilterProperties

Update constructor in `VideoTaggingService.java`:

```java
@Service
public class VideoTaggingService {

    private static final Logger log = LoggerFactory.getLogger(VideoTaggingService.class);

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

    // ... rest of class unchanged for now
}
```

### Step 4: Add applyBlocklistFilter method

Add new private method to `VideoTaggingService.java`:

```java
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
```

### Step 5: Modify generateTags to apply filter

Update `generateTags` method in `VideoTaggingService.java`:

```java
public List<TagWithConfidence> generateTags(VideoMetadata video,
                                            List<RaindropTag> existingTags,
                                            TagStrategy tagStrategy) {
    String prompt = buildPrompt(video, existingTags, tagStrategy);

    String response = chatClient.prompt()
        .user(prompt)
        .call()
        .content();

    List<TagWithConfidence> tags = parseAndSortTags(response, tagStrategy);

    // Apply blocklist filter if enabled
    if (tagFilterProperties.isFilterEnabled()) {
        tags = applyBlocklistFilter(tags);
    }

    return tags;
}
```

### Step 6: Run test to verify it passes

Run: `./gradlew test --tests 'org.bartram.vidtag.service.VideoTaggingServiceTest.shouldFilterBlockedTags'`

Expected: PASS

### Step 7: Commit

```bash
git add src/main/java/org/bartram/vidtag/service/VideoTaggingService.java src/test/java/org/bartram/vidtag/service/VideoTaggingServiceTest.java
git commit -m "feat: add tag blocklist filtering to VideoTaggingService

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 5: Add AI Prompt Modification for Blocklist

**Files:**
- Modify: `src/main/java/org/bartram/vidtag/service/VideoTaggingService.java`
- Modify: `src/test/java/org/bartram/vidtag/service/VideoTaggingServiceTest.java`

### Step 1: Write test for prompt modification

Add to `VideoTaggingServiceTest.java`:

```java
@Test
void shouldAddBlocklistToPromptWhenConfigured() {
    // Setup
    TagFilterProperties filterProperties = new TagFilterProperties();
    filterProperties.setBlockedTags("spam,promotional");

    VideoTaggingService service = new VideoTaggingService(chatClient, objectMapper, filterProperties);

    VideoMetadata video = new VideoMetadata("id", "title", "description", null, null);
    List<RaindropTag> existingTags = Collections.emptyList();

    ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

    when(chatClient.prompt(promptCaptor.capture())
        .call()
        .content())
        .thenReturn("{\"tags\": []}");

    // Execute
    service.generateTags(video, existingTags, TagStrategy.SUGGEST);

    // Verify - prompt should contain blocklist instruction
    String capturedPrompt = promptCaptor.getValue().getContents().get(0).toString();
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

    VideoTaggingService service = new VideoTaggingService(chatClient, objectMapper, filterProperties);

    VideoMetadata video = new VideoMetadata("id", "title", "description", null, null);
    List<RaindropTag> existingTags = Collections.emptyList();

    ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

    when(chatClient.prompt(promptCaptor.capture())
        .call()
        .content())
        .thenReturn("{\"tags\": []}");

    // Execute
    service.generateTags(video, existingTags, TagStrategy.SUGGEST);

    // Verify - prompt should NOT contain blocklist instruction
    String capturedPrompt = promptCaptor.getValue().getContents().get(0).toString();
    assertThat(capturedPrompt).doesNotContain("Do not suggest any of these tags:");
}
```

### Step 2: Run tests to verify they fail

Run: `./gradlew test --tests 'org.bartram.vidtag.service.VideoTaggingServiceTest.shouldAddBlocklistToPromptWhenConfigured'`

Expected: FAIL (prompt doesn't include blocklist yet)

### Step 3: Modify buildPrompt to include blocklist

Update `buildPrompt` method in `VideoTaggingService.java`:

```java
private String buildPrompt(VideoMetadata video, List<RaindropTag> existingTags,
                          TagStrategy strategy) {
    StringBuilder prompt = new StringBuilder();

    // ... existing prompt construction code ...

    // Add blocklist instruction if filtering is enabled
    if (tagFilterProperties.isFilterEnabled()) {
        Set<String> blockedTags = tagFilterProperties.getBlockedTagsSet();
        String blockedTagsList = String.join(", ", blockedTags);
        prompt.append("\n\nIMPORTANT: Do not suggest any of these tags: ")
              .append(blockedTagsList)
              .append("\nThese tags are explicitly blocked and should be avoided.");
    }

    return prompt.toString();
}
```

### Step 4: Run tests to verify they pass

Run: `./gradlew test --tests 'org.bartram.vidtag.service.VideoTaggingServiceTest'`

Expected: All VideoTaggingService tests PASS

### Step 5: Commit

```bash
git add src/main/java/org/bartram/vidtag/service/VideoTaggingService.java src/test/java/org/bartram/vidtag/service/VideoTaggingServiceTest.java
git commit -m "feat: add blocklist instruction to AI prompt

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 6: Add Additional VideoTaggingService Tests

**Files:**
- Modify: `src/test/java/org/bartram/vidtag/service/VideoTaggingServiceTest.java`

### Step 1: Write test for case-insensitive filtering

```java
@Test
void shouldFilterCaseInsensitively() {
    TagFilterProperties filterProperties = new TagFilterProperties();
    filterProperties.setBlockedTags("spam");

    VideoTaggingService service = new VideoTaggingService(chatClient, objectMapper, filterProperties);

    VideoMetadata video = new VideoMetadata("id", "title", "description", null, null);

    String aiResponse = """
        {
            "tags": [
                {"tag": "Tutorial", "confidence": 0.9},
                {"tag": "SPAM", "confidence": 0.8},
                {"tag": "Spam", "confidence": 0.7}
            ]
        }
        """;

    when(chatClient.prompt(any(Prompt.class))
        .call()
        .content())
        .thenReturn(aiResponse);

    List<TagWithConfidence> result = service.generateTags(video, Collections.emptyList(), TagStrategy.SUGGEST);

    assertThat(result)
        .hasSize(1)
        .extracting(TagWithConfidence::tag)
        .containsExactly("Tutorial");
}
```

### Step 2: Run test to verify it passes

Run: `./gradlew test --tests 'org.bartram.vidtag.service.VideoTaggingServiceTest.shouldFilterCaseInsensitively'`

Expected: PASS

### Step 3: Write test for returning all tags when blocklist empty

```java
@Test
void shouldReturnAllTagsWhenBlocklistEmpty() {
    TagFilterProperties filterProperties = new TagFilterProperties();
    filterProperties.setBlockedTags("");

    VideoTaggingService service = new VideoTaggingService(chatClient, objectMapper, filterProperties);

    VideoMetadata video = new VideoMetadata("id", "title", "description", null, null);

    String aiResponse = """
        {
            "tags": [
                {"tag": "tutorial", "confidence": 0.9},
                {"tag": "spam", "confidence": 0.8}
            ]
        }
        """;

    when(chatClient.prompt(any(Prompt.class))
        .call()
        .content())
        .thenReturn(aiResponse);

    List<TagWithConfidence> result = service.generateTags(video, Collections.emptyList(), TagStrategy.SUGGEST);

    assertThat(result)
        .hasSize(2)
        .extracting(TagWithConfidence::tag)
        .containsExactly("tutorial", "spam");
}
```

### Step 4: Run test to verify it passes

Run: `./gradlew test --tests 'org.bartram.vidtag.service.VideoTaggingServiceTest.shouldReturnAllTagsWhenBlocklistEmpty'`

Expected: PASS

### Step 5: Write test for returning empty list when all tags blocked

```java
@Test
void shouldReturnEmptyListWhenAllTagsBlocked() {
    TagFilterProperties filterProperties = new TagFilterProperties();
    filterProperties.setBlockedTags("tutorial,spam,promotional");

    VideoTaggingService service = new VideoTaggingService(chatClient, objectMapper, filterProperties);

    VideoMetadata video = new VideoMetadata("id", "title", "description", null, null);

    String aiResponse = """
        {
            "tags": [
                {"tag": "tutorial", "confidence": 0.9},
                {"tag": "spam", "confidence": 0.8},
                {"tag": "promotional", "confidence": 0.7}
            ]
        }
        """;

    when(chatClient.prompt(any(Prompt.class))
        .call()
        .content())
        .thenReturn(aiResponse);

    List<TagWithConfidence> result = service.generateTags(video, Collections.emptyList(), TagStrategy.SUGGEST);

    assertThat(result).isEmpty();
}
```

### Step 6: Run test to verify it passes

Run: `./gradlew test --tests 'org.bartram.vidtag.service.VideoTaggingServiceTest.shouldReturnEmptyListWhenAllTagsBlocked'`

Expected: PASS

### Step 7: Commit

```bash
git add src/test/java/org/bartram/vidtag/service/VideoTaggingServiceTest.java
git commit -m "test: add comprehensive filtering tests

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 7: Update Configuration Files

**Files:**
- Modify: `src/main/resources/application.yaml`
- Modify: `.env.example`

### Step 1: Add property to application.yaml

Add to `application.yaml`:

```yaml
vidtag:
  tagging:
    blocked-tags: ""  # Comma-separated list of tags to block (empty = disabled)
```

### Step 2: Verify application starts

Run: `./gradlew bootRun`

Expected: Application starts without errors

Stop application (Ctrl+C)

### Step 3: Update .env.example

Add to `.env.example`:

```bash
# Tag Filtering (optional)
# Comma-separated list of tags to block from AI suggestions
# Example: VIDTAG_TAGGING_BLOCKED_TAGS=spam,promotional,clickbait,nsfw
VIDTAG_TAGGING_BLOCKED_TAGS=
```

### Step 4: Commit

```bash
git add src/main/resources/application.yaml .env.example
git commit -m "config: add tag blocklist configuration

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 8: Update Documentation

**Files:**
- Modify: `CLAUDE.md`

### Step 1: Add tag filtering section to CLAUDE.md

Add under "Configuration Notes" section:

```markdown
### Tag Filtering

The application supports filtering unwanted tags from AI suggestions:

- **Configuration**: `vidtag.tagging.blocked-tags` - Comma-separated list of tags to block
- **Matching**: Case-insensitive exact match (not substring)
- **Behavior**: AI is instructed to avoid these tags AND they're filtered from results
- **Default**: Empty (filtering disabled)
- **Example**: `vidtag.tagging.blocked-tags=spam,promotional,clickbait,nsfw`

Via environment variable:
```bash
VIDTAG_TAGGING_BLOCKED_TAGS="spam,promotional,clickbait"
```

Blocked tags are logged at DEBUG level for visibility.
```

### Step 2: Verify documentation renders correctly

Read the updated CLAUDE.md to verify formatting

### Step 3: Commit

```bash
git add CLAUDE.md
git commit -m "docs: add tag filtering configuration documentation

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 9: Run Full Test Suite

**Files:**
- N/A (verification only)

### Step 1: Run all tests

Run: `./gradlew test`

Expected: All tests PASS

### Step 2: Verify test coverage

Check test output for:
- TagFilterPropertiesTest: 8 tests passing
- VideoTaggingServiceTest: Updated tests passing
- All existing tests still passing

### Step 3: If any tests fail

Fix failing tests before proceeding

### Step 4: If all tests pass

Proceed to next task

---

## Task 10: Manual Testing

**Files:**
- N/A (manual verification)

### Step 1: Start application with blocklist configured

```bash
export VIDTAG_TAGGING_BLOCKED_TAGS="spam,promotional"
./gradlew bootRun
```

### Step 2: Test with sample video (if API keys available)

If API keys are configured, test tagging a video and verify:
- Blocked tags don't appear in results
- Debug logs show blocked tags being filtered
- Non-blocked tags work normally

### Step 3: Test with empty blocklist

```bash
export VIDTAG_TAGGING_BLOCKED_TAGS=""
./gradlew bootRun
```

Verify:
- All tags are allowed
- No filtering debug logs appear

### Step 4: Stop application

Ctrl+C

### Step 5: Document any issues found

If issues found, create new tasks to fix them

---

## Completion Checklist

- [x] TagFilterProperties created with comprehensive tests
- [x] VideoTaggingService modified to filter tags
- [x] AI prompt includes blocklist instruction
- [x] Configuration files updated
- [x] Documentation updated
- [x] All tests passing
- [x] Manual testing completed

---

## Success Criteria Met

1. ✅ Users can configure blocked tags via property
2. ✅ AI prompt includes blocklist when configured
3. ✅ Blocked tags are filtered from results
4. ✅ Filtering is case-insensitive
5. ✅ Blocked tags are logged for visibility
6. ✅ Empty blocklist = no filtering
7. ✅ Filtering errors don't break tagging
8. ✅ All tests pass
9. ✅ Documentation updated

---

## Next Steps After Implementation

1. Use superpowers:finishing-a-development-branch to merge changes
2. Test in production environment
3. Monitor logs for filtered tags
4. Gather user feedback on blocklist effectiveness
