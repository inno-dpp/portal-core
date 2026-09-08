# SigNoz Integration - Quick Start Guide

## What is SigNoz?

SigNoz is an open-source observability platform that provides:
- **Distributed Tracing**: Track requests across your application
- **Metrics**: Monitor application and infrastructure performance
- **Logs**: Centralized logging with correlation to traces

## Getting Started

### Prerequisites

- Docker and Docker Compose installed
- SigNoz instance running at `your-otel-collector`
- Network access to SigNoz ports (4317 for gRPC or 4318 for HTTP)

### Quick Start

The application is **already configured** for SigNoz integration. Simply start it:

```bash
# Clone and navigate to repository
cd d4c-portal

# Start with Docker Compose
docker-compose up --build
```

That's it! The application will automatically:
- Download the OpenTelemetry Java Agent
- Instrument all code (HTTP, database, JVM)
- Send telemetry to SigNoz at `your-otel-collector:4317`

### Verify Integration

1. **Check application logs**:
   ```bash
   docker logs d4c-portal-app-dev | grep -i opentelemetry
   ```
   
   You should see:
   ```
   [otel.javaagent] OpenTelemetry automatic instrumentation enabled
   ```

2. **Access the application**:
   ```bash
   curl http://localhost:8080/actuator/health
   ```

3. **View in SigNoz**:
   - Navigate to SigNoz UI (default: `http://your-otel-collector:3301`)
   - Go to **Services** → Find `d4c-portal`
   - Click on the service to view traces, metrics, and logs

## Configuration

### Default Settings

The application uses these defaults:
- **Service Name**: `d4c-portal`
- **SigNoz Endpoint**: `http://your-otel-collector:4317`
- **Protocol**: gRPC (port 4317)
- **Telemetry**: Traces, Metrics, and Logs all enabled

### Customizing Configuration

Create a `.env` file in the project root to override defaults:

```bash
# .env file
OTEL_SERVICE_NAME=my-custom-service-name
OTEL_RESOURCE_ATTRIBUTES=deployment.environment=staging,team=backend
OTEL_EXPORTER_OTLP_ENDPOINT=http://my-signoz-host:4317
```

Then restart:
```bash
docker-compose down
docker-compose up --build
```

## What Gets Monitored?

### Automatically Instrumented

✅ **HTTP Requests**: All endpoints and REST API calls  
✅ **Database Queries**: PostgreSQL and H2 queries  
✅ **JVM Metrics**: Memory, CPU, garbage collection  
✅ **Spring Framework**: Controllers, services, and repositories  
✅ **Application Logs**: All log statements with trace correlation  

### No Code Changes Required

The OpenTelemetry agent instruments your application automatically. No need to modify Java code!

## Common Use Cases

### Debugging a Slow Request

1. Go to SigNoz → **Traces**
2. Filter by duration: `> 1s`
3. Click on a slow trace
4. See detailed breakdown:
   - Controller execution time
   - Database query time
   - External API call time

### Finding Database Performance Issues

1. Go to **Traces** → Filter by operation: `SELECT`
2. Sort by duration
3. Identify slow queries
4. See full SQL statement and execution time

### Monitoring Application Health

1. Go to **Metrics** → Select `d4c-portal`
2. View dashboards:
   - JVM memory usage
   - HTTP request rate
   - Error rate
   - Response time percentiles (p50, p95, p99)

### Correlating Logs with Traces

1. Go to **Logs** → Filter by service: `d4c-portal`
2. Find an error log
3. Click **View Trace** to see the full request context
4. Navigate through the trace to find the root cause

## Troubleshooting

### Problem: No data in SigNoz

**Solution 1**: Check connectivity
```bash
docker exec d4c-portal-app-dev ping your-otel-collector
```

**Solution 2**: Verify agent loaded
```bash
docker logs d4c-portal-app-dev | grep "OpenTelemetry"
```

**Solution 3**: Check environment variables
```bash
docker exec d4c-portal-app-dev env | grep OTEL
```

### Problem: Agent not loading

**Solution**: Rebuild the Docker image
```bash
docker-compose build --no-cache
docker-compose up
```

### Problem: Connection refused

**Solution**: Try HTTP protocol instead of gRPC
```bash
# Add to .env
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_EXPORTER_OTLP_ENDPOINT=http://your-otel-collector:4318
```

## Production Deployment

For production, use the production compose file:

```bash
# Build image
docker build -t d4c-portal:latest .

# Start production services
docker-compose -f docker-compose.prod.yml up -d
```

Production configuration includes:
- Resource attributes: `deployment.environment=production`
- All telemetry types enabled
- Optimized JVM settings with telemetry

## Disabling Telemetry

To disable temporarily:

```bash
# Add to .env or docker-compose environment
OTEL_TRACES_EXPORTER=none
OTEL_METRICS_EXPORTER=none
OTEL_LOGS_EXPORTER=none
```

Or remove the Java agent:
```bash
JAVA_TOOL_OPTIONS=""
```

## Next Steps

- **Read full documentation**: See `docs/developer/SIGNOZ-TELEMETRY.md`
- **Explore SigNoz**: Create custom dashboards and alerts
- **Configure sampling**: Optimize for high-traffic scenarios
- **Add custom spans**: Instrument specific business logic (optional)

## Support

- **SigNoz**: https://signoz.io/docs/
- **OpenTelemetry**: https://opentelemetry.io/docs/
- **Issues**: Open a GitHub issue in the repository

---

**Summary**: The integration is automatic and requires no code changes. Just start the application with Docker Compose, and telemetry flows to SigNoz immediately! 🚀
