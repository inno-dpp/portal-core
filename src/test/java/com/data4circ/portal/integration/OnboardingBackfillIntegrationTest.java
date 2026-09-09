package com.data4circ.portal.integration;

import com.data4circ.portal.features.connectors.repository.ConnectorRepository;
import com.data4circ.portal.features.onboardingsync.ckan.CkanOnboardingSyncService;
import com.data4circ.portal.features.onboardingsync.ckan.CkanSyncResult;
import com.data4circ.portal.features.onboardingsync.keycloak.KeycloakInitializationResult;
import com.data4circ.portal.features.onboardingsync.keycloak.KeycloakOnboardingService;
import com.data4circ.portal.features.onboardingsync.keycloak.KeycloakOnboardingToolProvisioner;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolSyncStateService;
import com.data4circ.portal.features.organization.entity.CompanySize;
import com.data4circ.portal.features.organization.entity.OnboardingRequestStatus;
import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;
import com.data4circ.portal.features.organization.repository.OnboardingRequestRepository;
import com.data4circ.portal.features.organization.repository.OrganizationRepository;
import com.data4circ.portal.features.organization.service.EmailService;
import com.data4circ.portal.features.organization.service.OnboardingRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link OnboardingRequestService#backfillTool} — retroactively adding a tool to an
 * organization that was already approved before that tool existed or was enabled in this
 * deployment (the motivating case: SPIP becoming available after upgrading a
 * portal-core-only deployment to spip-plugin).
 *
 * <p>Exercised here with Keycloak rather than SPIP: SPIP has no provisioner bean in core
 * at all (spip-plugin only), so it can't be driven end-to-end from this module's tests —
 * but the backfill mechanism itself is entirely tool-agnostic (registry/connector-service
 * based), so proving it here with a real, provisioner-backed, optional tool exercises the
 * exact same code path SPIP would take once spip-plugin supplies its provisioner.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OnboardingBackfillIntegrationTest {

    @Autowired
    private OnboardingRequestService onboardingRequestService;

    @Autowired
    private OnboardingRequestRepository onboardingRequestRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private OnboardingToolSyncStateService toolSyncState;

    @MockBean
    private CkanOnboardingSyncService ckanOnboardingSyncService;

    @MockBean
    private KeycloakOnboardingService keycloakOnboardingService;

    @MockBean
    private EmailService emailService;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Test
    void backfillingAnUnsyncedOptionalToolAddsItsConnectorWithoutTouchingExistingOnes() {
        mockCkanSuccess();
        OrganizationOnboardingRequest request = savedRequest();

        // Approved with only CKAN synced — Keycloak is optional (required=false by default),
        // so approval proceeds without it, same as a deployment approving orgs before SPIP
        // (or here, Keycloak) was ever part of the picture.
        onboardingRequestService.synchronizeTool(request.getId(), "ckan",
                Map.of("ckanOrgShortName", "backfill-org", "ckanUsername", "backfilluser"));
        OrganizationOnboardingRequest approved =
                onboardingRequestService.approveRequest(request.getId(), "admin", "approved without keycloak");
        assertThat(approved.getStatus()).isEqualTo(OnboardingRequestStatus.ORGANIZATION_CREATED);

        Organization organization = approved.getOrganization();
        assertThat(connectorRepository.findByOrganizationAndToolKey(organization, "ckan")).isPresent();
        assertThat(connectorRepository.findByOrganizationAndToolKey(organization, "keycloak")).isEmpty();
        assertThat(toolSyncState.isSynced(request.getId(), "keycloak")).isFalse();

        // Now "install" Keycloak for this already-live organization.
        mockKeycloakSuccess();
        OrganizationOnboardingRequest backfilled = onboardingRequestService.backfillTool(request.getId(), "keycloak", Map.of(
                KeycloakOnboardingToolProvisioner.BASE_URL_PARAM, "http://localhost:8081",
                KeycloakOnboardingToolProvisioner.REALM_PARAM, "data4circ",
                KeycloakOnboardingToolProvisioner.CLIENT_ID_PARAM, "portal-admin",
                KeycloakOnboardingToolProvisioner.CLIENT_SECRET_PARAM, "test-secret",
                KeycloakOnboardingToolProvisioner.GROUP_NAME_PARAM, "backfill-group",
                KeycloakOnboardingToolProvisioner.USERNAME_PARAM, request.getEmail()));

        assertThat(backfilled.getStatus()).isEqualTo(OnboardingRequestStatus.ORGANIZATION_CREATED);
        assertThat(toolSyncState.isSynced(request.getId(), "keycloak")).isTrue();

        // The new tool's connector now exists...
        assertThat(connectorRepository.findByOrganizationAndToolKey(organization, "keycloak")).isPresent();
        // ...and the pre-existing CKAN connector was left exactly as it was — materializeConnectors
        // is idempotent, but this pins down that a backfill doesn't duplicate or disturb it.
        assertThat(connectorRepository.findByOrganization(organization).stream()
                .filter(c -> "ckan".equals(c.getToolKey())).count()).isEqualTo(1);
    }

    @Test
    void backfillingAnAlreadySyncedToolIsRejected() {
        mockCkanSuccess();
        OrganizationOnboardingRequest request = savedRequest();
        onboardingRequestService.synchronizeTool(request.getId(), "ckan",
                Map.of("ckanOrgShortName", "backfill-org-2", "ckanUsername", "backfilluser2"));
        OrganizationOnboardingRequest approved =
                onboardingRequestService.approveRequest(request.getId(), "admin", "ok");

        assertThatThrownBy(() -> onboardingRequestService.backfillTool(approved.getId(), "ckan", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already synchronized");
    }

    @Test
    void backfillingAPendingRequestIsRejected() {
        OrganizationOnboardingRequest request = savedRequest();

        assertThatThrownBy(() -> onboardingRequestService.backfillTool(request.getId(), "keycloak", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only an approved request");
    }

    private void mockCkanSuccess() {
        CkanSyncResult result = new CkanSyncResult();
        result.setSuccess(true);
        result.setApiToken("backfill-token");
        when(ckanOnboardingSyncService.synchronizeOnboardingRequest(any(), anyString())).thenReturn(result);
    }

    private void mockKeycloakSuccess() {
        KeycloakInitializationResult result = new KeycloakInitializationResult();
        result.setSuccess(true);
        result.setUserId("kc-user-id");
        result.setGroupId("kc-group-id");
        when(keycloakOnboardingService.initializeOrganization(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(result);
    }

    private OrganizationOnboardingRequest savedRequest() {
        int id = SEQUENCE.incrementAndGet();
        OrganizationOnboardingRequest request = new OrganizationOnboardingRequest();
        request.setCompanyName("Backfill Test Co " + id);
        request.setCompanyType("manufacturer");
        request.setIndustry("METALS");
        request.setCompanySize(CompanySize.MEDIUM);
        request.setOrganizationDescription("Test description");
        request.setContactName("Jane Roe");
        request.setContactTitle("CTO");
        request.setEmail("backfill" + id + "@testcompany.example.com");
        request.setPhone("+1234567890");
        request.setParticipationGoals("Test goals");
        request.setGdprConsent(true);
        request.setStatus(OnboardingRequestStatus.PENDING);
        return onboardingRequestRepository.save(request);
    }
}
