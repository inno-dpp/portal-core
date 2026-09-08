package com.data4circ.portal.features.collaboration.dto;

import java.time.LocalDateTime;

/**
 * A single row in the combined collaboration-history timeline shown on the organisation
 * view page. Unifies rejections and cancellations between two organisations so both can be
 * rendered together, newest first.
 */
public class CollaborationHistoryEntry {

    public enum Type {
        REJECTION,
        CANCELLATION
    }

    private final Type type;
    private final LocalDateTime timestamp;
    /** Short human-readable description of who acted, e.g. "Rejected by Acme Corp". */
    private final String actor;
    private final String reason;

    public CollaborationHistoryEntry(Type type, LocalDateTime timestamp, String actor, String reason) {
        this.type = type;
        this.timestamp = timestamp;
        this.actor = actor;
        this.reason = reason;
    }

    public Type getType() {
        return type;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getActor() {
        return actor;
    }

    public String getReason() {
        return reason;
    }
}
