package com.data4circ.portal.features.connectors.entity;

import com.data4circ.portal.features.organization.entity.Organization;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "edc_port_allocation", indexes = {
    @Index(name = "idx_edc_port_org", columnList = "organization_id", unique = true),
    @Index(name = "idx_edc_port_slot", columnList = "slot_index", unique = true)
})
public class EdcPortAllocation {

    public enum ProvisioningStatus {
        PROVISIONED, RUNNING, STOPPED, ERROR
    }

    public enum EdcRole {
        PROVIDER, CONSUMER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "edc_role")
    private EdcRole edcRole;

    @Column(name = "slot_index", nullable = false, unique = true)
    private int slotIndex;

    @Column(name = "base_port", nullable = false)
    private int basePort;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProvisioningStatus status = ProvisioningStatus.PROVISIONED;

    @Column(name = "compose_project_name")
    private String composeProjectName;

    @Column(name = "backend_container_name")
    private String backendContainerName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Port accessors based on allocation scheme
    public int getEdcUiPort() { return basePort; }
    public int getEdcControlPort() { return basePort + 1; }
    public int getEdcManagementPort() { return basePort + 2; }
    public int getEdcDspPort() { return basePort + 3; }
    public int getEdcPublicPort() { return basePort + 4; }
    public int getEdcDebugPort() { return basePort + 5; }
    public int getPostgresPort() { return basePort + 6; }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }

    public EdcRole getEdcRole() { return edcRole; }
    public void setEdcRole(EdcRole edcRole) { this.edcRole = edcRole; }

    public int getSlotIndex() { return slotIndex; }
    public void setSlotIndex(int slotIndex) { this.slotIndex = slotIndex; }

    public int getBasePort() { return basePort; }
    public void setBasePort(int basePort) { this.basePort = basePort; }

    public ProvisioningStatus getStatus() { return status; }
    public void setStatus(ProvisioningStatus status) { this.status = status; }

    public String getComposeProjectName() { return composeProjectName; }
    public void setComposeProjectName(String composeProjectName) { this.composeProjectName = composeProjectName; }

    public String getBackendContainerName() { return backendContainerName; }
    public void setBackendContainerName(String backendContainerName) { this.backendContainerName = backendContainerName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
