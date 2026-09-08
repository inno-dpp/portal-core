package com.data4circ.portal.features.collaboration.entity;

import com.data4circ.portal.features.organization.entity.Organization;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Audit record for each time an approved collaboration between two organisations is cancelled.
 *
 * <p>Multiple cancellations can exist for the same pair of organisations (collaborate &rarr; cancel
 * &rarr; re-initiate &rarr; cancel again). All cancellations are surfaced as warnings when one of
 * the organisations attempts to initiate a new collaboration with the same partner.</p>
 */
@Entity
@Table(
    name = "collaboration_cancellations",
    indexes = {
        @Index(name = "idx_collab_cancel_pair_a", columnList = "requester_org_id,target_org_id"),
        @Index(name = "idx_collab_cancel_pair_b", columnList = "target_org_id,requester_org_id")
    }
)
public class CollaborationCancellation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Requester side of the original CollaborationRequest. Retained for symmetric lookups. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_org_id", nullable = false)
    private Organization requesterOrg;

    /** Target side of the original CollaborationRequest. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_org_id", nullable = false)
    private Organization targetOrg;

    /** Organisation whose admin pressed the Cancel Collaboration button. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiating_org_id", nullable = false)
    private Organization initiatingOrg;

    @Column(name = "initiating_username", nullable = false)
    private String initiatingUsername;

    @Column(name = "reason", length = 2000, nullable = false)
    private String reason;

    /**
     * Notes captured from the SPIP teardown step. Null if every SPIP delete succeeded.
     * Stored as a free-text summary so admins can investigate partial-cleanup incidents later.
     */
    @Column(name = "spip_teardown_notes", length = 4000)
    private String spipTeardownNotes;

    @Column(name = "cancelled_at", nullable = false, updatable = false)
    private LocalDateTime cancelledAt;

    @PrePersist
    protected void onCreate() {
        cancelledAt = LocalDateTime.now();
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

    public Organization getInitiatingOrg() {
        return initiatingOrg;
    }

    public void setInitiatingOrg(Organization initiatingOrg) {
        this.initiatingOrg = initiatingOrg;
    }

    public String getInitiatingUsername() {
        return initiatingUsername;
    }

    public void setInitiatingUsername(String initiatingUsername) {
        this.initiatingUsername = initiatingUsername;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getSpipTeardownNotes() {
        return spipTeardownNotes;
    }

    public void setSpipTeardownNotes(String spipTeardownNotes) {
        this.spipTeardownNotes = spipTeardownNotes;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }
}
