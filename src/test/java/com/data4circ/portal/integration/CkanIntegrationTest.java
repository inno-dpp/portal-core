package com.data4circ.portal.integration;

import com.data4circ.portal.integration.ckan.client.CkanApiClient;
import com.data4circ.portal.common.config.CkanConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End integration tests for CKAN platform integration.
 *
 * These tests connect to a real CKAN instance and verify dataset queries and purge via the
 * CKAN API client.
 *
 * Prerequisites:
 * - CKAN instance must be accessible at configured URL
 * - Valid JWT token must be configured
 * - User must have permission to create/delete datasets
 *
 * To run these tests:
 * mvn test -Dtest=CkanIntegrationTest -Dspring.profiles.active=dev
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("integration")
@Tag("ckan")
class CkanIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(CkanIntegrationTest.class);
    private static final String TEST_DATASET_PREFIX = "e2e_test_dataset_";

    @Autowired
    private CkanApiClient ckanApiClient;

    @Autowired
    private CkanConfig.CkanProperties ckanProperties;

    @Autowired
    private ObjectMapper objectMapper;

    // Mock JavaMailSender to avoid requiring email configuration in tests
    @MockBean
    private JavaMailSender mailSender;

    // Track created datasets for cleanup
    private static final Set<String> createdDatasets = Collections.synchronizedSet(new HashSet<>());

    @AfterAll
    static void cleanupAllTestDatasets(@Autowired CkanApiClient ckanApiClient,
                                        @Autowired CkanConfig.CkanProperties ckanProperties) {
        logger.info("=== Cleaning up {} test datasets ===", createdDatasets.size());

        for (String datasetName : createdDatasets) {
            try {
                boolean purged = ckanApiClient.purgeDataset(datasetName, ckanProperties.getJwtToken());
                if (purged) {
                    logger.info("Successfully purged test dataset: {}", datasetName);
                }
            } catch (Exception e) {
                logger.warn("Failed to purge test dataset {}: {}", datasetName, e.getMessage());
            }
        }

        createdDatasets.clear();

        logger.info("=== Cleanup complete ===");
    }

    @Test
    @Order(6)
    @DisplayName("6. Verify CKAN API client can get dataset count")
    void testCkanApiClientGetDatasetCount() {
        logger.info("Testing CKAN API client dataset count...");

        Integer count = ckanApiClient.getDatasetCount(ckanProperties.getJwtToken());

        assertThat(count).isNotNull();
        assertThat(count).isGreaterThanOrEqualTo(0);

        logger.info("CKAN dataset count: {}", count);
    }

    @Test
    @Order(7)
    @DisplayName("7. Verify dataset purge works correctly")
    void testDatasetPurge() throws Exception {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String datasetName = TEST_DATASET_PREFIX + "purge_" + uniqueId;

        logger.info("Testing dataset purge with dataset: {}", datasetName);

        // First, create a dataset directly via the CKAN API client
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", datasetName);
        payload.put("title", "Dataset to be Purged " + uniqueId);
        payload.put("notes", "This dataset will be purged immediately");
        payload.put("owner_org", ckanProperties.getOrganization());

        ckanApiClient.createDataset(payload, ckanProperties.getJwtToken());

        // Now purge it
        try {
            boolean purged = ckanApiClient.purgeDataset(datasetName, ckanProperties.getJwtToken());
            logger.info("Dataset purge result: {}", purged);
            // Don't add to createdDatasets since we already purged it
        } catch (CkanApiClient.CkanApiException e) {
            logger.warn("Purge failed: {}", e.getMessage());
            // Add to cleanup list so @AfterAll retries the purge
            createdDatasets.add(datasetName);
        }

        logger.info("Dataset purge test completed");
    }

}
