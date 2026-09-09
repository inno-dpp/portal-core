package com.data4circ.portal.integration;

import com.data4circ.portal.features.connectors.repository.ConnectorRepository;
import com.data4circ.portal.features.onboardingsync.ckan.CkanOnboardingSyncService;
import com.data4circ.portal.features.onboardingsync.ckan.CkanSyncResult;
import com.data4circ.portal.features.onboardingsync.keycloak.KeycloakOnboardingService;
import com.data4circ.portal.features.organization.entity.CompanySize;
import com.data4circ.portal.features.organization.entity.OnboardingRequestStatus;
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

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Regression test for the "SPIP Agent" phantom-connector bug: {@code spip-agent} is a
 * config-only tool (no provisioner, so {@link SpipModuleDisabledIntegrationTest}'s
 * {@code app.modules.spip.enabled=false} isn't the only way operators disable SPIP).
 * A real "no SPIP" deployment of portal-core alone typically sets only
 * {@code ONBOARDING_TOOL_SPIP_ENABLED=false} (see .env.example) and never touches
 * {@code app.modules.spip.enabled} at all — that override must disable "spip-agent" too,
 * not just the "spip" tool it's set on, or the connector keeps appearing in every
 * approved org's "My Tools" list regardless.
 */
@SpringBootTest(properties = "app.onboarding.tools.spip.enabled=false")
@ActiveProfiles("test")
@Transactional
class SpipAgentFollowsSpipToolOverrideTest {

    @Autowired
    private OnboardingRequestService onboardingRequestService;

    @Autowired
    private OnboardingRequestRepository onboardingRequestRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ConnectorRepository connectorRepository;

    @MockBean
    private CkanOnboardingSyncService ckanOnboardingSyncService;

    @MockBean
    private KeycloakOnboardingService keycloakOnboardingService;

    @MockBean
    private EmailService emailService;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Test
    void spipAgentConnectorIsNotCreatedWhenOnlySpipToolIsDisabled() {
        CkanSyncResult ckanResult = new CkanSyncResult();
        ckanResult.setSuccess(true);
        ckanResult.setApiToken("spip-agent-override-token");
        when(ckanOnboardingSyncService.synchronizeOnboardingRequest(any(), anyString())).thenReturn(ckanResult);

        int id = SEQUENCE.incrementAndGet();
        OrganizationOnboardingRequest request = new OrganizationOnboardingRequest();
        request.setCompanyName("Spip Agent Override Co " + id);
        request.setCompanyType("manufacturer");
        request.setIndustry("METALS");
        request.setCompanySize(CompanySize.MEDIUM);
        request.setOrganizationDescription("Test description");
        request.setContactName("Jane Roe");
        request.setContactTitle("CTO");
        request.setEmail("spipagentoverride" + id + "@testcompany.example.com");
        request.setPhone("+1234567890");
        request.setParticipationGoals("Test goals");
        request.setGdprConsent(true);
        request.setStatus(OnboardingRequestStatus.PENDING);
        request = onboardingRequestRepository.save(request);

        onboardingRequestService.synchronizeTool(request.getId(), "ckan",
                java.util.Map.of("ckanOrgShortName", "spip-agent-override-org", "ckanUsername", "spipagentoverrideuser"));

        OrganizationOnboardingRequest approved =
                onboardingRequestService.approveRequest(request.getId(), "admin", "ok, spip tool disabled only");
        assertThat(approved.getStatus()).isEqualTo(OnboardingRequestStatus.ORGANIZATION_CREATED);

        var organization = organizationRepository.findByName(request.getCompanyName()).orElseThrow();
        assertThat(connectorRepository.findByOrganizationAndToolKey(organization, "spip-agent")).isEmpty();
    }
}
