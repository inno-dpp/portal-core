# DATA4CIRC Portal — Technical Architecture Document

**Version:** 1.0
**Date:** February 2025
**Application Version:** 0.4.0-beta.7
**Reference:** WP3 Data Governance Platform Architecture Overview

---

## 1. Introduction

This document provides a complete technical description of the DATA4CIRC Governance Portal data infrastructure. It defines all platform modules, their functionalities, internal relationships, and integration points with external services that compose the WP3 Data Governance Platform.

The DATA4CIRC Portal is the centralized governance and administration layer for the DATA4CIRC data space. It orchestrates organization onboarding, identity provisioning, metadata catalog management, connector lifecycle, and security policy enforcement across the data space ecosystem described in the WP3 architecture.

### 1.1 Scope

This document covers:
- All internal modules of the DATA4CIRC Portal application
- Data model and entity relationships
- Integration interfaces with external platform services (SPIP, CKAN, Documents Manager)
- Security architecture and access control model
- Deployment topology and infrastructure dependencies

This document does **not** cover implementation-level details such as method signatures, class internals, or database query specifics.

### 1.2 Reference Architecture

This document complements the **WP3 Data Governance Platform Architecture Overview** (`WP3_Data_Governance_Platform_Architecture.md`), which defines the high-level architecture components and interaction patterns. The present document focuses on the internal architecture of the **Data Space Governance Portal** component and its relationships with the other WP3 components.

---

## 2. Technology Stack

| Layer | Technology |
|---|---|
| Runtime | Java 17, Spring Boot 3.2 |
| Web Framework | Spring MVC, Thymeleaf (server-side rendering) |
| Security | Spring Security, JWT (jjwt 0.11.5) |
| Persistence | Spring Data JPA, Hibernate |
| Database (Dev) | H2 (file-based or in-memory) |
| Database (Prod) | PostgreSQL 15 |
| Caching | Spring Cache with Caffeine |
| HTTP Client | Spring RestTemplate, Apache HttpClient 5 |
| Email | Spring Mail (AWS SES) |
| Real-time | Server-Sent Events (SSE) |
| Frontend | Bootstrap 5, jQuery, Thymeleaf templates |
| Observability | Spring Actuator, OpenTelemetry (SigNoz) |
| Containerization | Docker, Docker Compose |

---

## 3. Module Architecture

The portal is organized into a feature-based modular structure. Each module encapsulates its own controllers, services, entities, repositories, and DTOs. Cross-cutting concerns (security, configuration, error handling) reside in a shared `common` package.

### 3.1 Module Overview

```
┌─────────────────────────────────────────────────────────┐
│                   DATA4CIRC Portal                       │
├──────────────┬──────────────┬──────────────┬────────────┤
│ Organization │  Connectors  │   Dataset    │    SPIP    │
│  Management  │  Management  │  Publishing  │  Governance│
├──────────────┼──────────────┼──────────────┼────────────┤
│ Onboarding   │ Notification │    Guide     │            │
│    Sync      │   System     │              │            │
├──────────────┴──────────────┴──────────────┴────────────┤
│              Integration Layer                           │
│              (CKAN Client, SPIP Client)                 │
├─────────────────────────────────────────────────────────┤
│              Common Infrastructure                       │
│   (Security, Config, Caching, Email, Error Handling)    │
└─────────────────────────────────────────────────────────┘
```

### 3.2 Module Descriptions

#### 3.2.1 Organization Management Module

**Package:** `features.organization`

**Functionality:**
- Organization lifecycle management (creation, profile editing, member management)
- User account management (registration, authentication, password management)
- Organization directory (public listing of participating organizations)
- Role-based member management within organizations
- Self-service password reset via email token flow
- Forced password change on first login for onboarded users

**Key Entities:**
- `Organization` — Represents a participating company/institution with attributes such as type, industry sector, company size, certification status, and contact details
- `User` — Portal user implementing Spring Security `UserDetails`; linked to one organization with an assigned role
- `OrganizationOnboardingRequest` — Tracks the full onboarding request lifecycle from submission to approval, including SPIP/CKAN synchronization state and credentials
- `PasswordResetToken` — Time-limited token for self-service password reset

**User Roles:**
| Role | Scope | Purpose |
|---|---|---|
| `PLATFORM_ADMIN` | System-wide | Full platform administration, onboarding approval |
| `ORG_ADMIN` | Organization | Organization settings, member management |
| `SPIP_PRIVILEGED_USER` | Organization | SPIP dashboard access, policy/attribute management |
| `ORG_MEMBER` | Organization | Basic organization membership, read access |

