# ADR-0004: Server-Sent Events (SSE) for Real-Time Notifications

## Status
Accepted

## Date
2025-12-28

## Context

The DATA4CIRC Portal orchestrates multi-step workflows involving onboarding requests, connector status changes, dataset publications, SPIP synchronization, and organization membership management. These asynchronous processes require timely user notification to maintain workflow efficiency and provide operational awareness.

Several architectural forces shaped this decision:

**User Experience Requirements:**
- Users need immediate awareness of workflow state changes (onboarding approval/rejection, connector status)
- Organization administrators must be notified when members join, datasets are published, or connectors go offline
- Platform administrators require alerts for system-wide events and SPIP synchronization failures
- Notifications should persist across page navigations and survive browser tab closures
- Multi-tab support is essential (users may have portal open in multiple browser windows)

**Existing Architecture:**
- Spring Boot backend with Spring Security session-based authentication
- Thymeleaf server-rendered templates with Bootstrap 5 UI
- PostgreSQL database for persistence (H2 for development)
- User-scoped operations with role-based access control (PLATFORM_ADMIN, ORG_ADMIN, SPIP_PRIVILEGED_USER, ORG_MEMBER)
- Existing REST API endpoints for asynchronous operations (CKAN publication, SPIP sync)

**Technical Constraints:**
- No existing message broker infrastructure (RabbitMQ, Kafka, Redis)
- Single-instance deployment (no horizontal scaling requirement at current stage)
- Need to maintain notification history for audit trail and compliance
- Must integrate seamlessly with existing Spring Security authentication
- Frontend should work with standard browser APIs (no WebSocket library dependencies)

**Notification Delivery Patterns:**
- User-scoped: Notify specific user about their actions (dataset published, onboarding submitted)
- Organization-scoped: Notify all members of an organization (new member joined, connector offline)
- Role-scoped: Notify all users with a specific role (PLATFORM_ADMIN for system announcements)
- Broadcast: Notify all authenticated users (system maintenance, critical alerts)

**Real-Time vs. Eventual Delivery:**
- Notifications should be delivered in real-time to connected users
- If user is offline, notifications must persist in database for later retrieval
- No message loss is acceptable (notifications are business-critical for workflow continuity)
- Users need unread badge count and notification history access

## Decision

We will implement a Server-Sent Events (SSE) notification system using Spring's `SseEmitter` for server-side connection management and the native browser `EventSource` API for client-side consumption. Notifications will be persisted in a database table before being pushed via SSE, ensuring guaranteed delivery even if users are offline.

### Architecture Components

**1. Persistence Layer:**
- `Notification` entity stores all notifications with indexed fields for efficient retrieval:
  - `recipient_id`: Foreign key to User (indexed)
  - `type`: Enum-based notification category (onboarding, connector, dataset, SPIP, system)
  - `title`, `message`: Human-readable content
  - `actionUrl`, `actionLabel`: Optional call-to-action link
  - `is_read`, `read_at`: Read tracking for badge count
  - `created_at`: Timestamp for ordering (indexed DESC)
- Database indexes optimize common queries: unread count, recent notifications, recipient filtering
- `NotificationRepository` provides JPA queries with pagination support
- Supports cleanup of old read notifications via scheduled job

**2. Service Layer:**
- `NotificationService` orchestrates notification creation and delivery:
  - `sendToUser()`: Persist notification and push via SSE to single user
  - `sendToOrganization()`: Notify all members of an organization
  - `sendToRole()`: Notify all users with a specific role
  - `broadcastToAll()`: System-wide announcements (async execution)
  - `markAsRead()`, `markAllAsRead()`: Read state management
  - `cleanupOldNotifications()`: Batch deletion of old read notifications
- Convenience methods for common workflows: `notifyOnboardingStatus()`, `notifyConnectorStatus()`, `notifyMemberInvited()`
- Transactional guarantees ensure notification persistence before SSE delivery

