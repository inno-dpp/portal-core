package com.data4circ.portal.features.collaboration.entity;

/**
 * Status of a collaboration request between two organizations.
 */
public enum CollaborationStatus {

    PENDING("Pending", "bg-warning"),
    APPROVED("Approved", "bg-success"),
    REJECTED("Rejected", "bg-danger");

    private final String displayName;
    private final String badgeClass;

    CollaborationStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeClass() {
        return badgeClass;
    }
}
