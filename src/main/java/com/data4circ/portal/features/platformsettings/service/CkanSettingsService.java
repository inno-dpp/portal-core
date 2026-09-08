package com.data4circ.portal.features.platformsettings.service;

import com.data4circ.portal.common.config.CkanConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * Resolves the effective CKAN connection settings.
 *
 * <p>Platform admins can override the base URL and admin API token at runtime via
 * {@code /admin/settings}; overrides are stored encrypted in the database. When no
 * override exists, the values fall back to the application properties
 * ({@code CKAN_BASE_URL} / {@code CKAN_JWT_TOKEN} environment variables).</p>
 */
@Service
public class CkanSettingsService {

    public static final String KEY_BASE_URL = "ckan.base-url";
    public static final String KEY_JWT_TOKEN = "ckan.jwt-token";

    private static final Logger logger = LoggerFactory.getLogger(CkanSettingsService.class);

    private final PlatformSettingService platformSettingService;
    private final CkanConfig.CkanProperties ckanProperties;
    private final CacheManager cacheManager;

    public CkanSettingsService(PlatformSettingService platformSettingService,
                               CkanConfig.CkanProperties ckanProperties,
                               CacheManager cacheManager) {
        this.platformSettingService = platformSettingService;
        this.ckanProperties = ckanProperties;
        this.cacheManager = cacheManager;
    }

    /**
     * Effective CKAN base URL: database override if set, otherwise the property/env default.
     * Never ends with a trailing slash.
     */
    public String getBaseUrl() {
        String baseUrl = platformSettingService.getValue(KEY_BASE_URL)
                .orElseGet(ckanProperties::getBaseUrl);
        return normalizeBaseUrl(baseUrl);
    }

    /**
     * Browser-facing CKAN address for outgoing links (e.g. dashboard category "Explore Data"
     * buttons). Property/env-only — no database override, unlike {@link #getBaseUrl()}: this is
     * a display concern, not an admin-editable connection setting.
     */
    public String getPublicUrl() {
        return normalizeBaseUrl(ckanProperties.getPublicUrl());
    }

    /**
     * Effective CKAN admin API token: database override if set, otherwise the property/env default.
     */
    public String getJwtToken() {
        return platformSettingService.getValue(KEY_JWT_TOKEN)
                .orElseGet(ckanProperties::getJwtToken);
    }

    public boolean isBaseUrlOverridden() {
        return platformSettingService.getValue(KEY_BASE_URL).isPresent();
    }

    public boolean isJwtTokenOverridden() {
        return platformSettingService.getValue(KEY_JWT_TOKEN).isPresent();
    }

    public boolean isJwtTokenConfigured() {
        String token = getJwtToken();
        return token != null && !token.isBlank();
    }

    /**
     * Masked preview of the effective token for display (never the full value).
     */
    public String getJwtTokenPreview() {
        String token = getJwtToken();
        if (token == null || token.isBlank()) {
            return "";
        }
        if (token.length() <= 8) {
            return "••••••••";
        }
        return "••••••••" + token.substring(token.length() - 4);
    }

    /**
     * Base URL default coming from properties/environment (what a reset restores).
     */
    public String getEnvironmentBaseUrl() {
        return normalizeBaseUrl(ckanProperties.getBaseUrl());
    }

    /**
     * Save admin-provided overrides.
     *
     * @param baseUrl  new base URL; blank clears the base URL override (back to env default)
     * @param jwtToken new API token; blank keeps the current token unchanged
     * @param updatedBy username of the admin making the change
     */
    public void updateSettings(String baseUrl, String jwtToken, String updatedBy) {
        String trimmedUrl = baseUrl == null ? "" : baseUrl.trim();
        if (trimmedUrl.isEmpty()) {
            platformSettingService.clearValue(KEY_BASE_URL);
        } else {
            if (!trimmedUrl.matches("^https?://\\S+$")) {
                throw new IllegalArgumentException(
                        "CKAN base URL must start with http:// or https:// and contain no spaces");
            }
            platformSettingService.setValue(KEY_BASE_URL, normalizeBaseUrl(trimmedUrl), updatedBy);
        }

        String trimmedToken = jwtToken == null ? "" : jwtToken.trim();
        if (!trimmedToken.isEmpty()) {
            platformSettingService.setValue(KEY_JWT_TOKEN, trimmedToken, updatedBy);
        }

        evictCkanCaches();
        logger.info("CKAN connection settings updated by '{}' (token changed: {})",
                updatedBy, !trimmedToken.isEmpty());
    }

    /**
     * Remove all overrides so the environment/property defaults apply again.
     */
    public void resetToEnvironmentDefaults(String updatedBy) {
        platformSettingService.clearValue(KEY_BASE_URL);
        platformSettingService.clearValue(KEY_JWT_TOKEN);
        evictCkanCaches();
        logger.info("CKAN connection settings reset to environment defaults by '{}'", updatedBy);
    }

    private void evictCkanCaches() {
        Cache datasetCount = cacheManager.getCache("datasetCount");
        if (datasetCount != null) {
            datasetCount.clear();
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
