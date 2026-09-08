package com.data4circ.portal.features.organization.entity;

public enum OrganizationType {
    MANUFACTURER("Manufacturer"),
    RECYCLER("Recycler/Waste Manager"),
    REMANUFACTURER("Remanufacturer"),
    TECHNOLOGY_PROVIDER("Technology Provider"),
    RESEARCH_INSTITUTION("Research Institution"),
    ASSOCIATION("Industry Association"),
    OTHER("Other");

    private final String displayName;

    OrganizationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}