**3. SSE Connection Management:**
- `SseEmitterManager` maintains user-to-emitter mappings:
  - `Map<Long, Set<SseEmitter>>`: User ID → Multiple emitters (multi-tab support)
  - `Map<SseEmitter, Long>`: Reverse mapping for cleanup operations
  - Thread-safe with `ConcurrentHashMap` and `CopyOnWriteArraySet`
  - 30-minute SSE timeout with automatic cleanup callbacks (onCompletion, onTimeout, onError)
  - Scheduled heartbeat every 30 seconds to prevent proxy/firewall connection drops
  - Graceful connection removal on errors or timeouts

**4. REST API & SSE Endpoint:**
- `NotificationSseController` exposes notification endpoints:
  - `GET /api/notifications/stream`: SSE subscription endpoint (produces `text/event-stream`)
  - `GET /api/notifications`: Fetch recent notifications with limit parameter
  - `GET /api/notifications/unread`: Get unread notifications
  - `POST /api/notifications/{id}/read`: Mark single notification as read
  - `POST /api/notifications/read-all`: Mark all as read
  - `GET /api/notifications/stats`: Admin-only connection statistics
- Spring Security `@AuthenticationPrincipal User` injection for user-scoped operations
- SSE connection flow:
  1. Client connects to `/api/notifications/stream`
  2. Controller registers emitter with `SseEmitterManager`
  3. Controller sends `connected` event with current unread count
  4. Heartbeat events keep connection alive
  5. Notification events sent as typed SSE events (event name = notification type)

**5. Frontend Integration:**
- `notifications.js`: JavaScript class managing SSE lifecycle
  - `EventSource` API connects to `/api/notifications/stream`
  - Automatic reconnection with exponential backoff (max 10 attempts)
  - Event listeners for each `NotificationType` enum value
  - Dropdown UI updates (shows 5 most recent notifications)
  - Bootstrap toast notifications for real-time alerts (8-second auto-dismiss)
  - Badge counter updates based on unread count
  - Fetches notification history on dropdown open
- Navigation bar integration:
  - Bell icon with animated badge showing unread count
  - Bootstrap dropdown for notification history
  - "Mark all as read" action
  - Click-to-dismiss individual notifications
- Toast container for transient notifications
- Automatic cleanup on page unload

**6. Notification Type System:**
- `NotificationType` enum defines 14 notification categories:
  - **Onboarding**: SUBMITTED, APPROVED, REJECTED
  - **Connector**: ONLINE, OFFLINE, CREATED
  - **Dataset**: PUBLISHED, UPDATED, DELETED
  - **Organization**: MEMBER_INVITED, MEMBER_JOINED, MEMBER_REMOVED
  - **SPIP**: SYNC_COMPLETE, SYNC_FAILED
  - **System**: ANNOUNCEMENT, MAINTENANCE
- Each type has `displayName` (human-readable title) and `alertClass` (Bootstrap color: success/info/warning/danger)
- Type-specific event names in SSE stream enable client-side filtering

### Delivery Guarantees

**Persist-Before-Push Pattern:**
1. Notification is created in database (ACID guarantees)
2. Notification is sent via SSE to connected users
3. If SSE send fails (user offline), notification remains in database
4. User retrieves missed notifications on next login via REST API

This ensures zero message loss and supports both real-time and eventually-consistent delivery.

### Security Model

- SSE endpoint `/api/notifications/stream` requires Spring Security authentication
- `SseEmitterManager` enforces user-scoped emitter registration (userId from `@AuthenticationPrincipal`)
- Notifications are filtered by recipient_id in database queries
- REST endpoints use role-based access control (`@PreAuthorize` for admin-only stats)
- No cross-user notification leakage (each user only sees their notifications)

### Multi-Tab Support

- `SseEmitterManager` maintains a `Set<SseEmitter>` per user (not just one emitter)
- Each browser tab establishes its own SSE connection
- Notifications are sent to all active emitters for a user
- Badge count synchronized across tabs via shared notification state
- Connection cleanup removes individual emitters without affecting other tabs

### Connection Resilience

