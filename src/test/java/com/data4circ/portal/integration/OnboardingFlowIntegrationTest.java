//package com.data4circ.portal.integration;
//
//import com.data4circ.portal.dto.OnboardingRequestDTO;
//import com.data4circ.portal.dto.spip.SpipInitializationResult;
//import com.data4circ.portal.entity.*;
//import com.data4circ.portal.repository.OnboardingRequestRepository;
//import com.data4circ.portal.repository.OrganizationRepository;
//import com.data4circ.portal.repository.OrganizationSpipUserRepository;
//import com.data4circ.portal.repository.UserRepository;
//import com.data4circ.portal.service.EmailService;
//import com.data4circ.portal.service.OnboardingRequestService;
//import com.data4circ.portal.integration.spip.SpipApiClient;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.http.MediaType;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.web.servlet.MockMvc;
//import org.springframework.test.web.servlet.MvcResult;
//import org.springframework.test.web.servlet.setup.MockMvcBuilders;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.web.context.WebApplicationContext;
//
//import java.util.Base64;
//import java.util.List;
//import java.util.Map;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
///**
// * Integration test for the complete onboarding flow from submission to approval-ready status.
// *
// * This test validates:
// * 1. Submission of onboarding request
// * 2. SPIP synchronization process
// * 3. Final state verification before approval
// */
//@SpringBootTest
//@ActiveProfiles("test")
//@Transactional
//class OnboardingFlowIntegrationTest {
//
//    @Autowired
//    private WebApplicationContext webApplicationContext;
//
//    @Autowired
//    private OnboardingRequestService onboardingRequestService;
//
//    @Autowired
//    private OnboardingRequestRepository onboardingRequestRepository;
//
//    @Autowired
//    private OrganizationRepository organizationRepository;
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Autowired
//    private OrganizationSpipUserRepository organizationSpipUserRepository;
//
//    @MockBean
//    private EmailService emailService;
//
//    @MockBean
//    private SpipApiClient spipApiClient;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    private MockMvc mockMvc;
//
//    @BeforeEach
//    void setUp() {
//        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
//
//        // Clear all data before each test
//        onboardingRequestRepository.deleteAll();
//        organizationSpipUserRepository.deleteAll();
//        organizationRepository.deleteAll();
//        userRepository.deleteAll();
//
//        // Mock email service to avoid actual email sending
//        doNothing().when(emailService).sendOnboardingConfirmation(any(), any());
//        doNothing().when(emailService).sendOnboardingApproval(any(), any(), any());
//    }
//
//    @Test
//    void testCompleteOnboardingFlowUntilApprovalRequired() throws Exception {
//        // ==================== PHASE 1: Submit Onboarding Request ====================
//
//        OnboardingRequestDTO requestDTO = createValidOnboardingRequestDTO();
//
//        String requestJson = objectMapper.writeValueAsString(requestDTO);
//
//        MvcResult result = mockMvc.perform(post("/api/onboarding/request")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(requestJson))
//                .andExpect(status().isOk())
//                .andReturn();
//
//        String responseBody = result.getResponse().getContentAsString();
//        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
//
//        // Verify response contains request ID
//        assertThat(response).containsKey("id");
//        assertThat(response).containsKey("message");
//        Long requestId = ((Number) response.get("id")).longValue();
//
//        // Verify entity was created in database
//        OrganizationOnboardingRequest savedRequest = onboardingRequestRepository.findById(requestId)
//                .orElseThrow(() -> new AssertionError("Onboarding request not found in database"));
//
//        // Verify all fields were saved correctly
//        assertThat(savedRequest.getCompanyName()).isEqualTo("CircularTech Manufacturing Ltd");
//        assertThat(savedRequest.getCompanyType()).isEqualTo("manufacturer");
//        assertThat(savedRequest.getIndustry()).isEqualTo("electronics");
//        assertThat(savedRequest.getCompanySize()).isEqualTo(CompanySize.MEDIUM);
//        assertThat(savedRequest.getOrganizationDescription()).isEqualTo("Leading electronics manufacturer focused on circular economy practices");
//        assertThat(savedRequest.getContactName()).isEqualTo("Jane Smith");
//        assertThat(savedRequest.getContactTitle()).isEqualTo("Operations Director");
//        assertThat(savedRequest.getEmail()).isEqualTo("jane.smith@circulartech.test");
//        assertThat(savedRequest.getPhone()).isEqualTo("+44-20-1234-5678");
//        assertThat(savedRequest.getParticipationGoals()).isEqualTo("Share manufacturing data to support circular economy initiatives and collaborate with recycling partners");
//        assertThat(savedRequest.getGdprConsent()).isTrue();
//
//        // Verify initial status
//        assertThat(savedRequest.getStatus()).isEqualTo(OnboardingRequestStatus.PENDING);
//        assertThat(savedRequest.getSpipSynchronized()).isFalse();
//        assertThat(savedRequest.getOrganization()).isNull();
//
//        // Verify confirmation email was sent
//        verify(emailService, times(1)).sendOnboardingConfirmation(
//                eq(savedRequest.getEmail()),
//                eq(savedRequest.getContactName())
//        );
//
//        // Verify no Organization or User entities created yet
//        assertThat(organizationRepository.count()).isEqualTo(0);
//        assertThat(userRepository.count()).isEqualTo(0);
//
//        System.out.println("✓ Phase 1 Complete: Onboarding request submitted successfully");
//        System.out.println("  - Request ID: " + requestId);
//        System.out.println("  - Status: " + savedRequest.getStatus());
//        System.out.println("  - Email confirmation sent to: " + savedRequest.getEmail());
//
//        // ==================== PHASE 2: SPIP Synchronization ====================
//
//        // Mock SPIP API responses
//        SpipInitializationResult mockSpipResult = createMockSpipInitializationResult();
//        when(spipApiClient.initializeOrganization(any())).thenReturn(mockSpipResult);
//
//        // Perform SPIP synchronization
//        onboardingRequestService.synchronizeWithSpip(requestId);
//
//        // Reload entity to get updated state
//        savedRequest = onboardingRequestRepository.findById(requestId)
//                .orElseThrow(() -> new AssertionError("Request not found after SPIP sync"));
//
//        // Verify SPIP synchronization completed
//        assertThat(savedRequest.getSpipSynchronized()).isTrue();
//        assertThat(savedRequest.getSpipUser()).isNotNull();
//        assertThat(savedRequest.getSpipPassword()).isNotNull();
//        assertThat(savedRequest.getInitializationSetup()).isNotNull();
//
//        // Verify SPIP username generation (sanitized organization name, max 30 chars)
//        assertThat(savedRequest.getSpipUser()).matches("[a-z0-9_]+");
//        assertThat(savedRequest.getSpipUser().length()).isLessThanOrEqualTo(30);
//        assertThat(savedRequest.getSpipUser()).contains("circulartech");
//
//        // Verify password is Base64 encoded
//        String decodedPassword = new String(Base64.getDecoder().decode(savedRequest.getSpipPassword()));
//        assertThat(decodedPassword).isNotEmpty();
//
//        // Verify initialization setup JSON is valid
//        assertThat(savedRequest.getInitializationSetup()).contains("userId");
//        assertThat(savedRequest.getInitializationSetup()).contains("attributes");
//
//        // Verify status is still PENDING (not yet approved)
//        assertThat(savedRequest.getStatus()).isEqualTo(OnboardingRequestStatus.PENDING);
//
//        // Verify no Organization or User created yet
//        assertThat(organizationRepository.count()).isEqualTo(0);
//        assertThat(userRepository.count()).isEqualTo(0);
//
//        System.out.println("✓ Phase 2 Complete: SPIP synchronization successful");
//        System.out.println("  - SPIP User: " + savedRequest.getSpipUser());
//        System.out.println("  - SPIP Synchronized: " + savedRequest.getSpipSynchronized());
//        System.out.println("  - Initialization setup stored: " + (savedRequest.getInitializationSetup() != null));
//
//        // ==================== PHASE 3: Verify Ready for Approval ====================
//
//        // Fetch final state
//        OrganizationOnboardingRequest finalRequest = onboardingRequestRepository.findById(requestId)
//                .orElseThrow(() -> new AssertionError("Request not found in final verification"));
//
//        // Verify all prerequisites for approval are met
//        assertThat(finalRequest.getStatus()).isEqualTo(OnboardingRequestStatus.PENDING);
//        assertThat(finalRequest.getSpipSynchronized()).isTrue();
//        assertThat(finalRequest.getSpipUser()).isNotNull();
//        assertThat(finalRequest.getSpipPassword()).isNotNull();
//        assertThat(finalRequest.getInitializationSetup()).isNotNull();
//
//        // Verify no premature entity creation
//        assertThat(finalRequest.getOrganization()).isNull();
//        assertThat(organizationRepository.findByName(finalRequest.getCompanyName())).isEmpty();
//        assertThat(userRepository.findByEmail(finalRequest.getEmail())).isEmpty();
//
//        // Verify the request is ready for admin approval action
//        // The following conditions must all be true:
//        // 1. Status = PENDING ✓
//        // 2. SPIP synchronized = true ✓
//        // 3. SPIP credentials stored ✓
//        // 4. No organization created yet ✓
//        // 5. No user created yet ✓
//
//        System.out.println("\n✓ Phase 3 Complete: Request ready for approval");
//        System.out.println("  - All prerequisites met for approval workflow");
//        System.out.println("  - Status: " + finalRequest.getStatus());
//        System.out.println("  - SPIP Synchronized: " + finalRequest.getSpipSynchronized());
//        System.out.println("  - Organization Created: NO (as expected)");
//        System.out.println("  - User Created: NO (as expected)");
//        System.out.println("\n=== ONBOARDING FLOW TEST SUCCESSFUL ===");
//        System.out.println("Request ID " + requestId + " is ready for admin approval in the UI");
//    }
//
//    @Test
//    void testOnboardingRequestWithMinimalRequiredFields() throws Exception {
//        // Test with only required fields (no optional fields)
//        OnboardingRequestDTO minimalDTO = new OnboardingRequestDTO();
//        minimalDTO.setCompanyName("Minimal Test Corp");
//        minimalDTO.setCompanyType("recycler");
//        minimalDTO.setIndustry("waste_management");
//        minimalDTO.setOrganizationDescription("Minimal test organization");
//        minimalDTO.setContactName("John Minimal");
//        minimalDTO.setContactTitle("Manager");
//        minimalDTO.setEmail("john@minimaltest.com");
//        minimalDTO.setParticipationGoals("Test minimal participation");
//        minimalDTO.setGdprConsent(true);
//
//        String requestJson = objectMapper.writeValueAsString(minimalDTO);
//
//        MvcResult result = mockMvc.perform(post("/api/onboarding/request")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(requestJson))
//                .andExpect(status().isOk())
//                .andReturn();
//
//        String responseBody = result.getResponse().getContentAsString();
//        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
//
//        Long requestId = ((Number) response.get("id")).longValue();
//        OrganizationOnboardingRequest savedRequest = onboardingRequestRepository.findById(requestId)
//                .orElseThrow();
//
//        assertThat(savedRequest.getStatus()).isEqualTo(OnboardingRequestStatus.PENDING);
//        assertThat(savedRequest.getCompanyName()).isEqualTo("Minimal Test Corp");
//        assertThat(savedRequest.getEmail()).isEqualTo("john@minimaltest.com");
//
//        System.out.println("✓ Minimal required fields test passed");
//    }
//
//    @Test
//    void testDuplicateEmailRejection() throws Exception {
//        // Submit first request
//        OnboardingRequestDTO firstRequest = createValidOnboardingRequestDTO();
//        String firstJson = objectMapper.writeValueAsString(firstRequest);
//
//        mockMvc.perform(post("/api/onboarding/request")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(firstJson))
//                .andExpect(status().isOk());
//
//        // Try to submit second request with same email
//        OnboardingRequestDTO duplicateRequest = createValidOnboardingRequestDTO();
//        duplicateRequest.setCompanyName("Different Company Name");
//        String duplicateJson = objectMapper.writeValueAsString(duplicateRequest);
//
//        mockMvc.perform(post("/api/onboarding/request")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(duplicateJson))
//                .andExpect(status().isBadRequest());
//
//        System.out.println("✓ Duplicate email rejection test passed");
//    }
//
//    @Test
//    void testDuplicateCompanyNameRejection() throws Exception {
//        // Submit first request
//        OnboardingRequestDTO firstRequest = createValidOnboardingRequestDTO();
//        String firstJson = objectMapper.writeValueAsString(firstRequest);
//
//        mockMvc.perform(post("/api/onboarding/request")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(firstJson))
//                .andExpect(status().isOk());
//
//        // Try to submit second request with same company name
//        OnboardingRequestDTO duplicateRequest = createValidOnboardingRequestDTO();
//        duplicateRequest.setEmail("different.email@example.com");
//        String duplicateJson = objectMapper.writeValueAsString(duplicateRequest);
//
//        mockMvc.perform(post("/api/onboarding/request")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(duplicateJson))
//                .andExpect(status().isBadRequest());
//
//        System.out.println("✓ Duplicate company name rejection test passed");
//    }
//
//    /**
//     * Creates a valid onboarding request DTO with all required and optional fields
//     */
//    private OnboardingRequestDTO createValidOnboardingRequestDTO() {
//        OnboardingRequestDTO dto = new OnboardingRequestDTO();
//
//        // Required company information
//        dto.setCompanyName("CircularTech Manufacturing Ltd");
//        dto.setCompanyType("manufacturer");
//        dto.setIndustry("electronics");
//        dto.setCompanySize("MEDIUM");
//        dto.setOrganizationDescription("Leading electronics manufacturer focused on circular economy practices");
//
//        // Required contact information
//        dto.setContactName("Jane Smith");
//        dto.setContactTitle("Operations Director");
//        dto.setEmail("jane.smith@circulartech.test");
//
//        // Optional contact details
//        dto.setPhone("+44-20-1234-5678");
//        dto.setWebsite("https://www.circulartech.test");
//        dto.setAddress("123 Innovation Park, London, UK, EC1A 1BB");
//
//        // Required participation information
//        dto.setParticipationGoals("Share manufacturing data to support circular economy initiatives and collaborate with recycling partners");
//
//        // Optional data information
//        dto.setDataTypes("Manufacturing process data, material composition, product lifecycle information");
//        dto.setDataNeeds("Recycling capacity data, material market prices, sustainability metrics");
//        dto.setCurrentSystems("ERP: SAP S/4HANA, MES: Siemens Opcenter, PLM: PTC Windchill");
//
//        // Required GDPR consent
//        dto.setGdprConsent(true);
//
//        return dto;
//    }
//
//    /**
//     * Creates a mock SPIP initialization result
//     */
//    private SpipInitializationResult createMockSpipInitializationResult() {
//        SpipInitializationResult result = new SpipInitializationResult();
//        result.setSuccess(true);
//        result.setUserId("spip-user-123");
//        result.setMessage("Organization initialized successfully in SPIP");
//
//        // Add mock attributes
//        result.setAttributes(List.of(
//            "attr_organization_name",
//            "attr_industry_sector",
//            "attr_certification_status"
//        ));
//
//        // Add mock policies
//        result.setPolicies(List.of(
//            "policy_data_provider",
//            "policy_data_consumer"
//        ));
//
//        return result;
//    }
//}
