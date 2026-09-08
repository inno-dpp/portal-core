package com.data4circ.portal.features.onboardingsync.tool.entity;

import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Per-tool synchronization state of an onboarding request. One row per
 * (request, tool key) pair; replaces the legacy spip_synchronized /
 * ckan_synchronized flags on the request itself.
 */
@Entity
@Table(name = "onboarding_tool_sync",
       uniqueConstraints = @UniqueConstraint(columnNames = {"request_id", "tool_key"}))
public class OnboardingToolSync {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private OrganizationOnboardingRequest request;

    @Column(name = "tool_key", nullable = false, length = 50)
    private String toolKey;

    @Column(name = "synced", nullable = false)
    private boolean synced = false;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public OrganizationOnboardingRequest getRequest() {
        return request;
    }

    public void setRequest(OrganizationOnboardingRequest request) {
        this.request = request;
    }

    public String getToolKey() {
        return toolKey;
    }

    public void setToolKey(String toolKey) {
        this.toolKey = toolKey;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }

    public LocalDateTime getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(LocalDateTime syncedAt) {
        this.syncedAt = syncedAt;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
