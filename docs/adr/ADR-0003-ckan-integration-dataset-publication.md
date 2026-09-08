# ADR-0003: CKAN Integration for Dataset Publication

## Status
Accepted — partially superseded (2026-07): the webhook-based publication flow described below (`CkanWebhookController`, public `/webhook/ckan` endpoint) was removed for security reasons (unauthenticated write path with unverified JWT identity). Dataset publication is now available only through the authenticated web form flow. The rest of this ADR remains accurate.

## Date
2025-12-26

## Context

The DATA4CIRC Portal serves as a gateway for circular economy organizations to share datasets within the 4-phase value chain framework. Organizations need to publish structured datasets containing circular economy metadata (value chain phases, material categories, DPP relevance, traceability information) that comply with regulatory frameworks and enable cross-organizational data discovery.

Several architectural forces shaped this decision:

**Discovery & Cataloging Requirements:**
- Organizations need a searchable catalog to discover datasets across the circular economy ecosystem
- Datasets must be tagged and categorized by value chain phase (Collection & Assessment, Processing & Recovery, Transformation & Manufacturing, Validation & Certification)
- Metadata standards must accommodate Digital Product Passport (DPP) requirements and regulatory frameworks
- The solution must support both private (organization-scoped) and public datasets

**Integration Architecture:**
- The portal already integrates with SPIP (Secure Privacy-Preserving Infrastructure Platform) for secure data exchange
- SPIP clients need to publish datasets programmatically via API/webhook without web UI access
- Organizations access the portal through web forms, while SPIP agents use REST APIs
- The data catalog must be accessible to both portal users and external SPIP components

**Data Quality & Validation:**
- Form inputs from organization users require validation before submission to the catalog
- Circular economy metadata must conform to standardized enumerations and taxonomies
- The portal must track publication history and maintain audit trails
- Dataset names must follow CKAN-compatible slug conventions (lowercase, hyphenated)

**Security & Access Control:**
- Dataset publication must respect organization boundaries
- Users should only publish datasets under their organization's CKAN organization
- JWT-based authentication is required for programmatic access
- Organization information must be automatically attached to published datasets

**Existing Infrastructure:**
- The portal is built on Spring Boot with JPA/PostgreSQL backend
- CKAN is already deployed and accessible in the environment
- Organizations are onboarded through a multi-step workflow that includes SPIP user provisioning

## Decision

We will integrate with CKAN (Comprehensive Knowledge Archive Network) as the external data catalog platform for dataset publication, using a dual-interface approach that supports both web-based form submission (for organization users) and webhook-based API submission (for SPIP agents).

### Architecture Components

**1. User-Facing Publication Flow (Portal Web Forms):**
- `DatasetPublishController` provides web forms at `/datasets/publish` for authenticated organization users (ORG_ADMIN and ORG_MEMBER roles)
- `DatasetPublishFormDTO` captures 3 tiers of metadata:
  - **Mandatory fields**: Title, value chain phase, phase activity, material category
  - **High priority fields**: Circularity indicator, DPP relevance, regulatory framework, traceability level
  - **Optional context fields**: Process stage, material source type, DPP data category, data format
- Server-side validation ensures phase activities match their parent phases before submission
- `DatasetPublishService.publishDataset()` orchestrates the two-phase publication process

**2. SPIP Client Publication Flow (Webhook API):**
- `CkanWebhookController` exposes public endpoint `/webhook/ckan` (no authentication required)
- Accepts flat key-value payloads from SPIP clients via HTTP POST
- `CkanDatasetMapper` transforms flat inputs to structured CKAN schema using prefix-based field categorization:
  - Root fields: `name`, `title`, `notes`, `owner_org`, `license_id`, `private`
  - Indexed resources: `resource_0_url`, `resource_0_name`, `resource_1_url`, etc.
  - Indexed groups: `group_0_name`, `group_1_name`
  - Extras: `extra_*` prefix or unmapped fields automatically added to extras array
