# Collaboration Requests

Allows organizations to formally establish collaboration relationships via a request/approve/reject lifecycle.

---

## Data Model

Single table `collaboration_requests` — serves as both audit trail and source of truth for active collaborations. No separate join table.

```
collaboration_requests
├── id
├── requester_org_id  (FK → organizations)
├── target_org_id     (FK → organizations)
├── status            VARCHAR — PENDING | APPROVED | REJECTED
├── request_message   VARCHAR(1000), optional
├── processed_by      VARCHAR (username)
├── processed_at      TIMESTAMP
├── reviewer_notes    VARCHAR(1000), optional
├── created_at
└── updated_at
```

**Constraints:**
- Unique on `(requester_org_id, target_org_id)` — direction-specific; A→B and B→A are separate rows
- Indexes on `requester_org_id`, `target_org_id`, `status`

**Finding collaborators** uses two queries merged in Java (Hibernate cannot use CASE expressions returning entity types):
```java
// CollaborationService.findApprovedCollaborators(orgId)
repo.findApprovedCollaboratorsByRequester(orgId)  // SELECT target_org WHERE requester = X AND status = APPROVED
repo.findApprovedCollaboratorsByTarget(orgId)     // SELECT requester_org WHERE target = X AND status = APPROVED
```

---

## Package Structure

```
features/collaboration/
├── entity/
│   ├── CollaborationStatus.java     — PENDING | APPROVED | REJECTED (displayName, badgeClass)
│   └── CollaborationRequest.java    — JPA entity
├── repository/
│   └── CollaborationRequestRepository.java
├── service/
│   └── CollaborationService.java
└── controller/
    └── CollaborationController.java
```

---

## Lifecycle

```
ORG_ADMIN (Org A)          System                    ORG_ADMIN (Org B)
     │                        │                            │
     │── POST /organizations/{B}/collaboration-requests ──>│
     │                        │── save PENDING row         │
     │                        │── notify Org B members     │
     │                        │── email Org B contact ────>│
     │<── JSON {success, status: "PENDING"}                │
     │                        │                            │
     │                        │<── POST .../approve ───────│
     │                        │── set APPROVED             │
     │                        │── notify Org A members     │
     │<── email ──────────────│                            │
     │                        │── redirect to /edit ──────>│
```

Rejection follows the same flow with `REJECTED` status. A rejected request can be re-sent (the old row is deleted before inserting a new one).

---

## API Endpoints

All under `/organizations`, require `ORG_ADMIN` role.

| Method | Path | Response | Description |
|--------|------|----------|-------------|
| `POST` | `/{targetOrgId}/collaboration-requests` | JSON `{success, status}` | Send request; inline button update |
| `POST` | `/{targetOrgId}/collaboration-requests/{requestId}/approve` | Redirect to `/edit` | Approve pending request |
| `POST` | `/{targetOrgId}/collaboration-requests/{requestId}/reject` | Redirect to `/edit` | Reject with optional `reviewerNotes` |

---

## Notifications & Email

Three `NotificationType` values added:
- `COLLABORATION_REQUEST_RECEIVED` — sent to all target org members
- `COLLABORATION_REQUEST_APPROVED` — sent to all requester org members
- `COLLABORATION_REQUEST_REJECTED` — sent to all requester org members

Uses existing `notificationService.sendToOrganization(orgId, event)`.

Email templates in `templates/email/`:
- `collaboration-request-received.html`
- `collaboration-request-approved.html`
- `collaboration-request-rejected.html`

Email failures are swallowed (non-critical) — they do not abort the request flow.

---

## UI

**Organization view page** (`/organizations/{id}`):
- Collaborators card — list of approved partner orgs (always visible)
- Collaboration button — state-aware, shown only to `ORG_ADMIN` viewing another org:

  | State | Button |
  |-------|--------|
  | `NONE` | "Request Collaboration" (green, enabled) |
  | `PENDING` | "Request Pending" (yellow, disabled) |
  | `APPROVED` | "Collaborating" (green, disabled) |
  | `REJECTED` | "Request Collaboration Again" (grey, enabled) |

  Button uses `fetch` POST; updates inline on success without page reload.

**Organization edit page** (`/organizations/{id}/edit`):
- Collaboration Requests card — lists pending incoming requests with Approve / Reject (+ optional notes) inline forms
- Current Collaborators subsection

**Navigation bar:**
- `My Organization` link shows a yellow badge with the pending request count, populated globally via `GlobalModelAttributesAdvice.addPendingCollabCount()`.

---

## Duplicate / Race Condition Handling

- `sendRequest` checks for any `PENDING` or `APPROVED` request in either direction before inserting
- If only a `REJECTED` row exists in the same direction, it is deleted (`flush()` called before insert) to satisfy the unique constraint
- Simultaneous clicks by two different admins could in theory both pass the application-level check and hit the DB unique constraint — this surfaces as a 400 JSON error to the second caller, no data corruption occurs

---

## Key Files Modified

| File | Change |
|------|--------|
| `notification/entity/NotificationType.java` | Added 3 collaboration enum values |
| `organization/service/EmailService.java` | Added 3 non-critical email methods |
| `organization/controller/OrganizationController.java` | Injected `CollaborationService`; added model attributes to `viewOrganization` and `editOrganizationForm` |
| `common/config/GlobalModelAttributesAdvice.java` | Added `pendingCollabCount` model attribute |
| `notification/entity/Notification.java` | Added `columnDefinition = "VARCHAR(100)"` to `type` to suppress H2 check constraint |
| `templates/organizations/view.html` | Button block, collaborators card, fetch JS |
| `templates/organizations/form.html` | Collaboration requests management card |
| `templates/fragments/navigation.html` | Pending badge on My Organization link |
