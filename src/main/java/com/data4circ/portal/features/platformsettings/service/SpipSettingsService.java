package com.data4circ.portal.features.platformsettings.service;

import com.data4circ.portal.common.config.SpipProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves the effective SPIP platform connection settings.
 *
 * <p>Platform admins can override the base URL, customer tag, admin credentials and
 * default role at runtime via {@code /admin/settings}; overrides are stored encrypted
 * in the database. When no override exists, the values fall back to the application
 * properties ({@code SPIP_BASE_URL} / {@code SPIP_CUSTOMER_TAG} /
 * {@code SPIP_ADMIN_USERNAME} / {@code SPIP_ADMIN_PASSWORD} / {@code SPIP_DEFAULT_ROLE}
 * environment variables).</p>
 */
@Service
public class SpipSettingsService {

    public static final String KEY_BASE_URL = "spip.base-url";
    public static final String KEY_CUSTOMER_TAG = "spip.customer-tag";
    public static final String KEY_ADMIN_USERNAME = "spip.admin-username";
    public static final String KEY_ADMIN_PASSWORD = "spip.admin-password";
    public static final String KEY_DEFAULT_ROLE = "spip.default-role";

    private static final Logger logger = LoggerFactory.getLogger(SpipSettingsService.class);

    private final PlatformSettingService platformSettingService;
    private final SpipProperties spipProperties;

    public SpipSettingsService(PlatformSettingService platformSettingService,
                               SpipProperties spipProperties) {
        this.platformSettingService = platformSettingService;
        this.spipProperties = spipProperties;
    }

    public String getBaseUrl() {
        String baseUrl = platformSettingService.getValue(KEY_BASE_URL)
                .orElseGet(spipProperties::getBaseUrl);
        return normalizeBaseUrl(baseUrl);
    }

    public String getCustomerTag() {
        return platformSettingService.getValue(KEY_CUSTOMER_TAG)
                .orElseGet(spipProperties::getCustomerTag);
    }

    public String getAdminUsername() {
        return platformSettingService.getValue(KEY_ADMIN_USERNAME)
                .orElseGet(() -> spipProperties.getAdmin().getUsername());
    }

    public String getAdminPassword() {
        return platformSettingService.getValue(KEY_ADMIN_PASSWORD)
                .orElseGet(() -> spipProperties.getAdmin().getPassword());
    }

    public String getDefaultRole() {
        return platformSettingService.getValue(KEY_DEFAULT_ROLE)
                .orElseGet(spipProperties::getDefaultRole);
    }

    public boolean isBaseUrlOverridden() {
        return platformSettingService.getValue(KEY_BASE_URL).isPresent();
    }

    public boolean isCustomerTagOverridden() {
        return platformSettingService.getValue(KEY_CUSTOMER_TAG).isPresent();
    }

    public boolean isAdminUsernameOverridden() {
        return platformSettingService.getValue(KEY_ADMIN_USERNAME).isPresent();
    }

    public boolean isAdminPasswordOverridden() {
        return platformSettingService.getValue(KEY_ADMIN_PASSWORD).isPresent();
    }

    public boolean isDefaultRoleOverridden() {
        return platformSettingService.getValue(KEY_DEFAULT_ROLE).isPresent();
    }

    public boolean isAdminPasswordConfigured() {
        String password = getAdminPassword();
        return password != null && !password.isBlank();
    }

    public String getEnvironmentBaseUrl() {
        return normalizeBaseUrl(spipProperties.getBaseUrl());
    }

    /**
     * Save admin-provided overrides. The admin password is the only secret: a blank
     * password keeps the current one (the form never displays or re-posts it). All
     * other fields are plain text — blank clears that field's override so the
     * environment default applies again.
     *
     * @param updatedBy username of the admin making the change
     */
    public void updateSettings(String baseUrl, String customerTag, String adminUsername,
                               String adminPassword, String defaultRole, String updatedBy) {
        String trimmedUrl = trim(baseUrl);
        if (trimmedUrl.isEmpty()) {
            platformSettingService.clearValue(KEY_BASE_URL);
        } else {
            if (!trimmedUrl.matches("^https?://\\S+$")) {
                throw new IllegalArgumentException(
                        "SPIP base URL must start with http:// or https:// and contain no spaces");
            }
            platformSettingService.setValue(KEY_BASE_URL, normalizeBaseUrl(trimmedUrl), updatedBy);
        }

        setOrClear(KEY_CUSTOMER_TAG, customerTag, updatedBy);
        setOrClear(KEY_ADMIN_USERNAME, adminUsername, updatedBy);
        setOrClear(KEY_DEFAULT_ROLE, defaultRole, updatedBy);

        String trimmedPassword = trim(adminPassword);
        if (!trimmedPassword.isEmpty()) {
            platformSettingService.setValue(KEY_ADMIN_PASSWORD, trimmedPassword, updatedBy);
        }

        logger.info("SPIP connection settings updated by '{}' (password changed: {})",
                updatedBy, !trimmedPassword.isEmpty());
    }

    /**
     * Remove all overrides so the environment/property defaults apply again.
     */
    public void resetToEnvironmentDefaults(String updatedBy) {
        platformSettingService.clearValue(KEY_BASE_URL);
        platformSettingService.clearValue(KEY_CUSTOMER_TAG);
        platformSettingService.clearValue(KEY_ADMIN_USERNAME);
        platformSettingService.clearValue(KEY_ADMIN_PASSWORD);
        platformSettingService.clearValue(KEY_DEFAULT_ROLE);
        logger.info("SPIP connection settings reset to environment defaults by '{}'", updatedBy);
    }

    private void setOrClear(String key, String value, String updatedBy) {
        String trimmed = trim(value);
        if (trimmed.isEmpty()) {
            platformSettingService.clearValue(key);
        } else {
            platformSettingService.setValue(key, trimmed, updatedBy);
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
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
