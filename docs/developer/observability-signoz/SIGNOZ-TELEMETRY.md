# SigNoz Telemetry Integration

## Overview

The DATA4CIRC Portal is integrated with SigNoz for comprehensive observability, collecting:
- **Logs**: Application and system logs
- **Traces**: Distributed tracing for request flows
- **Metrics**: JVM metrics, HTTP metrics, database metrics, and custom metrics

This integration uses the OpenTelemetry Java Agent for automatic instrumentation without requiring any code changes.

## Architecture

```
┌─────────────────────┐
│   D4C Portal App    │
│   (Spring Boot)     │
│                     │
│  ┌───────────────┐  │
│  │  OTel Java    │  │──┐
│  │  Agent        │  │  │
│  └───────────────┘  │  │
└─────────────────────┘  │
                         │ OTLP Protocol
                         │ (gRPC/HTTP)
                         ▼
              ┌──────────────────┐
              │     SigNoz       │
              │ your-otel-       │
              │ collector        │
              │                  │
              │ Port 4317 (gRPC) │
              │ Port 4318 (HTTP) │
              └──────────────────┘
```

## Configuration

### Environment Variables

The integration is configured entirely through environment variables, set in:
- `docker-compose.local.yml` (local full stack)
- `docker-compose.prod.yml` (production)
- `.env` file (local overrides)

#### Core Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://your-otel-collector:4317` | SigNoz collector endpoint |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `grpc` | Protocol: `grpc` (4317) or `http/protobuf` (4318) |
| `OTEL_SERVICE_NAME` | `d4c-portal` | Service identifier in SigNoz |

#### Telemetry Types

| Variable | Default | Description |
|----------|---------|-------------|
| `OTEL_TRACES_EXPORTER` | `otlp` | Enable distributed tracing |
| `OTEL_METRICS_EXPORTER` | `otlp` | Enable metrics collection |
| `OTEL_LOGS_EXPORTER` | `otlp` | Enable log forwarding |

#### Resource Attributes

| Variable | Example | Description |
|----------|---------|-------------|
| `OTEL_RESOURCE_ATTRIBUTES` | `deployment.environment=production,service.namespace=d4c` | Metadata tags for telemetry |

#### Instrumentation Control

| Variable | Default | Description |
|----------|---------|-------------|
| `OTEL_INSTRUMENTATION_JDBC_ENABLED` | `true` | Database query tracing |
| `OTEL_INSTRUMENTATION_SPRING_WEB_ENABLED` | `true` | HTTP client instrumentation |
| `OTEL_INSTRUMENTATION_SPRING_WEBMVC_ENABLED` | `true` | Spring MVC controller tracing |

### Java Agent Attachment

The OpenTelemetry Java Agent is automatically attached via:
```bash
JAVA_TOOL_OPTIONS=-javaagent:/app/opentelemetry-javaagent.jar
```

This is handled by the Docker configuration and should not be modified manually.

## What Gets Instrumented

### Automatic Instrumentation

The OpenTelemetry agent automatically instruments:

1. **HTTP Requests**
   - All incoming HTTP requests to Spring controllers
   - HTTP status codes, paths, methods
   - Request/response headers (configurable)
   - Request duration and payload sizes

2. **Database Operations**
   - All JDBC queries (PostgreSQL, H2)
   - Query execution time
   - Connection pool metrics
   - Statement types (SELECT, INSERT, UPDATE, DELETE)

3. **Spring Components**
   - Spring MVC controllers
   - RestTemplate calls
   - WebClient calls
   - Spring Data JPA repositories

4. **JVM Metrics**
   - Memory usage (heap, non-heap)
   - Garbage collection
   - Thread counts
   - CPU usage
   - Class loading

5. **System Metrics**
   - Process CPU
   - Process memory
   - Disk I/O

### Logs

Application logs are automatically forwarded to SigNoz with:
- Log level (INFO, DEBUG, WARN, ERROR)
- Logger name
- Thread name
- Timestamp
- Correlation with traces (trace ID, span ID)

## Deployment

### Docker Compose

The integration is pre-configured in Docker Compose files. Simply start the application:

```bash
# Development
docker-compose up --build

# Production
docker-compose -f docker-compose.prod.yml up --build
```

**Note**: If you encounter SSL certificate issues during the Docker build (especially in CI/CD environments), see the [Docker Build Guide](SIGNOZ-DOCKER-BUILD.md) for solutions.

### Customizing Configuration

Create or update `.env` file:

```bash
# Use HTTP protocol instead of gRPC
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_EXPORTER_OTLP_ENDPOINT=http://your-otel-collector:4318

# Add custom resource attributes
OTEL_RESOURCE_ATTRIBUTES=deployment.environment=staging,service.namespace=d4c,service.version=0.3.0,team=platform

# Disable specific instrumentation if needed
OTEL_INSTRUMENTATION_JDBC_ENABLED=false
```

## Viewing Telemetry in SigNoz

### Accessing SigNoz

1. Navigate to SigNoz UI: `http://your-otel-collector:3301` (or configured UI port)
2. Log in with your credentials
3. Select the `d4c-portal` service from the services list

