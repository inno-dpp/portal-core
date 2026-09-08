package com.data4circ.portal.features.organization.service;

import com.data4circ.portal.common.util.SpipPolicyLabelUtil;
import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;
import com.data4circ.portal.features.organization.repository.OnboardingRejectionRepository;
import com.data4circ.portal.features.organization.repository.OnboardingRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OnboardingRequestService#createOnboardingRequest} and the
 * {@link OnboardingRequestService#validateForCreation} checks it shares with the admin
 * "create organization from scratch" path in {@code OrganizationController}. The focus is the
 * SPIP access-policy label collision check: two organizations whose company names normalize
 * (see {@link SpipPolicyLabelUtil}) to the same first {@value SpipPolicyLabelUtil#MAX_NAME_LENGTH}
 * characters would otherwise end up sharing one SPIP policy label.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OnboardingRequestServiceTest {

    @Mock
    private OnboardingRequestRepository onboardingRequestRepository;

    @Mock
    private OnboardingRejectionRepository onboardingRejectionRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private EmailService emailService;

    @Mock
    private com.data4circ.portal.features.notification.service.NotificationService notificationService;

    @InjectMocks
    private OnboardingRequestService onboardingRequestService;

    // 26 identical leading characters (SpipPolicyLabelUtil.MAX_NAME_LENGTH), each pair
    // distinguished only after that point so the sanitized-and-capped prefixes collide.
    private static final String SHARED_PREFIX = "a".repeat(SpipPolicyLabelUtil.MAX_NAME_LENGTH);

    @Nested
    @DisplayName("SPIP policy label collision check")
    class CollisionCheck {

        @Test
        @DisplayName("rejects a new company colliding with an existing onboarding request's label")
        void rejectsCollisionWithExistingOnboardingRequest() {
            String existingName = SHARED_PREFIX + "_existing_co";
            String newName = SHARED_PREFIX + "_newco";
            assertThat(SpipPolicyLabelUtil.sanitizeAndCapName(existingName))
                    .isEqualTo(SpipPolicyLabelUtil.sanitizeAndCapName(newName));

            mockNoDuplicates(newName);
            when(onboardingRequestRepository.findAllCompanyNames()).thenReturn(List.of(existingName));
            when(organizationService.findAllNames()).thenReturn(Collections.emptyList());

            OrganizationOnboardingRequest request = requestWithCompanyName(newName);

            assertThatThrownBy(() -> onboardingRequestService.createOnboardingRequest(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("too similar");

            verify(onboardingRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects a new company colliding with an existing organization's label")
        void rejectsCollisionWithExistingOrganization() {
            String existingName = SHARED_PREFIX + "_existing_org";
            String newName = SHARED_PREFIX + "_newco";
            assertThat(SpipPolicyLabelUtil.sanitizeAndCapName(existingName))
                    .isEqualTo(SpipPolicyLabelUtil.sanitizeAndCapName(newName));

            mockNoDuplicates(newName);
            when(onboardingRequestRepository.findAllCompanyNames()).thenReturn(Collections.emptyList());
            when(organizationService.findAllNames()).thenReturn(List.of(existingName));

            OrganizationOnboardingRequest request = requestWithCompanyName(newName);

            assertThatThrownBy(() -> onboardingRequestService.createOnboardingRequest(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("too similar");

            verify(onboardingRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("allows two company names that stay distinct within the capped prefix")
        void allowsNonCollidingCompanyNames() {
            String existingName = "b".repeat(SpipPolicyLabelUtil.MAX_NAME_LENGTH) + "_existing_co";
            String newName = SHARED_PREFIX + "_newco";
            assertThat(SpipPolicyLabelUtil.sanitizeAndCapName(existingName))
                    .isNotEqualTo(SpipPolicyLabelUtil.sanitizeAndCapName(newName));

            mockNoDuplicates(newName);
            when(onboardingRequestRepository.findAllCompanyNames()).thenReturn(List.of(existingName));
            when(organizationService.findAllNames()).thenReturn(Collections.emptyList());
            when(onboardingRequestRepository.save(any(OrganizationOnboardingRequest.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            OrganizationOnboardingRequest request = requestWithCompanyName(newName);

            onboardingRequestService.createOnboardingRequest(request);

            verify(onboardingRequestRepository).save(request);
        }

        private void mockNoDuplicates(String companyName) {
            when(onboardingRequestRepository.existsByEmail(anyString())).thenReturn(false);
            when(onboardingRequestRepository.existsByCompanyName(companyName)).thenReturn(false);
            when(organizationService.existsByName(companyName)).thenReturn(false);
        }

        private OrganizationOnboardingRequest requestWithCompanyName(String companyName) {
            OrganizationOnboardingRequest request = new OrganizationOnboardingRequest();
            request.setCompanyName(companyName);
            request.setEmail("contact@" + companyName.replaceAll("[^a-zA-Z0-9]", "") + ".example.com");
            return request;
        }
    }

    /**
     * {@code validateForCreation} is what {@code OrganizationController}'s admin
     * "create organization from scratch" flow calls before saving an
     * {@link OrganizationOnboardingRequest} directly (bypassing {@code createOnboardingRequest}),
     * so it must independently enforce every check - not just the collision one covered above.
     */
    @Nested
    @DisplayName("validateForCreation - shared checks used by the admin-created-organization path")
    class ValidateForCreation {

        @Test
        @DisplayName("rejects a duplicate email")
        void rejectsDuplicateEmail() {
            when(onboardingRequestRepository.existsByEmail("dup@example.com")).thenReturn(true);

            OrganizationOnboardingRequest request = new OrganizationOnboardingRequest();
            request.setEmail("dup@example.com");
            request.setCompanyName("Some Company");

            assertThatThrownBy(() -> onboardingRequestService.validateForCreation(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("email");
        }

        @Test
        @DisplayName("rejects a company name already used by another onboarding request")
        void rejectsDuplicateOnboardingRequestCompanyName() {
            when(onboardingRequestRepository.existsByEmail(anyString())).thenReturn(false);
            when(onboardingRequestRepository.existsByCompanyName("Acme Corp")).thenReturn(true);

            OrganizationOnboardingRequest request = new OrganizationOnboardingRequest();
            request.setEmail("contact@acme.example.com");
            request.setCompanyName("Acme Corp");

            assertThatThrownBy(() -> onboardingRequestService.validateForCreation(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("company name");
        }

        @Test
        @DisplayName("rejects a company name already used by an existing organization")
        void rejectsDuplicateOrganizationName() {
            when(onboardingRequestRepository.existsByEmail(anyString())).thenReturn(false);
            when(onboardingRequestRepository.existsByCompanyName(anyString())).thenReturn(false);
            when(organizationService.existsByName("Acme Corp")).thenReturn(true);

            OrganizationOnboardingRequest request = new OrganizationOnboardingRequest();
            request.setEmail("contact@acme.example.com");
            request.setCompanyName("Acme Corp");

            assertThatThrownBy(() -> onboardingRequestService.validateForCreation(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("organization");
        }

        @Test
        @DisplayName("rejects a 3-digit NACE code")
        void rejectsNonClassNaceCode() {
            when(onboardingRequestRepository.existsByEmail(anyString())).thenReturn(false);
            when(onboardingRequestRepository.existsByCompanyName(anyString())).thenReturn(false);
            when(organizationService.existsByName(anyString())).thenReturn(false);
            when(onboardingRequestRepository.findAllCompanyNames()).thenReturn(Collections.emptyList());
            when(organizationService.findAllNames()).thenReturn(Collections.emptyList());

            OrganizationOnboardingRequest request = new OrganizationOnboardingRequest();
            request.setEmail("contact@acme.example.com");
            request.setCompanyName("Acme Corp");
            request.setNaceCodes("01.1"); // 3-digit "group" code, not the required 4-digit "class" code

            assertThatThrownBy(() -> onboardingRequestService.validateForCreation(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("4-digit NACE class codes");
        }

        @Test
        @DisplayName("rejects a SPIP access-policy label collision")
        void rejectsLabelCollision() {
            String existingName = SHARED_PREFIX + "_existing_co";
            String newName = SHARED_PREFIX + "_newco";

            when(onboardingRequestRepository.existsByEmail(anyString())).thenReturn(false);
            when(onboardingRequestRepository.existsByCompanyName(newName)).thenReturn(false);
            when(organizationService.existsByName(newName)).thenReturn(false);
            when(onboardingRequestRepository.findAllCompanyNames()).thenReturn(List.of(existingName));
            when(organizationService.findAllNames()).thenReturn(Collections.emptyList());

            OrganizationOnboardingRequest request = new OrganizationOnboardingRequest();
            request.setEmail("contact@newco.example.com");
            request.setCompanyName(newName);

            assertThatThrownBy(() -> onboardingRequestService.validateForCreation(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("too similar");
        }

        @Test
        @DisplayName("passes a well-formed, non-colliding request without side effects")
        void passesValidRequest() {
            when(onboardingRequestRepository.existsByEmail(anyString())).thenReturn(false);
            when(onboardingRequestRepository.existsByCompanyName(anyString())).thenReturn(false);
            when(organizationService.existsByName(anyString())).thenReturn(false);
            when(onboardingRequestRepository.findAllCompanyNames()).thenReturn(Collections.emptyList());
            when(organizationService.findAllNames()).thenReturn(Collections.emptyList());

            OrganizationOnboardingRequest request = new OrganizationOnboardingRequest();
            request.setEmail("contact@acme.example.com");
            request.setCompanyName("Acme Corp");
            request.setNaceCodes("01.11");

            onboardingRequestService.validateForCreation(request);

            // Pure validation: no save, no email, no notification triggered by this method alone.
            verify(onboardingRequestRepository, never()).save(any());
        }
    }
}