**Server-Side:**
- Heartbeat scheduler sends ping every 30 seconds to all connected users
- Failed heartbeat triggers emitter cleanup (prevents stale connections)
- Timeout callback removes emitters after 30 minutes of inactivity
- Error callback handles network failures gracefully

**Client-Side:**
- Native `EventSource` API provides automatic reconnection on connection loss
- Custom exponential backoff logic limits reconnection attempts (max 10)
- Reconnection delay increases with each attempt (5s, 10s, 15s, up to 25s)
- Connection state logging aids debugging

## Consequences

### Positive

- **Simplicity**: SSE is built into Spring (`SseEmitter`) and browsers (`EventSource`), requiring no additional libraries or infrastructure
- **HTTP/2 Efficiency**: SSE uses standard HTTP, compatible with existing reverse proxies and load balancers (unlike WebSockets which require upgrade handshake)
- **Persistence Layer**: Database storage ensures audit trail, notification history, and recovery from missed real-time delivery
- **Multi-Tab Support**: Set-based emitter tracking naturally supports multiple browser windows per user
- **Zero Message Loss**: Persist-before-push pattern guarantees delivery even if SSE fails
- **Gradual Degradation**: Users can still access notifications via REST API if SSE connection fails
- **Type Safety**: Enum-based notification types prevent typos and enable exhaustive client-side handling
- **Authentication Integration**: Seamless integration with Spring Security's `@AuthenticationPrincipal`
- **Scalability for Current Needs**: Concurrent data structures handle expected load (<500 concurrent users)
- **Developer Experience**: Simple test endpoint (`POST /api/notifications/test`) enables rapid UI/UX iteration

### Negative

- **Stateful Connections**: SSE requires server to maintain open connections, consuming threads/memory per user (mitigated by 30-minute timeout)
- **Horizontal Scaling Complexity**: Multi-instance deployments require sticky sessions or shared state (Redis Pub/Sub); not an issue for current single-instance deployment
- **Unidirectional Only**: SSE is server-to-client only; bidirectional use cases would require WebSockets (not currently needed)
- **Browser Limit**: Browsers limit concurrent SSE connections per domain (6 in Chrome); affects users with many open tabs (unlikely scenario)
- **Heartbeat Overhead**: Scheduled pings consume bandwidth and CPU; necessary trade-off for connection liveness
- **Database Growth**: Notification table will grow over time; requires scheduled cleanup job (implemented via `cleanupOldNotifications()`)
- **No Priority Queuing**: Notifications are sent in order of creation; high-priority alerts not fast-tracked (acceptable for current requirements)

### Neutral

- **Spring Scheduler Dependency**: Uses `@Scheduled` for heartbeat; ties notification system to Spring container lifecycle
- **JSON Serialization**: SSE data payloads use Jackson for JSON; adds minor serialization overhead vs. plain text
- **Frontend Coupling**: `notifications.js` tightly coupled to Bootstrap 5 UI framework; acceptable given existing UI stack
- **Manual Event Type Registration**: Client must register listeners for each `NotificationType`; compile-time safety but requires code updates when adding types
- **30-Minute Timeout**: Balances resource usage vs. user experience; may require tuning based on usage patterns
- **Synchronous Broadcast**: `broadcastToAll()` uses `@Async` but still iterates users sequentially; sufficient for hundreds of users, not thousands

## Alternatives Considered

### Alternative 1: WebSockets (Full Duplex)

**Pros:**
- True bidirectional communication (client-to-server push)
- More efficient binary framing (less overhead than HTTP headers)
- Better support for high-frequency updates (gaming, live charts)
- Industry standard for real-time apps

**Cons:**
- Requires WebSocket library on client (e.g., SockJS, Socket.IO) or native WebSocket API
- More complex server-side management (Spring WebSocket + STOMP + message broker)
- Bidirectional capability not needed (portal notifications are server-initiated only)
- Proxy/firewall compatibility issues (require WebSocket upgrade handshake)
- Higher implementation complexity for no functional benefit in this use case

**Why rejected:** WebSockets are overkill for unidirectional server-to-client notifications. SSE provides the same real-time delivery with simpler architecture and better HTTP compatibility.

