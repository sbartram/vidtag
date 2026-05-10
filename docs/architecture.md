# Architecture

## Data flow: triggers → external systems

VidTag has four entry points that converge on two orchestration code paths and
fan out to three external APIs (YouTube, Raindrop, Anthropic Claude). Redis sits
on the side as a cross-cutting cache.

```mermaid
flowchart TB
    subgraph triggers["Triggers"]
        direction LR
        subgraph internal["⏰ Internal (scheduled)"]
            SCHED1["PlaylistProcessingScheduler<br/><i>@Scheduled fixedDelay 1h</i><br/>reads vidtag.scheduler.playlist-ids"]
            SCHED2["UnsortedBookmarkProcessor<br/><i>@Scheduled fixedDelay 1h</i>"]
        end
        subgraph http["🌐 External (HTTP)"]
            HTTP1["POST /api/v1/playlists/tag<br/><i>SseEmitter, returns immediately</i>"]
            HTTP2["POST /api/v1/unsorted/process<br/><i>blocking until done</i>"]
        end
    end

    subgraph orch["Orchestration"]
        ORCH["VideoTaggingOrchestrator.processPlaylist<br/><i>@Async, batches of 10</i>"]
        UPROC["UnsortedBookmarkProcessor.processRaindrop<br/><i>per-bookmark loop</i>"]
    end

    subgraph svc["Service layer (all circuit-breakered)"]
        YT["YouTubeService<br/><i>CB: youtube</i>"]
        RD["RaindropService<br/><i>CB: raindrop · @Cacheable</i>"]
        TAG["VideoTaggingService<br/><i>CB: claude</i>"]
        COLL["CollectionSelectionService<br/><i>cached decisions</i>"]
    end

    subgraph ext["External systems"]
        YTAPI["📺 YouTube Data API v3"]
        RDAPI["🔖 Raindrop.io REST API"]
        CLAUDE["🤖 Anthropic Claude<br/>via Spring AI ChatClient"]
    end

    REDIS[("💾 Redis<br/>raindrop-tags · 15m<br/>raindrop-collections-list · 1h<br/>collection-decisions · 24h")]
    SSE(("SSE stream<br/>back to client"))

    SCHED1 --> ORCH
    HTTP1 --> ORCH
    SCHED2 --> UPROC
    HTTP2 --> UPROC

    ORCH -- "fetchPlaylistVideos · getVideoMetadata" --> YT
    ORCH -- "resolveCollectionId · getUserTags · bookmarkExists · createBookmark" --> RD
    ORCH -- "generateTags per video" --> TAG
    ORCH -- "selectCollection per playlist" --> COLL

    UPROC -- "extractVideoId · getVideoMetadata" --> YT
    UPROC -- "getUnsortedRaindrops · getUserTags · resolveCollectionId · updateRaindrop" --> RD
    UPROC -- "generateTags per bookmark" --> TAG
    UPROC -- "selectCollectionForVideo per bookmark" --> COLL

    COLL -- "playlist meta + sample titles" --> YT
    COLL -- "user collection list" --> RD

    YT --> YTAPI
    RD --> RDAPI
    TAG --> CLAUDE
    COLL --> CLAUDE

    RD <-. "cache get/put" .-> REDIS
    COLL <-. "cache get/put" .-> REDIS

    ORCH -. "ProgressEvent" .-> SSE
    HTTP1 -. "stream events" .-> SSE
```

### Notes

**Trigger symmetry.** Each orchestration path has both a scheduled and an HTTP
trigger that converge on the same code:

| Orchestration                                   | Scheduled trigger              | HTTP trigger                       |
| ----------------------------------------------- | ------------------------------ | ---------------------------------- |
| `VideoTaggingOrchestrator.processPlaylist`      | `PlaylistProcessingScheduler`  | `POST /api/v1/playlists/tag`       |
| `UnsortedBookmarkProcessor.processRaindrop`     | `UnsortedBookmarkProcessor`    | `POST /api/v1/unsorted/process`    |

Schedulers pass a no-op `Consumer<ProgressEvent>` (events get logged at DEBUG
instead of streamed). Anything achievable via the API is therefore also
achievable via the scheduler.

**`CollectionSelectionService` is the most fan-out-y node.** It calls both
YouTube (sample playlist video titles) and Raindrop (user's collection list)
before asking Claude to pick the best collection. That's why the diagram shows
it consuming `YT` and `RD` in addition to `CLAUDE`.

**Per-playlist vs per-bookmark cost.** `VideoTaggingOrchestrator` calls
`selectCollection` **once per playlist**; `UnsortedBookmarkProcessor` calls
`selectCollectionForVideo` **once per bookmark**. The granularity is correct —
an unsorted bookmark has no playlist context — but the unsorted processor pays
N×Claude calls where the playlist orchestrator pays 1×. The 24-hour
`collection-decisions` cache softens this for re-runs but not first pass.

**Resilience.** Every external call goes through a Resilience4J circuit breaker
(`youtube`, `raindrop`, `claude`) with a fallback method that returns a stub
result so a single API outage degrades behavior rather than failing the entire
batch.
