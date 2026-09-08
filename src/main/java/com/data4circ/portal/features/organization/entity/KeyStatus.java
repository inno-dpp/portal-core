package com.data4circ.portal.features.organization.entity;

public enum KeyStatus {
    ACTIVE("Active"),
    PENDING("Pending"),
    EXPIRED("Expired"),
    REVOKED("Revoked");

    private final String displayName;

    KeyStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
