#!/bin/bash
set -e

echo "========================================="
echo "Account Service Test Runner"
echo "========================================="
echo ""
echo "Current directory: $(pwd)"
echo "Script location: $(dirname "$0")"
echo ""

# Navigate to project root (one level up from scripts/)
cd "$(dirname "$0")/.."
PROJECT_ROOT=$(pwd)

echo "Project root: $PROJECT_ROOT"
echo ""

# Check prerequisites
echo "Checking prerequisites..."
echo ""

# Check if devservices infrastructure is running
echo "Checking devservices infrastructure..."
RUNNING_CONTAINERS=$(docker ps --format "{{.Names}}" | grep "pipeline-" | wc -l)
if [ "$RUNNING_CONTAINERS" -eq 0 ]; then
    echo "⚠️  WARNING: No pipeline devservices containers detected!"
    echo "    Start them with: docker compose -f /path/to/compose-devservices.yml up -d"
    echo ""
else
    echo "✅ Found $RUNNING_CONTAINERS pipeline containers running"
    docker ps --format "table {{.Names}}\t{{.Status}}" | grep pipeline | head -10
    echo ""
fi

# Check Maven Local artifacts
echo "Checking required artifacts in Maven Local..."
MAVEN_LOCAL=~/.m2/repository/io/pipeline

if [ -d "$MAVEN_LOCAL/pipeline-bom-catalog" ]; then
    echo "✅ pipeline-bom-catalog found"
else
    echo "❌ pipeline-bom-catalog NOT found"
fi

if [ -d "$MAVEN_LOCAL/grpc-stubs" ]; then
    echo "✅ grpc-stubs found"
else
    echo "❌ grpc-stubs NOT found"
fi

if [ -d "$MAVEN_LOCAL/devservices-docker-compose" ]; then
    echo "✅ devservices-docker-compose found"
else
    echo "❌ devservices-docker-compose NOT found"
fi

echo ""
echo "========================================="
echo "Running Tests"
echo "========================================="
echo ""

# Ensure modern Docker API version is advertised to Testcontainers/docker-java
export DOCKER_API_VERSION="${DOCKER_API_VERSION:-1.44}"
# Prefer Unix socket strategy on Linux
export TESTCONTAINERS_DOCKER_CLIENT_STRATEGY="org.testcontainers.dockerclient.UnixSocketClientProviderStrategy"

echo "Environment overrides for Docker/Testcontainers:"
echo "  DOCKER_API_VERSION=${DOCKER_API_VERSION}"
echo "  TESTCONTAINERS_DOCKER_CLIENT_STRATEGY=${TESTCONTAINERS_DOCKER_CLIENT_STRATEGY}"
echo ""

# Run tests with full output
./gradlew clean test --no-daemon

# Capture exit code
TEST_EXIT_CODE=$?

echo ""
echo "========================================="
echo "Test Results"
echo "========================================="
echo ""
echo "Exit code: $TEST_EXIT_CODE"
echo ""

# Show test summary from Gradle output
if [ $TEST_EXIT_CODE -ne 0 ]; then
    echo "❌ Tests FAILED"
    echo ""
    echo "Test report: file://$PROJECT_ROOT/build/reports/tests/test/index.html"
    echo ""
    echo "Failed tests:"
    ./gradlew test --no-daemon 2>&1 | grep "FAILED" || echo "  (run again to see failures)"
else
    echo "✅ All tests PASSED"
fi

exit $TEST_EXIT_CODE