- `CkanWebhookService` extracts SPIP client identity from JWT token (Authorization header)
- Enriches datasets with organization metadata by looking up SPIP user in `organization_spip_users` table

**3. CKAN API Client Integration:**
- `CkanApiClient` wraps CKAN REST API calls (`package_create`, `package_search`, `dataset_purge`)
- Uses dedicated `RestTemplate` bean with 30-second timeouts for reliable HTTP communication
- Authentication via JWT token stored in `app.ckan.jwt-token` configuration property
- Supports future per-organization token management (currently single shared token)

**4. Data Mapping Strategy:**
- Portal form enums map to human-readable CKAN extras (e.g., `ValueChainPhase.PHASE_1` → `"Phase 1: Collection & Assessment"`)
- All datasets automatically tagged with `4phase-compliant` to identify DATA4CIRC-compliant datasets
- Organization metadata added to extras: `D4C Organization Name`, `D4C Organization Website`, `D4C Organization Industry Sector`, `D4C Organization Type`, `D4C Organization Primary Contact`
- Dataset `owner_org` field set from `OrganizationSpipUser.ckanOrganizationName` or fallback to default organization
- All portal-published datasets default to `private: true` visibility (configurable via `app.ckan.upload-dataset-private`)

**5. Local Audit Trail:**
- `PublishedDataset` entity tracks all publication attempts in portal database
- Stores request payload, CKAN response, status (PENDING/SUCCESS/FAILED), timestamps
- Enables troubleshooting, compliance reporting, and user publication history
- Survives CKAN dataset deletion (provides persistent local record)

**6. Security Model:**
- Webhook endpoint `/webhook/ckan/**` explicitly exempted from CSRF protection in `SecurityConfig`
- Webhook endpoint publicly accessible (permits external SPIP agents to call without portal session)
- JWT token in Authorization header decoded to extract SPIP client username (supports Keycloak `preferred_username` claim)
- Portal web forms protected by Spring Security authentication (session-based) and role checks (`@PreAuthorize`)
- CKAN API calls authenticated with JWT token from configuration

### Two-Phase Publication Process

Both publication flows follow the same two-phase pattern:

**Phase 1: Portal Validation & Recording**
1. Receive dataset metadata (from form or webhook)
2. Validate required fields and business rules (phase/activity consistency)
3. Create `PublishedDataset` record with status PENDING
4. Transform to CKAN-compatible structure
5. Store original payload as JSON for audit

**Phase 2: CKAN Submission**
1. Call `CkanApiClient.createDataset()` with structured payload
2. On success: Update `PublishedDataset` with status SUCCESS and CKAN dataset ID
3. On failure: Update `PublishedDataset` with status FAILED and error message
4. Return result to user/caller

This ensures the portal maintains complete publication history even if CKAN is temporarily unavailable.

## Consequences

### Positive

- **Proven Technology**: CKAN is mature, widely-adopted open-source data catalog software with robust API, search, and metadata capabilities
- **Separation of Concerns**: Portal focuses on authentication, validation, and business logic while CKAN handles catalog storage, search, and discoverability
- **Dual Interface**: Supports both human users (web forms) and programmatic clients (webhooks) with consistent backend processing
- **Audit Trail**: Local `published_datasets` table provides compliance records, debugging capability, and publication analytics
- **Standardized Metadata**: CKAN extras model accommodates circular economy domain metadata without custom CKAN schema modifications
- **Flexibility**: Flat-to-structured mapping in webhook flow allows SPIP clients to submit simple key-value pairs
- **Organization Scoping**: Automatic enrichment with organization metadata ensures datasets are properly attributed and categorized
- **Mandatory Compliance Tag**: `4phase-compliant` tag enables ecosystem-wide filtering of DATA4CIRC datasets

### Negative

- **External Dependency**: Portal availability now depends on CKAN platform health (mitigated by two-phase design that records failures)
- **API Token Management**: Currently uses single shared JWT token; future multi-organization deployments may require per-organization token storage and rotation
- **Schema Evolution**: Changes to CKAN metadata schema require coordinated updates to `DatasetPublishFormDTO`, `CkanDatasetMapper`, and Thymeleaf templates
- **No Rollback**: Dataset publication to CKAN is one-way; deletions must be performed separately (requires admin intervention)
- **Webhook Security**: Public webhook endpoint relies on optional JWT parsing; malicious actors could spam dataset requests (future: implement rate limiting or API key requirement)