**Relationships:**
- An `Organization` has many `Users` (members), many `Connectors`, and one `OrganizationSpipUser`
- An `OrganizationOnboardingRequest` is linked to an `Organization` upon approval
- `User` belongs to one `Organization` and has one `UserRole`

---

#### 3.2.2 Connectors Management Module

**Package:** `features.connectors`

**Functionality:**
- CRUD operations for data connectors scoped to organizations
- Connector type classification (Data Provider, Data Consumer, SPIP Agent, Platform Digital Tool, etc.)
- Real-time connector health monitoring via heartbeat mechanism
- Scheduled health checks for connectors with configured health endpoints
- Connector initialization during onboarding (template-based provisioning from configuration)
- Encrypted API token storage for connectors that require authenticated access

**Key Entities:**
- `Connector` — Represents a data integration endpoint belonging to an organization, with type, status, endpoint URL, health endpoint, heartbeat token, and encrypted API token

**Connector Types:**
| Type | Description |
|---|---|
| `DATA_PROVIDER` | Provides data to the data space |
| `DATA_CONSUMER` | Consumes data from the data space |
| `SPIP_AGENT` | Organization-level SPIP encryption/decryption agent |
| `PLATFORM_DIGITAL_TOOL` | External platform tool (DPP Demonstrator, BaSyx AAS, etc.) |
| `EXTERNAL_API` | External REST API integration |
| `DATABASE` | Database connector |
| `FILE_SYSTEM` | File system connector |
| `OBJECT_STORAGE_SYSTEM` | Object storage (e.g., SeaweedFS) |

**Connector Status Lifecycle:**
`ONLINE` ↔ `OFFLINE` ↔ `ERROR` ↔ `MAINTENANCE`

**Health Monitoring:**
- **Heartbeat (push model):** Connectors periodically call the portal's heartbeat API (`/api/connectors/heartbeat`) with their unique token. The `ConnectorHeartbeatService` updates last heartbeat timestamp. The `ConnectorHealthCheckService` runs on a configurable schedule to mark connectors with expired heartbeats as `OFFLINE`.
- **Health endpoint (pull model):** The portal actively polls connectors' configured health endpoints to verify availability.

**Connector Initialization:**
During onboarding approval, `OnboardingToolConnectorService` materializes connectors from the templates configured under `app.onboarding.tools.<key>.connector` in `application.yml`, tagging each with its tool key (`spip`, `ckan`, `edc`, ...). Provisioner-backed tools (SPIP, CKAN) are materialized only once synchronized — the CKAN connector receives the per-organization API token generated during CKAN synchronization — while config-only tools (e.g. EDC) are created unconditionally, including for organizations created directly by a platform admin.

**Relationships:**
- `Connector` belongs to one `Organization`
- CKAN Metadata Platform connector stores the per-organization API token generated during onboarding

---

#### 3.2.3 Dataset Publishing Module

**Package:** `features.dataset`

**Functionality:**
- Guided dataset publication wizard aligned with the DATA4CIRC metadata model (Deliverable D3.1)
- Circular economy domain-specific metadata fields (value chain phase, material category, process stage, DPP relevance, etc.)
- Dataset publishing to CKAN via the portal's CKAN API client, using per-organization authentication
- Local audit trail of all publishing attempts with request payload, CKAN response, and status tracking
- Dataset statistics aggregation for the dashboard (with Caffeine caching)

**Key Entities:**
- `PublishedDataset` — Audit record tracking each dataset publishing attempt, including the CKAN dataset ID, publish status, circular economy metadata, and full request/response payloads

**Domain Enumerations (Metadata Model):**
| Enum | Purpose |
|---|---|
| `ValueChainPhase` | Phase in circular economy value chain |
| `PhaseActivity` | Specific activity within a phase |
| `MaterialCategory` | Material classification |
| `MaterialSourceType` | Source type of material |
| `ProcessStage` | Manufacturing/processing stage |
| `CircularityIndicator` | Circularity metrics and indicators |
| `DppDataCategory` | Digital Product Passport data category |
| `DppRelevance` | DPP relevance classification |
| `RegulatoryFramework` | Applicable regulatory framework |
| `TraceabilityLevel` | Data traceability level |
| `DataFormat` | Data format classification |

