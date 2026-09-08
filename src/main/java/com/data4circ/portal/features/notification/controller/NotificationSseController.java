package com.data4circ.portal.features.notification.controller;

import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.notification.dto.NotificationEvent;
import com.data4circ.portal.features.notification.entity.NotificationType;
import com.data4circ.portal.features.notification.service.NotificationService;
import com.data4circ.portal.features.notification.service.SseEmitterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * REST controller for SSE notifications and notification management.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationSseController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationSseController.class);

    private final NotificationService notificationService;
    private final SseEmitterManager emitterManager;

    public NotificationSseController(NotificationService notificationService,
                                     SseEmitterManager emitterManager) {
        this.notificationService = notificationService;
        this.emitterManager = emitterManager;
    }

    /**
     * SSE endpoint for real-time notifications.
     * Clients connect here to receive push notifications.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal User user) {
        logger.info("User {} subscribing to notification stream", user.getUsername());

        // Capture user ID to avoid passing detached entity to service layer
        Long userId = user.getId();

        // Register the emitter
        SseEmitter emitter = emitterManager.register(userId);

        // Send initial connection event with unread count (pass ID, not entity)
        long unreadCount = notificationService.getUnreadCount(userId);
        emitterManager.sendConnectionEvent(emitter, unreadCount);

        return emitter;
    }

    /**
     * Get recent notifications for the current user.
     */
    @GetMapping
    public ResponseEntity<List<NotificationEvent>> getNotifications(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "20") int limit) {
        List<NotificationEvent> notifications = notificationService.getRecentNotifications(user, limit);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Get notifications with pagination.
     */
    @GetMapping("/page")
    public ResponseEntity<Page<NotificationEvent>> getNotificationsPaged(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationEvent> notifications = notificationService.getNotifications(user, pageable);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Get unread notifications.
     */
    @GetMapping("/unread")
    public ResponseEntity<List<NotificationEvent>> getUnreadNotifications(
            @AuthenticationPrincipal User user) {
        List<NotificationEvent> notifications = notificationService.getUnreadNotifications(user);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Get unread notification count.
     */
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal User user) {
        long count = notificationService.getUnreadCount(user);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Mark a specific notification as read.
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        boolean success = notificationService.markAsRead(id, user);
        if (success) {
            long newCount = notificationService.getUnreadCount(user);
            return ResponseEntity.ok(Map.of("success", true, "unreadCount", newCount));
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Mark all notifications as read.
     */
    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(@AuthenticationPrincipal User user) {
        int count = notificationService.markAllAsRead(user);
        return ResponseEntity.ok(Map.of("success", true, "markedCount", count));
    }

    /**
     * Delete a notification.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> deleteNotification(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        boolean success = notificationService.deleteNotification(id, user);
        if (success) {
            return ResponseEntity.ok(Map.of("success", true));
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Delete all notifications for the current user.
     */
    @DeleteMapping("/all")
    public ResponseEntity<Map<String, Object>> deleteAllNotifications(
            @AuthenticationPrincipal User user) {
        int count = notificationService.deleteAllForUser(user);
        return ResponseEntity.ok(Map.of("success", true, "deletedCount", count));
    }

    /**
     * Get SSE connection statistics (admin only).
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(emitterManager.getStats());
    }

    /**
     * Test endpoint to send a notification to the current user.
     * Available in dev/test profiles only - use for testing SSE functionality.
     *
     * Usage examples:
     *   POST /api/notifications/test                    - sends default test notification
     *   POST /api/notifications/test?type=CONNECTOR_ONLINE&message=My+connector+is+up
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> sendTestNotification(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "SYSTEM_ANNOUNCEMENT") String type,
            @RequestParam(defaultValue = "This is a test notification") String message,
            @RequestParam(defaultValue = "Test Notification") String title,
            @RequestParam(required = false) String actionUrl) {

        NotificationType notificationType;
        try {
            notificationType = NotificationType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid notification type: " + type,
                "validTypes", NotificationType.values()
            ));
        }

        NotificationEvent event = NotificationEvent.builder()
            .type(notificationType)
            .title(title)
            .message(message)
            .actionUrl(actionUrl != null ? actionUrl : "/")
            .actionLabel("View")
            .build();

        var notification = notificationService.sendToUser(user, event);

        logger.info("Test notification sent to user {}: type={}, title={}",
            user.getUsername(), type, title);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "notificationId", notification.getId(),
            "message", "Test notification sent successfully",
            "sentTo", user.getUsername()
        ));
    }

    /**
     * Get available notification types (for testing UI).
     */
    @GetMapping("/types")
    public ResponseEntity<List<Map<String, String>>> getNotificationTypes() {
        var types = java.util.Arrays.stream(NotificationType.values())
            .map(t -> Map.of(
                "name", t.name(),
                "displayName", t.getDisplayName(),
                "alertClass", t.getAlertClass()
            ))
            .toList();
        return ResponseEntity.ok(types);
    }
}