### Neutral

- **Private by Default**: Configuration `upload-dataset-private: true` ensures datasets are organization-scoped unless explicitly made public (aligns with data sensitivity requirements but may limit discoverability)
- **Slug Generation**: Automatic title-to-slug conversion (`title.toLowerCase().replaceAll()`) prevents Unicode and special characters but may cause collisions for similar titles
- **Extras-Based Metadata**: Using CKAN extras for circular economy fields is flexible but doesn't provide native CKAN UI widgets or faceted search (requires custom CKAN frontend development)
- **Synchronous API Calls**: Dataset publication is synchronous (user waits for CKAN response); future high-volume scenarios may require async job queue (e.g., Spring @Async, Redis queue)

## Alternatives Considered

### Alternative 1: Build Custom Data Catalog
**Pros:**
- Full control over schema, UI, and search behavior
- No external dependencies or integration complexity
- Custom circular economy metadata as first-class fields

**Cons:**
- Significant development effort (search indexing, faceted search, UI/UX, APIs)
- Reinventing well-solved problems (metadata standards, OAI-PMH, DCAT)
- Maintenance burden for catalog features, performance, and scalability
- Limited interoperability with external data portals

**Why rejected:** Building a data catalog from scratch would divert resources from core circular economy features and delay time-to-market. CKAN provides enterprise-grade catalog functionality out-of-the-box.

### Alternative 2: Direct Database-to-CKAN Sync
**Pros:**
- No webhook/API layer needed
- Simpler architecture (fewer components)

**Cons:**
- Tight coupling between portal database schema and CKAN metadata
- No validation or transformation layer
- Difficult to support SPIP client submissions
- Complex failure handling and rollback scenarios
- Poor audit trail (hard to distinguish user vs. system changes)

**Why rejected:** Lacks flexibility for dual-interface support and violates separation of concerns. The two-phase approach provides better error handling and audit capabilities.

### Alternative 3: CKAN as Primary Database
**Pros:**
- Single source of truth for datasets
- No synchronization lag
- Reduced database complexity

**Cons:**
- Portal becomes tightly coupled to CKAN schema and API
- Difficult to implement portal-specific business logic (workflow states, approvals)
- Limited query performance for portal-specific views (user history, organization statistics)
- CKAN API rate limits could impact portal responsiveness
- Local audit trail would be lost

**Why rejected:** Portal requires rich domain models (organizations, users, onboarding requests) and business workflows that don't map cleanly to CKAN's package-centric model. Maintaining local entities provides query performance and resilience.

### Alternative 4: Message Queue (RabbitMQ/Kafka) for Async Publication
**Pros:**
- Decouples portal from CKAN availability
- Supports high-volume batch submissions
- Better scalability for concurrent requests

**Cons:**
- Added infrastructure complexity (queue deployment, monitoring)
- Eventual consistency (user doesn't get immediate feedback)
- Requires background worker processes and job status polling
- Overkill for current volume (<100 datasets/day expected)

**Why rejected:** Current requirements don't justify message queue complexity. The two-phase design with local persistence provides sufficient resilience. Can be reconsidered if publication volume exceeds 1000/day or CKAN latency becomes problematic.

## References

- [CKAN API Documentation](https://docs.ckan.org/en/latest/api/)
- [CKAN Data Model - Packages and Resources](https://docs.ckan.org/en/latest/user-guide.html#what-is-ckan)
- DATA4CIRC Portal Integration Tests: `/src/test/java/com/data4circ/portal/integration/CkanIntegrationTest.java`
- CKAN Testing Guide: `/docs/developer/ckan/testing/CKAN-TESTING-GUIDE.md`
- Related ADR: ADR-0001 (SigNoz Observability Integration)
