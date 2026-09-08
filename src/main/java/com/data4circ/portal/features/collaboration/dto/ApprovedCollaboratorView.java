package com.data4circ.portal.features.collaboration.dto;

import com.data4circ.portal.features.organization.entity.Organization;

import java.time.LocalDateTime;

/**
 * View model for the "Current Collaborators" list on the organisation edit page. Pairs the partner
 * organisation with the full lifecycle of the underlying request (who asked, when, with what
 * message; who approved, when, with what note), so the entry can show status/"Approved on" context
 * and offer an inline "Cancel collaboration" action without the template needing to work out which
 * side of the request is the partner.
 */
public class ApprovedCollaboratorView {

    private final Organization partner;
    private final LocalDateTime approvedAt;
    private final Long requestId;

    /** Whether the organisation viewing this card was the one that sent the original request. */
    private final boolean requestedByViewer;
    private final LocalDateTime requestedAt;
    private final String requestedByUsername;
    private final String requestMessage;
    private final String approvedByUsername;
    private final String reviewerNotes;

    public ApprovedCollaboratorView(Organization partner, LocalDateTime approvedAt, Long requestId,
                                     boolean requestedByViewer, LocalDateTime requestedAt,
                                     String requestedByUsername, String requestMessage,
                                     String approvedByUsername, String reviewerNotes) {
        this.partner = partner;
        this.approvedAt = approvedAt;
        this.requestId = requestId;
        this.requestedByViewer = requestedByViewer;
        this.requestedAt = requestedAt;
        this.requestedByUsername = requestedByUsername;
        this.requestMessage = requestMessage;
        this.approvedByUsername = approvedByUsername;
        this.reviewerNotes = reviewerNotes;
    }

    public Organization getPartner() {
        return partner;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public Long getRequestId() {
        return requestId;
    }

    public boolean isRequestedByViewer() {
        return requestedByViewer;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public String getRequestedByUsername() {
        return requestedByUsername;
    }

    public String getRequestMessage() {
        return requestMessage;
    }

    public String getApprovedByUsername() {
        return approvedByUsername;
    }

    public String getReviewerNotes() {
        return reviewerNotes;
    }

    /** True if there is anything worth showing in a "View details" expander. */
    public boolean hasDetails() {
        return (requestMessage != null && !requestMessage.isBlank())
                || (reviewerNotes != null && !reviewerNotes.isBlank());
    }
}
