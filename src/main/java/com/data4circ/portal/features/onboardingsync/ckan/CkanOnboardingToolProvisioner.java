package com.data4circ.portal.features.onboardingsync.ckan;

import com.data4circ.portal.common.util.PasswordGenerator;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolInputField;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolProvisioner;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolRegistry;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolSyncOutcome;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolsProperties;
import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;
import com.data4circ.portal.features.organization.service.OrganizationSpipUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Onboarding tool adapter for CKAN: validates the organization short name and runs
 * the idempotent CKAN provisioning workflow through {@link CkanOnboardingSyncService}.
 *
 * <p>Depends on the platform's identity provider (see {@link OnboardingToolRegistry#identityProvider()};
 * SPIP today) only while that tool is required (mirrors
 * {@code KeycloakOnboardingToolProvisioner}'s pattern) — while it's required the admin
 * has no path to skip it, so the dependency keeps forcing identity-provider-before-CKAN
 * ordering and CKAN always reuses its username/password (one login for both systems,
 * the historical and still-default behavior). When the identity provider is optional
 * or disabled, CKAN is no longer gated on it: if the admin skipped it for this
 * request, CKAN provisions its own account instead — the admin picks a username here
 * exactly like the SPIP tool does, and a random password is generated. See
 * {@link CkanOnboardingSyncService#synchronizeOnboardingRequest} for the resolution
 * logic and {@link OrganizationSpipUserService#isCkanUsernameAlreadyTaken} for the
 * uniqueness check shared with SPIP usernames (both live in the same CKAN namespace).</p>
 */
@Component
public class CkanOnboardingToolProvisioner implements OnboardingToolProvisioner {

    public static final String TOOL_KEY = "ckan";
    public static final String ORG_SHORT_NAME_PARAM = "ckanOrgShortName";
    public static final String USERNAME_PARAM = "ckanUsername";

    private static final Logger logger = LoggerFactory.getLogger(CkanOnboardingToolProvisioner.class);

    private final CkanOnboardingSyncService ckanOnboardingSyncService;
    private final OrganizationSpipUserService spipUserService;
    private final OnboardingToolsProperties toolsProperties;
    private final List<OnboardingToolProvisioner> otherTools;

    public CkanOnboardingToolProvisioner(CkanOnboardingSyncService ckanOnboardingSyncService,
                                         OrganizationSpipUserService spipUserService,
                                         OnboardingToolsProperties toolsProperties,
                                         // Spring excludes this bean itself from a same-typed
                                         // collection injection; @Lazy defers resolving the rest
                                         // until first use, so this doesn't force KeycloakOnboarding-
                                         // ToolProvisioner (which asks the identical question) to be
                                         // built first — each would otherwise need the other built,
                                         // a construction cycle between the two.
                                         @Lazy List<OnboardingToolProvisioner> otherTools) {
        this.ckanOnboardingSyncService = ckanOnboardingSyncService;
        this.spipUserService = spipUserService;
        this.toolsProperties = toolsProperties;
        this.otherTools = otherTools;
    }

    /**
     * Depends on the identity provider (e.g. SPIP) only while it's required — once the
     * admin can legitimately skip it, CKAN must be able to run without it too.
     */
    @Override
    public List<String> getDependencies() {
        return identityProvider()
                .filter(tool -> toolsProperties.forKey(tool.getKey()).isRequired())
                .map(tool -> List.of(tool.getKey()))
                .orElse(List.of());
    }

    private Optional<OnboardingToolProvisioner> identityProvider() {
        return OnboardingToolRegistry.findIdentityProvider(otherTools, toolsProperties);
    }

    @Override
    public String getKey() {
        return TOOL_KEY;
    }

    @Override
    public String getDisplayName() {
        return "CKAN";
    }

    @Override
    public String getIconClass() {
        return "fas fa-database";
    }

    @Override
    public List<OnboardingToolInputField> describeInputs(OrganizationOnboardingRequest request) {
        List<OnboardingToolInputField> fields = new ArrayList<>();
        fields.add(new OnboardingToolInputField(
                ORG_SHORT_NAME_PARAM,
                "CKAN Organization Short Name",
                ckanOnboardingSyncService.generateCkanOrgShortName(request.getCompanyName()),
                "[a-z0-9_-]+",
                null,
                100,
                "Organization name format: lowercase letters, numbers, hyphens, and underscores only. "
                        + "Max 100 characters."));

        // Only ask for a CKAN username when there's no SPIP account yet to reuse.
        if (!hasSpipCredentials(request)) {
            fields.add(new OnboardingToolInputField(
                    USERNAME_PARAM,
                    "CKAN Username",
                    spipUserService.generateUsernameFromOrganizationName(request.getCompanyName()),
                    "[a-z0-9]+",
                    3,
                    30,
                    "SPIP is not synchronized for this request, so CKAN provisions its own login. "
                            + "Username format: lowercase letters and numbers only. 3-30 characters."));
        }

        return fields;
    }

    @Override
    public OnboardingToolSyncOutcome synchronize(OrganizationOnboardingRequest request, Map<String, String> params) {
        String ckanOrgShortName = params.get(ORG_SHORT_NAME_PARAM);
        validateCkanOrgShortName(ckanOrgShortName);
        String trimmedName = ckanOrgShortName.trim();

        if (!hasSpipCredentials(request)) {
            String ckanUsername = params.get(USERNAME_PARAM);
            validateCkanUsername(ckanUsername);
            String trimmedUsername = ckanUsername.trim();

            if (spipUserService.isCkanUsernameAlreadyTaken(trimmedUsername, request.getId())) {
                throw new IllegalStateException("CKAN username '" + trimmedUsername
                        + "' is already taken. Please choose a different name.");
            }

            request.setCkanUser(trimmedUsername);
            request.setCkanPassword(generateRandomPassword()); // Plaintext - encrypted by JPA EncryptedStringConverter
            logger.info("Provisioning independent CKAN account '{}' for '{}' (SPIP not synced)",
                    trimmedUsername, request.getCompanyName());
        }

        logger.info("Synchronizing organization '{}' with CKAN using short name '{}'",
                request.getCompanyName(), trimmedName);

        CkanSyncResult result = ckanOnboardingSyncService.synchronizeOnboardingRequest(request, trimmedName);

        if (!result.isSuccess()) {
            throw new IllegalStateException("CKAN synchronization failed: " + result.getErrorMessage());
        }

        // Store API token from sync result (if generated)
        if (result.getApiToken() != null) {
            request.setCkanApiToken(result.getApiToken());
        }
        request.setCkanOrganizationShortName(trimmedName);

        return OnboardingToolSyncOutcome.withDetails(result.buildSummaryMessage());
    }

    @Override
    public String getStatusNote(OrganizationOnboardingRequest request) {
        return request.getCkanApiToken() != null
                ? "API Token: Generated"
                : "API Token: Not generated";
    }

    @Override
    public String getConnectorApiToken(OrganizationOnboardingRequest request) {
        return request.getCkanApiToken();
    }

    private boolean hasSpipCredentials(OrganizationOnboardingRequest request) {
        return request.getSpipUser() != null && !request.getSpipUser().isBlank();
    }

    /**
     * Generates a random password for an independent CKAN user.
     * Reuses the platform's standard generated-credential recipe (see {@link PasswordGenerator}).
     */
    private String generateRandomPassword() {
        return PasswordGenerator.generate(16, "!@#$%^&*", true);
    }

    /**
     * Validate CKAN organization short name format and constraints.
     */
    private void validateCkanOrgShortName(String ckanOrgShortName) {
        if (ckanOrgShortName == null || ckanOrgShortName.trim().isEmpty()) {
            throw new IllegalArgumentException("CKAN organization short name cannot be empty");
        }

        String trimmedName = ckanOrgShortName.trim();

        if (!trimmedName.matches("^[a-z0-9_-]+$")) {
            throw new IllegalArgumentException(
                "CKAN organization short name must contain only lowercase letters, numbers, hyphens, and underscores. " +
                "Invalid name: '" + trimmedName + "'"
            );
        }

        if (trimmedName.length() > 100) {
            throw new IllegalArgumentException(
                "CKAN organization short name cannot exceed 100 characters. " +
                "Current length: " + trimmedName.length()
            );
        }

        if (trimmedName.matches("^[-_].*|.*[-_]$")) {
            throw new IllegalArgumentException(
                "CKAN organization short name cannot start or end with a hyphen or underscore. " +
                "Invalid name: '" + trimmedName + "'"
            );
        }
    }

    /**
     * Validate CKAN username format and constraints. Mirrors
     * {@code SpipOnboardingToolProvisioner}'s SPIP username rules since both share the
     * same CKAN account namespace.
     */
    private void validateCkanUsername(String ckanUsername) {
        if (ckanUsername == null || ckanUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("CKAN username cannot be empty");
        }

        String trimmedUsername = ckanUsername.trim();

        if (!trimmedUsername.matches("^[a-z0-9]+$")) {
            throw new IllegalArgumentException(
                "CKAN username must contain only lowercase letters and numbers. " +
                "Invalid username: '" + trimmedUsername + "'"
            );
        }

        if (trimmedUsername.length() > 30) {
            throw new IllegalArgumentException(
                "CKAN username cannot exceed 30 characters. " +
                "Current length: " + trimmedUsername.length()
            );
        }

        if (trimmedUsername.length() < 3) {
            throw new IllegalArgumentException(
                "CKAN username must be at least 3 characters long. " +
                "Current length: " + trimmedUsername.length()
            );
        }
    }
}