**Relationships:**
- `PublishedDataset` references `User` (publisher) and `Organization`
- Publishing uses `CkanApiClient` with per-organization token and base URL

---

#### 3.2.4 SPIP Governance Module

**Package:** `features.spip`

**Functionality:**
- Read-only dashboard for viewing organization-level SPIP attributes (encryption/decryption)
- Read-only dashboard for viewing SPIP policies associated with the organization
- Cryptographic key status tracking (encryption/decryption keys with active/expired/revoked states)
- SPIP data synchronization — fetching latest attributes, policies, and key status from the SPIP Platform API and storing them locally
- Facade pattern (`SpipFacade` / `DefaultSpipFacade`) for abstracting SPIP API access from the presentation layer

**Key Entities:**
- `OrganizationSpipUser` — Maps an organization to its SPIP platform credentials (username, encrypted password) and tracks SPIP/CKAN synchronization state
- `SpipAttribute` — Locally cached SPIP attribute (encryption or decryption type) with metadata including logic group, data type, resource, assignee/assigner
- `SpipPolicy` — Locally cached SPIP policy with type (ENCRYPTION/DECRYPTION) and policy expression
- `SpipKeyStatus` — Tracks cryptographic key lifecycle (key type, identifier, status, generation/expiration dates)

**Relationships:**
- `OrganizationSpipUser` has a one-to-one relationship with `Organization`
- `SpipAttribute`, `SpipPolicy`, `SpipKeyStatus` belong to an `Organization`
- `OrganizationSpipUser` stores CKAN organization name for cross-platform identity linking

---

#### 3.2.5 Onboarding Synchronization Module

**Package:** `features.onboardingsync`

**Functionality:**
This module orchestrates the automated provisioning of organization resources across the SPIP and CKAN platforms during the onboarding approval workflow. It is the central coordination point that implements the onboarding process described in Section 4 of the WP3 architecture document.

**Sub-modules:**

**SPIP Onboarding (`onboardingsync.spip`):**
Executes a 7-step initialization sequence on the SPIP Platform:
1. Admin login (obtain SPIP admin bearer token)
2. Create SPIP user for the organization
3. Assign `new_data_owner` role to the user
4. Add encryption attributes (orgType, industrySector, orgId, data4circ_partner)
5. Add decryption attributes (symmetric to encryption)
6. Create organization-specific access policy
7. Create partner verification policy

Returns a detailed `SpipInitializationResult` with step-by-step status, enabling partial failure recovery and admin visibility.

**CKAN Onboarding (`onboardingsync.ckan`):**
Executes a 4-step idempotent synchronization with the CKAN platform:
1. Create organization in CKAN (or detect existing)
2. Create user in CKAN using SPIP credentials
3. Add user as editor member to the CKAN organization
4. Generate per-organization API token (non-fatal if fails)

The service is designed for safe retry after partial failures — each step handles `409 CONFLICT` responses gracefully, allowing the process to resume from the point of failure.

**Relationships:**
- Consumes `SpipApiClient` and `CkanApiClient` from the integration layer
- Writes per-tool sync state to `onboarding_tool_sync` (via `OnboardingToolSyncStateService`)
- Invokes `OnboardingToolConnectorService` to materialize tool connectors (the CKAN one carries the per-org API token)

---

#### 3.2.6 Notification Module

**Package:** `features.notification`

**Functionality:**
- Persistent notification storage for user-targeted messages
- Real-time notification delivery via Server-Sent Events (SSE)
- Notification types: onboarding events, system alerts, action required items
- Read/unread tracking with timestamps
- Action URLs for direct navigation to relevant pages

**Key Entities:**
- `Notification` — Persistent notification with recipient, type, title, message, action URL, and read status

**Notification Types:**
`ONBOARDING_REQUEST`, `ONBOARDING_APPROVED`, `ONBOARDING_REJECTED`, `SYSTEM_ALERT`, `INFO`

**Delivery Mechanism:**
`SseEmitterManager` maintains per-user SSE connections. When a notification is created, `NotificationService` persists it and simultaneously pushes a `NotificationEvent` DTO through the SSE channel to the connected client for real-time UI updates.

**Relationships:**
- `Notification` references `User` (recipient)
- `NotificationService` is called by `OnboardingRequestService` and other services that generate user-facing events

---

#### 3.2.7 Guide Module

**Package:** `features.guide`

