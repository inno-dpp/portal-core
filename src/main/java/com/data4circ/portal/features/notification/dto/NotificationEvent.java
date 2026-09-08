package com.data4circ.portal.features.notification.dto;

import com.data4circ.portal.features.notification.entity.NotificationType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * DTO representing a notification event sent via SSE.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationEvent {

    private Long id;
    private NotificationType type;
    private String title;
    private String message;
    private String actionUrl;
    private String actionLabel;

    // The underlying value is always a UTC wall-clock LocalDateTime (see Notification.onCreate()
    // and the no-arg constructor below); tagging it 'Z' here makes that explicit in the JSON so
    // the frontend's `new Date(timestamp)` parses it as UTC instead of local-to-the-browser time,
    // which previously produced "time ago" values off by the server/browser UTC offset.
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private LocalDateTime timestamp;
    private boolean read;

    public NotificationEvent() {
        // UTC to match Notification.createdAt (stamped in UTC regardless of JVM default zone).
        this.timestamp = LocalDateTime.now(ZoneOffset.UTC);
    }

    // Builder pattern for fluent construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final NotificationEvent event = new NotificationEvent();

        public Builder id(Long id) {
            event.id = id;
            return this;
        }

        public Builder type(NotificationType type) {
            event.type = type;
            return this;
        }

        public Builder title(String title) {
            event.title = title;
            return this;
        }

        public Builder message(String message) {
            event.message = message;
            return this;
        }

        public Builder actionUrl(String actionUrl) {
            event.actionUrl = actionUrl;
            return this;
        }

        public Builder actionLabel(String actionLabel) {
            event.actionLabel = actionLabel;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            event.timestamp = timestamp;
            return this;
        }

        public Builder read(boolean read) {
            event.read = read;
            return this;
        }

        public NotificationEvent build() {
            return event;
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public String getAlertClass() {
        return type != null ? type.getAlertClass() : "info";
    }
}
