# Docker Deployment Guide

This guide covers running VidTag in Docker for local development and testing.

## Prerequisites

- Docker Desktop installed and running
- Docker Compose V2 (included with Docker Desktop)
- API keys (at minimum: `VIDTAG_CLAUDE_API_KEY`)

## Quick Start

### 1. Configure Environment Variables

Copy the example environment file:

```bash
cp .env.example .env
```

Edit `.env` and add your API keys:

```bash
VIDTAG_CLAUDE_API_KEY=your-actual-claude-api-key
YOUTUBE_API_KEY=your-youtube-key  # Optional
RAINDROP_API_TOKEN=your-raindrop-token  # Optional
```

**Note:** If YouTube or Raindrop keys are not provided, stub implementations will be used.

### 2. Build and Start Services

Build the application image and start all services:

```bash
docker compose up --build
```

This will:
- Build the VidTag application image (multi-stage build)
- Start Redis container
- Start VidTag application container
- Configure networking between containers

### 3. Verify Services

Check that both services are healthy:

```bash
docker compose ps
```

Expected output:
```
NAME          IMAGE          STATUS         PORTS
vidtag-app    vidtag-app     Up (healthy)   0.0.0.0:8080->8080/tcp
vidtag-redis  redis:7-alpine Up (healthy)   0.0.0.0:6379->6379/tcp
```

### 4. Test the Application

Access the Swagger UI:

```
http://localhost:8080/swagger-ui.html
```

Check application health:

```bash
curl http://localhost:8080/actuator/health
```

### 5. View Logs

View application logs:

```bash
docker compose logs app -f
```

View Redis logs:

```bash
docker compose logs redis -f
```

### 6. Stop Services

Stop and remove containers:

```bash
docker compose down
```

Keep volumes (preserves Redis data):

```bash
docker compose down --volumes
```

## Manual Testing

### Test Scheduled Job

The scheduled playlist processor runs automatically every hour. To test immediately:

1. Check logs for scheduler initialization:
```bash
docker compose logs app | grep "Scheduler"
```

2. Wait 10 seconds (initial delay) and watch for first execution:
```bash
docker compose logs app -f | grep "playlist processing"
```

### Test REST API

Use the existing manual test script with Docker:

```bash
# From project root
bash scripts/manual-test.sh
```

This will:
- Hit the `/api/v1/playlists/tag` endpoint
- Stream SSE events
- Show real-time progress

## Configuration

### Environment Variables

All Spring Boot properties can be overridden via environment variables in `compose.yaml`:

```yaml
environment:
  SPRING_DATA_REDIS_HOST: redis
  VIDTAG_SCHEDULER_ENABLED: "true"
  VIDTAG_SCHEDULER_FIXED_DELAY_HOURS: "1"
  VIDTAG_SCHEDULER_PLAYLIST_NAME: "tag"
```

### Port Mapping

Change ports in `compose.yaml`:

```yaml
services:
  app:
    ports:
      - "9090:8080"  # Access app on localhost:9090
```

## Troubleshooting

### Container Won't Start

Check logs:
```bash
docker compose logs app
```

Common issues:
- Missing `VIDTAG_CLAUDE_API_KEY` in `.env`
- Port 8080 already in use
- Redis not healthy

### API Keys Not Working

Verify environment variables are set:
```bash
docker compose exec app env | grep API_KEY
```

### Redis Connection Issues

Test Redis connectivity:
```bash
docker compose exec app sh -c 'wget -O- redis:6379'
```

### Rebuild After Code Changes

Force rebuild:
```bash
docker compose up --build --force-recreate
```

## Production Considerations

**DO NOT use this Docker Compose setup in production as-is.** Consider:

- Use secrets management (Docker Secrets, Kubernetes Secrets)
- Use external Redis (AWS ElastiCache, Redis Cloud)
- Configure logging aggregation
- Set resource limits
- Use container registry for images
- Enable TLS/HTTPS
- Configure backup strategy
