package com.data4circ.portal.features.notification.entity;

/**
 * Types of notifications that can be sent to users.
 */
public enum NotificationType {

    // Onboarding workflow notifications
    ONBOARDING_SUBMITTED("Onboarding Request Submitted", "info"),
    ONBOARDING_APPROVED("Onboarding Request Approved", "success"),
    ONBOARDING_REJECTED("Onboarding Request Rejected", "warning"),

    // Connector notifications
    CONNECTOR_ONLINE("Connector Online", "success"),
    CONNECTOR_OFFLINE("Connector Offline", "danger"),
    CONNECTOR_CREATED("Connector Created", "info"),

    // Dataset notifications
    DATASET_PUBLISHED("Dataset Published", "success"),
    DATASET_UPDATED("Dataset Updated", "info"),
    DATASET_DELETED("Dataset Deleted", "warning"),

    // Organization notifications
    MEMBER_INVITED("Member Invitation", "info"),
    MEMBER_JOINED("Member Joined", "success"),
    MEMBER_REMOVED("Member Removed", "warning"),

    // Collaboration notifications
    COLLABORATION_REQUEST_RECEIVED("Collaboration Request Received", "info"),
    COLLABORATION_REQUEST_APPROVED("Collaboration Request Approved", "success"),
    COLLABORATION_REQUEST_REJECTED("Collaboration Request Rejected", "warning"),
    COLLABORATION_CANCELLED("Collaboration Cancelled", "warning"),

    // SPIP notifications
    SPIP_SYNC_COMPLETE("SPIP Sync Complete", "success"),
    SPIP_SYNC_FAILED("SPIP Sync Failed", "danger"),

    // System notifications
    SYSTEM_ANNOUNCEMENT("System Announcement", "info"),
    SYSTEM_MAINTENANCE("Scheduled Maintenance", "warning");

    private final String displayName;
    private final String alertClass;

    NotificationType(String displayName, String alertClass) {
        this.displayName = displayName;
        this.alertClass = alertClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Bootstrap alert class (success, info, warning, danger)
     */
    public String getAlertClass() {
        return alertClass;
    }
}
