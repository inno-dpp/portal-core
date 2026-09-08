# Connector REST API

Public REST API for querying organization connectors by organization name or SPIP username, secured with a static API key.

## Key Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `ConnectorApiController` | `connectors/controller/` | REST endpoints for listing and fetching connectors |
| `ConnectorResponse` | `connectors/dto/` | Response DTO with all connector fields |
| `ConnectorService` | `connectors/service/` | Business logic for connector queries |
| `OrganizationSpipUserRepository` | `spip/repository/` | SPIP username lookup |

## Authentication

All endpoints require a static API key passed via the `X-API-Key` header.

The key is configured in `application.yml`:

```yaml
app:
  api:
    key: ${API_KEY:dev-api-key-change-in-production}
```

Override in production via the `API_KEY` environment variable.

## Endpoints

### List Connectors by Organization

```
GET /api/connectors?orgName={name}
GET /api/connectors?spipUser={spipUsername}
```

Exactly one of `orgName` or `spipUser` must be provided.

**Example Request:**

```bash
curl -H "X-API-Key: dev-api-key-change-in-production" \
  "http://localhost:8080/api/connectors?orgName=GreenTech%20Solutions"
```

**Example Response (200 OK):**

```json
[
  {
    "id": 1,
    "name": "CKAN Metadata Platform",
    "description": "Metadata management platform...",
    "type": "DATA_PROVIDER",
    "endpoint": "http://ckan.example.com/",
    "status": "ONLINE",
    "configuration": "{...}",
    "lastHeartbeat": "2026-04-07T10:30:00",
    "healthEndpoint": "http://ckan.example.com/health",
    "heartbeatToken": "abc123",
    "apiToken": "xyz789",
    "createdAt": "2026-01-15T08:00:00",
    "updatedAt": "2026-04-01T12:00:00"
  }
]
```

### Get Connector by ID

```
GET /api/connectors/{id}
```

**Example Request:**

```bash
curl -H "X-API-Key: dev-api-key-change-in-production" \
  "http://localhost:8080/api/connectors/1"
```

**Example Response (200 OK):**

```json
{
  "id": 1,
  "name": "CKAN Metadata Platform",
  "type": "DATA_PROVIDER",
  "endpoint": "http://ckan.example.com/",
  "status": "ONLINE",
  "lastHeartbeat": "2026-04-07T10:30:00",
  "createdAt": "2026-01-15T08:00:00",
  "updatedAt": "2026-04-01T12:00:00"
}
```

## Error Responses

| Status | Condition | Example |
|--------|-----------|---------|
| `401 Unauthorized` | Missing or invalid API key | `{"error": "Invalid or missing API key"}` |
| `400 Bad Request` | Neither or both params provided | `{"error": "Exactly one of 'orgName' or 'spipUser' must be provided"}` |
| `404 Not Found` | Organization or connector not found | `{"error": "Organization with id 'Unknown Corp' not found"}` |

## Security Configuration

The `/api/connectors/**` path is:
- Excluded from CSRF protection (stateless API)
- Excluded from JWT authentication filter
- Set to `permitAll()` in Spring Security (API key validation is handled in the controller)

These settings are configured in `SecurityConfig.java`.

## Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Connector ID |
| `name` | String | Connector name |
| `description` | String | Connector description |
| `type` | String | Connector type (`DATA_PROVIDER`, `DATA_CONSUMER`, `SPIP_AGENT`, `EXTERNAL_API`, `DATABASE`, `FILE_SYSTEM`, `PLATFORM_DIGITAL_TOOL`, `OBJECT_STORAGE_SYSTEM`) |
| `endpoint` | String | Connector endpoint URL |
| `status` | String | Current status (`ONLINE`, `OFFLINE`, `ERROR`, `MAINTENANCE`) |
| `configuration` | String | Connector configuration (JSON/text) |
| `lastHeartbeat` | DateTime | Last heartbeat timestamp |
| `healthEndpoint` | String | Health check endpoint URL |
| `heartbeatToken` | String | Token used for heartbeat authentication |
| `apiToken` | String | Connector API token |
| `createdAt` | DateTime | Creation timestamp |
| `updatedAt` | DateTime | Last update timestamp |

Null fields are omitted from the JSON response.
