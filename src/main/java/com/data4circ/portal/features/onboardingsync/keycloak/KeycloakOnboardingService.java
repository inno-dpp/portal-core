package com.data4circ.portal.features.onboardingsync.keycloak;

import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;
import com.data4circ.portal.integration.keycloak.KeycloakInstance;
import com.data4circ.portal.integration.keycloak.client.KeycloakApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Provisions an organization in a Keycloak instance during onboarding: a group
 * representing the organization plus its first user with a temporary password
 * (Keycloak forces a change at first login). Never throws — every step is recorded
 * in the returned {@link KeycloakInitializationResult}.
 */
@Service
public class KeycloakOnboardingService {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakOnboardingService.class);

    private final KeycloakApiClient keycloakApiClient;

    public KeycloakOnboardingService(KeycloakApiClient keycloakApiClient) {
        this.keycloakApiClient = keycloakApiClient;
    }

    public KeycloakInitializationResult initializeOrganization(OrganizationOnboardingRequest request,
                                                               KeycloakInstance instance,
                                                               String groupName,
                                                               String username,
                                                               String temporaryPassword) {
        return initialize(instance, groupName, username, request.getEmail(),
                request.getContactName(), temporaryPassword, request.getCompanyName());
    }

    /**
     * Request-independent variant, also used by the direct admin organization-creation
     * flow (which has no onboarding request).
     */
    public KeycloakInitializationResult initialize(KeycloakInstance instance,
                                                   String groupName,
                                                   String username,
                                                   String email,
                                                   String contactName,
                                                   String temporaryPassword,
                                                   String organizationLabel) {
        KeycloakInitializationResult result = new KeycloakInitializationResult();
        result.setInstance(instance.getBaseUrl());
        result.setRealm(instance.getRealm());
        result.setGroupName(groupName);
        result.setUsername(username);

        logger.info("Initializing organization '{}' on Keycloak {} (group '{}', user '{}')",
                organizationLabel, instance, groupName, username);

        String token;
        try {
            result.addStep(1, "Authenticate admin service account");
            token = keycloakApiClient.obtainAdminToken(instance);
            result.markLastStepCompleted(null);
        } catch (Exception e) {
            return fail(result, e);
        }

        try {
            result.addStep(2, "Create organization group");
            String groupId = keycloakApiClient.createGroup(instance, token, groupName);
            result.setGroupId(groupId);
            result.markLastStepCompleted(groupId);
        } catch (Exception e) {
            return fail(result, e);
        }

        try {
            result.addStep(3, "Create first user");
            String userId = keycloakApiClient.createUser(instance, token, username,
                    email, firstNameOf(contactName), lastNameOf(contactName));
            result.setUserId(userId);
            result.markLastStepCompleted(userId);
        } catch (Exception e) {
            return fail(result, e);
        }

        try {
            result.addStep(4, "Set temporary password (forced change at first login)");
            keycloakApiClient.setPassword(instance, token, result.getUserId(), temporaryPassword, true);
            result.markLastStepCompleted(null);
        } catch (Exception e) {
            return fail(result, e);
        }

        try {
            result.addStep(5, "Add user to organization group");
            keycloakApiClient.addUserToGroup(instance, token, result.getUserId(), result.getGroupId());
            result.markLastStepCompleted(null);
        } catch (Exception e) {
            return fail(result, e);
        }

        result.setSuccess(true);
        result.setOverallStatus("completed");
        logger.info("Keycloak initialization completed for organization '{}' (user id {})",
                organizationLabel, result.getUserId());
        return result;
    }

    private KeycloakInitializationResult fail(KeycloakInitializationResult result, Exception e) {
        logger.error("Keycloak initialization failed at step '{}': {}",
                result.getLastStep() != null ? result.getLastStep().getName() : "?", e.getMessage());
        result.markLastStepFailed(e.getMessage());
        result.setSuccess(false);
        result.setOverallStatus("failed");
        result.setErrorMessage(e.getMessage());
        return result;
    }

    private String firstNameOf(String contactName) {
        if (contactName == null || contactName.isBlank()) {
            return null;
        }
        String[] parts = contactName.trim().split("\\s+", 2);
        return parts[0];
    }

    private String lastNameOf(String contactName) {
        if (contactName == null || contactName.isBlank()) {
            return null;
        }
        String[] parts = contactName.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1] : null;
    }
}
