# Deployment & Configuration Guide

How to run the portal — locally for development, locally as a full Docker
stack, or in production — plus configuration, operations, and troubleshooting.

## Prerequisites

- **Java 17** and **Maven 3.8+** (bare-metal development)
- **Docker 20.10+** and **Docker Compose 2.0+** (containerized runs; ≥2 GB RAM, ≥5 GB disk)

## File Organization

### Docker Compose Files

| File | Purpose | Image | Services | Database |
|------|---------|-------|----------|----------|
| `docker-compose.local.yml` | Local full stack | Built from source | Postgres + App | PostgreSQL |
| `docker-compose.prod.yml` | Production | Pulled from registry | Postgres + App | PostgreSQL |

Both compose files read all application settings from a single `.env` file
(`cp .env.example .env`) via `env_file`; their own `environment:` blocks only
pin stack-inherent values (Spring profile, `DB_HOST`, JVM options). Everything
else — secrets, SPIP, CKAN, mail, branding, telemetry — is configured in one
place and cannot drift between files.

### Environment Files

| File | Used With | Contains |
|------|-----------|----------|
| `.env.example` | committed template | Documented placeholder for every supported variable |
| `.env` | both compose files (and optionally bare-metal runs) | Your real values |

`.env` is git-ignored (`*.env` in `.gitignore`).

### Spring Profiles & Application YML

```
application.yml          ← Shared base (always loaded)
application-dev.yml      ← Dev profile overrides
application-prod.yml     ← Prod profile overrides
application-test.yml     ← Test profile overrides
```

| Profile | Activated By | Database | DDL Strategy | App Port | Use Case |
|---------|-------------|----------|--------------|----------|----------|
| `dev` | `mvn spring-boot:run -Dspring-boot.run.profiles=dev` | H2 in-memory | `create-drop` | 8085 | Local development |
| `prod` | `docker-compose.local.yml` / `docker-compose.prod.yml` | PostgreSQL | `update` | 8080 in container (host port via `APP_PORT`) | Docker & production deployments |
| `test` | `mvn test` / CI | H2 in-memory | `create-drop` | random | Automated tests |

**How Spring config layering works:** `application.yml` is always loaded first, then the active profile's file overrides specific values. Shared config (API keys, SPIP endpoints, CKAN endpoints, branding, heartbeat settings) lives in the base file. Profile files only contain what differs.

### What Goes Where

| Config Type | Location | Examples |
|-------------|----------|----------|
| Shared app settings | `application.yml` | API key, JWT, SPIP endpoints, CKAN endpoints, branding, cache |
| Database connection | `application-{profile}.yml` | Datasource URL, driver, dialect, DDL strategy |
| Pool tuning | `application-{profile}.yml` | Hikari pool sizes, timeouts |
| Onboarding connectors | `application.yml` | Default connectors created for new orgs |
| Docker infrastructure | `docker-compose.*.yml` | Container config, ports, volumes, resource limits |
| Secrets & env-specific values | `.env` | Passwords, API tokens, encryption keys |

---

## Running the Application

There are exactly three supported ways to run the portal.

### 1. Development (bare metal, H2 — daily work)

No Docker, no setup: the dev profile ships its own H2 in-memory database and
built-in development secrets.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

A default development `ENCRYPTION_KEY` value is built into the dev profile; set the environment variable to override it. All other profiles require it to be set — the application fails at startup without it.

- Portal: http://localhost:8085
- H2 Console: http://localhost:8085/h2-console (JDBC URL: `jdbc:h2:mem:d4c_portal_dev`, user: `sa`, no password)
- Demo data seeded on start; database resets on every restart

### 2. Local full stack (Docker, PostgreSQL, image built from source)

Prod-like stack for verifying the containerized application locally.

```bash
cp .env.example .env   # first time only; fill in real values
docker compose -f docker-compose.local.yml up -d --build
```

- Portal: http://localhost:8085 (override with `APP_PORT`)
- PostgreSQL: localhost:5555 (override with `DB_PORT_EXTERNAL`)