**Functionality:**
- Public-facing onboarding guide page for prospective organizations
- Information about data space participation requirements and benefits
- Links to the onboarding request submission form

---

### 3.3 Common Infrastructure

**Package:** `common`

| Component | Responsibility |
|---|---|
| `SecurityConfig` | Spring Security configuration, URL pattern authorization, session-based `formLogin`, CORS policy |
| `CustomAuthenticationFailureHandler` | Custom handling for authentication failures |
| `EncryptedStringConverter` | JPA attribute converter for transparent AES encryption of sensitive fields (API tokens, passwords) in the database |
| `CkanConfig` | CKAN connection configuration, RestTemplate bean with properties binding |
| `SpipConfig` | SPIP connection configuration, RestTemplate bean with optional SSL verification bypass |
| `CacheConfig` | Caffeine cache configuration for dataset statistics |
| `DataInitializer` | Sample data seeding for development profile |
| `PasswordConfig` | BCrypt password encoder configuration |
| `ThemeProperties` | White-label branding configuration (colors, typography, logos, navbar) |
| `GlobalModelAttributesAdvice` | Injects branding and version attributes into all Thymeleaf templates |
| `GlobalExceptionHandler` | Centralized exception handling with custom error pages |
| `CorsConfig` | CORS policy for API endpoints |

---

## 4. Integration Layer

The integration layer provides API client abstractions for communicating with external platform services. All external communication is mediated through dedicated client classes with structured error handling.

### 4.1 SPIP Platform Integration

**Package:** `integration.spip`

**Client:** `SpipApiClient`

