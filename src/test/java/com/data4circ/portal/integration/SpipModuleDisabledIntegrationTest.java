package com.data4circ.portal.integration;

import com.data4circ.portal.features.connectors.entity.Connector;
import com.data4circ.portal.features.connectors.repository.ConnectorRepository;
import com.data4circ.portal.features.onboardingsync.ckan.CkanOnboardingSyncService;
import com.data4circ.portal.features.onboardingsync.ckan.CkanSyncResult;
import com.data4circ.portal.features.onboardingsync.keycloak.KeycloakInitializationResult;
import com.data4circ.portal.features.onboardingsync.keycloak.KeycloakOnboardingService;
import com.data4circ.portal.features.onboardingsync.keycloak.KeycloakOnboardingToolProvisioner;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolProvisioner;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolRegistry;
import com.data4circ.portal.features.organization.entity.CompanySize;
import com.data4circ.portal.features.organization.entity.OnboardingRequestStatus;
import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;
import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.organization.entity.UserRole;
import com.data4circ.portal.features.organization.repository.OnboardingRequestRepository;
import com.data4circ.portal.features.organization.repository.OrganizationRepository;
import com.data4circ.portal.features.organization.repository.UserRepository;
import com.data4circ.portal.features.organization.service.EmailService;
import com.data4circ.portal.features.organization.service.OnboardingRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Milestone 1, step 6 verification (SPIP-PLUGIN-DECOUPLING-PLAN.md): boots the whole
 * application with {@code app.modules.spip.enabled=false} and proves the SPIP module's
 * absence is structural, not just "nothing calls it" — the tool isn't in the registry,
 * its URLs aren't mapped, and CKAN + Keycloak onboard and approve an organization
 * completely independently of it, with no phantom SPIP connector left behind.
 *
 * <p>Deliberately imports no SPIP class: everything here is observable from core alone,
 * which is the property this test exists to pin down. (SPIP itself already has thorough
 * coverage with the module enabled — see e.g. {@code OnboardingToolFlowIntegrationTest}.)</p>
 */
@SpringBootTest(properties = "app.modules.spip.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SpipModuleDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OnboardingRequestService onboardingRequestService;

    @Autowired
    private OnboardingRequestRepository onboardingRequestRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OnboardingToolRegistry toolRegistry;

    @MockBean
    private CkanOnboardingSyncService ckanOnboardingSyncService;

    @MockBean
    private KeycloakOnboardingService keycloakOnboardingService;

    @MockBean
    private EmailService emailService;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Test
    void spipIsAbsentFromTheOnboardingToolRegistry() {
        assertThat(toolRegistry.get("spip")).isEmpty();
        assertThat(toolRegistry.identityProvider()).isEmpty();
        assertThat(toolRegistry.enabledTools())
                .extracting(OnboardingToolProvisioner::getKey)
                .doesNotContain("spip")
                .contains("ckan", "keycloak");
    }

    @Test
    void spipUrlsAreNotMapped() throws Exception {
        User admin = saveAdmin("spip-404-admin");

        // No controller bean exists for /spip (SpipController is conditional), so
        // Spring's dispatcher never finds a handler for it — asserted directly via the
        // resolved exception rather than the response status, since this app's generic
        // Exception handler (GlobalExceptionHandler, unrelated to SPIP) renders every
        // unmapped URL as an HTML error page without setting a non-200 status.
        mockMvc.perform(get("/spip")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(admin))))
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(NoResourceFoundException.class));
    }

    @Test
    void synchronizingSpipIsRejectedAsUnknown() {
        OrganizationOnboardingRequest request = savedRequest();

        assertThatThrownBy(() -> onboardingRequestService.synchronizeTool(request.getId(), "spip", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown or disabled onboarding tool: spip");
    }

    @Test
    void ckanAndKeycloakOnboardAndApproveIndependentlyOfSpip() {
        mockCkanSuccess();
        mockKeycloakSuccess();

        OrganizationOnboardingRequest request = savedRequest();

        // CKAN has no identity provider to depend on: it provisions its own account
        // instead of reusing SPIP credentials (CkanOnboardingToolProvisioner).
        onboardingRequestService.synchronizeTool(request.getId(), "ckan",
                Map.of("ckanOrgShortName", "no-spip-org", "ckanUsername", "nospipuser"));

        // Keycloak's group name becomes an editable admin input instead of the
        // (nonexistent) SPIP username (KeycloakOnboardingToolProvisioner).
        onboardingRequestService.synchronizeTool(request.getId(), "keycloak", Map.of(
                KeycloakOnboardingToolProvisioner.BASE_URL_PARAM, "http://localhost:8081",
                KeycloakOnboardingToolProvisioner.REALM_PARAM, "data4circ",
                KeycloakOnboardingToolProvisioner.CLIENT_ID_PARAM, "portal-admin",
                KeycloakOnboardingToolProvisioner.CLIENT_SECRET_PARAM, "test-secret",
                KeycloakOnboardingToolProvisioner.GROUP_NAME_PARAM, "no-spip-group",
                KeycloakOnboardingToolProvisioner.USERNAME_PARAM, request.getEmail()));

        OrganizationOnboardingRequest approved =
                onboardingRequestService.approveRequest(request.getId(), "admin", "ok without spip");

        assertThat(approved.getStatus()).isEqualTo(OnboardingRequestStatus.ORGANIZATION_CREATED);

        var organization = organizationRepository.findByName(request.getCompanyName()).orElseThrow();
        assertThat(connectorRepository.findByOrganizationAndToolKey(organization, "ckan")).isPresent();
        assertThat(connectorRepository.findByOrganizationAndToolKey(organization, "keycloak")).isPresent();

        // No phantom SPIP connector: "spip" has no provisioner while the module is off,
        // which (absent the app.modules.spip.enabled cascade in application.yml) would
        // otherwise be indistinguishable from a genuinely config-only tool like EDC and
        // get created unconditionally — see OnboardingToolConnectorService
        // #materializeConfigOnlyConnectors.
        assertThat(connectorRepository.findByOrganizationAndToolKey(organization, "spip")).isEmpty();
    }

    private void mockCkanSuccess() {
        CkanSyncResult result = new CkanSyncResult();
        result.setSuccess(true);
        result.setApiToken("no-spip-token");
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

    private UsernamePasswordAuthenticationToken auth(User user) {
        return new UsernamePasswordAuthenticationToken(user, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
    }

    private User saveAdmin(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFirstName("No");
        user.setLastName("Spip");
        user.setPassword("$2a$10$test");
        user.setRole(UserRole.PLATFORM_ADMIN);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private OrganizationOnboardingRequest savedRequest() {
        return onboardingRequestRepository.save(newRequest());
    }

    private OrganizationOnboardingRequest newRequest() {
        int id = SEQUENCE.incrementAndGet();
        OrganizationOnboardingRequest request = new OrganizationOnboardingRequest();
        request.setCompanyName("No SPIP Test Co " + id);
        request.setCompanyType("manufacturer");
        request.setIndustry("METALS");
        request.setCompanySize(CompanySize.MEDIUM);
        request.setOrganizationDescription("Test description");
        request.setContactName("Jane Roe");
        request.setContactTitle("CTO");
        request.setEmail("nospip" + id + "@testcompany.example.com");
        request.setPhone("+1234567890");
        request.setParticipationGoals("Test goals");
        request.setGdprConsent(true);
        request.setStatus(OnboardingRequestStatus.PENDING);
        return request;
    }
}
