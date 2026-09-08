# SigNoz Integration - Deployment Instructions

## Quick Reference

The DATA4CIRC Portal is configured to send telemetry (logs, traces, metrics) to SigNoz at `your-otel-collector` automatically using the OpenTelemetry Java Agent.

## Deployment Methods

### Method 1: Standard Docker Build (Recommended)

If you have proper SSL certificates configured:

```bash
# Build the image
docker build -t d4c-portal:latest .

# Start with development environment
docker-compose up -d

# Or start with production environment
docker-compose -f docker-compose.prod.yml up -d
```

### Method 2: Pre-downloaded Agent (For SSL Issues)

If you encounter SSL certificate errors during build:

```bash
# Step 1: Download OpenTelemetry agent locally
curl -L -o opentelemetry-javaagent.jar \
  https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.10.0/opentelemetry-javaagent.jar

# Step 2: Build with the pre-downloaded agent (see DEPLOYMENT.md "Behind a proxy" note)
docker build -t d4c-portal:latest .

# Step 3: Start the application
docker-compose up -d
```

### Method 3: Using Pre-built Images

If building locally fails, use pre-built images from GitHub Container Registry:

```bash
# Pull the image
docker pull ghcr.io/inno-dpp/d4c-portal:latest

# Set the image in .env
echo "APP_IMAGE=ghcr.io/inno-dpp/d4c-portal:latest" >> .env

# Start production environment
docker-compose -f docker-compose.prod.yml up -d
```

## Verifying the Integration

### 1. Check Application Startup

```bash
# View logs
docker logs d4c-portal-app-dev

# Look for this line:
# [otel.javaagent] OpenTelemetry automatic instrumentation enabled
```

### 2. Test with Configuration Validator

```bash
# Run validation script inside container
docker exec d4c-portal-app-dev /app/docker/test-otel-config.sh
```

Expected output:
```
===================================
OpenTelemetry Configuration Validator
===================================

Checking required variables...
  ✅ OTEL_EXPORTER_OTLP_ENDPOINT = http://your-otel-collector:4317
  ✅ OTEL_SERVICE_NAME = d4c-portal
  ✅ JAVA_TOOL_OPTIONS = -javaagent:/app/opentelemetry-javaagent.jar

Checking optional variables...
  ✅ OTEL_EXPORTER_OTLP_PROTOCOL = grpc
  ✅ OTEL_RESOURCE_ATTRIBUTES = deployment.environment=docker,service.namespace=d4c
  ...

✅ Configuration is valid!
Telemetry will be sent to: http://your-otel-collector:4317
Service name: d4c-portal
```

### 3. Generate Some Traffic

```bash
# Make some requests to generate traces
curl http://localhost:8080/
curl http://localhost:8080/actuator/health
curl http://localhost:8080/login
```

### 4. View in SigNoz

1. Open SigNoz UI: `http://your-otel-collector:3301` (or your configured UI port)
2. Navigate to **Services** → `d4c-portal`
3. View traces, metrics, and logs

## Customizing the Configuration

### Using Environment Variables

Create or edit `.env` file in the project root:

```bash
# Change SigNoz endpoint
OTEL_EXPORTER_OTLP_ENDPOINT=http://my-signoz-host:4317

# Change service name
OTEL_SERVICE_NAME=d4c-portal-staging

# Add custom resource attributes
OTEL_RESOURCE_ATTRIBUTES=deployment.environment=staging,team=backend,region=eu-west

# Use HTTP instead of gRPC
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_EXPORTER_OTLP_ENDPOINT=http://your-otel-collector:4318
```

Then restart:
```bash
docker-compose down
docker-compose up -d
```

### Disabling Telemetry Temporarily

Add to `.env`:
```bash
OTEL_TRACES_EXPORTER=none
OTEL_METRICS_EXPORTER=none
OTEL_LOGS_EXPORTER=none
```

Or remove the agent entirely:
```bash
JAVA_TOOL_OPTIONS=""
```

## Production Deployment

### Prerequisites

1. Ensure SigNoz is accessible from production environment
2. Configure firewall to allow outbound connections to port 4317 (gRPC) or 4318 (HTTP)
3. Set up proper environment variables in `.env`

### Deployment Steps

```bash
# 1. Create production .env file
cat > .env <<EOF
# Database
DB_HOST=postgres
DB_NAME=d4c_portal
DB_USERNAME=d4c_user
DB_PASSWORD=<secure-password>

# OpenTelemetry / SigNoz
OTEL_EXPORTER_OTLP_ENDPOINT=http://your-otel-collector:4317
OTEL_SERVICE_NAME=d4c-portal
OTEL_RESOURCE_ATTRIBUTES=deployment.environment=production,service.namespace=d4c,service.version=0.3.0

# Application
APP_BASE_URL=https://your-domain.com
SPRING_PROFILES_ACTIVE=prod
EOF

# 2. Build the image
docker build -t d4c-portal:latest .

# 3. Start production services
docker-compose -f docker-compose.prod.yml up -d

# 4. Verify
docker logs d4c-portal-app-prod | grep -i opentelemetry
```

### Health Checks

```bash
# Application health
curl http://localhost:8080/actuator/health

# Check metrics endpoint
curl http://localhost:8080/actuator/metrics

# View container logs
docker logs -f d4c-portal-app-prod
```

## Troubleshooting

### No Telemetry in SigNoz

**Check 1: Verify connectivity**
```bash
docker exec d4c-portal-app-dev ping your-otel-collector
docker exec d4c-portal-app-dev nc -zv your-otel-collector 4317
```

**Check 2: Verify agent is loaded**
```bash
docker logs d4c-portal-app-dev 2>&1 | grep -i "opentelemetry"
```

**Check 3: Verify environment variables**
```bash
docker exec d4c-portal-app-dev env | grep OTEL
```

### Build Fails with SSL Errors

Use Method 2 (pre-downloaded agent) or Method 3 (pre-built images).

See [SIGNOZ-DOCKER-BUILD.md](docs/developer/SIGNOZ-DOCKER-BUILD.md) for detailed solutions.

### Agent Not Loading

**Solution 1: Verify agent file exists**
```bash
docker exec d4c-portal-app-dev ls -lh /app/opentelemetry-javaagent.jar
```

**Solution 2: Check JAVA_TOOL_OPTIONS**
```bash
docker exec d4c-portal-app-dev env | grep JAVA_TOOL_OPTIONS
```

**Solution 3: Rebuild the image**
```bash
docker-compose down
docker-compose build --no-cache
docker-compose up -d
```

### Performance Issues

If telemetry causes overhead:

```bash
# Disable database instrumentation
OTEL_INSTRUMENTATION_JDBC_ENABLED=false

# Enable sampling (10% of traces)
OTEL_TRACES_SAMPLER=traceidratio
OTEL_TRACES_SAMPLER_ARG=0.1

# Disable logs (keep traces and metrics)
OTEL_LOGS_EXPORTER=none
```

## Reference Documentation

- [SigNoz Telemetry Guide](docs/developer/SIGNOZ-TELEMETRY.md) - Complete reference
- [SigNoz Quick Start](docs/SIGNOZ-QUICKSTART.md) - Quick start guide
- [Docker Build Guide](docs/developer/SIGNOZ-DOCKER-BUILD.md) - Build troubleshooting

## Support

- **SigNoz**: https://signoz.io/docs/
- **OpenTelemetry**: https://opentelemetry.io/docs/
- **Issues**: https://github.com/inno-dpp/d4c-portal/issues

---

**Ready to deploy?** Just run `docker-compose up -d` and telemetry starts flowing automatically! 🚀
