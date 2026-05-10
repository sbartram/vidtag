#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REGISTRY="registry.bartram.org/bartram/vidtag"

# If a version arg is provided, use it as-is (rollback path — image must already exist).
# Otherwise resolve from Gradle (build/push path).
if [[ -n "${1:-}" ]]; then
    VERSION="$1"
else
    VERSION="$("$SCRIPT_DIR/gradlew" currentVersion -q 2>&1 | grep 'Project version' | awk '{print $NF}')" || true
fi

if [[ -z "$VERSION" ]]; then
    echo "Error: could not determine version (gradlew currentVersion failed or produced unexpected output)" >&2
    exit 1
fi

echo "Deploying vidtag version: $VERSION"

# Build & push only when no explicit version was passed (rebuilds on rollback are wasteful).
if [[ -z "${1:-}" ]]; then
    docker build -t "$REGISTRY:$VERSION" "$SCRIPT_DIR"
    docker push "$REGISTRY:$VERSION"
fi

# Optional integrations — empty values cause the chart to skip injecting the
# corresponding env vars, keeping the @ConditionalOnProperty beans inactive.
helm upgrade --install vidtag "$SCRIPT_DIR/helm/vidtag" \
    --create-namespace -n vidtag \
    --set app.image.tag="$VERSION" \
    --set secrets.claudeApiKey="$VIDTAG_CLAUDE_API_KEY" \
    --set secrets.youtubeApiKey="${YOUTUBE_API_KEY:-}" \
    --set secrets.raindropApiToken="${RAINDROP_API_TOKEN:-}" \
    --history-max 3
