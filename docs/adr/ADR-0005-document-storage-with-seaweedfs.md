# ADR-0005: Document Storage with Separate Documents Manager Application

## Status
Accepted

## Date
2026-01-14

## Context

The DATA4CIRC Portal requires document storage capabilities for organizations to manage files such as contracts, certificates, technical documentation, and other business documents. Several architectural forces shaped this decision:

**Storage Requirements:**
- Organizations need to upload, download, and manage documents securely
- Documents must be isolated per organization (multi-tenancy)
- Large file support (up to 50MB) for technical documentation and reports
- Date-organized folder structure for easy navigation and archival
- Integration with existing SPIP platform for attribute-based access control

**Security Requirements:**
- Authorization must leverage existing SPIP credentials (single sign-on pattern)
- Documents must be tenant-isolated (organizations cannot access each other's files)
- Encryption capabilities for sensitive documents
- Audit trail for all document operations

**Infrastructure Considerations:**
- The portal is a Spring Boot application focused on workflow orchestration
- Document storage has different scaling characteristics than web application data
- Large binary file handling adds complexity to the main application
- Object storage is more cost-effective and performant for documents than database BLOBs

**Operational Requirements:**
- Independent scaling of storage infrastructure
- Ability to backup and restore documents separately from application data
- Support for future expansion to other storage backends
- Minimal impact on portal application performance

## Decision

We will implement document storage as a **separate Documents Manager application** (`d4c-documents-manager`) that integrates with **SeaweedFS** for distributed object storage and uses **SPIP credentials** for authorization.

### Architecture Components

**1. Documents Manager Application:**
- Standalone Spring Boot 3.2.5 application
- REST API (`/api/v1/**`) for programmatic access
- Web UI (Thymeleaf + Bootstrap 5) for browser-based management
- OpenAPI/Swagger documentation at `/swagger-ui.html`
- Independent deployment and lifecycle from the portal

**2. SeaweedFS Storage Backend:**
- S3-compatible distributed object storage
- Filer component with PostgreSQL metadata backend
- Date-organized file storage pattern: `{bucket}/{yyyy}/{MM}/{dd}/{filename}`
- Configurable via `s3.json` for credentials and `filer.toml` for metadata storage
- Docker-based deployment with multiple configuration options

**3. Dual Authentication System:**

*REST API Authentication:*
- API Key authentication via `X-API-Key` header
- Supports hardcoded service keys (for portal integration)
- Supports SPIP credential fallback: Base64-encoded `username|password`
- 5-minute cache for SPIP authentication results (reduces SPIP API calls)

*Web UI Authentication:*
- Form-based login with SPIP integration
- Session-based authentication for browser users

**4. Multi-Tenancy Model:**
- Tenant isolation via bucket name prefixing: `{serviceName}-{bucketName}`
- SPIP username becomes the tenant identifier
- Shared buckets (prefixed with `internal-`) accessible to all tenants
- `TenantContext` uses ThreadLocal for request-scoped tenant isolation

**5. Background Job Processing:**
- `JobWorker` polls for pending encrypt/decrypt jobs
- Job storage in dedicated buckets: `internal-jobs-processing`, `internal-jobs-completed`, `internal-jobs-failed`
- SPIP integration for document encryption/decryption operations
- Asynchronous processing prevents blocking API requests

### Integration Pattern

```
┌─────────────────────┐     ┌─────────────────────┐     ┌─────────────────┐
│   DATA4CIRC Portal  │────▶│  Documents Manager  │────▶│   SeaweedFS     │
│   (d4c-portal)      │     │  (d4c-documents-    │     │   (S3 Storage)  │
│                     │     │   manager)          │     │                 │
└─────────────────────┘     └─────────────────────┘     └─────────────────┘
         │                           │
         │                           │
         ▼                           ▼
┌─────────────────────┐     ┌─────────────────────┐
│   SPIP Platform     │◀────│   PostgreSQL        │
│   (Authentication)  │     │   (Filer Metadata)  │
└─────────────────────┘     └─────────────────────┘
```

**Portal to Documents Manager:**
- Portal uses API Key (`X-API-Key: d4c-portal-dev-key`) for server-to-server calls
- Service name `data4circ-portal` used for tenant isolation
- Async operations return immediately; background jobs process encrypt/decrypt

**User Direct Access:**
- Users can authenticate to Documents Manager Web UI using SPIP credentials
- REST API accepts Base64-encoded SPIP credentials for direct integration

### API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/buckets` | List all buckets for tenant |
| POST | `/api/v1/buckets` | Create new bucket |
| GET | `/api/v1/buckets/{bucket}/documents` | List documents in bucket |
| POST | `/api/v1/buckets/{bucket}/documents` | Upload document |
| GET | `/api/v1/buckets/{bucket}/documents/download` | Download document |
| DELETE | `/api/v1/buckets/{bucket}/documents` | Delete document |
| POST | `/api/v1/buckets/{bucket}/jobs` | Submit encrypt/decrypt job |
| GET | `/api/v1/buckets/{bucket}/jobs/{id}` | Get job status |

### File Validation

- **Allowed extensions**: pdf, doc, docx, xls, xlsx, ppt, pptx, txt, jpg, jpeg, png, gif
- **Max file size**: 50 MB (configurable)
- **Filename sanitization**: Special characters removed, path traversal prevented

### SeaweedFS Deployment Options

Three deployment configurations are provided:

1. **Configuration 1**: Single-node with PostgreSQL filer backend (development)
2. **Configuration 2**: Single-node with LevelDB filer backend (simple setup)
3. **Configuration 3**: Multi-node cluster for high availability (production)

## Consequences

### Positive

- **Separation of Concerns**: Document storage logic isolated from portal business logic; each application can evolve independently
- **Scalability**: SeaweedFS scales horizontally for storage; Documents Manager can be replicated for API throughput
- **S3 Compatibility**: SeaweedFS S3 API allows future migration to AWS S3, MinIO, or other S3-compatible storage without application changes
- **SPIP Integration**: Leverages existing SPIP platform for authentication; no separate user database needed
- **Multi-Tenancy**: Bucket prefixing provides strong tenant isolation with minimal complexity
- **Cost Efficiency**: Object storage is significantly cheaper than database BLOB storage for large files
- **Background Processing**: Async job model keeps API responsive during expensive encrypt/decrypt operations
- **Operational Independence**: Documents can be backed up, restored, and migrated independently of portal data
- **Developer Experience**: OpenAPI/Swagger documentation enables easy API exploration and client generation
- **Flexible Authentication**: Dual auth system supports both service-to-service (API key) and user-direct (SPIP credentials) access patterns

### Negative

- **Additional Infrastructure**: Requires deploying and maintaining SeaweedFS cluster and Documents Manager application
- **Network Latency**: Document operations traverse multiple services (Portal → Documents Manager → SeaweedFS)
- **Operational Complexity**: Two applications to monitor, update, and troubleshoot instead of one
- **SPIP Dependency**: If SPIP is unavailable, SPIP-based authentication fails (mitigated by hardcoded API keys for service accounts)
- **Eventual Consistency**: Background jobs mean encryption status may not be immediately reflected after API call returns
- **PostgreSQL Requirement**: SeaweedFS filer with PostgreSQL backend requires additional database infrastructure (or use LevelDB for simpler setups)

### Neutral

- **Technology Stack Alignment**: Documents Manager uses same tech stack (Spring Boot, Thymeleaf, Bootstrap) as portal; consistent developer experience
- **Cache TTL Trade-off**: 5-minute SPIP auth cache reduces load but means credential changes have propagation delay
- **Date-based Organization**: Files organized by upload date; good for archival, may be less intuitive for topic-based browsing
- **API Key Management**: Hardcoded keys require application restart to rotate; acceptable for current scale
- **Single Region**: Current SeaweedFS setup is single-region; multi-region replication available but not configured

## Alternatives Considered

### Alternative 1: Database BLOB Storage

**Approach**: Store documents as BLOBs in PostgreSQL within the portal application.

**Pros:**
- Simplest implementation (no additional infrastructure)
- Single backup/restore process
- ACID guarantees for document metadata and content
- Leverages existing PostgreSQL deployment

**Cons:**
- Poor performance for large files (streaming, concurrent access)
- Database size grows rapidly; backup/restore times increase
- No S3 compatibility for future migration
- Memory pressure on database server
- Not designed for binary large objects

**Why rejected:** Documents are expected to be large (up to 50MB) and numerous. PostgreSQL BLOB storage does not scale cost-effectively and impacts database performance.

### Alternative 2: Direct S3/MinIO Integration in Portal

**Approach**: Add S3 client library to portal application; direct integration without separate Documents Manager.

**Pros:**
- Fewer moving parts (no separate application)
- Lower network latency (portal → S3 directly)
- Simpler deployment

**Cons:**
- Increases portal application complexity
- S3 operations mixed with business logic
- No dedicated API for document management
- Harder to scale document operations independently
- Background job processing adds complexity to portal

**Why rejected:** Adding document management responsibilities to the portal violates separation of concerns. The portal should focus on workflow orchestration, not file I/O.

### Alternative 3: Cloud Object Storage (AWS S3, Azure Blob)

**Approach**: Use managed cloud storage service directly.

**Pros:**
- No infrastructure to manage
- Built-in durability and availability
- Global distribution options
- Pay-per-use pricing

**Cons:**
- Vendor lock-in
- Data sovereignty concerns (data leaves on-premise infrastructure)
- Ongoing costs (storage + egress)
- Requires internet connectivity
- May conflict with data residency requirements

**Why rejected:** DATA4CIRC handles sensitive circular economy data. On-premise storage via SeaweedFS satisfies data sovereignty requirements while maintaining S3 compatibility for future cloud migration if needed.

### Alternative 4: NFS/CIFS File Share

**Approach**: Use network file system mounted to portal application server.

**Pros:**
- Simple to set up
- Works with existing file management tools
- No application changes needed

**Cons:**
- No S3 API; harder to migrate later
- Single point of failure without clustering
- Limited metadata capabilities
- No built-in tenant isolation
- Harder to scale horizontally

**Why rejected:** NFS lacks the S3 compatibility needed for future flexibility and does not provide the tenant isolation model required for multi-organization document management.

### Alternative 5: Integrated Document Feature in Portal

**Approach**: Build document management as a feature module within the portal, with local file system storage.

**Pros:**
- Single codebase to maintain
- Shared authentication
- Simpler deployment

**Cons:**
- Local file system doesn't scale
- No object storage benefits
- Backup complexity (application + files)
- Horizontal scaling requires shared storage
- Tight coupling of concerns

**Why rejected:** This approach doesn't separate storage concerns and makes scaling difficult. The separate application pattern provides cleaner boundaries.

## References

- [SeaweedFS Documentation](https://github.com/seaweedfs/seaweedfs/wiki)
- [AWS SDK for Java v2](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html)
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)
- DATA4CIRC Documents Manager Implementation:
  - Repository: `d4c-documents-manager`
  - `/src/main/java/com/d4c/seaweedfs/features/api/` - REST API controllers
  - `/src/main/java/com/d4c/seaweedfs/features/common/security/` - Authentication
  - `/src/main/java/com/d4c/seaweedfs/features/common/tenant/` - Multi-tenancy
  - `/deployment/seaweedfs/` - SeaweedFS Docker configurations
- Related ADRs:
  - ADR-0001 (SigNoz Observability) - Monitoring document operations
  - ADR-0004 (SSE Notifications) - Notifying users of document upload completion
