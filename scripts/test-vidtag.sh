#!/bin/bash

# VidTag Manual Test Script
# Usage: ./test-vidtag.sh <youtube_playlist_url> <raindrop_collection_name>

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check arguments
if [ $# -ne 2 ]; then
    echo -e "${RED}Error: Missing required arguments${NC}"
    echo ""
    echo "Usage: $0 <youtube_playlist_url> <raindrop_collection_name>"
    echo ""
    echo "Examples:"
    echo "  $0 'https://www.youtube.com/playlist?list=PLxxx' 'my-collection'"
    echo "  $0 'PLxxx' 'my-collection'"
    echo ""
    exit 1
fi

PLAYLIST_INPUT="$1"
COLLECTION_NAME="$2"

# Extract playlist ID if full URL provided
if [[ "$PLAYLIST_INPUT" == *"youtube.com"* ]] || [[ "$PLAYLIST_INPUT" == *"youtu.be"* ]]; then
    # Extract playlist ID from URL
    if [[ "$PLAYLIST_INPUT" =~ list=([a-zA-Z0-9_-]+) ]]; then
        PLAYLIST_ID="${BASH_REMATCH[1]}"
    else
        echo -e "${RED}Error: Could not extract playlist ID from URL${NC}"
        exit 1
    fi
else
    # Assume it's already a playlist ID
    PLAYLIST_ID="$PLAYLIST_INPUT"
fi

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}🚀 VidTag Manual Test${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""
echo -e "${YELLOW}Playlist ID:${NC} $PLAYLIST_ID"
echo -e "${YELLOW}Collection:${NC} $COLLECTION_NAME"
echo ""

# Check if required environment variables are set
MISSING_VARS=()
if [ -z "$VIDTAG_CLAUDE_API_KEY" ]; then
    MISSING_VARS+=("VIDTAG_CLAUDE_API_KEY")
fi
if [ -z "$YOUTUBE_API_KEY" ]; then
    MISSING_VARS+=("YOUTUBE_API_KEY")
fi
if [ -z "$RAINDROP_API_TOKEN" ]; then
    MISSING_VARS+=("RAINDROP_API_TOKEN")
fi

if [ ${#MISSING_VARS[@]} -gt 0 ]; then
    echo -e "${RED}Error: Missing required environment variables:${NC}"
    for var in "${MISSING_VARS[@]}"; do
        echo -e "  ${RED}✗${NC} $var"
    done
    echo ""
    echo "Please set these environment variables before running the test:"
    echo ""
    echo "  export VIDTAG_CLAUDE_API_KEY='your-claude-api-key'"
    echo "  export YOUTUBE_API_KEY='your-youtube-api-key'"
    echo "  export RAINDROP_API_TOKEN='your-raindrop-api-token'"
    echo ""
    exit 1
fi

echo -e "${GREEN}✓ All required environment variables are set${NC}"
echo ""

# Check if application is running
echo -e "${YELLOW}Checking application status...${NC}"
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Application is already running${NC}"
else
    echo -e "${YELLOW}⚠ Application is not running${NC}"
    echo -e "${YELLOW}Please start the application first:${NC}"
    echo ""
    echo "  ./gradlew bootRun"
    echo ""
    echo "Or run in background:"
    echo ""
    echo "  VIDTAG_CLAUDE_API_KEY=\$VIDTAG_CLAUDE_API_KEY \\"
    echo "  YOUTUBE_API_KEY=\$YOUTUBE_API_KEY \\"
    echo "  RAINDROP_API_TOKEN=\$RAINDROP_API_TOKEN \\"
    echo "  ./gradlew bootRun > /tmp/vidtag.log 2>&1 &"
    echo ""
    exit 1
fi

echo ""

# Create test request JSON
REQUEST_FILE="/tmp/vidtag-test-request-$$.json"
cat > "$REQUEST_FILE" <<EOF
{
  "playlistInput": "$PLAYLIST_ID",
  "raindropCollectionTitle": "$COLLECTION_NAME",
  "filters": {
    "maxVideos": 5
  },
  "tagStrategy": {
    "maxTagsPerVideo": 5,
    "confidenceThreshold": 0.6,
    "customInstructions": null
  },
  "verbosity": "DETAILED"
}
EOF

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}📋 Request Configuration${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""
cat "$REQUEST_FILE" | jq '.'
echo ""

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}📡 SSE Event Stream${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""

# Send request and show SSE stream
timeout 180 curl -X POST http://localhost:8080/api/v1/playlists/tag \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d @"$REQUEST_FILE" \
  --no-buffer 2>/dev/null || {
    EXIT_CODE=$?
    if [ $EXIT_CODE -eq 124 ]; then
        echo ""
        echo -e "${YELLOW}⚠ Request timed out after 3 minutes${NC}"
    else
        echo ""
        echo -e "${RED}✗ Request failed with exit code: $EXIT_CODE${NC}"
    fi
}

# Cleanup
rm -f "$REQUEST_FILE"

echo ""
echo -e "${BLUE}=========================================${NC}"
echo -e "${GREEN}✅ Test Complete${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""
