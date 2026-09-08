package com.data4circ.portal.features.organization.entity;

public enum OnboardingRequestStatus {
    PENDING("Pending Review"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    ORGANIZATION_CREATED("Organization Created");

    private final String displayName;

    OnboardingRequestStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}