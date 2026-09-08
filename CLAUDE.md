# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Portal for Circularity is a Spring Boot web application — a data catalog and circular
economy data management portal — and the **open-source core**. It is fully functional on
its own (organizations, connectors, onboarding, CKAN, Keycloak). SPIP (Secure
Privacy-Preserving Infrastructure Platform) attribute-based encryption is a separate,
licensed plugin that depends on this artifact: [inno-dpp/spip-plugin](https://github.com/inno-dpp/spip-plugin).
This repo publishes itself to GitHub Packages (`com.data4circ:d4c-portal`) so that plugin
can build against it. See `docs/developer/SPIP-PLUGIN-DECOUPLING-PLAN.md` for the full
history of how the two were split apart (this repo used to contain both).

## Development Setup Commands

### Running the Application

**Encryption key**: The portal encrypts sensitive fields at rest (connector configuration,
and — when the SPIP plugin is installed — SPIP passwords) with AES-256 using
`ENCRYPTION_KEY` (exactly 32 bytes). No setup is needed for local development — the dev
and test profiles ship built-in keys (`application-dev.yml` / `application-test.yml`).
With any other profile the application **fails at startup** unless `ENCRYPTION_KEY` is
set (validated in `EncryptionConfig`).

```bash
# Development mode with H2 in-memory database (built-in dev key applies)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Optionally override the encryption key (must be exactly 32 characters)
export ENCRYPTION_KEY=<your-32-character-key>   # Linux/Mac
set ENCRYPTION_KEY=<your-32-character-key>      # Windows

# Production mode with PostgreSQL
mvn spring-boot:run -Dspring-boot.run.profiles=prod

# Test mode
mvn spring-boot:run -Dspring-boot.run.profiles=test
```

### Database Access
The dev profile serves the application on port **8085** (`application-dev.yml`).
- **H2 Console (Development)**: http://localhost:8085/h2-console
  - JDBC URL: `jdbc:h2:mem:d4c_portal_dev`
  - Username: `sa`
  - Password: (empty)

### Dev Keycloak Instance (onboarding sync)
The onboarding flow can provision each new organization (group + first user) in a
Keycloak instance — the `keycloak` onboarding tool, optional by default
(`ONBOARDING_TOOL_KEYCLOAK_REQUIRED=false`): the admin page marks it "Optional" and
approval proceeds without it, skipping the Keycloak user, connector and email section. A local
instance for development and E2E tests:

```bash
docker compose -f docker-compose.keycloak.yml up -d
```

- Admin console: http://localhost:8081 (admin / admin, dev only)
- Imported realm `data4circ` with service-account client `portal-admin`
  (secret `dev-portal-admin-secret`, matched by the dev/test profile defaults)
- Realm export: `docker/keycloak/dev-realm.json`
- The default instance is configurable via `KEYCLOAK_*` env vars or at runtime
  under `/admin/settings`; the onboarding sync form pre-fills from it and can
  target a different instance per organization.

### Building and Testing
```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Package application
mvn clean package

# Run with specific profile (version comes from pom.xml)
java -jar target/d4c-portal-<version>.jar --spring.profiles.active=dev

# Publish to GitHub Packages (needs GITHUB_ACTOR/GITHUB_TOKEN — CI does this
# automatically on push to main; see .github/workflows/build-push.yml)
mvn deploy -DskipTests
```

## Architecture Overview

### Multi-Layered Architecture
- **Presentation Layer**: Thymeleaf templates with Bootstrap 5 UI
- **Controller Layer**: Spring MVC controllers handling web requests
- **Service Layer**: Business logic and transaction management
- **Repository Layer**: Spring Data JPA repositories
- **Entity Layer**: JPA entities representing domain model

### Key Components

#### Security Framework
- Session-based authentication (Spring Security `formLogin`)
- Role-based access control with 4 user roles:
  - `PLATFORM_ADMIN`: System-wide administration
  - `ORG_ADMIN`: Organization management
  - `SPIP_PRIVILEGED_USER`: SPIP platform access
  - `ORG_MEMBER`: Basic organization member
- Spring Security configuration in `SecurityConfig`

#### Core Entities
- **User**: System users with organization membership and roles
- **Organization**: Companies/institutions participating in data sharing
- **Connector**: Data integration endpoints (providers, consumers, SPIP agents)

#### Database Configuration
- **Development**: H2 in-memory database with web console
- **Production**: PostgreSQL with connection pooling
- **Test**: H2 in-memory for fast test execution
- Multi-profile configuration in `application-{profile}.yml` files

### Directory Structure

Feature-sliced, not layered — each `features/*` package owns its own
`controller`/`service`/`repository`/`entity`/`dto`/`enums` internally:

```
src/main/java/com/data4circ/portal/
├── common/                    # cross-cutting: config, converter (field encryption), exception, security (JWT), util
│                               # incl. the plugin extension points: nav/NavContribution,
│                               # security/SecurityRuleContributor, demo/DemoDataContributor
├── features/
│   ├── organization/          # orgs, members, onboarding (incl. OrganizationSpipUser —
│   │                           # shared tool credentials table: SPIP, CKAN and Keycloak
│   │                           # all land here despite the name; see the decoupling plan)
│   ├── connectors/            # connector CRUD (EDC / CKAN / SPIP agent configs)
│   ├── dataset/                # dataset statistics (counts read from CKAN)
│   ├── collaboration/          # collaboration requests; spi/CollaborationPartnerHook is
│   │                           # the extension point a plugin (SPIP) reacts to a new/ended
│   │                           # partnership through
│   ├── notification/           # notifications (SSE-based)
│   ├── onboardingsync/         # sync to ckan / keycloak / generic tool targets during onboarding
│   └── platformsettings/       # CKAN/SPIP/Keycloak connection settings (config data only —
│                               # no live SPIP client here; that's the plugin's job)
└── integration/
    └── ckan/                   # client
```

Within a `features/<name>/` package, code layers the usual way
(`controller` → `service` → `repository` → `entity`/`dto`/`enums`) — the
slicing is by feature, not by layer.

**Plugin boundary.** This repo has zero compile-time dependency on SPIP — no `Spip*`
class outside a handful of deliberately core-owned exceptions
(`OrganizationSpipUser`/`OrganizationSpipUserRepository`/`OrganizationSpipUserService`,
`SpipSettingsService`, `SpipProperties`, `SpipPolicyLabelUtil` — all pure config/data,
explained in the decoupling plan). A plugin like SPIP depends on this artifact and
registers itself into the extension points above (`app.modules.spip.enabled` gates its
beans); this repo never depends on a plugin.

## Portal Features Implementation

### Dashboard (DashboardController)
- Statistics overview: organizations, connectors, datasets
- Use case categories for circular economy data
- Recent activity and system status

### Organization Management (OrganizationController)
- Public organization directory
- Organization profile management
- Member management with role assignment
- Certification status tracking

### Connector Management (ConnectorController)
- CRUD operations for data connectors
- Real-time status monitoring
- Multi-step setup wizard
- Organization-scoped access control

### SPIP Integration (SpipController)
- Attribute viewer (read-only from SPIP platform)
- Policy management viewer
- Agent status monitoring
- External redirect handlers to SPIP platform

## Development Guidelines

### Adding New Features
1. Pick (or create) the owning `features/<name>/` package
2. Add the JPA entity in that feature's `entity/` package
3. Add the repository interface in that feature's `repository/` package
4. Implement the service class in that feature's `service/` package
5. Create the controller in that feature's `controller/` package
6. Add Thymeleaf templates in `src/main/resources/templates/`

### Security Considerations
- All new endpoints require authentication by default
- Use `@PreAuthorize` annotations for role-based access
- Never expose sensitive configuration in templates
- Validate all user inputs in controllers

### Database Schema Changes
- Update JPA entities with proper annotations
- Add migration scripts for production PostgreSQL
- Test with both H2 (dev) and PostgreSQL (prod) profiles

### Sample Users (Development)
Seeded only when `app.demo-data.enabled=true` (on in the dev and test profiles, off by default). Production deployments create the first admin via `ADMIN_USERNAME`/`ADMIN_PASSWORD`/`ADMIN_EMAIL` instead.
- **admin / Demo_Admin#2025!**: Platform Administrator
- **john.doe / password**: Organization Admin (GreenTech Solutions)
- **jane.smith / password**: SPIP Privileged User (EcoRecycle Corp)
- **mike.wilson / password**: Organization Member (Agricultural Plastics Institute)

## Testing Strategy

### Profile-Specific Testing
`-Dtest=<ClassName>` targets one module's surefire run; in the multi-module reactor
(`core` + `spip-plugin`) it must be scoped with `-pl` or the *other* module's surefire
run fails the whole build with "No tests matching pattern... were executed!" (most
named test classes below live in `core`; SPIP-flow integration tests like
`OnboardingToolFlowIntegrationTest` live in `spip-plugin` — pass `-pl spip-plugin`
for those instead).
```bash
# Test with H2 database
mvn test -Dspring.profiles.active=test

# Integration tests with specific scenarios
mvn test -Dtest=OrganizationControllerTest

# Security tests
mvn test -Dtest=SecurityConfigTest
```

### Key Test Areas
- Authentication and authorization flows
- Organization and connector CRUD operations
- Role-based access control
- SPIP integration endpoints
- Database migration compatibility

### CKAN Integration Tests (E2E)

End-to-end tests for CKAN platform integration are located in `CkanIntegrationTest.java`.

**Prerequisites:**
- CKAN instance must be accessible at configured URL
- Valid JWT token configured in `application.yml`
- User must have permission to create/delete datasets

**Running CKAN E2E Tests:**
```bash
# Run all CKAN integration tests
mvn test -Dtest=CkanIntegrationTest -Dspring.profiles.active=dev

# Run by tag
mvn test -Dgroups=ckan -Dspring.profiles.active=dev
```

**Test Coverage:**
| Test | Description |
|------|-------------|
| Get dataset count | Tests `CkanApiClient.getDatasetCount()` |
| Dataset purge | Creates a dataset via `CkanApiClient.createDataset()` and purges it |

**Cleanup:** Tests automatically purge all created datasets (prefix `e2e_test_dataset_*`) after execution via `@AfterAll`.

**Key Classes:**
- `CkanApiClient` - HTTP client for CKAN API (`src/main/java/.../ckan/client/`)
- `CkanIntegrationTest` - E2E tests (`src/test/java/.../integration/`)

### Keycloak Integration Tests (E2E)

`KeycloakIntegrationTest` exercises the Keycloak Admin API client (token, group,
user, temporary password, group membership) against the dev container. Excluded
from `mvn test` like the CKAN suite.

```bash
docker compose -f docker-compose.keycloak.yml up -d
mvn test -Dgroups=keycloak -DexcludedGroups=
```

Fixtures use the `e2e-test-` prefix and are purged in `@AfterAll`.

## Common Development Tasks

### Adding a New User Role
1. Update `UserRole` enum with new role
2. Modify `SecurityConfig` URL patterns
3. Update Thymeleaf security expressions
4. Add role-specific navigation elements

### Integrating New External Systems
1. Create integration service in `integration/` package
2. Add configuration properties
3. Implement error handling and retry logic
4. Add integration tests with mock services

### Adding New Data Categories
1. Extend dashboard statistics
2. Create category-specific controllers
3. Add navigation menu items
4. Implement data filtering by category