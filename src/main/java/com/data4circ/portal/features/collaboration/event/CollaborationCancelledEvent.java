package com.data4circ.portal.features.collaboration.event;

/**
 * Published after an approved collaboration has been cancelled and the transaction has committed.
 * Carries only primitive identifiers (no JPA entities) so the after-commit listener can safely run
 * on a background thread. The slow side-effects — SPIP attribute/policy teardown and the
 * notification/email to the other organisation — are handled by the listener so the user's cancel
 * request returns immediately instead of blocking on external SPIP/SMTP calls.
 */
public class CollaborationCancelledEvent {

    private final Long cancellationId;
    private final Long requesterOrgId;
    private final Long targetOrgId;
    private final Long cancellingOrgId;
    private final String cancellingOrgName;
    private final Long otherOrgId;
    private final String reason;

    public CollaborationCancelledEvent(Long cancellationId, Long requesterOrgId, Long targetOrgId,
                                       Long cancellingOrgId, String cancellingOrgName, Long otherOrgId,
                                       String reason) {
        this.cancellationId = cancellationId;
        this.requesterOrgId = requesterOrgId;
        this.targetOrgId = targetOrgId;
        this.cancellingOrgId = cancellingOrgId;
        this.cancellingOrgName = cancellingOrgName;
        this.otherOrgId = otherOrgId;
        this.reason = reason;
    }

    public Long getCancellationId() {
        return cancellationId;
    }

    public Long getRequesterOrgId() {
        return requesterOrgId;
    }

    public Long getTargetOrgId() {
        return targetOrgId;
    }

    public Long getCancellingOrgId() {
        return cancellingOrgId;
    }

    public String getCancellingOrgName() {
        return cancellingOrgName;
    }

    public Long getOtherOrgId() {
        return otherOrgId;
    }

    public String getReason() {
        return reason;
    }
}
