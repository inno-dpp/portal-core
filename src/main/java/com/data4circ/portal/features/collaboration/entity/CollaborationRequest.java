package com.data4circ.portal.features.collaboration.entity;

import com.data4circ.portal.features.organization.entity.Organization;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a collaboration request between two organizations.
 * The approved record also serves as the source of truth for active collaborations.
 */
@Entity
@Table(
    name = "collaboration_requests",
    indexes = {
        @Index(name = "idx_collab_requester", columnList = "requester_org_id"),
        @Index(name = "idx_collab_target", columnList = "target_org_id"),
        @Index(name = "idx_collab_status", columnList = "status")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_collab_requester_target",
            columnNames = {"requester_org_id", "target_org_id"}
        )
    }
)
public class CollaborationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_org_id", nullable = false)
    private Organization requesterOrg;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_org_id", nullable = false)
    private Organization targetOrg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CollaborationStatus status = CollaborationStatus.PENDING;

    @Column(length = 1000)
    private String requestMessage;

    /** Username of the member who sent the request, so the reviewing admin sees who asked. */
    private String requestedByUsername;

    private String processedBy;

    private LocalDateTime processedAt;

    @Column(length = 1000)
    private String reviewerNotes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
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

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Organization getRequesterOrg() {
        return requesterOrg;
    }

    public void setRequesterOrg(Organization requesterOrg) {
        this.requesterOrg = requesterOrg;
    }

    public Organization getTargetOrg() {
        return targetOrg;
    }

    public void setTargetOrg(Organization targetOrg) {
        this.targetOrg = targetOrg;
    }

    public CollaborationStatus getStatus() {
        return status;
    }

    public void setStatus(CollaborationStatus status) {
        this.status = status;
    }

    public String getRequestMessage() {
        return requestMessage;
    }

    public void setRequestMessage(String requestMessage) {
        this.requestMessage = requestMessage;
    }

    public String getRequestedByUsername() {
        return requestedByUsername;
    }

    public void setRequestedByUsername(String requestedByUsername) {
        this.requestedByUsername = requestedByUsername;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(String processedBy) {
        this.processedBy = processedBy;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public String getReviewerNotes() {
        return reviewerNotes;
    }

    public void setReviewerNotes(String reviewerNotes) {
        this.reviewerNotes = reviewerNotes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
