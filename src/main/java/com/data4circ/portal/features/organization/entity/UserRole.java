package com.data4circ.portal.features.organization.entity;

public enum UserRole {
    PLATFORM_ADMIN("Platform Admin"),
    ORG_ADMIN("Organization Admin"),
    SPIP_PRIVILEGED_USER("SPIP Privileged User"),
    ORG_MEMBER("Organization Member");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}