package com.data4circ.portal.features.platformsettings.service;

import com.data4circ.portal.common.config.CkanConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the CKAN settings override resolution:
 * database override wins, environment/property values are the fallback.
 */
@ExtendWith(MockitoExtension.class)
class CkanSettingsServiceTest {

    @Mock
    private PlatformSettingService platformSettingService;

    @Mock
    private CacheManager cacheManager;

    private CkanConfig.CkanProperties ckanProperties;
    private CkanSettingsService ckanSettingsService;

    @BeforeEach
    void setUp() {
        ckanProperties = new CkanConfig.CkanProperties();
        ckanProperties.setBaseUrl("http://env-ckan:5000");
        ckanProperties.setJwtToken("env-token-1234");
        ckanSettingsService = new CkanSettingsService(platformSettingService, ckanProperties, cacheManager);
    }

    @Test
    void fallsBackToPropertiesWhenNoOverride() {
        when(platformSettingService.getValue(anyString())).thenReturn(Optional.empty());

        assertThat(ckanSettingsService.getBaseUrl()).isEqualTo("http://env-ckan:5000");
        assertThat(ckanSettingsService.getJwtToken()).isEqualTo("env-token-1234");
        assertThat(ckanSettingsService.isBaseUrlOverridden()).isFalse();
        assertThat(ckanSettingsService.isJwtTokenOverridden()).isFalse();
    }

    @Test
    void databaseOverrideWins() {
        when(platformSettingService.getValue(CkanSettingsService.KEY_BASE_URL))
                .thenReturn(Optional.of("https://ckan.example.com"));
        when(platformSettingService.getValue(CkanSettingsService.KEY_JWT_TOKEN))
                .thenReturn(Optional.of("db-token-5678"));

        assertThat(ckanSettingsService.getBaseUrl()).isEqualTo("https://ckan.example.com");
        assertThat(ckanSettingsService.getJwtToken()).isEqualTo("db-token-5678");
        assertThat(ckanSettingsService.isBaseUrlOverridden()).isTrue();
        assertThat(ckanSettingsService.isJwtTokenOverridden()).isTrue();
    }

    @Test
    void baseUrlTrailingSlashesAreStripped() {
        when(platformSettingService.getValue(CkanSettingsService.KEY_BASE_URL))
                .thenReturn(Optional.of("https://ckan.example.com/"));

        assertThat(ckanSettingsService.getBaseUrl()).isEqualTo("https://ckan.example.com");
    }

    @Test
    void updateStoresNormalizedBaseUrlAndToken() {
        ckanSettingsService.updateSettings("https://ckan.example.com/ ", " new-token ", "admin");

        verify(platformSettingService).setValue(CkanSettingsService.KEY_BASE_URL,
                "https://ckan.example.com", "admin");
        verify(platformSettingService).setValue(CkanSettingsService.KEY_JWT_TOKEN,
                "new-token", "admin");
    }

    @Test
    void blankBaseUrlClearsOverride() {
        ckanSettingsService.updateSettings("  ", "token", "admin");

        verify(platformSettingService).clearValue(CkanSettingsService.KEY_BASE_URL);
        verify(platformSettingService, never()).setValue(eq(CkanSettingsService.KEY_BASE_URL),
                anyString(), anyString());
    }

    @Test
    void blankTokenKeepsExistingToken() {
        ckanSettingsService.updateSettings("https://ckan.example.com", "", "admin");

        verify(platformSettingService, never()).setValue(eq(CkanSettingsService.KEY_JWT_TOKEN),
                anyString(), anyString());
        verify(platformSettingService, never()).clearValue(CkanSettingsService.KEY_JWT_TOKEN);
    }

    @Test
    void rejectsBaseUrlWithoutHttpScheme() {
        assertThatThrownBy(() -> ckanSettingsService.updateSettings("ftp://ckan.example.com", null, "admin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ckanSettingsService.updateSettings("not a url", null, "admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetClearsBothOverrides() {
        ckanSettingsService.resetToEnvironmentDefaults("admin");

        verify(platformSettingService).clearValue(CkanSettingsService.KEY_BASE_URL);
        verify(platformSettingService).clearValue(CkanSettingsService.KEY_JWT_TOKEN);
    }

    @Test
    void tokenPreviewNeverExposesFullToken() {
        when(platformSettingService.getValue(CkanSettingsService.KEY_JWT_TOKEN))
                .thenReturn(Optional.of("db-token-abcd"));

        String preview = ckanSettingsService.getJwtTokenPreview();
        assertThat(preview).doesNotContain("db-token");
        assertThat(preview).endsWith("abcd");
    }

    @Test
    void shortTokenPreviewIsFullyMasked() {
        when(platformSettingService.getValue(CkanSettingsService.KEY_JWT_TOKEN))
                .thenReturn(Optional.of("tiny"));

        assertThat(ckanSettingsService.getJwtTokenPreview()).doesNotContain("tiny");
    }
}
