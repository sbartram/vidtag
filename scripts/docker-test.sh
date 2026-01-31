#!/bin/bash
# docker-test.sh - End-to-end Docker testing script

set -e

echo "=== VidTag Docker Test ==="
echo ""

# Check if .env exists
if [ ! -f .env ]; then
    echo "ERROR: .env file not found"
    echo "Please copy .env.example to .env and configure your API keys"
    exit 1
fi

echo "1. Building Docker image..."
docker compose build

echo ""
echo "2. Starting services..."
docker compose up -d

echo ""
echo "3. Waiting for services to be healthy..."
sleep 10

# Wait for health check
for i in {1..30}; do
    if docker compose ps | grep -q "healthy"; then
        echo "Services are healthy!"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "ERROR: Services failed to become healthy"
        docker compose logs
        docker compose down
        exit 1
    fi
    sleep 2
done

echo ""
echo "4. Testing health endpoint..."
curl -f http://localhost:8080/actuator/health || {
    echo "ERROR: Health check failed"
    docker compose logs app
    docker compose down
    exit 1
}

echo ""
echo "5. Checking scheduler logs..."
docker compose logs app | grep -i "scheduler" || echo "No scheduler logs yet (may need to wait)"

echo ""
echo "=== Test Successful ==="
echo ""
echo "Services are running. Access:"
echo "  - Application: http://localhost:8080"
echo "  - Swagger UI: http://localhost:8080/swagger-ui.html"
echo "  - Health: http://localhost:8080/actuator/health"
echo ""
echo "View logs: docker compose logs app -f"
echo "Stop services: docker compose down"
