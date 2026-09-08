package com.data4circ.portal.features.connectors.entity;

import com.data4circ.portal.common.converter.EncryptedStringConverter;
import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.connectors.enums.ConnectorStatus;
import com.data4circ.portal.features.connectors.enums.ConnectorType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Entity
@Table(name = "connectors")
public class Connector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @NotNull
    private ConnectorType type;

    @NotBlank
    private String endpoint;

    @Enumerated(EnumType.STRING)
    private ConnectorStatus status = ConnectorStatus.OFFLINE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "configuration", columnDefinition = "TEXT")
    private String configuration;

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @Column(name = "health_endpoint")
    private String healthEndpoint;

    @Column(name = "heartbeat_token", unique = true)
    private String heartbeatToken;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "api_token", length = 1024)
    private String apiToken;

    /**
     * Key of the onboarding tool this connector was materialized for
     * (e.g. "spip", "ckan"); null for manually created connectors.
     */
    @Column(name = "tool_key", length = 50)
    private String toolKey;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ConnectorType getType() {
        return type;
    }

    public void setType(ConnectorType type) {
        this.type = type;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public ConnectorStatus getStatus() {
        return status;
    }

    public void setStatus(ConnectorStatus status) {
        this.status = status;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    public LocalDateTime getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(LocalDateTime lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public String getHealthEndpoint() {
        return healthEndpoint;
    }

    public void setHealthEndpoint(String healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    public String getHeartbeatToken() {
        return heartbeatToken;
    }

    public void setHeartbeatToken(String heartbeatToken) {
        this.heartbeatToken = heartbeatToken;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getToolKey() {
        return toolKey;
    }

    public void setToolKey(String toolKey) {
        this.toolKey = toolKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // Helper methods
    public boolean isOnline() {
        return status == ConnectorStatus.ONLINE;
    }

    public String getStatusBadgeClass() {
        return switch (status) {
            case ONLINE -> "bg-success";
            case OFFLINE -> "bg-secondary";
            case ERROR -> "bg-danger";
            case MAINTENANCE -> "bg-warning";
        };
    }

    public String getStatusIndicatorClass() {
        return switch (status) {
            case ONLINE -> "online";
            case OFFLINE -> "offline";
            case ERROR -> "error";
            case MAINTENANCE -> "maintenance";
        };
    }
}
