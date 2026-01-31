# Tag Blocklist Filter - Design Document

**Date:** 2026-01-30
**Status:** Design Complete - Ready for Implementation

---

## Overview

Add a configurable tag blocklist filter to prevent unwanted tags from being suggested by the AI service and saved to Raindrop.io. The filter works in two ways: instructing the AI to avoid certain tags AND filtering them out after generation as a backup.

---

## Requirements

### Functional Requirements
1. Users can configure a comma-separated list of blocked tags via property
2. AI is instructed to avoid suggesting blocked tags
3. Blocked tags are filtered from AI responses before saving to Raindrop
4. Tag matching is case-insensitive and exact (not substring)
5. Blocked tags are logged at DEBUG level for visibility
6. Filter is auto-enabled when property is set, disabled when empty

### Non-Functional Requirements
- No performance impact when blocklist is empty
- Graceful degradation - filtering failures don't break tagging
- Simple configuration - just a comma-separated string
- Clear logging for troubleshooting

---

## Architecture

### New Component: TagFilterProperties

Configuration properties class to manage the blocklist:

```java
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

    /**
     * Returns the blocked tags as a normalized set (lowercase, trimmed).
     * Returns empty set if blockedTags is null or blank.
     */
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

    /**
     * Returns true if filtering is enabled (blocklist is non-empty).
     */
    public boolean isFilterEnabled() {
        return !getBlockedTagsSet().isEmpty();
    }
}
```

### Modified Component: VideoTaggingService

Inject `TagFilterProperties` and add filtering logic:

1. **Constructor Injection**: Add `TagFilterProperties` parameter
2. **Prompt Modification**: When blocklist is configured, add to AI prompt
3. **Post-Generation Filtering**: Filter tags after AI response
4. **Logging**: Log blocked tags at DEBUG level

---

## Data Flow

### 1. AI Prompt Modification (Prevention)

When `TagFilterProperties.isFilterEnabled()` returns true, modify the AI prompt:

```java
private String buildPrompt(VideoMetadata video, List<RaindropTag> existingTags,
                          TagStrategy strategy) {
    StringBuilder prompt = new StringBuilder();
    // ... existing prompt construction ...

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

### 2. Post-Generation Filtering (Backup)

After parsing AI response, filter out blocked tags:

```java
public List<TagWithConfidence> generateTags(VideoMetadata video,
                                            List<RaindropTag> existingTags,
                                            TagStrategy tagStrategy) {
    // ... existing AI call and parsing ...
    List<TagWithConfidence> tags = parseAndSortTags(response, tagStrategy);

    // Apply blocklist filter if enabled
    if (tagFilterProperties.isFilterEnabled()) {
        tags = applyBlocklistFilter(tags);
    }

    return tags;
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
        return tags;  // Graceful degradation
    }
}
```

---

## Configuration

### application.yaml

```yaml
vidtag:
  tagging:
    blocked-tags: ""  # Comma-separated list of tags to block (empty = disabled)
```

### Environment Variable

```bash
VIDTAG_TAGGING_BLOCKED_TAGS="spam,promotional,clickbait,nsfw,explicit"
```

### Docker Compose

Add to `.env.example`:
```
# Tag Filtering (optional)
VIDTAG_TAGGING_BLOCKED_TAGS=
```

---

## Error Handling

### Edge Cases

1. **Null/empty blocklist**: Skip filtering entirely, return all tags
2. **All tags blocked**: Return empty list (valid scenario, orchestrator handles it)
3. **Invalid property format**: Log warning, treat as empty list
4. **Filtering exception**: Log warning, return unfiltered tags (graceful degradation)
5. **AI ignores blocklist**: Filtering catches it anyway (belt and suspenders)
6. **Runtime configuration changes**: Requires application restart (Spring Boot limitation)

### Logging Strategy

- `DEBUG`: Each individual blocked tag (can be verbose)
- `INFO`: When filtering is enabled: "Tag blocklist filter enabled with {count} blocked tags"
- `WARN`: Errors during filtering (with fallback to no filtering)

---

## Testing Strategy

### Unit Tests: TagFilterPropertiesTest

```java
@Test
void shouldReturnEmptySetWhenBlocked TagsIsEmpty()
void shouldParseSingleTag()
void shouldParseMultipleTags()
void shouldNormalizeToLowerCase()
void shouldTrimWhitespace()
void shouldFilterEmptyValues()
void shouldReturnTrueWhenFilterEnabled()
void shouldReturnFalseWhenFilterDisabled()
```

### Unit Tests: VideoTaggingServiceTest

```java
@Test
void shouldAddBlocklistToPromptWhenConfigured()
void shouldNotAddBlocklistToPromptWhenEmpty()
void shouldFilterBlockedTags()
void shouldFilterCaseInsensitively()
void shouldLogBlockedTags()
void shouldReturnAllTagsWhenBlocklistEmpty()
void shouldReturnEmptyListWhenAllTagsBlocked()
void shouldHandleFilteringException()
```

### Integration Test

Add to `VideoTaggingServiceIntegrationTest`:
```java
@Test
void shouldFilterBlockedTagsEndToEnd()
```

Test with real Claude API (if key available) to verify both prompt modification and filtering work together.

---

## Implementation Plan

### Files to Create

1. `src/main/java/org/bartram/vidtag/config/TagFilterProperties.java`
2. `src/test/java/org/bartram/vidtag/config/TagFilterPropertiesTest.java`

### Files to Modify

1. `src/main/java/org/bartram/vidtag/service/VideoTaggingService.java`
   - Add `TagFilterProperties` injection
   - Modify prompt building to include blocklist
   - Add `applyBlocklistFilter()` method
   - Add filtering after AI response parsing

2. `src/main/resources/application.yaml`
   - Add `vidtag.tagging.blocked-tags: ""`

3. `src/test/java/org/bartram/vidtag/service/VideoTaggingServiceTest.java`
   - Add filter-related tests
   - Mock `TagFilterProperties`

4. `.env.example`
   - Add `VIDTAG_TAGGING_BLOCKED_TAGS=`

5. `CLAUDE.md`
   - Document tag filtering configuration
   - Add example usage

---

## Documentation

### CLAUDE.md Update

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

---

## Future Enhancements (Out of Scope)

These are explicitly NOT included in this design:

- ❌ Substring matching (too aggressive, could block legitimate tags)
- ❌ Regular expression support (YAGNI - simple list is sufficient)
- ❌ Per-video or per-request blocklists (complexity not justified)
- ❌ Allowlist/whitelist (opposite approach, different use case)
- ❌ Tag replacement/aliasing (different feature)
- ❌ Runtime configuration updates without restart (Spring Boot limitation)
- ❌ Separate file for blocklist (property is sufficient for reasonable list sizes)

If any of these become needed, they can be added in future iterations.

---

## Success Criteria

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

## Next Steps

1. **Implementation**: Use `superpowers:writing-plans` to create detailed implementation plan
2. **Testing**: Follow TDD approach - tests first, then implementation
3. **Documentation**: Update CLAUDE.md and .env.example
4. **Validation**: Test with real API to verify both prevention and filtering work

---

**Design Status**: ✅ Ready for Implementation
