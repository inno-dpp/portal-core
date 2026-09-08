package com.data4circ.portal.features.notification.service;

import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.organization.entity.UserRole;
import com.data4circ.portal.features.notification.dto.NotificationEvent;
import com.data4circ.portal.features.notification.entity.Notification;
import com.data4circ.portal.features.notification.entity.NotificationType;
import com.data4circ.portal.features.notification.repository.NotificationRepository;
import com.data4circ.portal.features.organization.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing notifications and sending them via SSE.
 */
@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SseEmitterManager emitterManager;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               SseEmitterManager emitterManager) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.emitterManager = emitterManager;
    }

    /**
     * Send a notification to a specific user.
     * Persists the notification and sends it via SSE if user is connected.
     * SSE send is performed AFTER transaction commits to avoid holding DB connections.
     */
    @Transactional
    public Notification sendToUser(User recipient, NotificationEvent event) {
        // Persist notification
        Notification notification = Notification.fromEvent(event, recipient);
        notification = notificationRepository.save(notification);
        logger.info("Created notification {} for user {}", notification.getId(), recipient.getUsername());

        // Capture values for use after transaction commits (avoid closure over entities)
        final Long recipientId = recipient.getId();
        final NotificationEvent savedEvent = notification.toEvent();

        // Send via SSE AFTER transaction commits to release DB connection first
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emitterManager.sendToUser(recipientId, savedEvent);
                }
            });
        } else {
            // No active transaction, send immediately
            emitterManager.sendToUser(recipientId, savedEvent);
        }

        return notification;
    }

    /**
     * Send a notification to a user by ID.
     */
    @Transactional
    public Notification sendToUser(Long userId, NotificationEvent event) {
        User recipient = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return sendToUser(recipient, event);
    }

    /**
     * Send notifications to multiple users.
     */
    @Transactional
    public List<Notification> sendToUsers(Set<User> recipients, NotificationEvent event) {
        return recipients.stream()
            .map(recipient -> sendToUser(recipient, event))
            .collect(Collectors.toList());
    }

    /**
     * Send a notification to all users with a specific role.
     */
    @Transactional
    public List<Notification> sendToRole(UserRole role, NotificationEvent event) {
        List<User> users = userRepository.findByRole(role);
        logger.info("Sending notification to {} users with role {}", users.size(), role);
        return users.stream()
            .map(user -> sendToUser(user, event))
            .collect(Collectors.toList());
    }

    /**
     * Send a notification to all users in an organization.
     */
    @Transactional
    public List<Notification> sendToOrganization(Long organizationId, NotificationEvent event) {
        List<User> users = userRepository.findByOrganizationId(organizationId);
        logger.info("Sending notification to {} users in organization {}", users.size(), organizationId);
        return users.stream()
            .map(user -> sendToUser(user, event))
            .collect(Collectors.toList());
    }

    /**
     * Broadcast a notification to all users (system announcements).
     */
    @Async
    public void broadcastToAll(NotificationEvent event) {
        List<User> allUsers = userRepository.findAll();
        logger.info("Broadcasting notification to {} users", allUsers.size());
        for (User user : allUsers) {
            sendToUser(user, event);
        }
    }

    /**
     * Get unread notification count for a user.
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(User user) {
        return notificationRepository.countByRecipientAndReadFalse(user);
    }

    /**
     * Get unread notification count by user ID.
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    /**
     * Get recent notifications for a user.
     */
    @Transactional(readOnly = true)
    public List<NotificationEvent> getRecentNotifications(User user, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return notificationRepository.findRecentByRecipient(user, pageable).stream()
            .map(Notification::toEvent)
            .collect(Collectors.toList());
    }

    /**
     * Get all notifications for a user with pagination.
     */
    @Transactional(readOnly = true)
    public Page<NotificationEvent> getNotifications(User user, Pageable pageable) {
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(user, pageable)
            .map(Notification::toEvent);
    }

    /**
     * Get unread notifications for a user.
     */
    @Transactional(readOnly = true)
    public List<NotificationEvent> getUnreadNotifications(User user) {
        return notificationRepository.findByRecipientAndReadFalseOrderByCreatedAtDesc(user).stream()
            .map(Notification::toEvent)
            .collect(Collectors.toList());
    }

    /**
     * Mark a notification as read.
     */
    @Transactional
    public boolean markAsRead(Long notificationId, User user) {
        return notificationRepository.findById(notificationId)
            .filter(n -> n.getRecipient().getId().equals(user.getId()))
            .map(notification -> {
                notification.markAsRead();
                notificationRepository.save(notification);
                return true;
            })
            .orElse(false);
    }

    /**
     * Mark all notifications as read for a user.
     */
    @Transactional
    public int markAllAsRead(User user) {
        // UTC to match Notification.createdAt, which is stamped in UTC regardless of JVM default zone.
        return notificationRepository.markAllAsReadForUser(user, LocalDateTime.now(ZoneOffset.UTC));
    }

    /**
     * Delete a notification.
     */
    @Transactional
    public boolean deleteNotification(Long notificationId, User user) {
        return notificationRepository.findById(notificationId)
            .filter(n -> n.getRecipient().getId().equals(user.getId()))
            .map(notification -> {
                notificationRepository.delete(notification);
                return true;
            })
            .orElse(false);
    }

    /**
     * Delete all notifications for a user.
     */
    @Transactional
    public int deleteAllForUser(User user) {
        List<Notification> notifications = notificationRepository.findByRecipientOrderByCreatedAtDesc(user);
        int count = notifications.size();
        notificationRepository.deleteAll(notifications);
        logger.info("Deleted {} notifications for user {}", count, user.getUsername());
        return count;
    }

    /**
     * Clean up old read notifications (older than specified days).
     */
    @Transactional
    public int cleanupOldNotifications(int daysOld) {
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).minusDays(daysOld);
        int deleted = notificationRepository.deleteOldReadNotifications(before);
        logger.info("Cleaned up {} old notifications", deleted);
        return deleted;
    }

    // Convenience methods for common notification types

    /**
     * Send an onboarding status notification.
     */
    @Transactional
    public Notification notifyOnboardingStatus(User user, NotificationType type, String organizationName, String actionUrl) {
        String message = switch (type) {
            case ONBOARDING_APPROVED -> "Your organization '" + organizationName + "' has been approved!";
            case ONBOARDING_REJECTED -> "Your organization request for '" + organizationName + "' was not approved.";
            case ONBOARDING_SUBMITTED -> "Your onboarding request for '" + organizationName + "' has been submitted.";
            default -> "Onboarding status updated for '" + organizationName + "'.";
        };

        return sendToUser(user, NotificationEvent.builder()
            .type(type)
            .title(type.getDisplayName())
            .message(message)
            .actionUrl(actionUrl)
            .actionLabel("View Details")
            .build());
    }

    /**
     * Send a connector status notification.
     */
    @Transactional
    public Notification notifyConnectorStatus(User user, String connectorName, boolean isOnline, String actionUrl) {
        NotificationType type = isOnline ? NotificationType.CONNECTOR_ONLINE : NotificationType.CONNECTOR_OFFLINE;
        String message = "Connector '" + connectorName + "' is now " + (isOnline ? "online" : "offline") + ".";

        return sendToUser(user, NotificationEvent.builder()
            .type(type)
            .title(type.getDisplayName())
            .message(message)
            .actionUrl(actionUrl)
            .actionLabel("View Connector")
            .build());
    }

    /**
     * Send a member invitation notification.
     */
    @Transactional
    public Notification notifyMemberInvited(User invitedUser, String organizationName, String inviterName) {
        return sendToUser(invitedUser, NotificationEvent.builder()
            .type(NotificationType.MEMBER_INVITED)
            .title("Organization Invitation")
            .message(inviterName + " has invited you to join '" + organizationName + "'.")
            .actionUrl("/organizations")
            .actionLabel("View Organization")
            .build());
    }

    /**
     * Send a system announcement to all users.
     */
    public void announceSystem(String title, String message, String actionUrl) {
        broadcastToAll(NotificationEvent.builder()
            .type(NotificationType.SYSTEM_ANNOUNCEMENT)
            .title(title)
            .message(message)
            .actionUrl(actionUrl)
            .build());
    }
}
