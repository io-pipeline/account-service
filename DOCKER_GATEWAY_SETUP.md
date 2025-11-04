# Docker Gateway Configuration for Tests

## Overview

The test suite uses Docker Compose for infrastructure (MySQL, Kafka, Apicurio). These services need to be accessible from the test JVM, which requires different gateway addresses depending on your platform.

## Platform-Specific Configuration

### Linux (Default)
No configuration needed! The default gateway is `172.17.0.1` (Docker bridge gateway).

```bash
./gradlew test
```

### Mac/Windows
Set the `DOCKER_GATEWAY_HOST` environment variable to `host.docker.internal`:

```bash
export DOCKER_GATEWAY_HOST=host.docker.internal
./gradlew test
```

Or set it inline:

```bash
DOCKER_GATEWAY_HOST=host.docker.internal ./gradlew test
```

### Docker-in-Docker (CI/CD)
Uses the default `172.17.0.1` gateway. The CI workflow already sets the correct environment variables:

```yaml
env:
  TEST_MYSQL_HOST: 172.17.0.1
  TEST_KAFKA_HOST: 172.17.0.1
  TEST_APICURIO_HOST: 172.17.0.1
```

## How It Works

1. **Kafka Advertised Address**: Kafka advertises its address as `${DOCKER_GATEWAY_HOST:-172.17.0.1}:9095`
   - When producers connect, Kafka tells them to reconnect to this address
   - Must be accessible from the test JVM

2. **Test Configuration**: `application.properties` uses environment variables:
   - `TEST_MYSQL_HOST`: MySQL host (default: localhost)
   - `TEST_KAFKA_HOST`: Kafka host (default: localhost)
   - `TEST_APICURIO_HOST`: Apicurio host (default: localhost)
   - These default to localhost for local development
   - CI overrides them to use `172.17.0.1`

## Troubleshooting

### Kafka Connection Errors
If you see errors like:
```
Connection to node 0 (localhost/127.0.0.1:9095) could not be established
```

**On Mac/Windows**: Set `DOCKER_GATEWAY_HOST=host.docker.internal`

**On Linux**: Should work by default, verify Docker is running

### Test Failures in CI
The CI environment is Linux-based Docker-in-Docker, so it uses `172.17.0.1`. If tests fail:
1. Check that environment variables are set in the workflow
2. Verify compose containers are starting successfully
3. Check runner logs for connectivity issues

## Reference

- Compose file: `src/test/resources/compose-test-services.yml`
- Test config: `src/main/resources/application.properties` (test profile)
- CI workflow: `.gitea/workflows/build-and-publish.yml`