Stop / reset database:
```bash
docker compose -f docker-compose.local.yml down       # stop
docker compose -f docker-compose.local.yml down -v    # stop + wipe database
```

### 3. Production (Docker, PostgreSQL, image pulled from registry)

```bash
cp .env.example .env   # fill in real values
docker compose -f docker-compose.prod.yml --env-file .env up -d
```

- Portal: http://localhost:8080 (override with `APP_PORT`)
- Image selected via `APP_IMAGE` (e.g. `ghcr.io/inno-dpp/portal-for-circularity:develop`)

TLS and routing are handled outside this stack: point your reverse proxy
(nginx, Traefik, a cloud load balancer, ...) at the app container on
`${APP_PORT:-8080}`. The portal serves plain HTTP on 8080.

---

## Building the Docker Image

```bash
docker build -t d4c-portal:local .
```

The Dockerfile uses a multi-stage build:
1. **Builder stage**: Maven builds the JAR (dependencies cached separately)
2. **Runtime stage**: Eclipse Temurin 17 JRE + OpenTelemetry agent

**Behind a proxy that breaks SSL during build?** The Dockerfile downloads the
OpenTelemetry Java agent at build time. If that download fails, fetch the
agent on the host and adjust the Dockerfile's download step to `COPY` it in:

```bash
curl -L -o opentelemetry-javaagent.jar \
  https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.10.0/opentelemetry-javaagent.jar
```

---

## Running Tests

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=ConnectorApiControllerTest

# CKAN integration tests (requires running CKAN instance)
mvn test -Dtest=CkanIntegrationTest -Dspring.profiles.active=dev
```

---

## Environment Variable Reference

### Required for Production (.env)

| Variable | Description |
|----------|-------------|
| `DB_PASSWORD` | PostgreSQL password |
| `ENCRYPTION_KEY` | AES-256 encryption key (exactly 32 chars) |
| `SPIP_ADMIN_USERNAME` | SPIP platform admin username |
| `SPIP_ADMIN_PASSWORD` | SPIP platform admin password |

### Application Config

| Variable | Default | Description |
|----------|---------|-------------|
| `APP_PORT` | 8080 | Portal HTTP port on host |
| `APP_BASE_URL` | http://localhost:8080 | Public URL for links in emails |
| `APP_IMAGE` | d4c-portal:latest | Docker image to use (prod compose) |
| `PULL_POLICY` | always | `never` for local images, `always` for remote |

### Database (prod only)

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | localhost | PostgreSQL host |
| `DB_PORT` | 5432 | PostgreSQL port |
| `DB_NAME` | portal | Database name |
| `DB_USERNAME` | portal | Database user |
| `DB_PASSWORD` | — | Database password |
| `DB_PORT_EXTERNAL` | 5432 | PostgreSQL port exposed on host |

### SPIP Integration

| Variable | Default | Description |
|----------|---------|-------------|
| `SPIP_BASE_URL` | https://spip.example.com:8002 | SPIP platform URL |
| `SPIP_CUSTOMER_TAG` | — | SPIP customer identifier |
| `SPIP_ADMIN_USERNAME` | — | SPIP admin username |
| `SPIP_ADMIN_PASSWORD` | — | SPIP admin password |
| `SPIP_DISABLE_SSL_VERIFICATION` | true | Disable SSL cert verification |

### Email

| Variable | Default | Description |
|----------|---------|-------------|
| `EMAIL_ENABLED` | true | Enable/disable email sending |
| `MAIL_HOST` | localhost | SMTP server |
| `MAIL_PORT` | 587 | SMTP port |
| `MAIL_USERNAME` | — | SMTP username |
| `MAIL_PASSWORD` | — | SMTP password |
| `APP_EMAIL_FROM` | noreply@example.com | Sender email address |

### Telemetry (prod only)

| Variable | Default | Description |
|----------|---------|-------------|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | http://localhost:4317 | OpenTelemetry collector endpoint |
| `OTEL_SERVICE_NAME` | portal | Service name in traces |
| `JAVA_TOOL_OPTIONS` | -javaagent:/app/opentelemetry-javaagent.jar | JVM agent config (set to empty to disable) |

### First-Run Administrator (production)

Fresh deployments have no users. Set `ADMIN_USERNAME`, `ADMIN_PASSWORD`, and
optionally `ADMIN_EMAIL` before first startup; the portal creates the platform
administrator automatically. Without these variables the application starts
but logs a warning that no admin exists.

### Default Login Credentials (dev only, requires `DEMO_DATA_ENABLED=true`)

| User | Password | Role |
|------|----------|------|
| admin | Demo_Admin#2025! | Platform Administrator |
| john.doe | password | Organization Admin |
| jane.smith | password | SPIP Privileged User |
| mike.wilson | password | Organization Member |

---

## Operations

The commands below use `docker-compose.local.yml`; substitute
`docker-compose.prod.yml` for production stacks.

### Monitoring & Health

- `/actuator/health` — aggregate health (used by the container healthchecks;
  when email sending is disabled, the mail indicator is excluded)
- `/actuator/info` — application information
- `/actuator/metrics` — application metrics (prod profile)

```bash
docker compose -f docker-compose.local.yml ps            # container status
docker compose -f docker-compose.local.yml logs -f app   # application logs
curl http://localhost:8085/actuator/health               # health status
```

Production logging: JSON-structured, rotated (10 MB max, 5 files), persisted
in the `app_logs` volume.

### Data Persistence

- PostgreSQL data: `postgres_local_data` / `postgres_prod_data` volume —
  survives restarts; `down -v` wipes it
- Application logs: `app_logs` volume
- Prod backups directory: `./docker/backups` (mounted into the container)

Schema is managed by Hibernate (`update` in the Docker stacks, `create-drop`
in dev/test); demo data is only seeded when `DEMO_DATA_ENABLED=true`.

### Backup & Recovery

```bash
# Create database backup
docker compose -f docker-compose.prod.yml exec postgres pg_dump -U portal portal > backup.sql

