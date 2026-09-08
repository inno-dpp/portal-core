# SSE Real-Time Notifications

Server-Sent Events (SSE) based notification system for real-time user notifications.

## Architecture Overview

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Browser/Client │◄────│  SseEmitter      │◄────│  Notification   │
│  (JavaScript)   │ SSE │  Manager         │     │  Service        │
└─────────────────┘     └──────────────────┘     └─────────────────┘
                                                          │
                                                          ▼
                                                 ┌─────────────────┐
                                                 │  Notification   │
                                                 │  Repository     │
                                                 │  (Persistence)  │
                                                 └─────────────────┘
```

## Key Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `NotificationService` | `notification/service/` | Business logic, sends & persists notifications |
| `SseEmitterManager` | `notification/service/` | Manages SSE connections per user |
| `NotificationSseController` | `notification/controller/` | REST API & SSE endpoints |
| `Notification` | `notification/entity/` | JPA entity for persistence |
| `NotificationType` | `notification/entity/` | Enum of notification types |
| `NotificationEvent` | `notification/dto/` | DTO sent via SSE |
| `notifications.js` | `static/js/` | Frontend JavaScript client |

## Notification Types

| Type | Alert Class | Use Case |
|------|-------------|----------|
| `ONBOARDING_SUBMITTED` | info | New org request submitted |
| `ONBOARDING_APPROVED` | success | Org request approved |
| `ONBOARDING_REJECTED` | warning | Org request rejected |
| `CONNECTOR_ONLINE` | success | Connector came online |
| `CONNECTOR_OFFLINE` | danger | Connector went offline |
| `DATASET_PUBLISHED` | success | Dataset published to CKAN |
| `MEMBER_INVITED` | info | User invited to org |
| `SYSTEM_ANNOUNCEMENT` | info | System-wide announcements |

## Usage in Code

### Send to a Specific User

```java
@Autowired
private NotificationService notificationService;

// Send notification to a user
notificationService.sendToUser(user, NotificationEvent.builder()
    .type(NotificationType.CONNECTOR_ONLINE)
    .title("Connector Active")
    .message("Your connector 'MyConnector' is now online.")
    .actionUrl("/connectors/123")
    .actionLabel("View Connector")
    .build());
```

### Send to All Users with a Role

```java
// Notify all platform admins
notificationService.sendToRole(UserRole.PLATFORM_ADMIN, NotificationEvent.builder()
    .type(NotificationType.ONBOARDING_SUBMITTED)
    .title("New Request")
    .message("New organization wants to join.")
    .actionUrl("/admin/onboarding/5")
    .build());
```

### Send to All Users in an Organization

```java
notificationService.sendToOrganization(organizationId, NotificationEvent.builder()
    .type(NotificationType.DATASET_PUBLISHED)
    .title("New Dataset")
    .message("A new dataset has been published.")
    .actionUrl("/datasets")
    .build());
```

### Broadcast to All Users

```java
notificationService.announceSystem(
    "Scheduled Maintenance",
    "System will be down for maintenance at 2:00 AM.",
    "/announcements"
);
```

## REST API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/notifications/stream` | SSE stream (keep-alive connection) |
| GET | `/api/notifications` | Get recent notifications |
| GET | `/api/notifications/unread` | Get unread notifications |
| GET | `/api/notifications/unread/count` | Get unread count |
| POST | `/api/notifications/{id}/read` | Mark as read |
| POST | `/api/notifications/read-all` | Mark all as read |
| DELETE | `/api/notifications/{id}` | Delete notification |
| GET | `/api/notifications/stats` | Connection stats (admin only) |
| POST | `/api/notifications/test` | Send test notification |
| GET | `/api/notifications/types` | List notification types |

## Testing

### Browser Console (Quick Test)

```javascript
// Send test notification to yourself
fetch('/api/notifications/test', { method: 'POST' })
  .then(r => r.json()).then(console.log);

// Send with custom type and message
fetch('/api/notifications/test?type=CONNECTOR_ONLINE&title=Test&message=Hello',
  { method: 'POST' });

// Check connection status
console.log(notificationManager.eventSource.readyState); // 1 = connected

// Get available types
fetch('/api/notifications/types').then(r => r.json()).then(console.log);

// Check stats (admin only)
fetch('/api/notifications/stats').then(r => r.json()).then(console.log);
```

### Verify SSE Connection

1. Open DevTools → Network tab
2. Filter by "EventStream"
3. Look for `/api/notifications/stream`
4. Should show `connected` event on page load

### Test Full Flow

1. Log in as `admin`
2. Open a second browser/incognito as different user
3. Trigger an action (e.g., submit onboarding request)
4. Admin should receive real-time notification

## Frontend Integration

The notification UI is automatically included via:
- `fragments/navigation.html` - Bell icon with badge
- `fragments/scripts.html` - Loads `notifications.js`
- `fragments/navigation.html :: toast-container` - Toast display area

### Add Toast Container to New Templates

If creating a new template that needs toast notifications:

```html
<!-- Before closing </body> tag -->
<div th:replace="~{fragments/navigation :: toast-container}"></div>
```

## Configuration

SSE connection settings in `SseEmitterManager`:

| Setting | Value | Description |
|---------|-------|-------------|
| `SSE_TIMEOUT` | 30 minutes | Connection timeout |
| `HEARTBEAT_INTERVAL` | 30 seconds | Keep-alive ping |

## Database

Notifications are persisted in the `notifications` table:

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT | Primary key |
| `recipient_id` | BIGINT | FK to users |
| `type` | VARCHAR | NotificationType enum |
| `title` | VARCHAR | Notification title |
| `message` | VARCHAR(1000) | Notification body |
| `action_url` | VARCHAR | Link URL |
| `is_read` | BOOLEAN | Read status |
| `created_at` | TIMESTAMP | Creation time |
| `read_at` | TIMESTAMP | When marked read |

## Troubleshooting

### Notifications not appearing

1. Check browser console for JS errors
2. Verify SSE connection in Network tab
3. Ensure `notifications.js` is loaded
4. Check `notification-bell` element exists in DOM

### SSE connection keeps reconnecting

- Normal behavior if page is inactive
- Check server logs for connection errors
- Verify user is authenticated

### Badge not updating

```javascript
// Manual refresh
notificationManager.loadNotifications();
```
