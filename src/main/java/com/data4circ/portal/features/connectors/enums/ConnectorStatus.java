package com.data4circ.portal.features.connectors.enums;

public enum ConnectorStatus {
    ONLINE("Online"),
    OFFLINE("Offline"),
    ERROR("Error"),
    MAINTENANCE("Maintenance");

    private final String displayName;

    ConnectorStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
