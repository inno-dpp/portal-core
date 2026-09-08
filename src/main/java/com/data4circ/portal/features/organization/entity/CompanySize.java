package com.data4circ.portal.features.organization.entity;

public enum CompanySize {
    STARTUP("Startup (< 10 employees)"),
    SMALL("Small (10-49 employees)"),
    MEDIUM("Medium (50-249 employees)"),
    LARGE("Large (250+ employees)");

    private final String displayName;

    CompanySize(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}