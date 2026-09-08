# Per-Organization CKAN API Tokens

## Overview

Each onboarded organization receives its own CKAN API token, generated for the CKAN "editor" user created during onboarding. This token is stored (encrypted) on the organization's CKAN connector and delivered in the approval email, so the organization can call the CKAN API directly without the shared global token.

> **Note (2026-07):** The public CKAN webhook (`/webhook/ckan`), which consumed this token portal-side via `CkanWebhookService`, has been removed. Portal-side dataset publishing (the web form, `DatasetPublishService`) uses the global platform token; the per-org token remains available for organizations' direct CKAN API access.

## Data Flow

```
CKAN Sync (Step 2 of onboarding)
  1. Create organization in CKAN
  2. Create user in CKAN
  3. Add user as editor to organization
  4. Create API token for user          <-- NEW
  5. Store token on OnboardingRequest.ckanApiToken (encrypted, temporary)

Approval (Step 3 of onboarding)
  1. Create Organization entity
  2. Initialize connectors
     -> CKAN connector gets apiToken from OnboardingRequest
  3. Send approval email (includes CKAN API token)
```

## CKAN API Endpoint

```
POST /api/3/action/api_token_create
Authorization: <admin-token>
Content-Type: application/json

{"user": "<username>", "name": "d4c-portal-<org-short-name>"}
```

Response: `{"success": true, "result": {"token": "..."}}`

The token is **returned only once** at creation and cannot be retrieved later.

### Quick Test

```bash
curl -s -X POST \
  "${CKAN_BASE_URL}/api/3/action/api_token_create" \
  -H "Content-Type: application/json" \
  -H "Authorization: ${CKAN_JWT_TOKEN}" \
  -d '{"user": "USERNAME", "name": "d4c-portal-test"}' | python3 -m json.tool
```

## Key Files

| File | Role |
|------|------|
| `CkanApiClient.java` | `createApiToken()` - calls CKAN API; overloaded `createDataset()` with per-org baseUrl |
| `Connector.java` | `apiToken` field (encrypted, max 1024 chars) |
| `OrganizationOnboardingRequest.java` | `ckanApiToken` field (encrypted, temporary storage between sync and approval) |
| `CkanOnboardingSyncService.java` | Step 4: `createApiTokenIdempotent()` - generates token during sync |
| `CkanSyncResult.java` | `apiToken` + `apiTokenCreated` fields |
| `OnboardingRequestService.java` | Stores token from sync, passes to connector materialization + email on approval |
| `OnboardingToolConnectorService.java` | Sets `apiToken` on the CKAN connector (tool key `ckan`) when materializing it at approval |
| `EmailService.java` | Includes CKAN API token in approval email |
| `onboarding-approved.html` | Conditional CKAN token credentials box |
| `connectors/form.html` | API Token field on connector edit (admin only) |

## Backward Compatibility

- Organizations without per-org tokens use the global `CKAN_JWT_TOKEN` (fallback)
- Global admin token is still used for admin operations (creating orgs, users, tokens)
- `apiToken` column is nullable - no migration needed
- Connector init is unchanged for non-CKAN connectors

## Manual Token Management

Admins can manually set/replace an API token on any connector via the edit page:
**Connectors -> Edit -> API Token field** (visible to PLATFORM_ADMIN and ORG_ADMIN only).

Leaving the field blank preserves the existing token.

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Token not generated during sync | CKAN API error (logged as warning) | Check logs for "Failed to create API token"; generate manually via curl and paste in connector edit |
| `Value too long for column` | Column length < encrypted token size | Ensure `length = 1024` on `@Column` annotation; restart app for H2 (create-drop) |
| 403 on `api_token_create` | Admin token lacks permission | Verify `CKAN_JWT_TOKEN` is a sysadmin token (CKAN 2.9+) |
