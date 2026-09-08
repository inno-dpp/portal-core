package com.data4circ.portal.features.onboardingsync.tool;

import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;

import java.util.List;
import java.util.Map;

/**
 * A pluggable external tool that must (or may) be provisioned for an organization
 * during onboarding, e.g. SPIP or CKAN. Implementations are discovered by Spring and
 * exposed through {@link OnboardingToolRegistry}; per-tool enablement and approval
 * gating are controlled by {@code app.onboarding.tools.<key>} configuration.
 */
public interface OnboardingToolProvisioner {

    /**
     * Stable identifier used as the configuration key, the {@code tool_key} column
     * value and the synchronize endpoint path segment.
     */
    String getKey();

    /** Human-readable name used in UI cards, flash messages and error messages. */
    String getDisplayName();

    /** Font Awesome icon class for the admin UI card. */
    default String getIconClass() {
        return "fas fa-plug";
    }

    /**
     * Keys of tools that must be synchronized before this one can run
     * (e.g. CKAN consumes the SPIP credentials, so it depends on "spip").
     */
    default List<String> getDependencies() {
        return List.of();
    }

    /**
     * Whether this tool provisions user identity/authentication credentials that other
     * tools may reuse instead of provisioning their own (e.g. SPIP's admin-chosen
     * username and generated password). Dependents look this up as a capability
     * through {@link OnboardingToolRegistry#identityProvider()} instead of importing a
     * specific provisioner's key, so a tool offering identity can be swapped or
     * removed (see the "Dependents name a capability, not a tool" principle in
     * {@code docs/developer/SPIP-PLUGIN-DECOUPLING-PLAN.md}).
     */
    default boolean providesIdentity() {
        return false;
    }

    /**
     * Describes the admin input fields rendered on the onboarding request detail view,
     * in display order. All submitted values reach {@link #synchronize} in the params map.
     */
    List<OnboardingToolInputField> describeInputs(OrganizationOnboardingRequest request);

    /**
     * Provision the tool for the given onboarding request. Implementations mutate the
     * request's typed output fields (credentials, tokens, identifiers) but must not
     * change the sync state — the orchestrator records it after this method returns.
     *
     * @param request the onboarding request being synchronized
     * @param params  the raw form parameters submitted by the admin
     * @return diagnostic payload stored on the sync record
     * @throws IllegalArgumentException on invalid admin input
     * @throws IllegalStateException    when remote provisioning fails
     */
    OnboardingToolSyncOutcome synchronize(OrganizationOnboardingRequest request, Map<String, String> params);

    /** Optional extra status line for the UI card (e.g. "API Token: Generated"). */
    default String getStatusNote(OrganizationOnboardingRequest request) {
        return null;
    }

    /**
     * Credential to store (encrypted) on the connector materialized for this tool in
     * the organization's "My Tools" section, or {@code null} if the tool has none.
     */
    default String getConnectorApiToken(OrganizationOnboardingRequest request) {
        return null;
    }

    /**
     * Per-organization endpoint for the materialized connector, overriding the static
     * connector template endpoint when non-null (e.g. the Keycloak instance URL the
     * admin selected during synchronization).
     */
    default String getConnectorEndpoint(OrganizationOnboardingRequest request) {
        return null;
    }

    /**
     * Per-organization configuration payload for the materialized connector,
     * overriding the static connector template configuration when non-null.
     */
    default String getConnectorConfiguration(OrganizationOnboardingRequest request) {
        return null;
    }
}
