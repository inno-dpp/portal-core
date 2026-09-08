//package com.data4circ.portal.service;
//
//import com.data4circ.portal.dto.OnboardingRequestDTO;
//import com.data4circ.portal.dto.spip.SpipInitializationResult;
//import com.data4circ.portal.entity.*;
//import com.data4circ.portal.exception.ValidationException;
//import com.data4circ.portal.integration.spip.SpipApiClient;
//import com.data4circ.portal.repository.OnboardingRequestRepository;
//import com.data4circ.portal.repository.OrganizationRepository;
//import com.data4circ.portal.repository.UserRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.util.List;
//import java.util.Optional;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
///**
// * Unit tests for OnboardingRequestService
// *
// * These tests focus on business logic validation and error handling
// * using mocked dependencies.
// */
//@ExtendWith(MockitoExtension.class)
//class OnboardingRequestServiceTest {
//
//    @Mock
//    private OnboardingRequestRepository onboardingRequestRepository;
//
//    @Mock
//    private OrganizationRepository organizationRepository;
//
//    @Mock
//    private UserRepository userRepository;
//
//    @Mock
//    private EmailService emailService;
//
//    @Mock
//    private OrganizationService organizationService;
//
//    @Mock
//    private UserService userService;
//
//    @Mock
//    private OrganizationSpipUserService organizationSpipUserService;
//
//    @Mock
//    private SpipOnboardingService spipIntegrationService;
//
//    @Mock
//    private SpipApiClient spipApiClient;
//
//    @InjectMocks
//    private OnboardingRequestService onboardingRequestService;
//
//    private OnboardingRequestDTO validRequestDTO;
//    private OrganizationOnboardingRequest savedRequest;
//
//    @BeforeEach
//    void setUp() {
//        // Setup valid test data
//        validRequestDTO = createValidRequestDTO();
//        savedRequest = createSavedRequest();
//    }
//
//    @Test
//    void testCreateOnboardingRequest_Success() {
//        // Arrange
//        when(onboardingRequestRepository.existsByEmail(anyString())).thenReturn(false);
//        when(onboardingRequestRepository.existsByCompanyName(anyString())).thenReturn(false);
//        when(organizationRepository.existsByName(anyString())).thenReturn(false);
//        when(onboardingRequestRepository.save(any(OrganizationOnboardingRequest.class)))
//                .thenReturn(savedRequest);
//        doNothing().when(emailService).sendOnboardingConfirmation(anyString(), anyString());
//
//        // Act
//        OrganizationOnboardingRequest result = onboardingRequestService.createOnboardingRequest(validRequestDTO);
//
//        // Assert
//        assertThat(result).isNotNull();
//        assertThat(result.getStatus()).isEqualTo(OnboardingRequestStatus.PENDING);
//        assertThat(result.getSpipSynchronized()).isFalse();
//
//        verify(onboardingRequestRepository, times(1)).save(any(OrganizationOnboardingRequest.class));
//        verify(emailService, times(1)).sendOnboardingConfirmation(
//                eq(validRequestDTO.getEmail()),
//                eq(validRequestDTO.getContactName())
//        );
//    }
//
//    @Test
//    void testCreateOnboardingRequest_DuplicateEmail() {
//        // Arrange
//        when(onboardingRequestRepository.existsByEmail(validRequestDTO.getEmail())).thenReturn(true);
//
//        // Act & Assert
//        assertThatThrownBy(() -> onboardingRequestService.createOnboardingRequest(validRequestDTO))
//                .isInstanceOf(ValidationException.class)
//                .hasMessageContaining("email");
//
//        verify(onboardingRequestRepository, never()).save(any());
//        verify(emailService, never()).sendOnboardingConfirmation(anyString(), anyString());
//    }
//
//    @Test
//    void testCreateOnboardingRequest_DuplicateCompanyName() {
//        // Arrange
//        when(onboardingRequestRepository.existsByEmail(anyString())).thenReturn(false);
//        when(onboardingRequestRepository.existsByCompanyName(validRequestDTO.getCompanyName())).thenReturn(true);
//
//        // Act & Assert
//        assertThatThrownBy(() -> onboardingRequestService.createOnboardingRequest(validRequestDTO))
//                .isInstanceOf(ValidationException.class)
//                .hasMessageContaining("company name");
//
//        verify(onboardingRequestRepository, never()).save(any());
//        verify(emailService, never()).sendOnboardingConfirmation(anyString(), anyString());
//    }
//
//    @Test
//    void testCreateOnboardingRequest_OrganizationAlreadyExists() {
//        // Arrange
//        when(onboardingRequestRepository.existsByEmail(anyString())).thenReturn(false);
//        when(onboardingRequestRepository.existsByCompanyName(anyString())).thenReturn(false);
//        when(organizationRepository.existsByName(validRequestDTO.getCompanyName())).thenReturn(true);
//
//        // Act & Assert
//        assertThatThrownBy(() -> onboardingRequestService.createOnboardingRequest(validRequestDTO))
//                .isInstanceOf(ValidationException.class)
//                .hasMessageContaining("already exists");
//
//        verify(onboardingRequestRepository, never()).save(any());
//        verify(emailService, never()).sendOnboardingConfirmation(anyString(), anyString());
//    }
//
//    @Test
//    void testSynchronizeWithSpip_Success() {
//        // Arrange
//        OrganizationOnboardingRequest unsyncedRequest = createSavedRequest();
//        unsyncedRequest.setSpipSynchronized(false);
//
//        SpipInitializationResult mockResult = createMockSpipResult();
//
//        when(onboardingRequestRepository.findById(1L)).thenReturn(Optional.of(unsyncedRequest));
//        when(spipApiClient.initializeOrganization(any())).thenReturn(mockResult);
//        when(onboardingRequestRepository.save(any(OrganizationOnboardingRequest.class)))
//                .thenAnswer(invocation -> invocation.getArgument(0));
//
//        // Act
//        onboardingRequestService.synchronizeWithSpip(1L);
//
//        // Assert
//        verify(onboardingRequestRepository, times(1)).save(argThat(request -> {
//            assertThat(request.getSpipSynchronized()).isTrue();
//            assertThat(request.getSpipUser()).isNotNull();
//            assertThat(request.getSpipPassword()).isNotNull();
//            assertThat(request.getInitializationSetup()).isNotNull();
//            return true;
//        }));
//    }
//
//    @Test
//    void testSynchronizeWithSpip_AlreadySynchronized() {
//        // Arrange
//        savedRequest.setSpipSynchronized(true);
//        when(onboardingRequestRepository.findById(1L)).thenReturn(Optional.of(savedRequest));
//
//        // Act & Assert
//        assertThatThrownBy(() -> onboardingRequestService.synchronizeWithSpip(1L))
//                .isInstanceOf(ValidationException.class)
//                .hasMessageContaining("already synchronized");
//
//        verify(spipApiClient, never()).initializeOrganization(any());
//        verify(onboardingRequestRepository, never()).save(any());
//    }
//
//    @Test
//    void testSynchronizeWithSpip_InvalidStatus() {
//        // Arrange
//        savedRequest.setStatus(OnboardingRequestStatus.ORGANIZATION_CREATED);
//        when(onboardingRequestRepository.findById(1L)).thenReturn(Optional.of(savedRequest));
//
//        // Act & Assert
//        assertThatThrownBy(() -> onboardingRequestService.synchronizeWithSpip(1L))
//                .isInstanceOf(ValidationException.class)
//                .hasMessageContaining("status");
//
//        verify(spipApiClient, never()).initializeOrganization(any());
//    }
//
//    @Test
//    void testApproveRequest_WithoutSpipSync() {
//        // Arrange
//        savedRequest.setSpipSynchronized(false);
//        when(onboardingRequestRepository.findById(1L)).thenReturn(Optional.of(savedRequest));
//
//        // Act & Assert
//        assertThatThrownBy(() -> onboardingRequestService.approveRequest(1L, "admin"))
//                .isInstanceOf(ValidationException.class)
//                .hasMessageContaining("SPIP");
//
//        verify(organizationService, never()).createOrganization(any());
//        verify(userService, never()).createUser(any());
//    }
//
//    @Test
//    void testApproveRequest_InvalidStatus() {
//        // Arrange
//        savedRequest.setStatus(OnboardingRequestStatus.ORGANIZATION_CREATED);
//        savedRequest.setSpipSynchronized(true);
//        when(onboardingRequestRepository.findById(1L)).thenReturn(Optional.of(savedRequest));
//
//        // Act & Assert
//        assertThatThrownBy(() -> onboardingRequestService.approveRequest(1L, "admin"))
//                .isInstanceOf(ValidationException.class)
//                .hasMessageContaining("status");
//
//        verify(organizationService, never()).createOrganization(any());
//        verify(userService, never()).createUser(any());
//    }
//
//    @Test
//    void testApproveRequest_DuplicateOrganizationExists() {
//        // Arrange
//        savedRequest.setSpipSynchronized(true);
//        savedRequest.setStatus(OnboardingRequestStatus.PENDING);
//        when(onboardingRequestRepository.findById(1L)).thenReturn(Optional.of(savedRequest));
//        when(organizationRepository.existsByName(savedRequest.getCompanyName())).thenReturn(true);
//
//        // Act & Assert
//        assertThatThrownBy(() -> onboardingRequestService.approveRequest(1L, "admin"))
//                .isInstanceOf(ValidationException.class)
//                .hasMessageContaining("organization");
//
//        verify(organizationService, never()).createOrganization(any());
//        verify(userService, never()).createUser(any());
//    }
//
//    @Test
//    void testApproveRequest_DuplicateUserExists() {
//        // Arrange
//        savedRequest.setSpipSynchronized(true);
//        savedRequest.setStatus(OnboardingRequestStatus.PENDING);
//        when(onboardingRequestRepository.findById(1L)).thenReturn(Optional.of(savedRequest));
//        when(organizationRepository.existsByName(anyString())).thenReturn(false);
//        when(userRepository.existsByEmail(savedRequest.getEmail())).thenReturn(true);
//
//        // Act & Assert
//        assertThatThrownBy(() -> onboardingRequestService.approveRequest(1L, "admin"))
//                .isInstanceOf(ValidationException.class)
//                .hasMessageContaining("user");
//
//        verify(organizationService, never()).createOrganization(any());
//        verify(userService, never()).createUser(any());
//    }
//
//    @Test
//    void testRejectRequest_WithoutSpipSync() {
//        // Arrange
//        savedRequest.setSpipSynchronized(false);
//        when(onboardingRequestRepository.findById(1L)).thenReturn(Optional.of(savedRequest));
//
//        // Act & Assert
//        assertThatThrownBy(() -> onboardingRequestService.rejectRequest(1L, "admin", "Not qualified"))
//                .isInstanceOf(ValidationException.class)
//                .hasMessageContaining("SPIP");
//
//        verify(emailService, never()).sendOnboardingRejection(anyString(), anyString(), anyString());
//    }
//
//    @Test
//    void testRejectRequest_InvalidStatus() {
//        // Arrange
//        savedRequest.setStatus(OnboardingRequestStatus.REJECTED);
//        savedRequest.setSpipSynchronized(true);
//        when(onboardingRequestRepository.findById(1L)).thenReturn(Optional.of(savedRequest));
//
//        // Act & Assert
//        assertThatThrownBy(() -> onboardingRequestService.rejectRequest(1L, "admin", "Reason"))
//                .isInstanceOf(ValidationException.class)
//                .hasMessageContaining("status");
//
//        verify(emailService, never()).sendOnboardingRejection(anyString(), anyString(), anyString());
//    }
//
//    @Test
//    void testGetPendingRequests() {
//        // Arrange
//        List<OrganizationOnboardingRequest> pendingRequests = List.of(savedRequest);
//        when(onboardingRequestRepository.findByStatus(OnboardingRequestStatus.PENDING))
//                .thenReturn(pendingRequests);
//
//        // Act
//        List<OrganizationOnboardingRequest> result = onboardingRequestService.getPendingRequests();
//
//        // Assert
//        assertThat(result).hasSize(1);
//        assertThat(result.get(0).getStatus()).isEqualTo(OnboardingRequestStatus.PENDING);
//    }
//
//    @Test
//    void testSpipUsernameGeneration() {
//        // Test various company names to ensure username generation works correctly
//        String[] companyNames = {
//                "CircularTech Manufacturing Ltd",
//                "A Very Long Company Name That Exceeds Thirty Characters Limit",
//                "Company-With-Dashes",
//                "Company With Spaces",
//                "123 Numeric Start Company"
//        };
//
//        for (String companyName : companyNames) {
//            validRequestDTO.setCompanyName(companyName);
//
//            when(onboardingRequestRepository.existsByEmail(anyString())).thenReturn(false);
//            when(onboardingRequestRepository.existsByCompanyName(anyString())).thenReturn(false);
//            when(organizationRepository.existsByName(anyString())).thenReturn(false);
//
//            OrganizationOnboardingRequest mockRequest = new OrganizationOnboardingRequest();
//            mockRequest.setCompanyName(companyName);
//            mockRequest.setStatus(OnboardingRequestStatus.PENDING);
//
//            when(onboardingRequestRepository.save(any())).thenReturn(mockRequest);
//            doNothing().when(emailService).sendOnboardingConfirmation(anyString(), anyString());
//
//            OrganizationOnboardingRequest result = onboardingRequestService.createOnboardingRequest(validRequestDTO);
//
//            // Verify username constraints would be met during sync
//            // (actual username generation happens in synchronizeWithSpip)
//            assertThat(result.getCompanyName()).isEqualTo(companyName);
//        }
//    }
//
//    // ==================== Helper Methods ====================
//
//    private OnboardingRequestDTO createValidRequestDTO() {
//        OnboardingRequestDTO dto = new OnboardingRequestDTO();
//        dto.setCompanyName("Test Company Ltd");
//        dto.setCompanyType("manufacturer");
//        dto.setIndustry("electronics");
//        dto.setCompanySize("MEDIUM");
//        dto.setOrganizationDescription("Test description");
//        dto.setContactName("John Doe");
//        dto.setContactTitle("CEO");
//        dto.setEmail("john@testcompany.com");
//        dto.setPhone("+1234567890");
//        dto.setParticipationGoals("Test goals");
//        dto.setGdprConsent(true);
//        return dto;
//    }
//
//    private OrganizationOnboardingRequest createSavedRequest() {
//        OrganizationOnboardingRequest request = new OrganizationOnboardingRequest();
//        request.setId(1L);
//        request.setCompanyName("Test Company Ltd");
//        request.setCompanyType("manufacturer");
//        request.setIndustry("electronics");
//        request.setCompanySize(CompanySize.MEDIUM);
//        request.setOrganizationDescription("Test description");
//        request.setContactName("John Doe");
//        request.setContactTitle("CEO");
//        request.setEmail("john@testcompany.com");
//        request.setPhone("+1234567890");
//        request.setParticipationGoals("Test goals");
//        request.setGdprConsent(true);
//        request.setStatus(OnboardingRequestStatus.PENDING);
//        request.setSpipSynchronized(false);
//        return request;
//    }
//
//    private SpipInitializationResult createMockSpipResult() {
//        SpipInitializationResult result = new SpipInitializationResult();
//        result.setSuccess(true);
//        result.setUserId("spip-user-123");
//        result.setMessage("Success");
//        result.setAttributes(List.of("attr1", "attr2"));
//        result.setPolicies(List.of("policy1", "policy2"));
//        return result;
//    }
//}
