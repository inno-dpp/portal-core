package com.data4circ.portal.features.platformsettings.service;

import com.data4circ.portal.common.config.KeycloakConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves the effective default Keycloak instance settings.
 *
 * <p>Platform admins can override the connection values at runtime via
 * {@code /admin/settings}; overrides are stored encrypted in the database. When no
 * override exists, the values fall back to the application properties
 * ({@code KEYCLOAK_*} environment variables). These values pre-fill the Keycloak
 * card on the onboarding sync form, where they can still be overridden per
 * organization.</p>
 */
@Service
public class KeycloakSettingsService {

    public static final String KEY_BASE_URL = "keycloak.base-url";
    public static final String KEY_REALM = "keycloak.realm";
    public static final String KEY_CLIENT_ID = "keycloak.client-id";
    public static final String KEY_CLIENT_SECRET = "keycloak.client-secret";

    private static final Logger logger = LoggerFactory.getLogger(KeycloakSettingsService.class);

    private final PlatformSettingService platformSettingService;
    private final KeycloakConfig.KeycloakProperties keycloakProperties;

    public KeycloakSettingsService(PlatformSettingService platformSettingService,
                                   KeycloakConfig.KeycloakProperties keycloakProperties) {
        this.platformSettingService = platformSettingService;
        this.keycloakProperties = keycloakProperties;
    }

    /**
     * Effective base URL: database override if set, otherwise the property/env default.
     * Never ends with a trailing slash.
     */
    public String getBaseUrl() {
        String baseUrl = platformSettingService.getValue(KEY_BASE_URL)
                .orElseGet(keycloakProperties::getBaseUrl);
        return normalizeBaseUrl(baseUrl);
    }

    public String getRealm() {
        return platformSettingService.getValue(KEY_REALM)
                .orElseGet(keycloakProperties::getRealm);
    }

    public String getClientId() {
        return platformSettingService.getValue(KEY_CLIENT_ID)
                .orElseGet(keycloakProperties::getClientId);
    }

    public String getClientSecret() {
        return platformSettingService.getValue(KEY_CLIENT_SECRET)
                .orElseGet(keycloakProperties::getClientSecret);
    }

    public boolean isBaseUrlOverridden() {
        return platformSettingService.getValue(KEY_BASE_URL).isPresent();
    }

    public boolean isRealmOverridden() {
        return platformSettingService.getValue(KEY_REALM).isPresent();
    }

    public boolean isClientIdOverridden() {
        return platformSettingService.getValue(KEY_CLIENT_ID).isPresent();
    }

    public boolean isClientSecretOverridden() {
        return platformSettingService.getValue(KEY_CLIENT_SECRET).isPresent();
    }

    public boolean isClientSecretConfigured() {
        String secret = getClientSecret();
        return secret != null && !secret.isBlank();
    }

    /**
     * Base URL default coming from properties/environment (what a reset restores).
     */
    public String getEnvironmentBaseUrl() {
        return normalizeBaseUrl(keycloakProperties.getBaseUrl());
    }

    /**
     * Whether the given coordinates identify the platform's default Keycloak
     * instance. The default admin client secret must only ever be sent there —
     * both the onboarding provisioner and the password mirror gate on this.
     */
    public boolean isDefaultInstance(String baseUrl, String realm) {
        String defaultBaseUrl = getBaseUrl();
        String defaultRealm = getRealm();
        return defaultBaseUrl != null && defaultBaseUrl.equals(normalizeBaseUrl(baseUrl))
                && defaultRealm != null && defaultRealm.equals(realm);
    }

    /**
     * Save admin-provided overrides.
     *
     * @param baseUrl      new base URL; blank clears the override (back to env default)
     * @param realm        new realm; blank clears the override
     * @param clientId     new admin client id; blank clears the override
     * @param clientSecret new admin client secret; blank keeps the current secret unchanged
     * @param updatedBy    username of the admin making the change
     */
    public void updateSettings(String baseUrl, String realm, String clientId,
                               String clientSecret, String updatedBy) {
        String trimmedUrl = baseUrl == null ? "" : baseUrl.trim();
        if (trimmedUrl.isEmpty()) {
            platformSettingService.clearValue(KEY_BASE_URL);
        } else {
            if (!trimmedUrl.matches("^https?://\\S+$")) {
                throw new IllegalArgumentException(
                        "Keycloak base URL must start with http:// or https:// and contain no spaces");
            }
            platformSettingService.setValue(KEY_BASE_URL, normalizeBaseUrl(trimmedUrl), updatedBy);
        }

        setOrClear(KEY_REALM, realm, updatedBy);
        setOrClear(KEY_CLIENT_ID, clientId, updatedBy);

        String trimmedSecret = clientSecret == null ? "" : clientSecret.trim();
        if (!trimmedSecret.isEmpty()) {
            platformSettingService.setValue(KEY_CLIENT_SECRET, trimmedSecret, updatedBy);
        }

        logger.info("Keycloak connection settings updated by '{}' (secret changed: {})",
                updatedBy, !trimmedSecret.isEmpty());
    }

    /**
     * Remove all overrides so the environment/property defaults apply again.
     */
    public void resetToEnvironmentDefaults(String updatedBy) {
        platformSettingService.clearValue(KEY_BASE_URL);
        platformSettingService.clearValue(KEY_REALM);
        platformSettingService.clearValue(KEY_CLIENT_ID);
        platformSettingService.clearValue(KEY_CLIENT_SECRET);
        logger.info("Keycloak connection settings reset to environment defaults by '{}'", updatedBy);
    }

    private void setOrClear(String key, String value, String updatedBy) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            platformSettingService.clearValue(key);
        } else {
            platformSettingService.setValue(key, trimmed, updatedBy);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return null;
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
