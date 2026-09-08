#!/bin/bash
# Script to validate OpenTelemetry configuration
# This script checks that all required environment variables are set correctly

set -e

echo "==================================="
echo "OpenTelemetry Configuration Validator"
echo "==================================="
echo ""

# Define required OTEL environment variables
required_vars=(
  "OTEL_EXPORTER_OTLP_ENDPOINT"
  "OTEL_SERVICE_NAME"
  "JAVA_TOOL_OPTIONS"
)

optional_vars=(
  "OTEL_EXPORTER_OTLP_PROTOCOL"
  "OTEL_RESOURCE_ATTRIBUTES"
  "OTEL_TRACES_EXPORTER"
  "OTEL_METRICS_EXPORTER"
  "OTEL_LOGS_EXPORTER"
  "OTEL_INSTRUMENTATION_JDBC_ENABLED"
  "OTEL_INSTRUMENTATION_SPRING_WEB_ENABLED"
  "OTEL_INSTRUMENTATION_SPRING_WEBMVC_ENABLED"
)

# Check required variables
echo "Checking required variables..."
all_required_set=true
for var in "${required_vars[@]}"; do
  if [ -z "${!var}" ]; then
    echo "  ❌ $var is NOT set"
    all_required_set=false
  else
    echo "  ✅ $var = ${!var}"
  fi
done

echo ""
echo "Checking optional variables..."
for var in "${optional_vars[@]}"; do
  if [ -z "${!var}" ]; then
    echo "  ⚠️  $var is NOT set (using default)"
  else
    echo "  ✅ $var = ${!var}"
  fi
done

echo ""

# Validate JAVA_TOOL_OPTIONS contains the agent
if [[ "${JAVA_TOOL_OPTIONS}" == *"javaagent"* ]]; then
  echo "✅ JAVA_TOOL_OPTIONS contains javaagent configuration"
else
  echo "❌ JAVA_TOOL_OPTIONS does not contain javaagent configuration"
  all_required_set=false
fi

# Check if OpenTelemetry agent file exists
if [ -f "/app/opentelemetry-javaagent.jar" ]; then
  echo "✅ OpenTelemetry Java Agent file exists at /app/opentelemetry-javaagent.jar"
  ls -lh /app/opentelemetry-javaagent.jar
else
  echo "❌ OpenTelemetry Java Agent file NOT FOUND at /app/opentelemetry-javaagent.jar"
  all_required_set=false
fi

echo ""
echo "==================================="

if [ "$all_required_set" = true ]; then
  echo "✅ Configuration is valid!"
  echo "Telemetry will be sent to: ${OTEL_EXPORTER_OTLP_ENDPOINT}"
  echo "Service name: ${OTEL_SERVICE_NAME}"
  exit 0
else
  echo "❌ Configuration has errors. Please check the output above."
  exit 1
fi
