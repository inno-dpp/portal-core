package com.data4circ.portal.features.platformsettings.service;

import com.data4circ.portal.common.config.SpipProperties;
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
 * Unit tests for the SPIP settings override resolution:
 * database override wins, environment/property values are the fallback.
 */
@ExtendWith(MockitoExtension.class)
class SpipSettingsServiceTest {

    @Mock
    private PlatformSettingService platformSettingService;

    private SpipProperties spipProperties;
    private SpipSettingsService spipSettingsService;

    @BeforeEach
    void setUp() {
        spipProperties = new SpipProperties();
        spipProperties.setBaseUrl("https://env-spip:8002");
        spipProperties.setCustomerTag("env-tag");
        spipProperties.getAdmin().setUsername("env-admin");
        spipProperties.getAdmin().setPassword("env-password");
        spipProperties.setDefaultRole("data_owner");
        spipSettingsService = new SpipSettingsService(platformSettingService, spipProperties);
    }

    @Test
    void fallsBackToPropertiesWhenNoOverride() {
        when(platformSettingService.getValue(anyString())).thenReturn(Optional.empty());

        assertThat(spipSettingsService.getBaseUrl()).isEqualTo("https://env-spip:8002");
        assertThat(spipSettingsService.getCustomerTag()).isEqualTo("env-tag");
        assertThat(spipSettingsService.getAdminUsername()).isEqualTo("env-admin");
        assertThat(spipSettingsService.getAdminPassword()).isEqualTo("env-password");
        assertThat(spipSettingsService.getDefaultRole()).isEqualTo("data_owner");
        assertThat(spipSettingsService.isBaseUrlOverridden()).isFalse();
        assertThat(spipSettingsService.isAdminPasswordOverridden()).isFalse();
    }

    @Test
    void databaseOverrideWins() {
        when(platformSettingService.getValue(SpipSettingsService.KEY_BASE_URL))
                .thenReturn(Optional.of("https://spip.example.com/"));
        when(platformSettingService.getValue(SpipSettingsService.KEY_ADMIN_USERNAME))
                .thenReturn(Optional.of("db-admin"));
        when(platformSettingService.getValue(SpipSettingsService.KEY_ADMIN_PASSWORD))
                .thenReturn(Optional.of("db-password"));
        when(platformSettingService.getValue(SpipSettingsService.KEY_DEFAULT_ROLE))
                .thenReturn(Optional.of("new_data_owner"));

        assertThat(spipSettingsService.getBaseUrl()).isEqualTo("https://spip.example.com");
        assertThat(spipSettingsService.getAdminUsername()).isEqualTo("db-admin");
        assertThat(spipSettingsService.getAdminPassword()).isEqualTo("db-password");
        assertThat(spipSettingsService.getDefaultRole()).isEqualTo("new_data_owner");
        assertThat(spipSettingsService.isDefaultRoleOverridden()).isTrue();
    }

    @Test
    void updateStoresTrimmedValues() {
        spipSettingsService.updateSettings("https://spip.example.com/ ", " tag ",
                " admin ", " secret ", " new_data_owner ", "admin");

        verify(platformSettingService).setValue(SpipSettingsService.KEY_BASE_URL,
                "https://spip.example.com", "admin");
        verify(platformSettingService).setValue(SpipSettingsService.KEY_CUSTOMER_TAG, "tag", "admin");
        verify(platformSettingService).setValue(SpipSettingsService.KEY_ADMIN_USERNAME, "admin", "admin");
        verify(platformSettingService).setValue(SpipSettingsService.KEY_ADMIN_PASSWORD, "secret", "admin");
        verify(platformSettingService).setValue(SpipSettingsService.KEY_DEFAULT_ROLE, "new_data_owner", "admin");
    }

    @Test
    void blankNonSecretFieldsClearTheirOverrides() {
        spipSettingsService.updateSettings("", "", "", "secret", "", "admin");

        verify(platformSettingService).clearValue(SpipSettingsService.KEY_BASE_URL);
        verify(platformSettingService).clearValue(SpipSettingsService.KEY_CUSTOMER_TAG);
        verify(platformSettingService).clearValue(SpipSettingsService.KEY_ADMIN_USERNAME);
        verify(platformSettingService).clearValue(SpipSettingsService.KEY_DEFAULT_ROLE);
    }

    @Test
    void blankPasswordKeepsExistingPassword() {
        spipSettingsService.updateSettings("https://spip.example.com", "tag", "admin", "", "role", "admin");

        verify(platformSettingService, never()).setValue(eq(SpipSettingsService.KEY_ADMIN_PASSWORD),
                anyString(), anyString());
        verify(platformSettingService, never()).clearValue(SpipSettingsService.KEY_ADMIN_PASSWORD);
    }

    @Test
    void rejectsBaseUrlWithoutHttpScheme() {
        assertThatThrownBy(() -> spipSettingsService.updateSettings("spip.example.com",
                null, null, null, null, "admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetClearsAllOverrides() {
        spipSettingsService.resetToEnvironmentDefaults("admin");

        verify(platformSettingService).clearValue(SpipSettingsService.KEY_BASE_URL);
        verify(platformSettingService).clearValue(SpipSettingsService.KEY_CUSTOMER_TAG);
        verify(platformSettingService).clearValue(SpipSettingsService.KEY_ADMIN_USERNAME);
        verify(platformSettingService).clearValue(SpipSettingsService.KEY_ADMIN_PASSWORD);
        verify(platformSettingService).clearValue(SpipSettingsService.KEY_DEFAULT_ROLE);
    }
}
