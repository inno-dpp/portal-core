package com.data4circ.portal.integration;

import com.data4circ.portal.features.organization.entity.*;
import com.data4circ.portal.features.onboardingsync.ckan.CkanOnboardingSyncService;
import com.data4circ.portal.features.onboardingsync.tool.entity.OnboardingToolSync;
import com.data4circ.portal.features.onboardingsync.tool.repository.OnboardingToolSyncRepository;
import com.data4circ.portal.features.organization.dto.OnboardingRequestDTO;
import com.data4circ.portal.features.organization.repository.OnboardingRequestRepository;
import com.data4circ.portal.features.organization.repository.OrganizationRepository;
import com.data4circ.portal.features.organization.repository.UserRepository;
import com.data4circ.portal.features.organization.service.EmailService;
import com.data4circ.portal.features.organization.entity.OrganizationSpipUser;
import com.data4circ.portal.features.organization.repository.OrganizationSpipUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for the complete organization registration flow.
 *
 * This test validates the entire onboarding process:
 * 1. User submits join form
 * 2. Onboarding request is created in database
 * 3. Admin synchronizes with SPIP
 * 4. Admin synchronizes with CKAN
 * 5. Admin approves request
 * 6. Organization and user are created
 *
 * Uses real database (H2 in-memory) and mocked external services (Email, SPIP API, CKAN API).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrganizationRegistrationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OnboardingRequestRepository onboardingRequestRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationSpipUserRepository organizationSpipUserRepository;

    @Autowired
    private OnboardingToolSyncRepository onboardingToolSyncRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    // Mock external services to avoid real API calls
    @MockBean
    private EmailService emailService;

    @MockBean
    private CkanOnboardingSyncService ckanSyncService;

    private OnboardingRequestDTO testRequestDTO;

    @BeforeEach
    void setUp() {
        // Note: Database cleanup is handled automatically by @Transactional
        // which rolls back changes after each test

        // Create test onboarding request data
        testRequestDTO = createValidOnboardingRequest();

        // Create a platform admin user for testing approval flow (if not exists)
        if (userRepository.findByUsername("admin").isEmpty()) {
            User adminUser = new User();
            adminUser.setUsername("admin");
            adminUser.setEmail("admin@portal.com");
            adminUser.setFirstName("Test");
            adminUser.setLastName("Admin");
            adminUser.setPassword("$2a$10$test"); // Doesn't matter for test
            adminUser.setRole(UserRole.PLATFORM_ADMIN);
            adminUser.setEnabled(true);
            userRepository.save(adminUser);
        }

        // Mock email service (don't send real emails during tests)
        doNothing().when(emailService).sendOnboardingConfirmation(anyString(), anyString(), anyString(), anyLong(), any());
        doNothing().when(emailService).sendOnboardingApproval(
                any(com.data4circ.portal.features.organization.dto.OnboardingApprovalEmail.class));
    }

    @Test
    void testCompleteRegistrationFlow_Success() throws Exception {
        // ==================== STEP 1: User submits join form ====================
        String requestJson = objectMapper.writeValueAsString(testRequestDTO);

        MvcResult submitResult = mockMvc.perform(post("/api/onboarding/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Onboarding request submitted successfully"))
                .andExpect(jsonPath("$.requestId").exists())
                .andReturn();

        // Extract request ID from response
        String responseBody = submitResult.getResponse().getContentAsString();
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
        Long requestId = Long.valueOf(responseMap.get("requestId").toString());

        // Verify onboarding request was created in database
        OrganizationOnboardingRequest savedRequest = onboardingRequestRepository.findById(requestId)
                .orElseThrow(() -> new AssertionError("Request not found in database"));

        assertThat(savedRequest.getCompanyName()).isEqualTo("CircularTech Manufacturing Ltd");
        assertThat(savedRequest.getEmail()).isEqualTo("contact@circulartech.example.com");
        assertThat(savedRequest.getStatus()).isEqualTo(OnboardingRequestStatus.PENDING);
        assertThat(onboardingToolSyncRepository.findByRequestId(requestId)).isEmpty();

        // Verify confirmation email was sent
        verify(emailService, times(1)).sendOnboardingConfirmation(
                eq("contact@circulartech.example.com"),
                eq("CircularTech Manufacturing Ltd"),
                eq("Jane Smith"),
                eq(requestId),
                any()
        );

        // ==================== STEP 2: Manually prepare request for approval ====================
        // In a real scenario, admin would sync with SPIP and CKAN via UI
        // For this test, we'll manually set the flags to focus on the approval flow
        String mockSpipUsername = "circulartech_spip";
        String mockSpipPassword = Base64.getEncoder().encodeToString("test_password".getBytes());
        String mockCkanOrgName = "circulartech-manufacturing-ltd";

        // Update the request with SPIP/CKAN sync data (simulating successful sync)
        savedRequest.setSpipUser(mockSpipUsername);
        savedRequest.setSpipPassword(mockSpipPassword);
        savedRequest.setCkanOrganizationShortName(mockCkanOrgName);
        savedRequest = onboardingRequestRepository.save(savedRequest);
        markToolSynced(savedRequest, "spip");
        markToolSynced(savedRequest, "ckan");

        // ==================== STEP 3: Admin approves the request ====================
        // Load the actual admin user entity for authentication
        User adminUser = userRepository.findByUsername("admin").orElseThrow();

        // Create authentication with the actual User entity as principal
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        adminUser,
                        null,
                        java.util.Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
                );

        mockMvc.perform(post("/admin/onboarding/" + requestId + "/approve")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("adminNotes", "Approved - meets all requirements"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/onboarding/" + requestId));

        // Flush and clear to ensure all changes are persisted and refresh from database
        entityManager.flush();
        entityManager.clear();

        // ==================== STEP 4: Verify organization and user were created ====================
        // Verify organization was created
        Organization createdOrganization = organizationRepository.findByName("CircularTech Manufacturing Ltd")
                .orElseThrow(() -> new AssertionError("Organization was not created"));

        assertThat(createdOrganization.getName()).isEqualTo("CircularTech Manufacturing Ltd");
        assertThat(createdOrganization.getType()).isEqualTo(OrganizationType.MANUFACTURER);
        assertThat(createdOrganization.getIndustrySector()).isEqualTo(IndustrySector.ELECTRICAL_ELECTRONICS);
        assertThat(createdOrganization.getContactEmail()).isEqualTo("contact@circulartech.example.com");

        // Verify admin user was created for the organization
        User createdUser = userRepository.findByEmail("contact@circulartech.example.com")
                .orElseThrow(() -> new AssertionError("User was not created"));

        assertThat(createdUser.getFullName()).isEqualTo("Jane Smith");
        assertThat(createdUser.getEmail()).isEqualTo("contact@circulartech.example.com");
        assertThat(createdUser.getOrganization()).isEqualTo(createdOrganization);
        assertThat(createdUser.getRole()).isEqualTo(UserRole.ORG_ADMIN);

        // Verify SPIP credentials were linked
        OrganizationSpipUser spipUser = organizationSpipUserRepository.findByOrganization(createdOrganization)
                .orElseThrow(() -> new AssertionError("SPIP user was not created"));

        assertThat(spipUser.getSpipUser()).isEqualTo(mockSpipUsername);
        assertThat(spipUser.getSpipSynchronized()).isTrue();
        assertThat(spipUser.getCkanOrganizationName()).isEqualTo(mockCkanOrgName);
        assertThat(spipUser.getCkanSynchronized()).isTrue();

        // Verify onboarding request status is updated
        savedRequest = onboardingRequestRepository.findById(requestId).orElseThrow();
        assertThat(savedRequest.getStatus()).isEqualTo(OnboardingRequestStatus.ORGANIZATION_CREATED);

        // Verify approval email was sent with the expected payload
        org.mockito.ArgumentCaptor<com.data4circ.portal.features.organization.dto.OnboardingApprovalEmail> emailCaptor =
                org.mockito.ArgumentCaptor.forClass(com.data4circ.portal.features.organization.dto.OnboardingApprovalEmail.class);
        verify(emailService, times(1)).sendOnboardingApproval(emailCaptor.capture());
        assertThat(emailCaptor.getValue().getToEmail()).isEqualTo("contact@circulartech.example.com");
        assertThat(emailCaptor.getValue().getCompanyName()).isEqualTo("CircularTech Manufacturing Ltd");
        assertThat(emailCaptor.getValue().getContactName()).isEqualTo("Jane Smith");
        assertThat(emailCaptor.getValue().getSpipUsername()).isEqualTo(mockSpipUsername);
        assertThat(emailCaptor.getValue().getSetPasswordUrl()).isNotBlank();
    }

    @Test
    void testSubmitOnboardingRequest_ValidationFailure() throws Exception {
        // Create invalid request (missing required fields)
        OnboardingRequestDTO invalidRequest = new OnboardingRequestDTO();
        invalidRequest.setCompanyName(""); // Empty name
        invalidRequest.setEmail("invalid-email"); // Invalid email format
        invalidRequest.setGdprConsent(false); // Must be true

        String requestJson = objectMapper.writeValueAsString(invalidRequest);

        mockMvc.perform(post("/api/onboarding/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isNotEmpty());

        // Verify no request was created
        assertThat(onboardingRequestRepository.count()).isZero();

        // Verify no email was sent
        verify(emailService, never()).sendOnboardingConfirmation(anyString(), anyString(), anyString(), anyLong(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"PLATFORM_ADMIN"})
    void testApproveRequest_WithoutSpipSync_ShouldFail() throws Exception {
        // Create request without SPIP synchronization
        OrganizationOnboardingRequest request = createTestOnboardingRequest();
        request = onboardingRequestRepository.save(request);
        markToolSynced(request, "ckan");

        // Attempt to approve should fail
        mockMvc.perform(post("/admin/onboarding/" + request.getId() + "/approve")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("adminNotes", "Attempting to approve without SPIP sync"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        // Verify organization was NOT created
        assertThat(organizationRepository.findByName(request.getCompanyName())).isEmpty();

        // Verify request status unchanged
        OrganizationOnboardingRequest unchanged = onboardingRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(OnboardingRequestStatus.PENDING);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"PLATFORM_ADMIN"})
    void testApproveRequest_WithoutCkanSync_ShouldFail() throws Exception {
        // Create request without CKAN synchronization
        OrganizationOnboardingRequest request = createTestOnboardingRequest();
        request.setSpipUser("test_spip_user");
        request = onboardingRequestRepository.save(request);
        markToolSynced(request, "spip");

        // Attempt to approve should fail
        mockMvc.perform(post("/admin/onboarding/" + request.getId() + "/approve")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("adminNotes", "Attempting to approve without CKAN sync"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        // Verify organization was NOT created
        assertThat(organizationRepository.findByName(request.getCompanyName())).isEmpty();

        // Verify request status unchanged
        OrganizationOnboardingRequest unchanged = onboardingRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(OnboardingRequestStatus.PENDING);
    }

    // ==================== Helper Methods ====================

    /**
     * Insert a synced onboarding_tool_sync row, simulating a completed tool synchronization.
     */
    private void markToolSynced(OrganizationOnboardingRequest request, String toolKey) {
        OnboardingToolSync sync = new OnboardingToolSync();
        sync.setRequest(request);
        sync.setToolKey(toolKey);
        sync.setSynced(true);
        sync.setSyncedAt(java.time.LocalDateTime.now());
        onboardingToolSyncRepository.save(sync);
    }

    /**
     * Create a valid onboarding request DTO for testing
     */
    private OnboardingRequestDTO createValidOnboardingRequest() {
        OnboardingRequestDTO dto = new OnboardingRequestDTO();
        dto.setCompanyName("CircularTech Manufacturing Ltd");
        dto.setCompanyType("manufacturer");
        dto.setIndustry("ELECTRICAL_ELECTRONICS"); // Must match IndustrySector enum value
        dto.setCompanySize("MEDIUM");
        dto.setAddress("123 Innovation Drive, Tech Park, TP1 2AB, United Kingdom");
        dto.setOrganizationDescription("Leading manufacturer of circular economy solutions for electronics");
        dto.setWebsite("https://www.circulartech.example.com");
        dto.setContactName("Jane Smith");
        dto.setContactTitle("Chief Sustainability Officer");
        dto.setEmail("contact@circulartech.example.com");
        dto.setPhone("+44 20 1234 5678");
        dto.setParticipationGoals("Share recycling data and collaborate on circular supply chains");
        dto.setDataTypes("Product lifecycle data, material composition, recycling rates");
        dto.setDataNeeds("Supplier sustainability metrics, end-of-life processing capabilities");
        dto.setCurrentSystems("SAP ERP, custom sustainability dashboard");
        dto.setGdprConsent(true);
        dto.setNaceCodes(List.of("96.99"));
        return dto;
    }

    /**
     * Create a test onboarding request entity
     */
    private OrganizationOnboardingRequest createTestOnboardingRequest() {
        OrganizationOnboardingRequest request = new OrganizationOnboardingRequest();
        request.setCompanyName("Test Company Ltd");
        request.setCompanyType("manufacturer");
        request.setIndustry("METALS"); // Must match IndustrySector enum value
        request.setCompanySize(CompanySize.MEDIUM);
        request.setOrganizationDescription("Test description");
        request.setContactName("John Doe");
        request.setContactTitle("CEO");
        request.setEmail("john@testcompany.com");
        request.setPhone("+1234567890");
        request.setParticipationGoals("Test goals");
        request.setGdprConsent(true);
        request.setStatus(OnboardingRequestStatus.PENDING);
        return request;
    }
}
