package com.data4circ.portal.features.onboardingsync.keycloak;

import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolInputField;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolProvisioner;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolSyncOutcome;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolsProperties;
import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;
import com.data4circ.portal.features.organization.service.OrganizationSpipUserService;
import com.data4circ.portal.features.platformsettings.service.KeycloakSettingsService;
import com.data4circ.portal.integration.keycloak.KeycloakInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakOnboardingToolProvisionerTest {

    /** Stands in for SPIP: a generic identity provider, looked up by capability, not by class. */
    private static final String IDENTITY_TOOL_KEY = "identity-provider";

    private static class StubIdentityProvider implements OnboardingToolProvisioner {
        @Override
        public String getKey() {
            return IDENTITY_TOOL_KEY;
        }

        @Override
        public String getDisplayName() {
            return "Stub Identity Provider";
        }

        @Override
        public boolean providesIdentity() {
            return true;
        }

        @Override
        public List<OnboardingToolInputField> describeInputs(OrganizationOnboardingRequest request) {
            return List.of();
        }

        @Override
        public OnboardingToolSyncOutcome synchronize(OrganizationOnboardingRequest request,
                                                      Map<String, String> params) {
            return OnboardingToolSyncOutcome.empty();
        }
    }

    @Mock
    private KeycloakOnboardingService keycloakOnboardingService;

    @Mock
    private KeycloakSettingsService keycloakSettings;

    @Mock
    private OrganizationSpipUserService spipUserService;

    private OnboardingToolsProperties toolsProperties;
    private KeycloakOnboardingToolProvisioner provisioner;
    private OrganizationOnboardingRequest request;

    @BeforeEach
    void setUp() {
        lenient().when(keycloakSettings.getBaseUrl()).thenReturn("http://localhost:8081");
        lenient().when(keycloakSettings.getRealm()).thenReturn("data4circ");
        lenient().when(keycloakSettings.getClientId()).thenReturn("portal-admin");
        lenient().when(keycloakSettings.getClientSecret()).thenReturn("default-secret");
        lenient().when(keycloakSettings.isDefaultInstance("http://localhost:8081", "data4circ")).thenReturn(true);

        toolsProperties = new OnboardingToolsProperties(); // unknown keys default to enabled
        provisioner = new KeycloakOnboardingToolProvisioner(
                keycloakOnboardingService, keycloakSettings, toolsProperties,
                List.of(new StubIdentityProvider()), spipUserService);

        request = new OrganizationOnboardingRequest();
        request.setCompanyName("Acme Corp");
        request.setEmail("Jane.Doe@acme.example.com");
        request.setContactName("Jane Doe");
        request.setSpipUser("acme");
    }

    private void disableIdentityProvider() {
        OnboardingToolsProperties.ToolProperties identityProvider = new OnboardingToolsProperties.ToolProperties();
        identityProvider.setEnabled(false);
        toolsProperties.getTools().put(IDENTITY_TOOL_KEY, identityProvider);
    }

    private Map<String, String> validParams() {
        Map<String, String> params = new HashMap<>();
        params.put(KeycloakOnboardingToolProvisioner.BASE_URL_PARAM, "http://localhost:8081");
        params.put(KeycloakOnboardingToolProvisioner.REALM_PARAM, "data4circ");
        params.put(KeycloakOnboardingToolProvisioner.CLIENT_ID_PARAM, "portal-admin");
        params.put(KeycloakOnboardingToolProvisioner.USERNAME_PARAM, "jane.doe@acme.example.com");
        return params;
    }

    private KeycloakInitializationResult successResult() {
        KeycloakInitializationResult result = new KeycloakInitializationResult();
        result.setSuccess(true);
        result.setUserId("user-id");
        result.setGroupId("group-id");
        return result;
    }

    @Test
    void describesInstanceAndIdentityFields() {
        List<OnboardingToolInputField> fields = provisioner.describeInputs(request);

        assertThat(fields).extracting(OnboardingToolInputField::getName).containsExactly(
                KeycloakOnboardingToolProvisioner.BASE_URL_PARAM,
                KeycloakOnboardingToolProvisioner.REALM_PARAM,
                KeycloakOnboardingToolProvisioner.CLIENT_ID_PARAM,
                KeycloakOnboardingToolProvisioner.CLIENT_SECRET_PARAM,
                KeycloakOnboardingToolProvisioner.GROUP_NAME_PARAM,
                KeycloakOnboardingToolProvisioner.USERNAME_PARAM);

        // Instance fields pre-filled from the default instance; secret never rendered
        assertThat(fields.get(0).getProposedValue()).isEqualTo("http://localhost:8081");
        assertThat(fields.get(1).getProposedValue()).isEqualTo("data4circ");
        assertThat(fields.get(2).getProposedValue()).isEqualTo("portal-admin");
        assertThat(fields.get(3).getProposedValue()).isNull();
        assertThat(fields.get(3).getType()).isEqualTo("password");
        assertThat(fields.get(3).isRequired()).isFalse();
        // SPIP enabled: group field read-only, fixed to the SPIP username
        assertThat(fields.get(4).getProposedValue()).isEqualTo("acme");
        assertThat(fields.get(4).isReadOnly()).isTrue();
        // Username is the contact email address
        assertThat(fields.get(5).getProposedValue()).isEqualTo("jane.doe@acme.example.com");
    }

    @Test
    void declaresIdentityProviderDependencyOnlyWhenEnabled() {
        assertThat(provisioner.getDependencies())
                .containsExactly(IDENTITY_TOOL_KEY);

        disableIdentityProvider();
        assertThat(provisioner.getDependencies()).isEmpty();
    }

    @Test
    void missingSpipUsernameIsRejected() {
        request.setSpipUser(null);

        assertThatThrownBy(() -> provisioner.synchronize(request, validParams()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPIP");
    }

    @Test
    void submittedGroupNameIsIgnoredWhileSpipEnabled() {
        Map<String, String> params = validParams();
        params.put(KeycloakOnboardingToolProvisioner.GROUP_NAME_PARAM, "tampered-value");
        when(keycloakOnboardingService.initializeOrganization(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(successResult());

        provisioner.synchronize(request, params);

        verify(keycloakOnboardingService).initializeOrganization(
                any(), any(), eq("acme"), anyString(), anyString());
        assertThat(request.getKeycloakGroupName()).isEqualTo("acme");
    }

    @Test
    void spipDisabledMakesGroupFieldEditableWithSlugProposal() {
        disableIdentityProvider();
        when(spipUserService.generateUsernameFromOrganizationName("Acme Corp")).thenReturn("acmecorp");

        List<OnboardingToolInputField> fields = provisioner.describeInputs(request);

        OnboardingToolInputField group = fields.get(4);
        assertThat(group.getName()).isEqualTo(KeycloakOnboardingToolProvisioner.GROUP_NAME_PARAM);
        assertThat(group.isReadOnly()).isFalse();
        assertThat(group.isRequired()).isTrue();
        assertThat(group.getProposedValue()).isEqualTo("acmecorp");
    }

    @Test
    void spipDisabledUsesSubmittedGroupName() {
        disableIdentityProvider();
        request.setSpipUser(null);
        Map<String, String> params = validParams();
        params.put(KeycloakOnboardingToolProvisioner.GROUP_NAME_PARAM, "manual-group");
        when(keycloakOnboardingService.initializeOrganization(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(successResult());

        provisioner.synchronize(request, params);

        verify(keycloakOnboardingService).initializeOrganization(
                any(), any(), eq("manual-group"), anyString(), anyString());
        assertThat(request.getKeycloakGroupName()).isEqualTo("manual-group");
    }

    @Test
    void spipDisabledRejectsMissingOrInvalidGroupName() {
        disableIdentityProvider();
        request.setSpipUser(null);

        assertThatThrownBy(() -> provisioner.synchronize(request, validParams()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("group name");

        Map<String, String> params = validParams();
        params.put(KeycloakOnboardingToolProvisioner.GROUP_NAME_PARAM, "Bad Name!");
        assertThatThrownBy(() -> provisioner.synchronize(request, params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("group name");
    }

    @Test
    void synchronizeUsesDefaultSecretWhenBlankAndMutatesRequest() {
        when(keycloakOnboardingService.initializeOrganization(
                eq(request), any(KeycloakInstance.class), eq("acme"), eq("jane.doe@acme.example.com"), anyString()))
                .thenReturn(successResult());

        OnboardingToolSyncOutcome outcome = provisioner.synchronize(request, validParams());

        ArgumentCaptor<KeycloakInstance> instanceCaptor = ArgumentCaptor.forClass(KeycloakInstance.class);
        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(keycloakOnboardingService).initializeOrganization(
                eq(request), instanceCaptor.capture(), eq("acme"), eq("jane.doe@acme.example.com"), passwordCaptor.capture());

        assertThat(instanceCaptor.getValue().getClientSecret()).isEqualTo("default-secret");
        assertThat(outcome.getDetails()).isNotBlank();
        assertThat(request.getKeycloakBaseUrl()).isEqualTo("http://localhost:8081");
        assertThat(request.getKeycloakRealm()).isEqualTo("data4circ");
        assertThat(request.getKeycloakGroupName()).isEqualTo("acme");
        assertThat(request.getKeycloakUsername()).isEqualTo("jane.doe@acme.example.com");
        assertThat(request.getKeycloakUserId()).isEqualTo("user-id");
        assertThat(request.getKeycloakTempPassword()).isEqualTo(passwordCaptor.getValue());
        // Same complexity recipe as the SPIP credentials
        assertThat(passwordCaptor.getValue()).hasSize(16).matches(".*[A-Z].*").matches(".*[0-9].*");
    }

    @Test
    void synchronizeUsesEnteredSecretOverDefault() {
        Map<String, String> params = validParams();
        params.put(KeycloakOnboardingToolProvisioner.CLIENT_SECRET_PARAM, "typed-secret");
        when(keycloakOnboardingService.initializeOrganization(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(successResult());

        provisioner.synchronize(request, params);

        ArgumentCaptor<KeycloakInstance> instanceCaptor = ArgumentCaptor.forClass(KeycloakInstance.class);
        verify(keycloakOnboardingService).initializeOrganization(
                any(), instanceCaptor.capture(), anyString(), anyString(), anyString());
        assertThat(instanceCaptor.getValue().getClientSecret()).isEqualTo("typed-secret");
    }

    @Test
    void missingSecretEverywhereIsRejected() {
        when(keycloakSettings.getClientSecret()).thenReturn(null);

        assertThatThrownBy(() -> provisioner.synchronize(request, validParams()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client secret");
    }

    @Test
    void blankSecretForNonDefaultInstanceIsRejected() {
        Map<String, String> params = validParams();
        params.put(KeycloakOnboardingToolProvisioner.BASE_URL_PARAM, "https://other-kc.example.com");

        assertThatThrownBy(() -> provisioner.synchronize(request, params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-default instance");
        org.mockito.Mockito.verifyNoInteractions(keycloakOnboardingService);
    }

    @Test
    void typedSecretForNonDefaultInstanceIsAccepted() {
        Map<String, String> params = validParams();
        params.put(KeycloakOnboardingToolProvisioner.BASE_URL_PARAM, "https://other-kc.example.com");
        params.put(KeycloakOnboardingToolProvisioner.CLIENT_SECRET_PARAM, "own-secret");
        when(keycloakOnboardingService.initializeOrganization(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(successResult());

        provisioner.synchronize(request, params);

        ArgumentCaptor<KeycloakInstance> instanceCaptor = ArgumentCaptor.forClass(KeycloakInstance.class);
        verify(keycloakOnboardingService).initializeOrganization(
                any(), instanceCaptor.capture(), anyString(), anyString(), anyString());
        assertThat(instanceCaptor.getValue().getClientSecret()).isEqualTo("own-secret");
    }

    @Test
    void invalidRealmIsRejected() {
        Map<String, String> params = validParams();
        params.put(KeycloakOnboardingToolProvisioner.REALM_PARAM, "bad\"realm{");

        assertThatThrownBy(() -> provisioner.synchronize(request, params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("realm");
    }

    @Test
    void invalidUsernameIsRejected() {
        Map<String, String> params = validParams();
        params.put(KeycloakOnboardingToolProvisioner.USERNAME_PARAM, "Bad Name!");

        assertThatThrownBy(() -> provisioner.synchronize(request, params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");
    }

    @Test
    void failedInitializationSurfacesAsIllegalState() {
        KeycloakInitializationResult failed = new KeycloakInitializationResult();
        failed.setSuccess(false);
        failed.setErrorMessage("Forbidden");
        lenient().when(keycloakOnboardingService.initializeOrganization(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(failed);

        assertThatThrownBy(() -> provisioner.synchronize(request, validParams()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Forbidden");
        assertThat(request.getKeycloakTempPassword()).isNull();
    }
}
