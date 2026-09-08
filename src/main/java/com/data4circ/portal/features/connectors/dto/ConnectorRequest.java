package com.data4circ.portal.features.connectors.dto;

import com.data4circ.portal.features.connectors.entity.Connector;
import com.data4circ.portal.features.connectors.enums.ConnectorStatus;
import com.data4circ.portal.features.connectors.enums.ConnectorType;
import com.data4circ.portal.features.organization.entity.Organization;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConnectorRequest {

    private String name;
    private String description;
    private String type;
    private String endpoint;
    private String status;
    private String configuration;
    private String apiToken;
    private String healthEndpoint;

    public Map<String, String> validateForCreate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (name == null || name.isBlank()) {
            errors.put("name", "Name is required");
        }
        if (type == null || type.isBlank()) {
            errors.put("type", "Type is required");
        } else if (!isValidEnum(ConnectorType.class, type)) {
            errors.put("type", "Invalid type. Valid values: " + enumValues(ConnectorType.class));
        }
        if (endpoint == null || endpoint.isBlank()) {
            errors.put("endpoint", "Endpoint is required");
        }
        if (status != null && !status.isBlank() && !isValidEnum(ConnectorStatus.class, status)) {
            errors.put("status", "Invalid status. Valid values: " + enumValues(ConnectorStatus.class));
        }
        return errors;
    }

    public Map<String, String> validateForUpdate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (type != null && !type.isBlank() && !isValidEnum(ConnectorType.class, type)) {
            errors.put("type", "Invalid type. Valid values: " + enumValues(ConnectorType.class));
        }
        if (status != null && !status.isBlank() && !isValidEnum(ConnectorStatus.class, status)) {
            errors.put("status", "Invalid status. Valid values: " + enumValues(ConnectorStatus.class));
        }
        return errors;
    }

    public Connector toEntity(Organization organization) {
        Connector connector = new Connector();
        connector.setName(name);
        connector.setDescription(description);
        connector.setType(ConnectorType.valueOf(type));
        connector.setEndpoint(endpoint);
        connector.setStatus(status != null && !status.isBlank()
                ? ConnectorStatus.valueOf(status) : ConnectorStatus.OFFLINE);
        connector.setConfiguration(configuration);
        connector.setApiToken(apiToken);
        connector.setHealthEndpoint(healthEndpoint);
        connector.setOrganization(organization);
        return connector;
    }

    public void applyTo(Connector connector) {
        if (name != null && !name.isBlank()) {
            connector.setName(name);
        }
        if (description != null) {
            connector.setDescription(description);
        }
        if (type != null && !type.isBlank()) {
            connector.setType(ConnectorType.valueOf(type));
        }
        if (endpoint != null && !endpoint.isBlank()) {
            connector.setEndpoint(endpoint);
        }
        if (status != null && !status.isBlank()) {
            connector.setStatus(ConnectorStatus.valueOf(status));
        }
        if (configuration != null) {
            connector.setConfiguration(configuration);
        }
        if (apiToken != null && !apiToken.isBlank()) {
            connector.setApiToken(apiToken);
        }
        if (healthEndpoint != null) {
            connector.setHealthEndpoint(healthEndpoint);
        }
    }

    private static <E extends Enum<E>> boolean isValidEnum(Class<E> enumClass, String value) {
        try {
            Enum.valueOf(enumClass, value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static <E extends Enum<E>> String enumValues(Class<E> enumClass) {
        StringBuilder sb = new StringBuilder();
        for (E constant : enumClass.getEnumConstants()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(constant.name());
        }
        return sb.toString();
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getConfiguration() { return configuration; }
    public void setConfiguration(String configuration) { this.configuration = configuration; }

    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }

    public String getHealthEndpoint() { return healthEndpoint; }
    public void setHealthEndpoint(String healthEndpoint) { this.healthEndpoint = healthEndpoint; }
}
