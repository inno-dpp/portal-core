package com.data4circ.portal.features.collaboration.event;

import com.data4circ.portal.features.collaboration.entity.CollaborationCancellation;
import com.data4circ.portal.features.collaboration.repository.CollaborationCancellationRepository;
import com.data4circ.portal.features.collaboration.spi.CollaborationPartnerHook;
import com.data4circ.portal.features.notification.dto.NotificationEvent;
import com.data4circ.portal.features.notification.entity.NotificationType;
import com.data4circ.portal.features.notification.service.NotificationService;
import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.organization.service.EmailService;
import com.data4circ.portal.features.organization.service.OrganizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Handles the slow, best-effort side-effects of cancelling a collaboration — partner
 * attribute/policy teardown (e.g. SPIP's) on both organisations and the notification/email
 * to the other organisation — on a background thread, after the cancellation transaction
 * has committed.
 *
 * <p>This keeps the user-facing cancel request fast: previously SPIP's API and the SMTP call
 * ran inline (the SPIP client has a 30s connect timeout), so a slow or unreachable SPIP server
 * made the cancel appear to hang or fail even though it eventually succeeded.</p>
 */
@Component
public class CollaborationCancellationSideEffects {

    private static final Logger logger = LoggerFactory.getLogger(CollaborationCancellationSideEffects.class);

    // Modules reacting to a partnership ending (e.g. SPIP's partner attribute/policy
    // teardown) — see CollaborationPartnerHook. Constructor-injected List<T> tolerates
    // zero matching beans fine (unlike a required field-level @Autowired collection).
    private final List<CollaborationPartnerHook> partnerHooks;
    private final OrganizationService organizationService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final CollaborationCancellationRepository cancellationRepository;

    public CollaborationCancellationSideEffects(List<CollaborationPartnerHook> partnerHooks,
                                                OrganizationService organizationService,
                                                NotificationService notificationService,
                                                EmailService emailService,
                                                CollaborationCancellationRepository cancellationRepository) {
        this.partnerHooks = partnerHooks;
        this.organizationService = organizationService;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.cancellationRepository = cancellationRepository;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCollaborationCancelled(CollaborationCancelledEvent event) {
        Organization requesterOrg = organizationService.findById(event.getRequesterOrgId()).orElse(null);
        Organization targetOrg = organizationService.findById(event.getTargetOrgId()).orElse(null);

        // Partner teardown for both organisations (best-effort; failures captured, never
        // rethrown). Notes from every hook are joined for the audit row below.
        List<String> teardownNotes = new java.util.ArrayList<>();
        if (requesterOrg != null && targetOrg != null) {
            for (CollaborationPartnerHook hook : partnerHooks) {
                try {
                    String notes = hook.onPartnershipEnded(requesterOrg, targetOrg);
                    if (notes != null) {
                        teardownNotes.add(notes);
                    }
                } catch (Exception e) {
                    logger.error("Partner hook {} teardown failed for cancellation id={}: {}",
                            hook.getClass().getSimpleName(), event.getCancellationId(), e.getMessage(), e);
                    teardownNotes.add("unexpected error: " + e.getMessage());
                }
            }
        }
        String combinedNotes = teardownNotes.isEmpty() ? null : String.join("; ", teardownNotes);

        // Record the teardown outcome on the audit row.
        try {
            CollaborationCancellation cancellation =
                    cancellationRepository.findById(event.getCancellationId()).orElse(null);
            if (cancellation != null) {
                cancellation.setSpipTeardownNotes(combinedNotes);
                cancellationRepository.save(cancellation);
            }
        } catch (Exception e) {
            logger.error("Failed to persist partner teardown notes for cancellation id={}: {}",
                    event.getCancellationId(), e.getMessage(), e);
        }

        // Notify the other organisation.
        try {
            NotificationEvent notification = NotificationEvent.builder()
                    .type(NotificationType.COLLABORATION_CANCELLED)
                    .title("Collaboration Cancelled")
                    .message(event.getCancellingOrgName() + " has cancelled the collaboration with your organisation.")
                    .actionUrl("/organizations/" + event.getCancellingOrgId())
                    .actionLabel("View Organization")
                    .build();
            notificationService.sendToOrganization(event.getOtherOrgId(), notification);
        } catch (Exception e) {
            logger.error("Failed to send collaboration cancelled notification for cancellation id={}: {}",
                    event.getCancellationId(), e.getMessage(), e);
        }

        // Email the other organisation.
        try {
            Organization otherOrg = organizationService.findById(event.getOtherOrgId()).orElse(null);
            Organization cancellingOrg = organizationService.findById(event.getCancellingOrgId()).orElse(null);
            if (otherOrg != null && cancellingOrg != null) {
                emailService.sendCollaborationCancelled(otherOrg, cancellingOrg, event.getReason());
            }
        } catch (Exception e) {
            logger.error("Failed to send collaboration cancelled email for cancellation id={}: {}",
                    event.getCancellationId(), e.getMessage(), e);
        }

        logger.info("Collaboration cancellation side-effects completed for cancellation id={} (partner teardown notes: {})",
                event.getCancellationId(), combinedNotes);
    }
}
