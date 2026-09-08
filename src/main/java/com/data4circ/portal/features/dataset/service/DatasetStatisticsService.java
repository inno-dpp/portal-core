package com.data4circ.portal.features.dataset.service;

import com.data4circ.portal.integration.ckan.client.CkanApiClient;
import com.data4circ.portal.features.platformsettings.service.CkanSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Service for retrieving CKAN platform statistics
 * Provides dashboard metrics and counts
 */
@Service
public class DatasetStatisticsService {

    private static final Logger logger = LoggerFactory.getLogger(DatasetStatisticsService.class);

    private final CkanApiClient ckanApiClient;
    private final CkanSettingsService ckanSettings;

    public DatasetStatisticsService(CkanApiClient ckanApiClient,
                                    CkanSettingsService ckanSettings) {
        this.ckanApiClient = ckanApiClient;
        this.ckanSettings = ckanSettings;
    }

    /**
     * Get dataset count owned by a single CKAN organization.
     * Shares the {@code datasetCount} cache; keyed by {@code "owner:" + slug} so it cannot collide
     * with the group-keyed entries that use the raw slug as the key.
     *
     * @param ckanOrgSlug CKAN organisation short name
     * @return Dataset count, or {@code null} if CKAN is unavailable or the slug is blank
     */
    @Cacheable(value = "datasetCount", key = "'owner:' + #ckanOrgSlug")
    public Integer getDatasetCountByOwnerOrg(String ckanOrgSlug) {
        logger.debug("Fetching dataset count for CKAN org '{}' (cache miss or expired)", ckanOrgSlug);

        try {
            String jwtToken = ckanSettings.getJwtToken();
            if (jwtToken == null || jwtToken.trim().isEmpty()) {
                logger.warn("CKAN JWT token not configured");
                return null;
            }
            return ckanApiClient.getDatasetCountByOwnerOrg(jwtToken, ckanOrgSlug);
        } catch (Exception e) {
            logger.error("Unexpected error fetching dataset count for CKAN org '{}': {}",
                    ckanOrgSlug, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get dataset count for a single CKAN group (category).
     * Shares the {@code datasetCount} cache with {@link #getDatasetCount()} but uses the group
     * name as the cache key, so each category is cached independently with a 1-minute TTL
     * (per {@code application.yml} caffeine spec).
     *
     * @param groupName CKAN group slug (e.g. "catalytic-converters")
     * @return Dataset count, or {@code null} if CKAN is unavailable or the JWT token is missing
     */
    @Cacheable(value = "datasetCount", key = "#groupName")
    public Integer getDatasetCountInGroup(String groupName) {
        logger.debug("Fetching dataset count for group '{}' from CKAN (cache miss or expired)", groupName);

        try {
            String jwtToken = ckanSettings.getJwtToken();
            if (jwtToken == null || jwtToken.trim().isEmpty()) {
                logger.warn("CKAN JWT token not configured");
                return null;
            }
            return ckanApiClient.getDatasetCountInGroup(jwtToken, groupName);
        } catch (Exception e) {
            logger.error("Unexpected error fetching dataset count for group '{}': {}",
                    groupName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get total dataset count from CKAN platform
     * Cached for 1 minute to reduce load on CKAN API
     *
     * @return Dataset count as Integer, or null if CKAN is unavailable
     */
    @Cacheable(value = "datasetCount")
    public Integer getDatasetCount() {
        logger.debug("Fetching dataset count from CKAN (cache miss or expired)");

        try {
            // Get JWT token from configuration
            String jwtToken = ckanSettings.getJwtToken();

            if (jwtToken == null || jwtToken.trim().isEmpty()) {
                logger.warn("CKAN JWT token not configured");
                return null;
            }

            // Call CKAN API client
            Integer count = ckanApiClient.getDatasetCount(jwtToken);

            if (count == null) {
                logger.warn("CKAN dataset count returned null - CKAN may be unavailable");
                return null;
            }

            logger.debug("Successfully retrieved dataset count: {}", count);
            return count;

        } catch (Exception e) {
            logger.error("Unexpected error fetching dataset count: {}", e.getMessage(), e);
            return null;
        }
    }
}
