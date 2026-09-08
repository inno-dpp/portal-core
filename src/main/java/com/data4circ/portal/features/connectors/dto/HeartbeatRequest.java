package com.data4circ.portal.features.connectors.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for incoming heartbeat payload from connectors.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HeartbeatRequest {

    private String status;
    private String version;

    public HeartbeatRequest() {
    }

    public HeartbeatRequest(String status, String version) {
        this.status = status;
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
