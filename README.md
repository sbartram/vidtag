# VidTag

AI-powered video tagging service that analyzes YouTube playlist videos and automatically creates bookmarks in Raindrop.io with intelligent tags.

## Features

- 🎥 Fetch videos from YouTube playlists
- 🤖 AI-powered tag generation using Claude
- 🏷️ Smart tag selection from existing Raindrop tags
- 📡 Real-time progress via Server-Sent Events (SSE)
- 🛡️ Circuit breakers and retry logic for external APIs
- ⚡ Redis caching for improved performance
- 📚 Full OpenAPI/Swagger documentation

## Quick Start

### Prerequisites

- Java 21+
- Docker (for Redis)
- API Keys:
  - Anthropic Claude API key
  - YouTube Data API v3 key
  - Raindrop.io API token

### Environment Variables

```bash
export VIDTAG_CLAUDE_API_KEY='your-claude-api-key'
export YOUTUBE_API_KEY='your-youtube-api-key'
export RAINDROP_API_TOKEN='your-raindrop-api-token'
```

### Build and Run

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun
```

The application will automatically start Redis using Docker Compose.

### Access Points

- **API Endpoint**: http://localhost:8080/api/v1/playlists/tag
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI Docs**: http://localhost:8080/v3/api-docs
- **Health Check**: http://localhost:8080/actuator/health

## Manual Testing

A convenient test script is provided for manual testing:

```bash
./test-vidtag.sh <youtube_playlist_url> <raindrop_collection_name>
```

### Examples

```bash
# Using full playlist URL
./test-vidtag.sh 'https://www.youtube.com/playlist?list=PLxxx' 'my-collection'

# Using playlist ID only
./test-vidtag.sh 'PLxxx' 'my-collection'
```

The script will:
- ✅ Validate all required environment variables are set
- ✅ Check if the application is running
- ✅ Create and send a test request
- ✅ Display the real-time SSE event stream
- ✅ Show processing results

### Test Script Features

- Color-coded output for easy reading
- Automatic playlist ID extraction from URLs
- Environment variable validation
- Application health check
- Configurable request parameters (maxVideos: 5, maxTagsPerVideo: 5)
- 3-minute timeout protection

## API Usage

### Tag Playlist Videos

**Endpoint**: `POST /api/v1/playlists/tag`

**Content-Type**: `application/json`

**Accept**: `text/event-stream`

**Request Body**:
```json
{
  "playlistInput": "PLxxx",
  "raindropCollectionTitle": "my-collection",
  "filters": {
    "maxVideos": 10,
    "publishedAfter": "2024-01-01T00:00:00Z",
    "minDuration": 300,
    "titleContains": "tutorial"
  },
  "tagStrategy": {
    "maxTagsPerVideo": 5,
    "confidenceThreshold": 0.6,
    "customInstructions": "Focus on technical topics"
  },
  "verbosity": "DETAILED"
}
```

### SSE Event Types

The endpoint streams the following event types:

- `started` - Processing began
- `progress` - Status update
- `video_completed` - Video processed successfully
- `video_skipped` - Video already exists (duplicate)
- `batch_completed` - Batch of videos completed
- `error` - Error occurred
- `completed` - Processing finished with summary

## Architecture

### Technology Stack

- **Framework**: Spring Boot 4.0.2
- **Language**: Java 21
- **AI**: Spring AI with Anthropic Claude
- **Cache**: Redis
- **Resilience**: Resilience4J (Circuit Breaker, Retry)
- **API Docs**: springdoc-openapi 3.0.0-M1
- **Testing**: Testcontainers

### External APIs

- **YouTube Data API v3** - Video metadata retrieval
- **Raindrop.io API** - Bookmark and tag management
- **Anthropic Claude API** - AI-powered video analysis

### Key Design Decisions

- **Collection Title Resolution**: Uses collection title (not ID) as input for better UX
- **Hybrid Tagging Strategy**: Prefers existing Raindrop tags, allows new tags when confident
- **Batch Processing**: Processes 10 videos at a time to manage memory and API load
- **Duplicate Detection**: Skips videos that already exist as bookmarks
- **SSE Streaming**: Provides real-time progress updates to client

## Configuration

### Circuit Breaker Settings

All external APIs (YouTube, Raindrop, Claude) have circuit breakers configured:

- Failure threshold: 50%
- Wait duration: 30s in open state
- Sliding window: 10 calls (COUNT_BASED)
- Retry: 3 attempts with exponential backoff (2x multiplier, 1s initial wait)

### Cache Configuration

- Raindrop tags cached in Redis with 15-minute TTL
- Reduces API calls and improves performance

## Development

### Running Tests

```bash
# Run all tests
./gradlew test

# Run tests with Testcontainers (requires Docker)
./gradlew test --tests '*Integration*'
```

### Code Structure

```
src/main/java/org/bartram/vidtag/
├── client/          # API client interfaces and implementations
├── config/          # Spring configuration classes
├── controller/      # REST controllers
├── dto/             # Data Transfer Objects
├── event/           # SSE event models
├── exception/       # Custom exceptions
├── handler/         # Exception handlers
├── model/           # Domain models
└── service/         # Business logic services
```

## Documentation

- **API Documentation**: See [Swagger UI](http://localhost:8080/swagger-ui.html) when running
- **Implementation Details**: See `CLAUDE.md` for detailed development context
- **Design Documents**: See `docs/plans/` for design and implementation plans

## License

MIT License

## Contributing

This is a personal project. For questions or suggestions, please open an issue.
