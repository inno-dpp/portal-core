package com.data4circ.portal.features.connectors.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * DTO for heartbeat response sent back to connectors.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HeartbeatResponse {

    private String status;
    private Long connectorId;
    private String connectorName;
    private LocalDateTime timestamp;

    public HeartbeatResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public HeartbeatResponse(String status, Long connectorId, String connectorName) {
        this.status = status;
        this.connectorId = connectorId;
        this.connectorName = connectorName;
        this.timestamp = LocalDateTime.now();
    }

    public static HeartbeatResponse accepted(Long connectorId, String connectorName) {
        return new HeartbeatResponse("accepted", connectorId, connectorName);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getConnectorId() {
        return connectorId;
    }

    public void setConnectorId(Long connectorId) {
        this.connectorId = connectorId;
    }

    public String getConnectorName() {
        return connectorName;
    }

    public void setConnectorName(String connectorName) {
        this.connectorName = connectorName;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