### Alternative 2: Client-Side Polling

**Pros:**
- No persistent connections (stateless server)
- Trivial implementation (setInterval + fetch)
- No connection lifecycle management
- Easy horizontal scaling (no sticky sessions needed)

**Cons:**
- High latency (notification delay = polling interval, typically 10-30 seconds)
- Wasteful bandwidth (constant requests even when no new notifications)
- Database load (N users × polling frequency queries per second)
- Poor user experience (delayed notifications, no real-time feel)
- Inefficient for low-frequency events (most notification events are rare)

**Why rejected:** Polling is unacceptable for workflow-critical notifications (e.g., onboarding approval). Users expect immediate feedback for their actions. The bandwidth and database overhead outweigh the stateless simplicity.

### Alternative 3: External Message Broker (Redis Pub/Sub, RabbitMQ)

**Pros:**
- Proven scalability for multi-instance deployments
- Decouples notification publishing from SSE connection management
- Better horizontal scaling (broker handles message distribution)
- Supports fanout patterns natively

**Cons:**
- Requires additional infrastructure (Redis/RabbitMQ deployment, monitoring, backups)
- Increased operational complexity (broker failure modes, message persistence, queue management)
- Network hop adds latency (app → broker → app → SSE)
- Overkill for single-instance deployment (current deployment model)
- Still requires SSE/WebSocket layer for browser delivery (broker doesn't eliminate client connection management)

**Why rejected:** The portal currently runs as a single instance with <500 expected concurrent users. Adding a message broker introduces significant operational overhead without immediate benefit. If horizontal scaling becomes necessary, Redis Pub/Sub can be introduced later to synchronize notifications across instances while preserving the SSE delivery mechanism.

### Alternative 4: Email/SMS Notifications Only

**Pros:**
- No in-app infrastructure needed
- Works offline (email delivered when user is away)
- Leverages existing `EmailService`

**Cons:**
- High latency (email delivery delays, user may not check email)
- Not real-time (defeats purpose of immediate workflow feedback)
- Poor user experience for frequent events (email fatigue)
- Requires external email service (SMTP configuration)
- No in-app notification history or badge counts

**Why rejected:** Email is complementary to in-app notifications, not a replacement. Critical events (onboarding approval) should trigger email, but routine events (dataset published) need in-app delivery. The portal already sends emails for workflow milestones; SSE provides the real-time layer for operational awareness.

### Alternative 5: Third-Party Notification Service (Pusher, Firebase Cloud Messaging)

**Pros:**
- Managed infrastructure (no SSE server-side management)
- Multi-platform support (web, mobile, desktop)
- Advanced features (presence, typing indicators)

**Cons:**
- External dependency and vendor lock-in
- Recurring costs (usage-based pricing)
- Data privacy concerns (notification content sent to third-party)
- Integration complexity (SDK setup, authentication mapping)
- Overkill for web-only portal
- Requires internet egress for notification delivery

**Why rejected:** The portal handles sensitive circular economy data and SPIP integration. Sending notification content to a third-party SaaS conflicts with data sovereignty requirements. SSE keeps all notification data within the portal infrastructure, satisfying privacy and compliance needs.

## References

- [Server-Sent Events Specification (W3C)](https://html.spec.whatwg.org/multipage/server-sent-events.html)
- [Spring Framework SseEmitter Documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/SseEmitter.html)
- [MDN EventSource API](https://developer.mozilla.org/en-US/docs/Web/API/EventSource)
- DATA4CIRC Portal Implementation:
  - `/src/main/java/com/data4circ/portal/notification/service/NotificationService.java`
  - `/src/main/java/com/data4circ/portal/notification/service/SseEmitterManager.java`
  - `/src/main/java/com/data4circ/portal/notification/controller/NotificationSseController.java`
  - `/src/main/resources/static/js/notifications.js`
- Related ADRs:
  - ADR-0001 (SigNoz Observability Integration) - Monitoring SSE connection metrics
  - ADR-0003 (CKAN Integration) - Dataset publication notifications
