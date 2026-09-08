package com.data4circ.portal.integration;

import com.data4circ.portal.common.config.KeycloakConfig;
import com.data4circ.portal.integration.keycloak.KeycloakInstance;
import com.data4circ.portal.integration.keycloak.client.KeycloakApiClient;
import com.data4circ.portal.integration.keycloak.dto.KeycloakUserRepresentation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for the Keycloak Admin API client against a live Keycloak
 * instance (the dev container from docker-compose.keycloak.yml).
 *
 * Prerequisites:
 * - docker compose -f docker-compose.keycloak.yml up -d
 *   (realm data4circ with the portal-admin service-account client imported)
 *
 * To run these tests:
 * mvn test -Dgroups=keycloak -DexcludedGroups=
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("integration")
@Tag("keycloak")
class KeycloakIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakIntegrationTest.class);
    private static final String TEST_PREFIX = "e2e-test-";

    private static final String GROUP_NAME = TEST_PREFIX + "org";
    private static final String USERNAME = TEST_PREFIX + "user";

    @Autowired
    private KeycloakApiClient keycloakApiClient;

    @Autowired
    private KeycloakConfig.KeycloakProperties keycloakProperties;

    // Mock JavaMailSender to avoid requiring email configuration in tests
    @MockBean
    private JavaMailSender mailSender;

    private static KeycloakInstance instance;
    private static String token;
    private static String groupId;
    private static String userId;

    @BeforeAll
    static void configure(@Autowired KeycloakConfig.KeycloakProperties properties) {
        instance = new KeycloakInstance(
                properties.getBaseUrl(),
                properties.getRealm(),
                properties.getClientId(),
                properties.getClientSecret());
    }

    @AfterAll
    static void cleanup(@Autowired KeycloakApiClient client) {
        logger.info("=== Cleaning up Keycloak E2E fixtures ===");
        try {
            String cleanupToken = client.obtainAdminToken(instance);
            client.findUserByUsername(instance, cleanupToken, USERNAME)
                    .ifPresent(user -> client.deleteUser(instance, cleanupToken, user.getId()));
            client.findGroupByName(instance, cleanupToken, GROUP_NAME)
                    .ifPresent(group -> client.deleteGroup(instance, cleanupToken, group.getId()));
        } catch (Exception e) {
            logger.warn("Keycloak E2E cleanup failed (fixtures may remain): {}", e.getMessage());
        }
    }

    @Test
    @Order(1)
    void obtainsAdminToken() {
        token = keycloakApiClient.obtainAdminToken(instance);
        assertThat(token).isNotBlank();
    }

    @Test
    @Order(2)
    void createsOrganizationGroup() {
        groupId = keycloakApiClient.createGroup(instance, token, GROUP_NAME);
        assertThat(groupId).isNotBlank();

        // Idempotency: creating the same group again resolves the same id
        assertThat(keycloakApiClient.createGroup(instance, token, GROUP_NAME)).isEqualTo(groupId);
    }

    @Test
    @Order(3)
    void createsFirstUserWithForcedPasswordChange() {
        userId = keycloakApiClient.createUser(instance, token, USERNAME,
                USERNAME + "@example.com", "E2e", "Test");
        assertThat(userId).isNotBlank();

        keycloakApiClient.setPassword(instance, token, userId, "E2e#Temp1234", true);

        Optional<KeycloakUserRepresentation> user =
                keycloakApiClient.findUserByUsername(instance, token, USERNAME);
        assertThat(user).isPresent();
        assertThat(user.get().getId()).isEqualTo(userId);
        assertThat(user.get().getEnabled()).isTrue();
        // Temporary password forces the UPDATE_PASSWORD required action
        assertThat(user.get().getRequiredActions()).contains("UPDATE_PASSWORD");
    }

    @Test
    @Order(4)
    void addsUserToGroup() {
        keycloakApiClient.addUserToGroup(instance, token, userId, groupId);
        // Idempotent by Keycloak semantics — repeating must not fail
        keycloakApiClient.addUserToGroup(instance, token, userId, groupId);
    }

    @Test
    @Order(5)
    void badCredentialsAreReportedAsUnauthorized() {
        KeycloakInstance badInstance = new KeycloakInstance(
                instance.getBaseUrl(), instance.getRealm(), instance.getClientId(), "wrong-secret");
        try {
            keycloakApiClient.obtainAdminToken(badInstance);
            org.junit.jupiter.api.Assertions.fail("Expected KeycloakApiException");
        } catch (KeycloakApiClient.KeycloakApiException e) {
            assertThat(e.getStatus()).isEqualTo(401);
        }
    }
}
