package com.data4circ.portal.features.collaboration.spi;

import com.data4circ.portal.features.organization.entity.Organization;

/**
 * Extension point for a module to react to a collaboration starting or ending between two
 * organizations — SPIP uses this to create/remove the reciprocal "is_partner_of_" attributes
 * and verification policy each side needs to decrypt the other's data.
 *
 * <p>{@code CollaborationService} / {@code CollaborationCancellationSideEffects} depend on
 * {@code List<CollaborationPartnerHook>} rather than a specific module's service — the same
 * "ask by capability" shape as {@link com.data4circ.portal.common.nav.NavContribution} and
 * {@link com.data4circ.portal.common.demo.DemoDataContributor}. Every hook runs best-effort:
 * a failure in one must not stop the others or the collaboration change itself.</p>
 */
public interface CollaborationPartnerHook {

    void onPartnershipEstablished(Organization orgA, Organization orgB);

    /**
     * @return failure notes for the cancellation audit trail, or {@code null} if this hook's
     * teardown fully succeeded (or found nothing to tear down).
     */
    String onPartnershipEnded(Organization orgA, Organization orgB);
}
