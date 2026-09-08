package com.data4circ.portal.features.notification.entity;

import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.notification.dto.NotificationEvent;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Persistent notification entity for storing user notifications.
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_recipient", columnList = "recipient_id"),
    @Index(name = "idx_notification_read", columnList = "recipient_id, is_read"),
    @Index(name = "idx_notification_created", columnList = "created_at DESC")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(100)")
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String message;

    @Column(name = "action_url")
    private String actionUrl;

    @Column(name = "action_label")
    private String actionLabel;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    protected void onCreate() {
        // Stored (and later serialized) as UTC explicitly, regardless of the JVM's default
        // timezone, so the frontend's "time ago" display computes the correct elapsed time
        // no matter where the server or the browser is running.
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Convert this entity to a NotificationEvent DTO.
     */
    public NotificationEvent toEvent() {
        return NotificationEvent.builder()
            .id(this.id)
            .type(this.type)
            .title(this.title)
            .message(this.message)
            .actionUrl(this.actionUrl)
            .actionLabel(this.actionLabel)
            .timestamp(this.createdAt)
            .read(this.read)
            .build();
    }

    /**
     * Create a Notification entity from an event DTO.
     */
    public static Notification fromEvent(NotificationEvent event, User recipient) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(event.getType());
        notification.setTitle(event.getTitle());
        notification.setMessage(event.getMessage());
        notification.setActionUrl(event.getActionUrl());
        notification.setActionLabel(event.getActionLabel());
        return notification;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getRecipient() {
        return recipient;
    }

    public void setRecipient(User recipient) {
        this.recipient = recipient;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public void setActionLabel(String actionLabel) {
        this.actionLabel = actionLabel;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    /**
     * Mark notification as read and record the timestamp.
     */
    public void markAsRead() {
        this.read = true;
        this.readAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
