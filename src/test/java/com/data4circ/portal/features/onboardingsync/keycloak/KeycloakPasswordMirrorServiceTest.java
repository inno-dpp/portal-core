package com.data4circ.portal.features.onboardingsync.keycloak;

import com.data4circ.portal.common.config.KeycloakConfig;
import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.platformsettings.service.KeycloakSettingsService;
import com.data4circ.portal.integration.keycloak.KeycloakInstance;
import com.data4circ.portal.integration.keycloak.client.KeycloakApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakPasswordMirrorServiceTest {

    @Mock
    private KeycloakApiClient keycloakApiClient;

    @Mock
    private KeycloakSettingsService keycloakSettings;

    private KeycloakConfig.KeycloakProperties properties;
    private KeycloakPasswordMirrorService service;
    private User user;

    @BeforeEach
    void setUp() {
        properties = new KeycloakConfig.KeycloakProperties();
        properties.setMirrorPortalPassword(true);

        service = new KeycloakPasswordMirrorService(keycloakApiClient, keycloakSettings, properties);

        user = new User();
        user.setUsername("erika");
        user.setEmail("erika@acme.example.com");
        user.setKeycloakUserId("kc-user-id");

        lenient().when(keycloakSettings.getBaseUrl()).thenReturn("http://localhost:8081");
        lenient().when(keycloakSettings.getRealm()).thenReturn("data4circ");
        lenient().when(keycloakSettings.getClientId()).thenReturn("portal-admin");
        lenient().when(keycloakSettings.getClientSecret()).thenReturn("secret");
    }

    @Test
    void mirrorsPasswordAndRemovesUpdatePasswordAction() {
        when(keycloakApiClient.obtainAdminToken(any(KeycloakInstance.class))).thenReturn("token");

        service.mirrorPasswordIfLinked(user, "New#Password1");

        verify(keycloakApiClient).setPassword(any(KeycloakInstance.class), eq("token"),
                eq("kc-user-id"), eq("New#Password1"), eq(false));
        verify(keycloakApiClient).removeRequiredAction(any(KeycloakInstance.class), eq("token"),
                eq("kc-user-id"), eq("UPDATE_PASSWORD"));
    }

    @Test
    void disabledFlagIsNoOp() {
        properties.setMirrorPortalPassword(false);

        service.mirrorPasswordIfLinked(user, "New#Password1");

        verifyNoInteractions(keycloakApiClient);
    }

    @Test
    void userWithoutStampedKeycloakIdIsSkipped() {
        user.setKeycloakUserId(null);

        service.mirrorPasswordIfLinked(user, "New#Password1");

        verifyNoInteractions(keycloakApiClient);
    }

    @Test
    void missingDefaultSecretIsSkipped() {
        when(keycloakSettings.getClientSecret()).thenReturn(" ");

        service.mirrorPasswordIfLinked(user, "New#Password1");

        verifyNoInteractions(keycloakApiClient);
    }

    @Test
    void keycloakFailureIsSwallowed() {
        when(keycloakApiClient.obtainAdminToken(any(KeycloakInstance.class)))
                .thenThrow(new KeycloakApiClient.KeycloakApiException("connection refused"));

        service.mirrorPasswordIfLinked(user, "New#Password1"); // must not throw

        verify(keycloakApiClient, never()).setPassword(any(), anyString(), anyString(), anyString(), eq(false));
    }

    @Test
    void passwordPolicyRejectionIsSwallowed() {
        when(keycloakApiClient.obtainAdminToken(any(KeycloakInstance.class))).thenReturn("token");
        doThrow(new KeycloakApiClient.KeycloakApiException("400 - password policy not met"))
                .when(keycloakApiClient).setPassword(any(), anyString(), anyString(), anyString(), eq(false));

        service.mirrorPasswordIfLinked(user, "weak"); // must not throw

        verify(keycloakApiClient, never()).removeRequiredAction(any(), anyString(), anyString(), anyString());
    }
}
