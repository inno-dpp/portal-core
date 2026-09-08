package com.data4circ.portal.features.onboardingsync.keycloak;

import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolInputField;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolProvisioner;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolRegistry;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolSyncOutcome;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolsProperties;
import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;
import com.data4circ.portal.features.organization.service.OrganizationSpipUserService;
import com.data4circ.portal.features.platformsettings.service.KeycloakSettingsService;
import com.data4circ.portal.integration.keycloak.KeycloakInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Onboarding tool adapter for Keycloak: the admin confirms (or overrides) the target
 * Keycloak instance and the first-user username, then the organization is
 * provisioned through {@link KeycloakOnboardingService}. The instance fields make
 * multi-instance deployments possible — the pre-filled values describe the
 * platform's default instance.
 *
 * <p>The organization group is named exactly like the admin-confirmed identity
 * provider username (see {@link OnboardingToolRegistry#identityProvider()}; SPIP
 * today), keeping tenant naming unified across the platform tools. When an identity
 * provider is enabled this is enforced (dependency-gated, group field read-only); when
 * none is enabled the group name becomes an editable required field pre-filled with
 * the same slug SPIP would have proposed.</p>
 */
@Component
public class KeycloakOnboardingToolProvisioner implements OnboardingToolProvisioner {

    public static final String TOOL_KEY = "keycloak";
    public static final String BASE_URL_PARAM = "kcBaseUrl";
    public static final String REALM_PARAM = "kcRealm";
    public static final String CLIENT_ID_PARAM = "kcClientId";
    public static final String CLIENT_SECRET_PARAM = "kcClientSecret";
    public static final String GROUP_NAME_PARAM = "kcGroupName";
    public static final String USERNAME_PARAM = "kcUsername";

    private static final Logger logger = LoggerFactory.getLogger(KeycloakOnboardingToolProvisioner.class);

    private final KeycloakOnboardingService keycloakOnboardingService;
    private final KeycloakSettingsService keycloakSettings;
    private final OnboardingToolsProperties toolsProperties;
    private final List<OnboardingToolProvisioner> otherTools;
    private final OrganizationSpipUserService spipUserService;

    public KeycloakOnboardingToolProvisioner(KeycloakOnboardingService keycloakOnboardingService,
                                             KeycloakSettingsService keycloakSettings,
                                             OnboardingToolsProperties toolsProperties,
                                             // Spring excludes this bean itself from a same-typed
                                             // collection injection; @Lazy defers resolving the rest
                                             // until first use, so this doesn't force CkanOnboarding-
                                             // ToolProvisioner (which asks the identical question) to
                                             // be built first — each would otherwise need the other
                                             // built, a construction cycle between the two.
                                             @Lazy List<OnboardingToolProvisioner> otherTools,
                                             OrganizationSpipUserService spipUserService) {
        this.keycloakOnboardingService = keycloakOnboardingService;
        this.keycloakSettings = keycloakSettings;
        this.toolsProperties = toolsProperties;
        this.otherTools = otherTools;
        this.spipUserService = spipUserService;
    }

    /**
     * Depends on the identity provider (e.g. SPIP) only while it's enabled — a
     * deployment without one can still provision Keycloak (the group name becomes an
     * admin input).
     */
    @Override
    public List<String> getDependencies() {
        return identityProvider()
                .map(tool -> List.of(tool.getKey()))
                .orElse(List.of());
    }

    @Override
    public String getKey() {
        return TOOL_KEY;
    }

    @Override
    public String getDisplayName() {
        return "Keycloak";
    }

    @Override
    public String getIconClass() {
        return "fas fa-user-lock";
    }

    @Override
    public List<OnboardingToolInputField> describeInputs(OrganizationOnboardingRequest request) {
        return List.of(
                new OnboardingToolInputField(
                        BASE_URL_PARAM,
                        "Keycloak Base URL",
                        defaultBaseUrl(),
                        null, null, 255,
                        "Address of the Keycloak instance for this organization. "
                                + "Pre-filled with the platform's default instance.",
                        "url", true),
                new OnboardingToolInputField(
                        REALM_PARAM,
                        "Realm",
                        defaultRealm(),
                        null, 1, 255,
                        "Realm in which the organization group and first user are created.",
                        "text", true),
                new OnboardingToolInputField(
                        CLIENT_ID_PARAM,
                        "Admin Client ID",
                        defaultClientId(),
                        null, 1, 255,
                        "Confidential client (service account) used for the Admin API calls.",
                        "text", true),
                new OnboardingToolInputField(
                        CLIENT_SECRET_PARAM,
                        "Admin Client Secret",
                        null,
                        null, null, 512,
                        "Leave blank to use the platform's configured default secret "
                                + "(default instance only — a different URL or realm requires its own secret).",
                        "password", false),
                groupNameField(request),
                new OnboardingToolInputField(
                        USERNAME_PARAM,
                        "First User Username (email)",
                        proposeUsername(request),
                        "[a-z0-9][a-z0-9.@+_-]*", 3, 255,
                        "Username of the organization's first Keycloak user — the contact email "
                                + "address from the onboarding request.",
                        "text", true));
    }

    /**
     * Identity provider enabled: read-only, fixed to its username (available once that
     * dependency has synchronized). Identity provider disabled: editable and required,
     * pre-filled with the same slug SPIP would have proposed.
     */
    private OnboardingToolInputField groupNameField(OrganizationOnboardingRequest request) {
        if (hasIdentityProvider()) {
            return new OnboardingToolInputField(
                    GROUP_NAME_PARAM,
                    "Organization Group Name",
                    request.getSpipUser(),
                    null, null, 100,
                    "Fixed to the SPIP username — tenant naming is identical across SPIP, CKAN and Keycloak.",
                    "text", true, true);
        }
        return new OnboardingToolInputField(
                GROUP_NAME_PARAM,
                "Organization Group Name",
                spipUserService.generateUsernameFromOrganizationName(request.getCompanyName()),
                "[a-z0-9][a-z0-9._-]*", 2, 100,
                "Keycloak group representing the organization. Lowercase letters, numbers, "
                        + "dots, hyphens and underscores.",
                "text", true, false);
    }

    @Override
    public OnboardingToolSyncOutcome synchronize(OrganizationOnboardingRequest request, Map<String, String> params) {
        String baseUrl = requireParam(params, BASE_URL_PARAM, "Keycloak base URL");
        String realm = requireParam(params, REALM_PARAM, "Keycloak realm");
        String clientId = requireParam(params, CLIENT_ID_PARAM, "Keycloak admin client ID");
        String username = requireParam(params, USERNAME_PARAM, "Keycloak username");
        validateRealm(realm);
        validateUsername(username);
        String groupName = resolveGroupName(request, params);

        String clientSecret = params.get(CLIENT_SECRET_PARAM);
        if (clientSecret == null || clientSecret.isBlank()) {
            // The default admin secret must only ever be sent to the default instance —
            // falling back for an overridden URL/realm would leak it to a foreign host.
            if (!keycloakSettings.isDefaultInstance(baseUrl, realm)) {
                throw new IllegalArgumentException("Keycloak admin client secret is required when "
                        + "targeting a non-default instance: the platform's default secret is only "
                        + "used for " + keycloakSettings.getBaseUrl() + " (realm '"
                        + keycloakSettings.getRealm() + "').");
            }
            clientSecret = keycloakSettings.getClientSecret();
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("Keycloak admin client secret is required: no value was "
                    + "entered and no default secret is configured (platform settings or "
                    + "app.keycloak.client-secret).");
        }

        KeycloakInstance instance = new KeycloakInstance(baseUrl, realm, clientId, clientSecret);

        // Generated plaintext here; encrypted at rest by the JPA EncryptedStringConverter
        String temporaryPassword = generateRandomPassword();

        KeycloakInitializationResult result = keycloakOnboardingService.initializeOrganization(
                request, instance, groupName, username, temporaryPassword);

        String detailsJson = serializeInitializationResult(result);

        if (!result.isSuccess()) {
            throw new IllegalStateException("Keycloak synchronization failed: " + result.getErrorMessage());
        }

        request.setKeycloakBaseUrl(instance.getBaseUrl());
        request.setKeycloakRealm(instance.getRealm());
        request.setKeycloakGroupName(groupName);
        request.setKeycloakUsername(username);
        request.setKeycloakUserId(result.getUserId());
        request.setKeycloakTempPassword(temporaryPassword);

        return OnboardingToolSyncOutcome.withDetails(detailsJson);
    }

    @Override
    public String getStatusNote(OrganizationOnboardingRequest request) {
        if (request.getKeycloakUsername() == null) {
            return null;
        }
        return "User: " + request.getKeycloakUsername() + " @ realm " + request.getKeycloakRealm();
    }

    @Override
    public String getConnectorEndpoint(OrganizationOnboardingRequest request) {
        return request.getKeycloakBaseUrl();
    }

    @Override
    public String getConnectorConfiguration(OrganizationOnboardingRequest request) {
        if (request.getKeycloakRealm() == null) {
            return null;
        }
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode configuration = objectMapper.createObjectNode();
        configuration.put("realm", request.getKeycloakRealm());
        configuration.put("group", request.getKeycloakGroupName());
        configuration.put("username", request.getKeycloakUsername());
        return configuration.toPrettyString();
    }

    private String defaultBaseUrl() {
        return keycloakSettings.getBaseUrl();
    }

    private String defaultRealm() {
        return keycloakSettings.getRealm();
    }

    private String defaultClientId() {
        return keycloakSettings.getClientId();
    }

    /** The contact email address doubles as the Keycloak username (Keycloak default setup). */
    private String proposeUsername(OrganizationOnboardingRequest request) {
        String email = request.getEmail();
        if (email == null || email.isBlank() || !email.contains("@")) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private String requireParam(Map<String, String> params, String name, String label) {
        String value = params.get(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be empty");
        }
        return value.trim();
    }

    /**
     * Identity provider enabled: the group name is taken from the request's SPIP
     * username; the submitted read-only field is deliberately ignored (readonly is
     * client-side only and must not be trusted). Identity provider disabled: the
     * admin-entered value is used.
     */
    private String resolveGroupName(OrganizationOnboardingRequest request, Map<String, String> params) {
        if (hasIdentityProvider()) {
            String groupName = request.getSpipUser();
            if (groupName == null || groupName.isBlank()) {
                throw new IllegalStateException("SPIP synchronization must run before Keycloak: "
                        + "the Keycloak group is named after the SPIP username, which is not set yet.");
            }
            return groupName;
        }
        String groupName = requireParam(params, GROUP_NAME_PARAM, "Keycloak group name");
        validateSlug(groupName, "Keycloak group name");
        return groupName;
    }

    private boolean hasIdentityProvider() {
        return identityProvider().isPresent();
    }

    private Optional<OnboardingToolProvisioner> identityProvider() {
        return OnboardingToolRegistry.findIdentityProvider(otherTools, toolsProperties);
    }

    private void validateSlug(String value, String label) {
        if (!value.matches("^[a-z0-9][a-z0-9._-]*$")) {
            throw new IllegalArgumentException(label + " must contain only lowercase letters, numbers, "
                    + "dots, hyphens and underscores, and start with a letter or number. Invalid value: '"
                    + value + "'");
        }
    }

    private void validateRealm(String value) {
        if (!value.matches("^[a-zA-Z0-9._-]+$")) {
            throw new IllegalArgumentException("Keycloak realm must contain only letters, numbers, "
                    + "dots, hyphens and underscores. Invalid value: '" + value + "'");
        }
    }

    private void validateUsername(String value) {
        if (!value.matches("^[a-z0-9][a-z0-9.@+_-]*$")) {
            throw new IllegalArgumentException("Keycloak username must be a lowercase email address or "
                    + "slug (letters, numbers, dots, hyphens, underscores, '@', '+'), starting with a "
                    + "letter or number. Invalid value: '" + value + "'");
        }
    }

    /**
     * Generates a random temporary password for the first Keycloak user
     * (same recipe as the SPIP onboarding credentials: at least one numeral,
     * one uppercase, one lowercase, exactly one special character).
     */
    private String generateRandomPassword() {
        return com.data4circ.portal.common.util.PasswordGenerator.generate(16, "!@#$%^&*", true);
    }

    private String serializeInitializationResult(KeycloakInitializationResult result) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.error("Failed to serialize initialization result: {}", e.getMessage(), e);
            return "{\"error\": \"Failed to serialize result\"}";
        }
    }
}