# Restore
docker compose -f docker-compose.prod.yml exec -T postgres psql -U portal portal < backup.sql
```

### Troubleshooting

| Symptom | Check / Fix |
|---------|-------------|
| Port already in use | Change `APP_PORT` / `DB_PORT_EXTERNAL` in `.env` |
| App exits at startup with `ENCRYPTION_KEY` error | Set it in `.env` — required outside dev/test |
| Health DOWN, mail component | No reachable SMTP: set `MAIL_HOST` or `EMAIL_ENABLED=false` |
| Database connection errors | `docker compose -f docker-compose.local.yml ps postgres` and its logs; is `DB_PASSWORD` set? |
| Tables missing / schema drift | `down -v` to wipe the volume, then start fresh |
| No login possible on fresh stack | Set `ADMIN_USERNAME`/`ADMIN_PASSWORD` in `.env` before first start |
| CKAN/SPIP unreachable from container | `localhost` inside a container is the container itself — use `host.docker.internal` (or the real host) in `.env` |

```bash
# Useful commands
docker compose -f docker-compose.local.yml build --no-cache      # full rebuild
docker compose -f docker-compose.local.yml restart app           # restart app only
docker compose -f docker-compose.local.yml exec app bash         # shell in container
docker compose -f docker-compose.local.yml exec postgres psql -U portal -d portal
docker compose -f docker-compose.local.yml down -v --rmi all --remove-orphans  # clean slate
```

### Security Checklist (production)

- Strong, unique `DB_PASSWORD`, `ENCRYPTION_KEY` (exactly 32 chars)
- Set a real `ADMIN_PASSWORD`; never enable `DEMO_DATA_ENABLED` in production
- Terminate TLS at your external reverse proxy; keep the app on plain HTTP behind it
- Expose only the necessary host ports; keep Postgres internal where possible
- Update images regularly (`PULL_POLICY=always` + re-`up`)
