# SigNoz Docker Build Guide

## Overview

The SigNoz integration requires downloading the OpenTelemetry Java Agent during the Docker build process. This guide covers troubleshooting Docker build issues related to SSL certificates.

## Normal Build Process

The Dockerfile automatically downloads the OpenTelemetry Java Agent during the build:

```bash
docker build -t d4c-portal:latest .
```

## SSL Certificate Issues

If you encounter SSL certificate errors during the Docker build (especially in CI/CD environments with SSL inspection), you may see:

```
curl: (60) SSL certificate problem: self-signed certificate in certificate chain
```

### Solution 1: Pre-download the OpenTelemetry Agent

Download the agent manually and modify the Dockerfile to copy it instead:

```bash
# Download the agent locally
curl -L -o opentelemetry-javaagent.jar \
  https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.10.0/opentelemetry-javaagent.jar
```

Then update the Dockerfile to copy the local file:

```dockerfile
# Replace the download step with:
COPY opentelemetry-javaagent.jar opentelemetry-javaagent.jar
```

### Solution 2: Use wget with no-check-certificate

If curl fails, modify the Dockerfile to use wget:

```dockerfile
# Replace curl with wget
RUN apt-get update && apt-get install -y wget ca-certificates && \
    update-ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Download with wget (allows --no-check-certificate if needed)
RUN wget --no-check-certificate -O opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.10.0/opentelemetry-javaagent.jar
```

### Solution 3: Use Maven to Download (Alternative)

Add the OpenTelemetry agent as a Maven dependency and extract it:

Add to `pom.xml`:
```xml
<dependency>
    <groupId>io.opentelemetry.javaagent</groupId>
    <artifactId>opentelemetry-javaagent</artifactId>
    <version>2.10.0</version>
    <scope>runtime</scope>
</dependency>
```

Then in the Dockerfile:
```dockerfile
# Copy from Maven dependencies
COPY --from=builder /root/.m2/repository/io/opentelemetry/javaagent/opentelemetry-javaagent/2.10.0/opentelemetry-javaagent-2.10.0.jar opentelemetry-javaagent.jar
```

## Verifying the Agent is Present

After building, verify the agent is in the image:

```bash
# Run a temporary container
docker run --rm d4c-portal:latest ls -lh /app/opentelemetry-javaagent.jar

# Should output something like:
# -rw-r--r-- 1 appuser appuser 38M Dec 19 15:00 /app/opentelemetry-javaagent.jar
```

## Testing Locally Without Docker

You can test the OpenTelemetry integration without Docker:

1. Download the agent:
   ```bash
   curl -L -o opentelemetry-javaagent.jar \
     https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.10.0/opentelemetry-javaagent.jar
   ```

2. Build the application:
   ```bash
   mvn clean package -DskipTests
   ```

3. Run with the agent:
   ```bash
   export OTEL_EXPORTER_OTLP_ENDPOINT=http://your-otel-collector:4317
   export OTEL_SERVICE_NAME=d4c-portal-local
   export OTEL_RESOURCE_ATTRIBUTES=deployment.environment=local
   
   java -javaagent:opentelemetry-javaagent.jar \
        -jar target/d4c-portal-*.jar \
        --spring.profiles.active=dev
   ```

4. Verify telemetry is being sent by checking the application logs:
   ```
   [otel.javaagent] OpenTelemetry automatic instrumentation enabled
   ```

## CI/CD Considerations

For GitHub Actions or other CI/CD pipelines:

1. **Cache the agent**: Download once and cache it across builds
   ```yaml
   - name: Cache OpenTelemetry Agent
     uses: actions/cache@v3
     with:
       path: opentelemetry-javaagent.jar
       key: otel-agent-2.10.0
   ```

2. **Use build args**: Pass the agent location as a build argument
   ```yaml
   - name: Build Docker image
     run: |
       docker build \
         --build-arg OTEL_AGENT_URL=file://./opentelemetry-javaagent.jar \
         -t d4c-portal:latest .
   ```

## Disabling OpenTelemetry for Development

If you want to build without OpenTelemetry temporarily:

1. Comment out the agent download in Dockerfile
2. Set `JAVA_TOOL_OPTIONS=""` in `.env`
3. Or use a multi-stage build with conditional logic

## Alternative: Using OpenTelemetry Collector

Instead of direct export to SigNoz, you can use an OpenTelemetry Collector sidecar:

```yaml
# .env
services:
  otel-collector:
    image: otel/opentelemetry-collector:latest
    volumes:
      - ./otel-collector-config.yaml:/etc/otel-collector-config.yaml
    command: ["--config=/etc/otel-collector-config.yaml"]
    ports:
      - "4317:4317"  # OTLP gRPC receiver
      - "4318:4318"  # OTLP HTTP receiver
  
  app:
    environment:
      OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4317
```

This allows more flexibility in routing telemetry and can help with network issues.

## Support

For issues:
- Check [OpenTelemetry Java Agent releases](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases)
- Verify network connectivity to GitHub
- Check Docker build logs for specific errors
- Ensure SSL certificates are properly configured in your environment