| API Operation | SPIP Endpoint | Purpose |
|---|---|---|
| Login (admin) | `POST /user/login` | Obtain bearer token for admin operations |
| Login (user) | `POST /user/login` | Obtain bearer token for organization user |
| Create user | `POST /user/add` | Create organization user in SPIP |
| Assign role | `POST /pap/role/assign` | Assign `new_data_owner` role |
| Add attributes | `POST /user/attribute/add` | Assign encryption/decryption attributes |
| Get attributes (admin) | `GET /user/attribute/get` | Fetch user attributes (admin perspective) |
| Get attributes (user) | `GET /attribute/get` | Fetch attributes (user's own perspective) |
| Create policy | `POST /policy_builder/add` | Create ABE access policy |
| Get policies | `GET /policy_builder/get` | Fetch user's policies |

**Authentication:** Bearer token obtained via admin login, passed in `Authorization` header.

**SSL:** Configurable SSL verification bypass (`SPIP_DISABLE_SSL_VERIFICATION`) for development environments where SPIP uses self-signed certificates. Uses Apache HttpClient 5.

**Integration DTOs:**
`SpipLoginRequest/Response`, `SpipCreateUserRequest/Response`, `SpipAssignRoleRequest`, `SpipAttributeRequest/Response`, `SpipPolicyRequest/Response`

---

### 4.2 CKAN Platform Integration

**Package:** `integration.ckan`

#### 4.2.1 CKAN API Client

**Client:** `CkanApiClient`

| API Operation | CKAN Endpoint | Purpose |
|---|---|---|
| Create dataset | `POST /api/3/action/package_create` | Publish dataset metadata |
| Search datasets | `GET /api/3/action/package_search` | Count/search datasets |
| Purge dataset | `POST /api/3/action/dataset_purge` | Permanently delete dataset |
| Create organization | `POST /api/3/action/organization_create` | Create CKAN organization during onboarding |
| Check organization | `GET /api/3/action/organization_show` | Verify organization existence |
| Create user | `POST /api/3/action/user_create` | Create CKAN user during onboarding |
| Add user to org | `POST /api/3/action/organization_member_create` | Assign user to organization |
| Create API token | `POST /api/3/action/api_token_create` | Generate per-organization API token |

**Authentication Model (dual-token):**
- **Global admin token:** Configured via `CKAN_JWT_TOKEN` environment variable. Used for administrative operations during onboarding (org/user creation).
- **Per-organization token:** Generated during onboarding Step 4, stored encrypted on `Connector.apiToken` of the organization's CKAN Metadata Platform connector, and delivered to the organization in the approval email for direct CKAN API access.

The `CkanApiClient` supports both global base URL and per-organization base URL for dataset creation, enabling multi-instance CKAN deployments.

> **Note (2026-07):** The former CKAN webhook service (`integration.ckan.webhook`, public endpoint `/webhook/ckan`) that accepted dataset creation requests from external SPIP clients has been removed, together with the `ckan_dataset_requests` audit table. Dataset publication now happens only through the authenticated portal form (`DatasetPublishService`).

---

### 4.3 Email Service Integration

**Service:** `EmailService`

The portal integrates with AWS Simple Email Service (SES) via SMTP for transactional emails:

| Email Type | Trigger | Template |
|---|---|---|
| Onboarding confirmation | Request submitted | `email/onboarding-confirmation.html` |
| Onboarding approved | Admin approves request | `email/onboarding-approved.html` |
| Onboarding rejected | Admin rejects request | `email/onboarding-rejected.html` |
| Member invitation | Admin invites member | `email/member-invitation.html` |
| Password reset | User requests reset | `email/forgot-password.html` |

All emails are rendered via Thymeleaf templates and sent asynchronously. Email sending can be globally disabled via `EMAIL_ENABLED=false`.

---

## 5. Data Model

### 5.1 Entity Relationship Overview

```
OrganizationOnboardingRequest ──┐
                                │ approved
                                ▼
                          Organization ◄──────── User (members)
                           │   │   │
                           │   │   └──────────── Connector (1..N)
                           │   │                    │
                           │   │                    └── apiToken (encrypted)
                           │   │
                           │   └──── OrganizationSpipUser (1:1)
                           │              │
                           │              ├── spipUser / spipPassword
                           │              └── ckanOrganizationName
                           │
                           ├──── SpipAttribute (1..N)
                           ├──── SpipPolicy (1..N)
                           ├──── SpipKeyStatus (1..N)
                           └──── PublishedDataset (1..N)

Notification ──────────── User (recipient)

CkanDatasetRequest (standalone audit log)

PasswordResetToken ──── User
```

### 5.2 Database Tables

| Table | Entity | Description |
|---|---|---|
| `organizations` | `Organization` | Participating organizations |
| `users` | `User` | Portal user accounts |
| `connectors` | `Connector` | Data integration endpoints |
| `organization_onboarding_requests` | `OrganizationOnboardingRequest` | Onboarding request lifecycle |
| `organization_spip_users` | `OrganizationSpipUser` | SPIP credentials per organization |
| `spip_attributes` | `SpipAttribute` | Cached SPIP ABE attributes |
| `spip_policies` | `SpipPolicy` | Cached SPIP ABE policies |
| `spip_key_status` | `SpipKeyStatus` | Cryptographic key status |
| `published_datasets` | `PublishedDataset` | Dataset publishing audit trail |
| `notifications` | `Notification` | User notifications |
| `password_reset_tokens` | `PasswordResetToken` | Password reset tokens |

### 5.3 Sensitive Data Protection

Fields containing secrets are encrypted at rest using `EncryptedStringConverter` (AES symmetric encryption with a 32-character key from `ENCRYPTION_KEY` environment variable):

| Entity | Field | Content |
|---|---|---|
| `Connector` | `apiToken` | Per-organization CKAN API token |
| `OrganizationSpipUser` | `spipPassword` | Organization's SPIP password |
| `OrganizationOnboardingRequest` | `spipPassword` | Temporary SPIP password during onboarding |
| `OrganizationOnboardingRequest` | `ckanApiToken` | Temporary CKAN token during onboarding |

All encrypted fields use `@Column(length = 1024)` to accommodate ciphertext expansion.

---

## 6. Security Architecture

### 6.1 Authentication

The portal uses **session-based authentication** via Spring Security's own `formLogin`:

1. User submits credentials via the login form
2. Spring Security authenticates against the database (BCrypt-hashed passwords)
3. On success, Spring Security establishes an `HttpSession`, tracked via the standard
   `JSESSIONID` cookie
4. Subsequent requests are authenticated by the session; the `SecurityContext` is populated from
   it automatically by Spring Security's own filter chain — there is no separate token-issuing or
   token-validating layer

*(An earlier JWT-based layer — `JwtUtil`/`JwtAuthenticationFilter`, `JWT_SECRET` — existed
alongside this but was removed as dead code: it was never wired to actually issue a token during
login, so the validating filter could never succeed in practice. See git history if this is
revisited, e.g. as part of a move to an external identity provider.)*

### 6.2 Authorization

**URL-based authorization** is enforced in `SecurityConfig`:
- Public paths: `/`, `/login`, `/join`, `/guide`, `/forgot-password`, `/reset-password`, `/api/connectors/heartbeat`
- Admin paths: `/admin/**` restricted to `PLATFORM_ADMIN`
- All other paths require authentication
- Method-level authorization via `@PreAuthorize` annotations where finer-grained control is needed

**Organization-scoped access control:**
Services enforce that users can only access resources belonging to their own organization (except `PLATFORM_ADMIN` who has cross-organization access).

### 6.3 External API Security

| Endpoint | Authentication Method |
|---|---|
| Portal Web UI | JWT in HTTP-only cookie |
| Connector Heartbeat API | Unique heartbeat token per connector |
| SPIP Platform API | Bearer token from SPIP admin login |
| CKAN Platform API | JWT token (global admin or per-organization) |

---

## 7. External Service Relationships

This section maps how the DATA4CIRC Portal relates to the other WP3 architecture components described in `WP3_Data_Governance_Platform_Architecture.md`.

### 7.1 Relationship Diagram

```
┌──────────────────────────────────────────────────────────────┐
│             Data Space Trust & Governance Services            │
│                                                              │
│  ┌───────────────────┐  ┌─────────────┐  ┌──────────────┐  │
│  │  DATA4CIRC Portal │  │    SPIP     │  │     CKAN     │  │
│  │  (This Document)  │◄►│  Platform   │  │   Catalog    │  │
│  │                   │◄►│             │  │              │  │
│  │                   │◄►│             │  │              │  │
│  └───────┬───────────┘  └──────┬──────┘  └──────┬───────┘  │
│          │                     │                 │           │
└──────────┼─────────────────────┼─────────────────┼───────────┘
           │                     │                 │
           │      ┌──────────────┼─────────────────┤
           │      │              │                 │
┌──────────┼──────┼──────────────┼─────────────────┼───────────┐
│          ▼      ▼              ▼                 ▼           │
│  Organization Data Space Services (per organization)         │
│                                                              │
│  ┌──────────────┐  ┌───────────┐  ┌──────────────────────┐  │
│  │  Documents    │  │   SPIP    │  │   Data Space         │  │
│  │  Manager      │  │   Agent   │  │   Connector          │  │
│  └──────┬───────┘  └───────────┘  └──────────────────────┘  │
│         │                                                    │
│  ┌──────┴───────┐                                           │
│  │  SeaweedFS   │                                           │
│  │  Storage     │                                           │
│  └──────────────┘                                           │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 Integration Points

#### Portal → SPIP Platform

| Integration | Direction | Protocol | Purpose |
|---|---|---|---|
| Onboarding provisioning | Portal → SPIP | REST API | Create user, assign role, add attributes, create policies |
| Attribute synchronization | Portal → SPIP | REST API | Fetch latest attributes for dashboard display |
| Policy synchronization | Portal → SPIP | REST API | Fetch latest policies for dashboard display |
| Key status retrieval | Portal → SPIP | REST API | Check cryptographic key status |
| User redirect | Portal → SPIP | HTTP redirect | Link to SPIP web dashboard for manual operations |

#### Portal → CKAN Catalog

| Integration | Direction | Protocol | Purpose |
|---|---|---|---|
| Onboarding provisioning | Portal → CKAN | REST API | Create organization, user, membership, API token |
| Dataset publishing | Portal → CKAN | REST API | Publish datasets via guided wizard |
| Dataset statistics | Portal → CKAN | REST API | Retrieve dataset count for dashboard |
| User redirect | Portal → CKAN | HTTP redirect | Link to CKAN web interface for manual operations |

#### Portal ← Connectors (Health Monitoring)

| Integration | Direction | Protocol | Purpose |
|---|---|---|---|
| Heartbeat | Connector → Portal | REST API (push) | Report connector liveness |
| Health check | Portal → Connector | HTTP GET (pull) | Actively verify connector availability |

---

## 8. Onboarding Workflow — Technical Flow

The onboarding process is the most complex cross-system workflow in the platform. The following describes the complete technical flow from request submission to organization activation.

### 8.1 Request Submission Phase
1. Prospective organization representative fills the onboarding form (public, unauthenticated)
2. `OnboardingController` validates input and creates `OrganizationOnboardingRequest` with status `PENDING`
3. `NotificationService` sends real-time notification to all `PLATFORM_ADMIN` users
4. `EmailService` sends confirmation email to the requester

### 8.2 Admin Review Phase
1. Platform admin reviews the request in the admin dashboard
2. Admin can approve, reject, or request additional information

### 8.3 Approval — SPIP Synchronization
1. Admin triggers approval → `OnboardingRequestService` generates SPIP credentials (username derived from company name, random password)
2. `SpipOnboardingService.initializeOrganization()` executes the 7-step SPIP provisioning:
   - Admin login → User creation → Role assignment → Encryption attributes → Decryption attributes → Access policy → Partner verification policy
3. Results stored on `OrganizationOnboardingRequest` (`spipSynchronized` flag, credentials, initialization log)

### 8.4 Approval — CKAN Synchronization
1. `CkanOnboardingSyncService.synchronizeOnboardingRequest()` executes the 4-step CKAN provisioning:
   - Organization creation → User creation → Membership assignment → API token generation
2. Generated CKAN API token stored temporarily on `OrganizationOnboardingRequest.ckanApiToken`
3. CKAN organization short name stored on the request for reference

### 8.5 Organization Activation
1. Portal creates the `Organization` entity from request data
2. Portal creates the `OrganizationSpipUser` entity with SPIP credentials and CKAN org name
3. `OnboardingToolConnectorService` materializes tool connectors from the `app.onboarding.tools` templates, tagging each with its tool key and setting the CKAN API token on the CKAN connector
4. Portal creates the `User` entity (ORG_ADMIN role) with temporary password and `mustChangePassword = true`
5. `EmailService` sends approval email with both credential sets (Portal + SPIP/CKAN) and the CKAN API token
6. `OnboardingRequestStatus` updated to `APPROVED`
7. Notification sent to the new user

---

## 9. Deployment Architecture

### 9.1 Development Environment

```
┌─────────────────────────────┐
│   Spring Boot Application   │
│   (Profile: dev)            │
│   Port: 8080                │
│                             │
│   ┌─────────────────────┐   │
│   │  Embedded H2 DB     │   │
│   │  (File: ./data/)    │   │
│   └─────────────────────┘   │
│                             │
│   ┌─────────────────────┐   │
│   │  H2 Console         │   │
│   │  (/h2-console)      │   │
│   └─────────────────────┘   │
└─────────────────────────────┘
         │
         ▼ REST API calls
   External Services
   (SPIP, CKAN on your data-space domain)
```

### 9.2 Production Environment (Docker)

```
┌─── Docker Compose ──────────────────────────────┐
│                                                  │
│  ┌────────────────────────────┐                 │
│  │  d4c-portal-app            │                 │
│  │  (Spring Boot + OTel Agent)│                 │
│  │  Port: 8080                │                 │
│  └──────────┬─────────────────┘                 │
│             │                                    │
│  ┌──────────┴─────────────────┐                 │
│  │  PostgreSQL 15             │                 │
│  │  (d4c-portal-postgres)     │                 │
│  │  Port: 5432                │                 │
│  └────────────────────────────┘                 │
│                                                  │
│  Network: d4c-network (bridge)                  │
└──────────────────────────────────────────────────┘
         │
         ▼ REST API + SMTP
   ┌─────────────────┐  ┌──────────┐  ┌─────────┐
   │ SPIP Platform   │  │   CKAN   │  │ AWS SES │
   │ (Port 8002)     │  │ Catalog  │  │ (SMTP)  │
   └─────────────────┘  └──────────┘  └─────────┘
         │
         ▼ OpenTelemetry (gRPC)
   ┌──────────────┐
   │    SigNoz    │
   │ (Port 4317)  │
   └──────────────┘
```

### 9.3 Environment Configuration

| Variable | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Profile selection (dev, prod, docker, test) |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `ENCRYPTION_KEY` | AES key for encrypting sensitive DB fields (32 chars) |
| `SPIP_BASE_URL`, `SPIP_ADMIN_USERNAME`, `SPIP_ADMIN_PASSWORD` | SPIP Platform connection |
| `SPIP_CUSTOMER_TAG` | SPIP customer identifier |
| `CKAN_BASE_URL`, `CKAN_JWT_TOKEN` | CKAN global admin connection |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP configuration |
| `APP_BASE_URL` | Public URL for email links |
| `BRANDING_*` | White-label branding overrides |
| `OTEL_*` | OpenTelemetry/SigNoz observability |

---

## 10. Preconfigured Connectors

The following connectors are automatically provisioned for each organization during onboarding, representing the tools available in the DATA4CIRC data space ecosystem:

| Connector | Type | Description | External Service |
|---|---|---|---|
| CKAN Metadata Platform | Platform Digital Tool | Metadata publishing and cataloging | CKAN instance |
| SPIP Platform | Platform Digital Tool | Security, identity, and encryption management | SPIP web dashboard |
| Documents Manager | Platform Digital Tool | Document upload, encryption/decryption | SeaweedFS-backed app |
| DPP Demonstrator | Platform Digital Tool | Digital Product Passport creation | DPP tool |
| BaSyx AAS Infrastructure | Platform Digital Tool | Asset Administration Shell demonstrator | BaSyx server |

---

## 11. API Endpoints Summary

### 11.1 Web UI Routes (Authenticated)

| Route | Controller | Function |
|---|---|---|
| `/dashboard` | `DashboardController` | Main dashboard with statistics |
| `/organizations` | `OrganizationController` | Organization directory and management |
| `/connectors` | `ConnectorController` | Connector CRUD and monitoring |
| `/datasets/publish` | `DatasetPublishController` | Guided dataset publication |
| `/datasets` | `DatasetPublishController` | Dataset publishing history |
| `/spip/dashboard` | `SpipController` | SPIP attributes, policies, keys viewer |
| `/profile` | `ProfileController` | User profile and password management |
| `/admin/onboarding` | `OnboardingController` | Admin onboarding request management |

### 11.2 Public Routes

| Route | Controller | Function |
|---|---|---|
| `/login` | `AuthController` | Login page |
| `/join` | `OnboardingController` | Onboarding request form |
| `/guide` | `GuideController` | Platform guide |
| `/forgot-password` | `AuthController` | Password reset request |
| `/reset-password` | `AuthController` | Password reset form |

### 11.3 API Endpoints

| Route | Method | Authentication | Function |
|---|---|---|---|
| `/api/connectors/heartbeat` | POST | Heartbeat token | Connector liveness report |
| `/api/notifications/stream` | GET | JWT | SSE notification stream |
| `/api/spip/sync` | POST | JWT (Admin) | Trigger SPIP data sync |

---

## 12. Observability

### 12.1 Health Monitoring

Spring Actuator exposes health and info endpoints at `/actuator/health` and `/actuator/info`. In production, detailed health information is restricted to authorized users.

### 12.2 Distributed Tracing

The production deployment includes the OpenTelemetry Java agent configured to export traces, metrics, and logs to a SigNoz instance via gRPC (port 4317). Instrumentation covers:
- JDBC queries
- Spring Web (incoming HTTP requests)
- Spring WebMVC (controller processing)
- HTTP client calls (outgoing requests to SPIP and CKAN)

### 12.3 Application Logging

Structured logging with SLF4J/Logback. Integration API calls include structured markers (`=== CKAN API Call: ... ===`) for correlation.

---

## 13. White-Label Branding

The portal supports complete white-label customization via environment variables, enabling deployment for different projects or organizations without code changes:

| Aspect | Configuration |
|---|---|
| Brand name & taglines | `BRANDING_NAME`, `BRANDING_TAGLINE`, `BRANDING_SUBTAGLINE` |
| Color palette | `BRANDING_COLOR_PRIMARY`, `BRANDING_COLOR_SECONDARY`, `BRANDING_COLOR_ACCENT`, etc. |
| Typography | `BRANDING_FONT_FAMILY`, `BRANDING_HEADING_FONT_FAMILY` |
| Logo & assets | `BRANDING_LOGO`, `BRANDING_LOGO_SMALL`, `BRANDING_FAVICON` |
| Navigation bar | `BRANDING_NAVBAR_BACKGROUND`, `BRANDING_NAVBAR_TEXT_COLOR` |

Branding values are injected into all Thymeleaf templates via `GlobalModelAttributesAdvice`.

---

## 14. References

- **WP3 Data Governance Platform Architecture Overview** — `docs/architecture/WP3_Data_Governance_Platform_Architecture.md`
- **D3.1 — Data Availability and Characterisation** — Defines the metadata model and CKAN configuration
- **CKAN Per-Organization API Tokens** — `docs/developer/ckan/CKAN-PER-ORG-API-TOKENS.md`
- **SPIP Encryption & Policy Documentation** — Separate document detailing ABE mechanisms
- **SEKURRA Data Governance Platform** — Foundational security platform documentation
