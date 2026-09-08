package com.data4circ.portal.features.collaboration.entity;

import com.data4circ.portal.features.organization.entity.Organization;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Audit record for each time a pending collaboration request is rejected.
 *
 * <p>The originating {@link CollaborationRequest} row is deleted when the requester re-sends
 * (the unique constraint permits only one row per direction), so rejection details would
 * otherwise be lost. This immutable record preserves the history: multiple rejections can exist
 * for the same pair of organisations (request &rarr; reject &rarr; re-request &rarr; reject again).
 * All rejections are surfaced as warnings when the requester attempts a new request with the same
 * partner and to the reviewing admin before they decide.</p>
 */
@Entity
@Table(
    name = "collaboration_rejections",
    indexes = {
        @Index(name = "idx_collab_reject_pair_a", columnList = "requester_org_id,target_org_id"),
        @Index(name = "idx_collab_reject_pair_b", columnList = "target_org_id,requester_org_id")
    }
)
public class CollaborationRejection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Organisation that had sent the collaboration request. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_org_id", nullable = false)
    private Organization requesterOrg;

    /** Organisation whose admin rejected the request. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_org_id", nullable = false)
    private Organization targetOrg;

    /** Username of the admin who pressed Reject. */
    @Column(name = "rejected_by", nullable = false)
    private String rejectedBy;

    /** Free-text reason supplied by the reviewing admin. Null/blank when none was given. */
    @Column(name = "reason", length = 2000)
    private String reason;

    @Column(name = "rejected_at", nullable = false, updatable = false)
    private LocalDateTime rejectedAt;

    @PrePersist
    protected void onCreate() {
        rejectedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
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

    public String getRejectedBy() {
        return rejectedBy;
    }

    public void setRejectedBy(String rejectedBy) {
        this.rejectedBy = rejectedBy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }
}
