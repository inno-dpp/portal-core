# Multi-stage build for DATA4CIRC Portal
# Stage 1: Build stage using official Maven image
FROM maven:3.9-eclipse-temurin-17 as builder

# Set working directory
WORKDIR /app

# Copy Maven settings to use mirrors and configure timeouts
COPY settings.xml /root/.m2/settings.xml

# Copy Maven configuration files
COPY pom.xml .

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime stage
FROM eclipse-temurin:17-jre-jammy

# Install curl and ca-certificates (health checks, agent download)
RUN apt-get update && apt-get install -y curl ca-certificates && \
    update-ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Create application user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Set working directory
WORKDIR /app

# Download OpenTelemetry Java Agent
ARG OTEL_AGENT_VERSION=2.10.0
RUN curl -L -o opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar

# Copy the JAR file from builder stage — the runnable/executable one specifically
# (classifier "exec"; see pom.xml's spring-boot-maven-plugin config): target/ also has
# a plain library jar with the same version, and an unqualified *.jar glob would match
# both.
COPY --from=builder /app/target/*-exec.jar app.jar

# Create logs directory and set ownership
RUN mkdir -p /app/logs && chown -R appuser:appuser /app

# Switch to application user
USER appuser

# Expose port
EXPOSE 8080

# Set JVM options (OpenTelemetry agent will be added via JAVA_TOOL_OPTIONS in docker-compose)
ENV JAVA_OPTS="-Xmx512m -Xms256m -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT exec java $JAVA_OPTS -jar app.jar