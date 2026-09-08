package com.data4circ.portal.features.connectors.dto;

import com.data4circ.portal.features.connectors.entity.Connector;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnectorResponse {

    private Long id;
    private String name;
    private String description;
    private String type;
    private String endpoint;
    private String status;
    private String configuration;
    private LocalDateTime lastHeartbeat;
    private String healthEndpoint;
    private String heartbeatToken;
    private String apiToken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ConnectorResponse fromEntity(Connector connector) {
        ConnectorResponse response = new ConnectorResponse();
        response.id = connector.getId();
        response.name = connector.getName();
        response.description = connector.getDescription();
        response.type = connector.getType() != null ? connector.getType().name() : null;
        response.endpoint = connector.getEndpoint();
        response.status = connector.getStatus() != null ? connector.getStatus().name() : null;
        response.configuration = connector.getConfiguration();
        response.lastHeartbeat = connector.getLastHeartbeat();
        response.healthEndpoint = connector.getHealthEndpoint();
        response.heartbeatToken = connector.getHeartbeatToken();
        response.apiToken = connector.getApiToken();
        response.createdAt = connector.getCreatedAt();
        response.updatedAt = connector.getUpdatedAt();
        return response;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public String getEndpoint() { return endpoint; }
    public String getStatus() { return status; }
    public String getConfiguration() { return configuration; }
    public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
    public String getHealthEndpoint() { return healthEndpoint; }
    public String getHeartbeatToken() { return heartbeatToken; }
    public String getApiToken() { return apiToken; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
