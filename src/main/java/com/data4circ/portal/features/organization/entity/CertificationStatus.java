package com.data4circ.portal.features.organization.entity;

public enum CertificationStatus {
    ACTIVE("Active"),
    SUSPENDED("Suspended"),
    INACTIVE("Inactive"),
    RESTRICTED("Restricted Access");

    private final String displayName;

    CertificationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return switch (this) {
            case ACTIVE -> "Organization is fully operational and can use all platform features";
            case SUSPENDED -> "Organization is temporarily suspended due to policy violations or administrative reasons";
            case INACTIVE -> "Organization has voluntarily deactivated their account but may reactivate";
            case RESTRICTED -> "Organization has limited access pending review or compliance verification";
        };
    }

    public String getBadgeClass() {
        return switch (this) {
            case ACTIVE -> "bg-success";        // Green - Operational and healthy
            case SUSPENDED -> "bg-danger";      // Red - Critical issue, blocked
            case INACTIVE -> "bg-info";         // Light Blue - Informational, temporarily paused
            case RESTRICTED -> "bg-warning";    // Orange - Caution, limited access
        };
    }

    public String getIconClass() {
        return switch (this) {
            case ACTIVE -> "fas fa-check-circle";
            case SUSPENDED -> "fas fa-ban";
            case INACTIVE -> "fas fa-pause-circle";
            case RESTRICTED -> "fas fa-exclamation-triangle";
        };
    }
}