package com.data4circ.portal.features.platformsettings.service;

import com.data4circ.portal.common.config.KeycloakConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Keycloak settings override resolution:
 * database override wins, environment/property values are the fallback.
 */
@ExtendWith(MockitoExtension.class)
class KeycloakSettingsServiceTest {

    @Mock
    private PlatformSettingService platformSettingService;

    private KeycloakConfig.KeycloakProperties keycloakProperties;
    private KeycloakSettingsService keycloakSettingsService;

    @BeforeEach
    void setUp() {
        keycloakProperties = new KeycloakConfig.KeycloakProperties();
        keycloakProperties.setBaseUrl("http://env-keycloak:8081");
        keycloakProperties.setRealm("env-realm");
        keycloakProperties.setClientId("env-client");
        keycloakProperties.setClientSecret("env-secret");
        keycloakSettingsService = new KeycloakSettingsService(platformSettingService, keycloakProperties);
    }

    @Test
    void fallsBackToPropertiesWhenNoOverride() {
        when(platformSettingService.getValue(anyString())).thenReturn(Optional.empty());

        assertThat(keycloakSettingsService.getBaseUrl()).isEqualTo("http://env-keycloak:8081");
        assertThat(keycloakSettingsService.getRealm()).isEqualTo("env-realm");
        assertThat(keycloakSettingsService.getClientId()).isEqualTo("env-client");
        assertThat(keycloakSettingsService.getClientSecret()).isEqualTo("env-secret");
        assertThat(keycloakSettingsService.isBaseUrlOverridden()).isFalse();
        assertThat(keycloakSettingsService.isClientSecretOverridden()).isFalse();
    }

    @Test
    void databaseOverrideWins() {
        when(platformSettingService.getValue(KeycloakSettingsService.KEY_BASE_URL))
                .thenReturn(Optional.of("https://kc.example.com/"));
        when(platformSettingService.getValue(KeycloakSettingsService.KEY_REALM))
                .thenReturn(Optional.of("db-realm"));
        when(platformSettingService.getValue(KeycloakSettingsService.KEY_CLIENT_ID))
                .thenReturn(Optional.of("db-client"));
        when(platformSettingService.getValue(KeycloakSettingsService.KEY_CLIENT_SECRET))
                .thenReturn(Optional.of("db-secret"));

        assertThat(keycloakSettingsService.getBaseUrl()).isEqualTo("https://kc.example.com");
        assertThat(keycloakSettingsService.getRealm()).isEqualTo("db-realm");
        assertThat(keycloakSettingsService.getClientId()).isEqualTo("db-client");
        assertThat(keycloakSettingsService.getClientSecret()).isEqualTo("db-secret");
        assertThat(keycloakSettingsService.isBaseUrlOverridden()).isTrue();
        assertThat(keycloakSettingsService.isClientSecretOverridden()).isTrue();
    }

    @Test
    void updateStoresTrimmedValuesAndSkipsBlankSecret() {
        keycloakSettingsService.updateSettings(" https://kc.example.com/ ", " myrealm ", " portal-admin ", "  ", "admin");

        verify(platformSettingService).setValue(KeycloakSettingsService.KEY_BASE_URL, "https://kc.example.com", "admin");
        verify(platformSettingService).setValue(KeycloakSettingsService.KEY_REALM, "myrealm", "admin");
        verify(platformSettingService).setValue(KeycloakSettingsService.KEY_CLIENT_ID, "portal-admin", "admin");
        verify(platformSettingService, never()).setValue(eq(KeycloakSettingsService.KEY_CLIENT_SECRET), anyString(), anyString());
        verify(platformSettingService, never()).clearValue(KeycloakSettingsService.KEY_CLIENT_SECRET);
    }

    @Test
    void blankNonSecretValuesClearTheirOverrides() {
        keycloakSettingsService.updateSettings("", "", "", "new-secret", "admin");

        verify(platformSettingService).clearValue(KeycloakSettingsService.KEY_BASE_URL);
        verify(platformSettingService).clearValue(KeycloakSettingsService.KEY_REALM);
        verify(platformSettingService).clearValue(KeycloakSettingsService.KEY_CLIENT_ID);
        verify(platformSettingService).setValue(KeycloakSettingsService.KEY_CLIENT_SECRET, "new-secret", "admin");
    }

    @Test
    void invalidBaseUrlIsRejected() {
        assertThatThrownBy(() ->
                keycloakSettingsService.updateSettings("keycloak.example.com", null, null, null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http");
    }

    @Test
    void resetClearsAllOverrides() {
        keycloakSettingsService.resetToEnvironmentDefaults("admin");

        verify(platformSettingService).clearValue(KeycloakSettingsService.KEY_BASE_URL);
        verify(platformSettingService).clearValue(KeycloakSettingsService.KEY_REALM);
        verify(platformSettingService).clearValue(KeycloakSettingsService.KEY_CLIENT_ID);
        verify(platformSettingService).clearValue(KeycloakSettingsService.KEY_CLIENT_SECRET);
    }

    @Test
    void secretConfiguredReflectsEffectiveValue() {
        when(platformSettingService.getValue(KeycloakSettingsService.KEY_CLIENT_SECRET))
                .thenReturn(Optional.empty());
        assertThat(keycloakSettingsService.isClientSecretConfigured()).isTrue();

        keycloakProperties.setClientSecret("");
        assertThat(keycloakSettingsService.isClientSecretConfigured()).isFalse();
    }
}
