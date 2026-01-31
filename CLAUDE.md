# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

VidTag is a video tagging application built with Spring Boot 4.0.2, using Java 21. The application integrates with:
- **Spring AI with Anthropic Claude** for AI-powered video analysis and tagging
- **Redis** for caching
- **Spring Cloud Circuit Breaker (Resilience4J)** for fault tolerance
- **Spring Boot Actuator** for monitoring and management
- **Raindrop.io** for saving video bookmarks with tags
- **YouTube** for retrieving video playlists to tag

## Development Commands

### Build and Run
```bash
# Build the project
./gradlew build

# Run the application (standard mode)
./gradlew bootRun

# Run with Testcontainers for development (automatically starts Redis)
./gradlew test --args='--spring.profiles.active=test'
# OR run the TestVidtagApplication main class directly
```

### Testing
```bash
# Run all tests
./gradlew test

# Run tests with verbose output
./gradlew test --info

# Run a single test class
./gradlew test --tests 'org.bartram.vidtag.VidtagApplicationTests'

# Run tests continuously (useful during development)
./gradlew test --continuous
```

### Code Quality
```bash
# Check for dependency updates
./gradlew dependencyUpdates

# Clean build artifacts
./gradlew clean
```

### Docker Commands
```bash
# Build Docker image
docker build -t vidtag:latest .

# Run with Docker Compose (recommended)
docker compose up --build

# Run standalone container (requires external Redis)
docker run -d \
  -p 8080:8080 \
  -e VIDTAG_CLAUDE_API_KEY=your-key \
  -e SPRING_DATA_REDIS_HOST=host.docker.internal \
  vidtag:latest

# View logs
docker compose logs app -f

# Stop and remove containers
docker compose down
```

See [docs/DOCKER.md](docs/DOCKER.md) for comprehensive Docker deployment guide.

## Architecture

### Dependency Management
The project uses Spring Boot dependency management with:
- Spring AI BOM version 2.0.0-M2
- Spring Cloud BOM version 2025.1.0

### AI Integration
The application is configured to use Anthropic Claude via Spring AI. The API key must be provided via the environment variable `VIDTAG_CLAUDE_API_KEY`. Model selection is configurable in `application.yaml`:
- Default: `claude-haiku-4-5`
- Alternatives: `claude-sonnet-4-5`, `claude-opus-4-5`

### External API Integrations