### Traces

- **Services > d4c-portal**: View all traces
- **Traces**: Filter by status, duration, or custom attributes
- Click on a trace to see the full request flow:
  - HTTP request details
  - Database queries
  - External API calls
  - Error stack traces (if any)

### Metrics

- **Metrics**: View JVM and application metrics
  - JVM memory usage
  - HTTP request rates
  - Database connection pool
  - Request latencies (p50, p95, p99)

### Logs

- **Logs**: View application logs
  - Filter by log level
  - Search log content
  - Correlate with traces (click "View Trace" from log entry)

## Troubleshooting

### No Telemetry Data in SigNoz

1. **Check connectivity**:
   ```bash
   docker exec d4c-portal-app-dev ping your-otel-collector
   docker exec d4c-portal-app-dev nc -zv your-otel-collector 4317
   ```

2. **Verify agent is loaded**:
   ```bash
   docker logs d4c-portal-app-dev | grep -i "opentelemetry"
   ```
   
   You should see:
   ```
   [otel.javaagent] OpenTelemetry automatic instrumentation enabled
   ```

3. **Check environment variables**:
   ```bash
   docker exec d4c-portal-app-dev env | grep OTEL
   ```

### Agent Not Loading

If the agent fails to load:

1. Verify the agent JAR exists:
   ```bash
   docker exec d4c-portal-app-dev ls -lh /app/opentelemetry-javaagent.jar
   ```

2. Check JAVA_TOOL_OPTIONS:
   ```bash
   docker exec d4c-portal-app-dev env | grep JAVA_TOOL_OPTIONS
   ```

3. Review application startup logs:
   ```bash
   docker logs d4c-portal-app-dev 2>&1 | head -50
   ```

### High Overhead

If telemetry causes performance issues:

1. **Disable unnecessary instrumentation**:
   ```bash
   OTEL_INSTRUMENTATION_JDBC_ENABLED=false
   ```

2. **Reduce sampling** (add to environment):
   ```bash
   OTEL_TRACES_SAMPLER=traceidratio
   OTEL_TRACES_SAMPLER_ARG=0.1  # Sample 10% of traces
   ```

3. **Disable logs** (keep traces and metrics):
   ```bash
   OTEL_LOGS_EXPORTER=none
   ```

### Connection Errors

If seeing connection errors to SigNoz:

1. **Verify SigNoz is accessible**:
   ```bash
   curl -v http://your-otel-collector:4317
   ```

2. **Check firewall rules**: Ensure ports 4317 (gRPC) or 4318 (HTTP) are open

3. **Try HTTP protocol** instead of gRPC:
   ```bash
   OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
   OTEL_EXPORTER_OTLP_ENDPOINT=http://your-otel-collector:4318
   ```

## Disabling Telemetry

To temporarily disable telemetry without rebuilding:

```bash
# Set in .env or docker-compose
OTEL_TRACES_EXPORTER=none
OTEL_METRICS_EXPORTER=none
OTEL_LOGS_EXPORTER=none
```

Or remove the JAVA_TOOL_OPTIONS:
```bash
JAVA_TOOL_OPTIONS=""
```

## Performance Impact

The OpenTelemetry Java Agent has minimal performance impact:
- **Memory**: ~50-100MB additional heap usage
- **CPU**: <2% overhead for typical workloads
- **Latency**: <1ms per request for trace recording

The agent is production-ready and used by many organizations in high-throughput systems.

## Advanced Configuration

### Custom Spans (Optional - Requires Code Changes)

While the current integration requires no code changes, you can optionally add custom spans:

```java
// Add to pom.xml (if needed)
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <version>1.32.0</version>
</dependency>

// In your code
import io.opentelemetry.api.trace.Span;

public void businessLogic() {
    Span span = Span.current();
    span.setAttribute("user.id", userId);
    span.addEvent("Processing started");
    // ... business logic
    span.addEvent("Processing completed");
}
```

### Sampling Configuration

For high-traffic applications, configure sampling:

```bash
# Sample 10% of traces
OTEL_TRACES_SAMPLER=traceidratio
OTEL_TRACES_SAMPLER_ARG=0.1

# Always sample errors
OTEL_TRACES_SAMPLER=parentbased_traceidratio
```

### Exporting to Multiple Backends

To export to both SigNoz and another backend:

```bash
# Not directly supported; requires using OpenTelemetry Collector
# Deploy OTel Collector as a separate service and configure it to fan out
```

## References

- [OpenTelemetry Java Instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation)
- [SigNoz Documentation](https://signoz.io/docs/)
- [OpenTelemetry Specification](https://opentelemetry.io/docs/specs/otel/)
- [OTLP Protocol](https://opentelemetry.io/docs/specs/otlp/)

## Support

For issues with:
- **SigNoz configuration**: Contact SigNoz support or check [SigNoz Slack](https://signoz.io/slack)
- **OpenTelemetry agent**: Check [OTel Java issues](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues)
- **D4C Portal integration**: Open an issue in the repository
