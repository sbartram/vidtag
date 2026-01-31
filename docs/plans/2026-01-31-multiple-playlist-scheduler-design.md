# Multiple Playlist Scheduler Design

**Date:** 2026-01-31
**Status:** Approved
**Breaking Change:** Yes - renames `playlist-id` to `playlist-ids`

## Overview

Change the scheduled playlist processor from handling a single playlist ID to supporting a comma-separated list of playlist IDs. Playlists will be processed sequentially with individual error handling.

## Requirements

- Support multiple playlist IDs via comma-separated configuration
- Process playlists sequentially (one at a time)
- Log and continue if one playlist fails (don't stop entire job)
- Handle whitespace gracefully (trim each ID)
- Skip empty/blank IDs after splitting
- Maintain clear progress logging

## Design Decisions

### 1. Configuration Changes

**SchedulerProperties.java:**
- Rename `playlistId` → `playlistIds` (keep as String, not List)
- Update getter/setter to `getPlaylistIds()` / `setPlaylistIds()`
- Keep deprecated `playlistName` field unchanged
- Update JavaDoc to document comma-separated format

**Why String instead of List<String>?**
- Gives us control over parsing (trimming, empty handling)
- Matches existing pattern (`blocked-tags` uses same approach)
- Spring Boot could auto-parse to List, but manual parsing is clearer

**application.yaml:**
```yaml
vidtag:
  scheduler:
    enabled: false
    fixed-delay-hours: 1
    playlist-ids: ${VIDTAG_YT_PLAYLIST_IDS:}  # Comma-separated playlist IDs
```

**Example usage:**
```yaml
playlist-ids: PLxxx...,PLyyy...,PLzzz...
```

**Environment variable:**
```bash
VIDTAG_YT_PLAYLIST_IDS="PLxxx...,PLyyy...,PLzzz..."
```

### 2. Scheduler Implementation

**PlaylistProcessingScheduler.java - processTagPlaylist() method:**

1. **Validate configuration**
   - Check if `playlistIds` is blank
   - Log error and return if empty

2. **Parse playlist IDs**
   - Split by comma: `playlistIds.split(",")`
   - Trim whitespace: `Arrays.stream().map(String::trim)`
   - Filter blanks: `.filter(s -> !s.isBlank())`
   - Collect to list

3. **Process sequentially**
   - Loop through each playlist ID
   - Track success/failure counts
   - Log progress: `"Processing playlist 1 of 3: PLxxx..."`
   - Create TagPlaylistRequest (same as current implementation)
   - Call `orchestrator.processPlaylist()`
   - Catch exceptions per playlist, log error, continue to next

4. **Summary logging**
   - After loop completes: `"Completed scheduled processing: 3 total, 2 succeeded, 1 failed"`

**Error handling strategy:**
- Each playlist wrapped in try-catch
- Exceptions logged with playlist ID context
- Failure doesn't stop remaining playlists
- Aligns with existing pattern (line 84: "Don't rethrow")

**Parsing example:**
```java
List<String> playlistIds = Arrays.stream(schedulerProperties.getPlaylistIds().split(","))
    .map(String::trim)
    .filter(s -> !s.isBlank())
    .toList();
```

### 3. Testing Strategy

**SchedulerPropertiesTest.java:**
- Update to use `playlistIds` field name
- Verify property binding still works

**PlaylistProcessingSchedulerTest.java - new tests:**

| Test Case | Purpose |
|-----------|---------|
| Single playlist | Verify backwards compatibility (one ID still works) |
| Multiple playlists | Verify sequential processing (orchestrator called N times) |
| Mixed success/failure | One fails, others succeed (verify error doesn't stop processing) |
| Whitespace handling | `"PLxxx, PLyyy , PLzzz"` → all trimmed correctly |
| Empty/blank IDs | `"PLxxx,,PLyyy"` → skip empty, process valid ones |
| Empty config | Blank/null `playlistIds` → logs error, no crash |

**Test implementation approach:**
- Mock `VideoTaggingOrchestrator` to verify call count and arguments
- Use `ArgumentCaptor` to verify correct playlist IDs passed
- Mock orchestrator to throw exception for specific IDs (test error handling)
- Verify log messages contain expected counts and playlist IDs
- Use `@CaptureSystemOutput` or similar to verify logging

### 4. Documentation Updates

**CLAUDE.md - "Scheduled Processing" section:**

Update all references:
- Change `playlist-id` → `playlist-ids`
- Document comma-separated format
- Note whitespace is trimmed automatically
- Clarify error handling (individual failures don't stop job)

**Example configuration block:**
```yaml
vidtag:
  scheduler:
    enabled: true
    fixed-delay-hours: 1
    playlist-ids: PLxxx...,PLyyy...,PLzzz...  # Comma-separated playlist IDs
```

**Environment variable example:**
```bash
VIDTAG_YT_PLAYLIST_IDS="PLxxx...,PLyyy...,PLzzz..."
```

**Breaking change notice:**
Add note that users must update their configuration from `playlist-id` to `playlist-ids`.

**application.yaml inline comment:**
```yaml
playlist-ids: ${VIDTAG_YT_PLAYLIST_IDS:}  # Comma-separated YouTube playlist IDs (e.g., PLxxx...,PLyyy...)
```

## Implementation Checklist

- [ ] Update SchedulerProperties.java (rename field, getter/setter, JavaDoc)
- [ ] Update PlaylistProcessingScheduler.java (split/parse logic, sequential loop, error handling)
- [ ] Update application.yaml (rename property, update comment)
- [ ] Update SchedulerPropertiesTest.java (use new field name)
- [ ] Add new tests to PlaylistProcessingSchedulerTest.java (6 test cases)
- [ ] Update CLAUDE.md (Scheduled Processing section, breaking change note)
- [ ] Verify tests pass
- [ ] Manual testing with multiple playlists (if possible)

## Non-Goals

- Parallel processing (explicitly rejected - chose sequential)
- Backwards compatibility support for `playlist-id` (clean break preferred)
- Per-playlist configuration (different collections, filters, etc.)
- Retry logic for failed playlists (just log and continue)

## Migration Impact

**Breaking change:** Existing users must update configuration:
```yaml
# Old (no longer works)
playlist-id: PLxxx...

# New (required)
playlist-ids: PLxxx...
```

**Mitigation:** Since scheduler is disabled by default (`enabled: false`), impact is limited to users who explicitly enabled it. Clear error message will guide users if they forget to update.