#### YouTube Data API
The application uses Google's YouTube Data API v3 to retrieve playlist videos:
- **Implementation**: `YouTubeApiClientImpl` (activated when `youtube.api.key` is configured)
- **Dependencies**: google-api-services-youtube v3-rev20250714-2.0.0
- **Features**: Pagination support (50 videos/page), video duration retrieval, ISO 8601 duration parsing
- **Documentation**: [YouTube Data API Reference](https://developers.google.com/youtube/v3/docs)

#### Raindrop.io API
The application uses Raindrop.io REST API to manage bookmarks and tags:
- **Implementation**: `RaindropApiClientImpl` (activated when `raindrop.api.token` is configured)
- **Authentication**: Bearer token
- **Base URL**: https://api.raindrop.io/rest/v1
- **Features**: Tag retrieval, collection management, bookmark existence checking, bookmark creation
- **Documentation**: [Raindrop.io API Documentation](https://developer.raindrop.io/)

#### Stub Implementations
When API keys are not configured, stub implementations are automatically used (via `@ConditionalOnMissingBean` in `TestcontainersConfiguration`):
- `YouTubeApiClient` stub: Returns empty list for playlist videos
- `RaindropApiClient` stub: Returns empty lists for tags/collections, always returns false for bookmark existence
- This allows tests to run without external API credentials

### Docker Compose Integration
Spring Boot's Docker Compose support is enabled for development. Running the application automatically starts the Redis container defined in `compose.yaml`.

### Testing Strategy
The project uses Testcontainers for integration testing:
- `TestVidtagApplication` - Entry point for running the app with Testcontainers configuration
- `TestcontainersConfiguration` - Provides a Redis container for tests using `@ServiceConnection`
- This allows tests to run against real Redis instances without manual Docker setup

### Package Structure
All application code resides in `org.bartram.vidtag`. The project is currently in early stages with minimal implementation.

## Configuration Notes

### Environment Variables

#### Required
- `VIDTAG_CLAUDE_API_KEY` - Anthropic API key for Claude integration (required for runtime)

#### Optional (use stub implementations if not set)
- `YOUTUBE_API_KEY` - Google YouTube Data API v3 key (activates `YouTubeApiClientImpl`)
- `RAINDROP_API_TOKEN` - Raindrop.io API token (activates `RaindropApiClientImpl`)

When optional API keys are not configured, stub implementations are used that return empty data, allowing the application to start and tests to run without external API dependencies.

### Redis Configuration
Redis runs on the default port 6379 and is automatically managed via:
- Docker Compose in development mode
- Testcontainers in test mode

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

## Development Workflow

When adding new features to this Spring Boot application:
1. New REST controllers, services, and configuration classes should be added to the `org.bartram.vidtag` package or appropriate subpackages
2. Use Spring Boot's auto-configuration where possible
3. Leverage Spring AI's chat client for Claude integration
4. Write integration tests using Testcontainers to validate against real Redis instances
5. The application uses Spring Boot 4.0.2, which may have different APIs than Spring Boot 3.x (check documentation links in HELP.md)

## Implementation Status

### Completed Features
- Data models and DTOs for video tagging workflow
- **YouTube API client** with Google's YouTube Data API v3 (conditional on `youtube.api.key`)
- **Raindrop API client** with Raindrop.io REST API (conditional on `raindrop.api.token`)
- YouTube service with circuit breaker, filtering, and pagination
- Raindrop service with Redis caching (15min TTL) and collection title resolution
- Video tagging service with Spring AI (Claude integration)
- Video tagging orchestrator with batch processing (10 videos/batch)
- REST controller with SSE streaming support
- Integration tests with Testcontainers
- Stub implementations for testing without external API dependencies

### API Endpoint
```
POST /api/v1/playlists/tag
Content-Type: application/json
Accept: text/event-stream
```

### Testing
Integration tests are disabled by default (require API key):
- Enable by removing `@Disabled` annotation
- Set environment variable: `VIDTAG_CLAUDE_API_KEY=<your-key>`
- Run: `./gradlew test`

### Circuit Breaker Configuration
All external APIs (YouTube, Raindrop, Claude) have circuit breakers configured in `application.yaml`:
- Failure threshold: 50%
- Wait duration: 30s
- Retry attempts: 2-3 with exponential backoff

### Cache Configuration
Raindrop tags are cached in Redis with 15-minute TTL to reduce API calls.

### Scheduled Processing
- **Playlist Processor Scheduler** runs every hour (configurable)
- Processes all videos from configured YouTube playlists through AI tagging workflow
- **Important**: Requires YouTube playlist IDs (not names). Get from URL: `https://www.youtube.com/playlist?list=PLxxx...`
- Supports multiple playlists via comma-separated list (processed sequentially)
- Individual playlist failures are logged but don't stop processing of remaining playlists
- Disabled by default (set `vidtag.scheduler.enabled=true` and configure `playlist-ids` to enable)
- Configuration in `application.yaml`:
  - `vidtag.scheduler.enabled` - enable/disable scheduler (default: false)
  - `vidtag.scheduler.fixed-delay-hours` - delay between runs (default: 1 hour)
  - `vidtag.scheduler.playlist-ids` - Comma-separated YouTube playlist IDs to process (required when enabled)

**Example configuration:**
```yaml
vidtag:
  scheduler:
    enabled: true
    fixed-delay-hours: 1
    playlist-ids: PLxxx...,PLyyy...,PLzzz...
```

**Environment variable:**
```bash
VIDTAG_YT_PLAYLIST_IDS="PLxxx...,PLyyy...,PLzzz..."
```

**Note**:
- Whitespace around playlist IDs is trimmed automatically
- Finding playlists by name requires OAuth 2.0 authentication (planned for future release). For now, use playlist IDs directly.
- **Breaking change from v1.0**: Configuration property renamed from `playlist-id` to `playlist-ids`. Users must update their configuration.

### Docker Deployment
- Multi-stage Dockerfile (JDK21 build, JRE21-alpine runtime)
- Docker Compose orchestration with Redis
- Health checks via Spring Actuator
- Environment variable configuration
- Non-root container user for security
- See [docs/DOCKER.md](docs/DOCKER.md) for deployment guide
