package com.data4circ.portal.features.connectors.enums;

public enum ConnectorType {
    DATA_PROVIDER("Data Provider"),
    DATA_CONSUMER("Data Consumer"),
    EDC_CONNECTOR("EDC Connector"),
    SPIP_AGENT("SPIP Agent"),
    SPIP_PLATFORM("SPIP Platform"),
    DOCUMENTS_MANAGER("Documents Manager"),
    EXTERNAL_API("External API"),
    DATABASE("Database"),
    FILE_SYSTEM("File System"),
    PLATFORM_DIGITAL_TOOL("Platform Digital Tool"),
    OBJECT_STORAGE_SYSTEM("Object Storage System"),
    FEDERATED_CATALOG_CKAN("Federated Catalog CKAN"),
    IDENTITY_PROVIDER("Identity Provider");

    private final String displayName;

    ConnectorType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
