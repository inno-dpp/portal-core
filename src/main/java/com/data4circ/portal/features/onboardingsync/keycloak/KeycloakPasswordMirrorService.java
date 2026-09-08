package com.data4circ.portal.features.onboardingsync.keycloak;

import com.data4circ.portal.common.config.KeycloakConfig;
import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.platformsettings.service.KeycloakSettingsService;
import com.data4circ.portal.integration.keycloak.KeycloakInstance;
import com.data4circ.portal.integration.keycloak.client.KeycloakApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Optionally mirrors the portal password of an onboarding-provisioned first user
 * into their Keycloak account at first-login activation, so the user ends up with
 * one password for both systems instead of the Keycloak temporary password.
 *
 * <p>The link is the {@code keycloakUserId} stamped on the portal {@link User} at
 * onboarding approval — deliberately not inferred from the user's email, which can
 * be edited or duplicated. Only default-instance identities are ever stamped, so
 * the default admin secret is never sent to another host.</p>
 *
 * <p>Strictly best-effort: every failure (Keycloak unreachable, wrong credentials,
 * realm password policy rejection) is logged and swallowed — the portal password
 * change always succeeds regardless. Disabled by default via
 * {@code app.keycloak.mirror-portal-password}.</p>
 */
@Service
public class KeycloakPasswordMirrorService {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakPasswordMirrorService.class);

    private final KeycloakApiClient keycloakApiClient;
    private final KeycloakSettingsService keycloakSettings;
    private final KeycloakConfig.KeycloakProperties keycloakProperties;

    public KeycloakPasswordMirrorService(KeycloakApiClient keycloakApiClient,
                                         KeycloakSettingsService keycloakSettings,
                                         KeycloakConfig.KeycloakProperties keycloakProperties) {
        this.keycloakApiClient = keycloakApiClient;
        this.keycloakSettings = keycloakSettings;
        this.keycloakProperties = keycloakProperties;
    }

    /**
     * Mirror the given plaintext password (transient, never stored) into the user's
     * linked Keycloak account on the default instance. Never throws.
     */
    public void mirrorPasswordIfLinked(User user, String plainPassword) {
        if (!keycloakProperties.isMirrorPortalPassword()) {
            return;
        }
        String keycloakUserId = user.getKeycloakUserId();
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            return; // no Keycloak identity was provisioned for this user
        }
        try {
            String clientSecret = keycloakSettings.getClientSecret();
            if (clientSecret == null || clientSecret.isBlank()) {
                logger.info("Skipping Keycloak password mirror for user '{}': no admin client secret configured.",
                        user.getUsername());
                return;
            }

            KeycloakInstance instance = new KeycloakInstance(
                    keycloakSettings.getBaseUrl(),
                    keycloakSettings.getRealm(),
                    keycloakSettings.getClientId(),
                    clientSecret);

            String token = keycloakApiClient.obtainAdminToken(instance);
            keycloakApiClient.setPassword(instance, token, keycloakUserId, plainPassword, false);
            keycloakApiClient.removeRequiredAction(instance, token, keycloakUserId, "UPDATE_PASSWORD");

            logger.info("Mirrored portal password to Keycloak user id '{}' (realm '{}') for portal user '{}'",
                    keycloakUserId, instance.getRealm(), user.getUsername());
        } catch (Exception e) {
            // Non-fatal by design: Keycloak down, bad credentials, or a realm password
            // policy stricter than the portal's. The user keeps the Keycloak temporary
            // password from onboarding as fallback.
            logger.warn("Keycloak password mirror failed for user '{}' (portal password is unaffected): {}",
                    user.getUsername(), e.getMessage());
        }
    }
}
