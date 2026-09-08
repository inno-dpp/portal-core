package com.data4circ.portal.features.onboardingsync.keycloak;

import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;
import com.data4circ.portal.integration.keycloak.KeycloakInstance;
import com.data4circ.portal.integration.keycloak.client.KeycloakApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakOnboardingServiceTest {

    @Mock
    private KeycloakApiClient keycloakApiClient;

    private KeycloakOnboardingService service;
    private OrganizationOnboardingRequest request;
    private KeycloakInstance instance;

    @BeforeEach
    void setUp() {
        service = new KeycloakOnboardingService(keycloakApiClient);

        request = new OrganizationOnboardingRequest();
        request.setCompanyName("Acme Corp");
        request.setEmail("jane.doe@acme.example.com");
        request.setContactName("Jane Doe");

        instance = new KeycloakInstance("http://localhost:8081", "data4circ", "portal-admin", "secret");
    }

    @Test
    void provisionsGroupUserPasswordAndMembership() {
        when(keycloakApiClient.obtainAdminToken(instance)).thenReturn("token");
        when(keycloakApiClient.createGroup(instance, "token", "acme")).thenReturn("group-id");
        when(keycloakApiClient.createUser(instance, "token", "jane.doe",
                "jane.doe@acme.example.com", "Jane", "Doe")).thenReturn("user-id");

        KeycloakInitializationResult result = service.initializeOrganization(
                request, instance, "acme", "jane.doe", "Temp#Pass123");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getGroupId()).isEqualTo("group-id");
        assertThat(result.getUserId()).isEqualTo("user-id");
        assertThat(result.getSteps()).hasSize(5);
        assertThat(result.getSteps()).allMatch(step -> "completed".equals(step.getStatus()));
        verify(keycloakApiClient).setPassword(instance, "token", "user-id", "Temp#Pass123", true);
        verify(keycloakApiClient).addUserToGroup(instance, "token", "user-id", "group-id");
    }

    @Test
    void failedAuthenticationShortCircuits() {
        when(keycloakApiClient.obtainAdminToken(instance))
                .thenThrow(new KeycloakApiClient.KeycloakApiException("401 Unauthorized"));

        KeycloakInitializationResult result = service.initializeOrganization(
                request, instance, "acme", "jane.doe", "Temp#Pass123");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("401");
        assertThat(result.getSteps()).hasSize(1);
        assertThat(result.getLastStep().getStatus()).isEqualTo("failed");
        verify(keycloakApiClient, never()).createGroup(any(), anyString(), anyString());
    }

    @Test
    void failedPasswordResetMarksStepAndStops() {
        when(keycloakApiClient.obtainAdminToken(instance)).thenReturn("token");
        when(keycloakApiClient.createGroup(instance, "token", "acme")).thenReturn("group-id");
        when(keycloakApiClient.createUser(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn("user-id");
        doThrow(new KeycloakApiClient.KeycloakApiException("boom"))
                .when(keycloakApiClient).setPassword(any(), anyString(), anyString(), anyString(), eq(true));

        KeycloakInitializationResult result = service.initializeOrganization(
                request, instance, "acme", "jane.doe", "Temp#Pass123");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getSteps()).hasSize(4);
        assertThat(result.getLastStep().getStatus()).isEqualTo("failed");
        verify(keycloakApiClient, never()).addUserToGroup(any(), anyString(), anyString(), anyString());
    }

    @Test
    void singleWordContactNameBecomesFirstNameOnly() {
        request.setContactName("Cher");
        when(keycloakApiClient.obtainAdminToken(instance)).thenReturn("token");
        when(keycloakApiClient.createGroup(instance, "token", "acme")).thenReturn("group-id");
        when(keycloakApiClient.createUser(instance, "token", "jane.doe",
                "jane.doe@acme.example.com", "Cher", null)).thenReturn("user-id");

        KeycloakInitializationResult result = service.initializeOrganization(
                request, instance, "acme", "jane.doe", "Temp#Pass123");

        assertThat(result.isSuccess()).isTrue();
    }
}
