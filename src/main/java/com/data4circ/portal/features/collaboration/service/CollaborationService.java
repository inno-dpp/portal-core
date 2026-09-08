package com.data4circ.portal.features.collaboration.service;

import com.data4circ.portal.features.collaboration.dto.ApprovedCollaboratorView;
import com.data4circ.portal.features.collaboration.dto.CollaborationHistoryEntry;
import com.data4circ.portal.features.collaboration.entity.CollaborationCancellation;
import com.data4circ.portal.features.collaboration.entity.CollaborationRejection;
import com.data4circ.portal.features.collaboration.entity.CollaborationRequest;
import com.data4circ.portal.features.collaboration.entity.CollaborationStatus;
import com.data4circ.portal.features.collaboration.event.CollaborationCancelledEvent;
import com.data4circ.portal.features.collaboration.repository.CollaborationCancellationRepository;
import com.data4circ.portal.features.collaboration.repository.CollaborationRejectionRepository;
import com.data4circ.portal.features.collaboration.repository.CollaborationRequestRepository;
import com.data4circ.portal.features.notification.dto.NotificationEvent;
import com.data4circ.portal.features.notification.entity.NotificationType;
import com.data4circ.portal.features.notification.service.NotificationService;
import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.organization.service.EmailService;
import com.data4circ.portal.features.collaboration.spi.CollaborationPartnerHook;
import com.data4circ.portal.features.organization.service.OrganizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class CollaborationService {

    private static final Logger logger = LoggerFactory.getLogger(CollaborationService.class);

    @Autowired
    private CollaborationRequestRepository collaborationRequestRepository;

    @Autowired
    private CollaborationCancellationRepository collaborationCancellationRepository;

    @Autowired
    private CollaborationRejectionRepository collaborationRejectionRepository;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EmailService emailService;

    // Modules reacting to a new partnership (e.g. SPIP's partner attributes/policy) — see
    // CollaborationPartnerHook. required = false: a required List<T> autowiring still
    // demands at least one matching bean, which fails outright when every hook (e.g. just
    // SPIP today) is disabled.
    @Autowired(required = false)
    private List<CollaborationPartnerHook> partnerHooks = List.of();

    @Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    /**
     * Send a collaboration request from the requesting user's organization to the target org.
     */
    @Transactional
    public CollaborationRequest sendRequest(User requestingUser, Long targetOrgId, String requestMessage) {
        Organization requesterOrg = requestingUser.getOrganization();
        if (requesterOrg == null) {
            throw new IllegalStateException("You must belong to an organization to send collaboration requests.");
        }
        if (requesterOrg.getId().equals(targetOrgId)) {
            throw new IllegalArgumentException("You cannot send a collaboration request to your own organization.");
        }

        Organization targetOrg = organizationService.findById(targetOrgId)
                .orElseThrow(() -> new IllegalArgumentException("Target organization not found: " + targetOrgId));

        // Check for existing active (PENDING or APPROVED) request in either direction
        Optional<CollaborationRequest> existingActive = collaborationRequestRepository.findActiveRequestBetween(
                requesterOrg.getId(), targetOrgId,
                Arrays.asList(CollaborationStatus.PENDING, CollaborationStatus.APPROVED));

        if (existingActive.isPresent()) {
            CollaborationRequest existing = existingActive.get();
            if (existing.getStatus() == CollaborationStatus.PENDING) {
                throw new IllegalStateException("A collaboration request is already pending between these organizations.");
            } else {
                throw new IllegalStateException("These organizations are already collaborating.");
            }
        }

        // Check if there is an existing rejected request from this requester in this direction
        // (unique constraint allows only one row per direction, so delete it before re-inserting)
        List<CollaborationRequest> existing = collaborationRequestRepository.findLatestBetween(
                requesterOrg.getId(), targetOrgId);
        for (CollaborationRequest old : existing) {
            if (old.getRequesterOrg().getId().equals(requesterOrg.getId())
                    && old.getTargetOrg().getId().equals(targetOrgId)
                    && old.getStatus() == CollaborationStatus.REJECTED) {
                collaborationRequestRepository.delete(old);
                collaborationRequestRepository.flush(); // ensure delete is visible before insert
                logger.info("Deleted previously rejected collaboration request id={} to allow re-send.", old.getId());
                break;
            }
        }

        CollaborationRequest request = new CollaborationRequest();
        request.setRequesterOrg(requesterOrg);
        request.setTargetOrg(targetOrg);
        request.setStatus(CollaborationStatus.PENDING);
        request.setRequestMessage(requestMessage);
        request.setRequestedByUsername(requestingUser.getUsername());
        CollaborationRequest saved = collaborationRequestRepository.save(request);

        logger.info("Collaboration request sent from org {} to org {}", requesterOrg.getName(), targetOrg.getName());

        // Notify target org members
        try {
            NotificationEvent event = NotificationEvent.builder()
                    .type(NotificationType.COLLABORATION_REQUEST_RECEIVED)
                    .title("Collaboration request from " + requesterOrg.getName())
                    .message(requesterOrg.getName() + " wants to collaborate with your organisation. "
                            + "Review the request to approve or decline access.")
                    .actionUrl("/organizations/" + targetOrg.getId() + "/edit#collaboration-requests-section")
                    .actionLabel("Review request")
                    .build();
            notificationService.sendToOrganization(targetOrg.getId(), event);
        } catch (Exception e) {
            logger.error("Failed to send collaboration request notification: {}", e.getMessage(), e);
        }

        // Send email
        emailService.sendCollaborationRequestReceived(targetOrg, requesterOrg, saved.getRequestMessage());

        return saved;
    }

    /**
     * Approve a pending collaboration request.
     */
    @Transactional
    public CollaborationRequest approveRequest(Long requestId, User approvingUser, String reviewerNotes) {
        CollaborationRequest request = collaborationRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Collaboration request not found: " + requestId));

        Organization targetOrg = request.getTargetOrg();
        if (approvingUser.getOrganization() == null
                || !approvingUser.getOrganization().getId().equals(targetOrg.getId())) {
            throw new com.data4circ.portal.common.exception.AccessDeniedException(
                    "You do not have permission to approve this collaboration request.");
        }
        if (request.getStatus() != CollaborationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be approved.");
        }

        request.setStatus(CollaborationStatus.APPROVED);
        request.setProcessedBy(approvingUser.getUsername());
        request.setProcessedAt(LocalDateTime.now());
        request.setReviewerNotes(reviewerNotes);
        CollaborationRequest saved = collaborationRequestRepository.save(request);

        logger.info("Collaboration request id={} approved by {}", requestId, approvingUser.getUsername());

        Organization requesterOrg = request.getRequesterOrg();

        // Let every registered module (e.g. SPIP) react to the new partnership
        for (CollaborationPartnerHook hook : partnerHooks) {
            try {
                hook.onPartnershipEstablished(requesterOrg, targetOrg);
            } catch (Exception e) {
                logger.error("Partner hook {} failed for collaboration {}: {}",
                        hook.getClass().getSimpleName(), requestId, e.getMessage(), e);
            }
        }

        // Notify requester org members
        try {
            NotificationEvent event = NotificationEvent.builder()
                    .type(NotificationType.COLLABORATION_REQUEST_APPROVED)
                    .title("Collaboration Request Approved")
                    .message(targetOrg.getName() + " has approved your collaboration request.")
                    .actionUrl("/organizations/" + targetOrg.getId())
                    .actionLabel("View Partner")
                    .build();
            notificationService.sendToOrganization(requesterOrg.getId(), event);
        } catch (Exception e) {
            logger.error("Failed to send collaboration approved notification: {}", e.getMessage(), e);
        }

        // Send email
        emailService.sendCollaborationRequestApproved(requesterOrg, targetOrg, reviewerNotes);

        return saved;
    }

    /**
     * Reject a pending collaboration request.
     */
    @Transactional
    public CollaborationRequest rejectRequest(Long requestId, User rejectingUser, String reviewerNotes) {
        CollaborationRequest request = collaborationRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Collaboration request not found: " + requestId));

        Organization targetOrg = request.getTargetOrg();
        if (rejectingUser.getOrganization() == null
                || !rejectingUser.getOrganization().getId().equals(targetOrg.getId())) {
            throw new com.data4circ.portal.common.exception.AccessDeniedException(
                    "You do not have permission to reject this collaboration request.");
        }
        if (request.getStatus() != CollaborationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be rejected.");
        }

        request.setStatus(CollaborationStatus.REJECTED);
        request.setProcessedBy(rejectingUser.getUsername());
        request.setProcessedAt(LocalDateTime.now());
        request.setReviewerNotes(reviewerNotes);
        CollaborationRequest saved = collaborationRequestRepository.save(request);

        Organization requesterOrg = request.getRequesterOrg();

        // Persist an immutable audit row so the rejection (and its reason) survives the request
        // row being deleted when the requester re-sends.
        CollaborationRejection rejection = new CollaborationRejection();
        rejection.setRequesterOrg(requesterOrg);
        rejection.setTargetOrg(targetOrg);
        rejection.setRejectedBy(rejectingUser.getUsername());
        rejection.setReason(reviewerNotes);
        collaborationRejectionRepository.save(rejection);

        logger.info("Collaboration request id={} rejected by {}", requestId, rejectingUser.getUsername());

        // Notify requester org members
        try {
            NotificationEvent event = NotificationEvent.builder()
                    .type(NotificationType.COLLABORATION_REQUEST_REJECTED)
                    .title("Collaboration Request Rejected")
                    .message(targetOrg.getName() + " has declined your collaboration request.")
                    .actionUrl("/organizations")
                    .actionLabel("Browse Organizations")
                    .build();
            notificationService.sendToOrganization(requesterOrg.getId(), event);
        } catch (Exception e) {
            logger.error("Failed to send collaboration rejected notification: {}", e.getMessage(), e);
        }

        // Send email
        emailService.sendCollaborationRequestRejected(requesterOrg, targetOrg, reviewerNotes);

        return saved;
    }

    /**
     * Cancel an approved collaboration between two organisations.
     *
     * <p>Authorised for ORG_ADMIN of either side of the relationship. The CollaborationRequest row
     * is deleted (so the unique constraint allows future re-initiation), an immutable
     * {@link CollaborationCancellation} audit row is inserted, and the partner SPIP attributes +
     * collaboration policy are removed from both organisations. The other organisation receives an
     * in-app notification and an email.</p>
     */
    @Transactional
    public CollaborationCancellation cancelCollaboration(Long requestId, User cancellingUser, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Cancellation reason is required.");
        }

        CollaborationRequest request = collaborationRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Collaboration request not found: " + requestId));

        if (request.getStatus() != CollaborationStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED collaborations can be cancelled.");
        }

        Organization requesterOrg = request.getRequesterOrg();
        Organization targetOrg = request.getTargetOrg();
        Organization cancellingOrg = cancellingUser.getOrganization();
        if (cancellingOrg == null
                || (!cancellingOrg.getId().equals(requesterOrg.getId())
                    && !cancellingOrg.getId().equals(targetOrg.getId()))) {
            throw new com.data4circ.portal.common.exception.AccessDeniedException(
                    "You do not have permission to cancel this collaboration.");
        }

        Organization otherOrg = cancellingOrg.getId().equals(requesterOrg.getId()) ? targetOrg : requesterOrg;

        // Persist the audit record. SPIP teardown notes are filled in later by the after-commit
        // side-effects listener (the SPIP teardown runs off the request thread).
        CollaborationCancellation cancellation = new CollaborationCancellation();
        cancellation.setRequesterOrg(requesterOrg);
        cancellation.setTargetOrg(targetOrg);
        cancellation.setInitiatingOrg(cancellingOrg);
        cancellation.setInitiatingUsername(cancellingUser.getUsername());
        cancellation.setReason(reason.trim());
        CollaborationCancellation saved = collaborationCancellationRepository.save(cancellation);

        // Remove the active request row so re-initiation is possible (unique constraint on pair+direction).
        collaborationRequestRepository.delete(request);
        collaborationRequestRepository.flush();

        logger.info("Collaboration id={} cancelled by {} from org {} (other org: {}); SPIP teardown + notifications run asynchronously",
                requestId, cancellingUser.getUsername(), cancellingOrg.getName(), otherOrg.getName());

        // Slow, best-effort work (SPIP teardown, notification, email) runs after this transaction
        // commits, on a background thread, so the cancel request returns immediately.
        eventPublisher.publishEvent(new CollaborationCancelledEvent(
                saved.getId(), requesterOrg.getId(), targetOrg.getId(),
                cancellingOrg.getId(), cancellingOrg.getName(), otherOrg.getId(), reason.trim()));

        return saved;
    }

    /**
     * All prior cancellations between two organisations (either direction), newest first.
     * Used by the view page to warn the user before sending a new collaboration request.
     */
    @Transactional(readOnly = true)
    public List<CollaborationCancellation> findCancellationsBetween(Long orgAId, Long orgBId) {
        return collaborationCancellationRepository.findBetweenOrgsOrderByCancelledAtDesc(orgAId, orgBId);
    }

    /**
     * All prior rejections between two organisations (either direction), newest first.
     * Used to warn the requester before sending a new request and for the history panel.
     */
    @Transactional(readOnly = true)
    public List<CollaborationRejection> findRejectionsBetween(Long orgAId, Long orgBId) {
        return collaborationRejectionRepository.findBetweenOrgsOrderByRejectedAtDesc(orgAId, orgBId);
    }

    /**
     * Rejections a target org has previously issued against a specific requester, newest first.
     * Shown to the reviewing admin alongside that requester's pending request.
     */
    @Transactional(readOnly = true)
    public List<CollaborationRejection> findRejectionsByTargetAgainst(Long requesterOrgId, Long targetOrgId) {
        return collaborationRejectionRepository.findByRequesterAndTargetOrderByRejectedAtDesc(requesterOrgId, targetOrgId);
    }

    /**
     * All collaboration requests this org has rejected (as target), newest first. Backs the
     * "Past Collaboration Requests" section on the edit page so rejections remain traceable.
     */
    @Transactional(readOnly = true)
    public List<CollaborationRejection> findRejectionsIssuedBy(Long targetOrgId) {
        return collaborationRejectionRepository.findByTargetOrgIdOrderByRejectedAtDesc(targetOrgId);
    }

    /**
     * Combined timeline of all rejections and cancellations between two organisations (either
     * direction), newest first. Backs the collaboration-history panel on the org view page.
     */
    @Transactional(readOnly = true)
    public List<CollaborationHistoryEntry> findHistoryBetween(Long orgAId, Long orgBId) {
        List<CollaborationHistoryEntry> entries = new ArrayList<>();
        for (CollaborationRejection r : findRejectionsBetween(orgAId, orgBId)) {
            entries.add(new CollaborationHistoryEntry(
                    CollaborationHistoryEntry.Type.REJECTION,
                    r.getRejectedAt(),
                    r.getTargetOrg().getName() + " rejected " + r.getRequesterOrg().getName(),
                    r.getReason()));
        }
        for (CollaborationCancellation c : findCancellationsBetween(orgAId, orgBId)) {
            entries.add(new CollaborationHistoryEntry(
                    CollaborationHistoryEntry.Type.CANCELLATION,
                    c.getCancelledAt(),
                    "Cancelled by " + c.getInitiatingOrg().getName(),
                    c.getReason()));
        }
        entries.sort(java.util.Comparator.comparing(
                CollaborationHistoryEntry::getTimestamp,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        return entries;
    }

    /**
     * Find the most recent collaboration relationship between two organizations.
     */
    @Transactional(readOnly = true)
    public Optional<CollaborationRequest> findRelationship(Long orgAId, Long orgBId) {
        List<CollaborationRequest> results = collaborationRequestRepository.findLatestBetween(orgAId, orgBId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Find all approved collaborators for a given organization (either side of the relationship).
     */
    @Transactional(readOnly = true)
    public List<Organization> findApprovedCollaborators(Long orgId) {
        List<Organization> result = new ArrayList<>();
        result.addAll(collaborationRequestRepository.findApprovedCollaboratorsByRequester(orgId));
        result.addAll(collaborationRequestRepository.findApprovedCollaboratorsByTarget(orgId));
        return result;
    }

    /**
     * Approved collaborators for an org paired with the approval date, newest first. Backs the
     * enriched "Current Collaborators" list (partner, status, "Approved on").
     */
    @Transactional(readOnly = true)
    public List<ApprovedCollaboratorView> findApprovedCollaboratorViews(Long orgId) {
        List<ApprovedCollaboratorView> result = new ArrayList<>();
        for (CollaborationRequest cr : collaborationRequestRepository.findApprovedInvolvingOrg(orgId)) {
            boolean requestedByViewer = cr.getRequesterOrg().getId().equals(orgId);
            Organization partner = requestedByViewer ? cr.getTargetOrg() : cr.getRequesterOrg();
            result.add(new ApprovedCollaboratorView(
                    partner, cr.getProcessedAt(), cr.getId(),
                    requestedByViewer, cr.getCreatedAt(),
                    cr.getRequestedByUsername(), cr.getRequestMessage(),
                    cr.getProcessedBy(), cr.getReviewerNotes()));
        }
        return result;
    }

    /**
     * Find pending incoming collaboration requests for the given target org.
     */
    @Transactional(readOnly = true)
    public List<CollaborationRequest> findPendingIncomingRequests(Long targetOrgId) {
        return collaborationRequestRepository.findByTargetOrgIdAndStatusOrderByCreatedAtDesc(
                targetOrgId, CollaborationStatus.PENDING);
    }

    /**
     * Count pending incoming requests (for notification badge).
     */
    @Transactional(readOnly = true)
    public long countPendingIncoming(Long targetOrgId) {
        return collaborationRequestRepository.countByTargetOrgIdAndStatus(targetOrgId, CollaborationStatus.PENDING);
    }

    /**
     * All requests (PLATFORM_ADMIN).
     */
    @Transactional(readOnly = true)
    public List<CollaborationRequest> findAll() {
        return collaborationRequestRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * All requests of given status (PLATFORM_ADMIN).
     */
    @Transactional(readOnly = true)
    public List<CollaborationRequest> findByStatus(CollaborationStatus status) {
        return collaborationRequestRepository.findByStatusOrderByCreatedAtDesc(status);
    }
}
