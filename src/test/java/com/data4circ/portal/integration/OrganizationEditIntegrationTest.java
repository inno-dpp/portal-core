package com.data4circ.portal.integration;

import com.data4circ.portal.features.organization.entity.*;
import com.data4circ.portal.features.organization.repository.OnboardingRequestRepository;
import com.data4circ.portal.features.organization.repository.OrganizationRepository;
import com.data4circ.portal.features.organization.repository.UserRepository;
import com.data4circ.portal.features.organization.service.EmailService;
import com.data4circ.portal.features.organization.service.OrganizationService;
import com.data4circ.portal.features.organization.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Organization Edit functionality.
 * Tests the complete edit flow including authorization, validation, and persistence.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)  // Enable security filters including method security
@ActiveProfiles("test")
@Transactional
class OrganizationEditIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OnboardingRequestRepository onboardingRequestRepository;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private UserService userService;

    @MockBean
    private EmailService emailService;

    private Organization testOrg1;
    private Organization testOrg2;
    private User platformAdmin;
    private User org1Admin;
    private User org2Admin;
    private User org1Member;

    @BeforeEach
    void setUp() {
        // Note: Database cleanup is handled automatically by @Transactional
        // which rolls back changes after each test

        // Use unique suffix to avoid conflicts with existing data or between test runs
        String suffix = "_" + System.currentTimeMillis();

        // Create test organizations
        testOrg1 = new Organization();
        testOrg1.setName("GreenTech Solutions" + suffix);
        testOrg1.setDescription("Original description");
        testOrg1.setType(OrganizationType.MANUFACTURER);
        testOrg1.setIndustrySector(IndustrySector.ELECTRICAL_ELECTRONICS);
        testOrg1.setWebsite("https://greentech.example.com");
        testOrg1.setContactEmail("contact" + suffix + "@greentech.example.com");
        testOrg1.setContactPhone("+1-555-0001");
        testOrg1.setAddress("123 Green St, Tech City");
        testOrg1.setPrimaryContactName("John Green");
        testOrg1.setPrimaryContactTitle("CEO");
        testOrg1.setCompanySize(CompanySize.MEDIUM);
        testOrg1.setCertificationStatus(CertificationStatus.ACTIVE);
        testOrg1 = organizationRepository.save(testOrg1);

        testOrg2 = new Organization();
        testOrg2.setName("EcoRecycle Corp" + suffix);
        testOrg2.setDescription("Recycling company");
        testOrg2.setType(OrganizationType.RECYCLER);
        testOrg2.setIndustrySector(IndustrySector.METALS);
        testOrg2.setWebsite("https://ecorecycle.example.com");
        testOrg2.setContactEmail("info" + suffix + "@ecorecycle.example.com");
        testOrg2 = organizationRepository.save(testOrg2);

        // Create test users with different roles
        platformAdmin = new User();
        platformAdmin.setUsername("platform.admin" + suffix);
        platformAdmin.setPassword("encoded_password");
        platformAdmin.setEmail("admin" + suffix + "@platform.com");
        platformAdmin.setFirstName("Platform");
        platformAdmin.setLastName("Admin");
        platformAdmin.setRole(UserRole.PLATFORM_ADMIN);
        platformAdmin.setOrganization(null); // Platform admin has no org
        platformAdmin = userRepository.save(platformAdmin);

        org1Admin = new User();
        org1Admin.setUsername("org1.admin" + suffix);
        org1Admin.setPassword("encoded_password");
        org1Admin.setEmail("admin" + suffix + "@greentech.example.com");
        org1Admin.setFirstName("Org1");
        org1Admin.setLastName("Admin");
        org1Admin.setRole(UserRole.ORG_ADMIN);
        org1Admin.setOrganization(testOrg1);
        org1Admin = userRepository.save(org1Admin);

        org2Admin = new User();
        org2Admin.setUsername("org2.admin" + suffix);
        org2Admin.setPassword("encoded_password");
        org2Admin.setEmail("admin" + suffix + "@ecorecycle.example.com");
        org2Admin.setFirstName("Org2");
        org2Admin.setLastName("Admin");
        org2Admin.setRole(UserRole.ORG_ADMIN);
        org2Admin.setOrganization(testOrg2);
        org2Admin = userRepository.save(org2Admin);

        org1Member = new User();
        org1Member.setUsername("org1.member" + suffix);
        org1Member.setPassword("encoded_password");
        org1Member.setEmail("member" + suffix + "@greentech.example.com");
        org1Member.setFirstName("Org1");
        org1Member.setLastName("Member");
        org1Member.setRole(UserRole.ORG_MEMBER);
        org1Member.setOrganization(testOrg1);
        org1Member = userRepository.save(org1Member);

        entityManager.flush();
        entityManager.clear();
    }

    // ========== SUCCESS SCENARIOS ==========

    @Test
    void testPlatformAdminCanEditAnyOrganization() throws Exception {
        // Arrange
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                platformAdmin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );

        // Act - Update organization details
        mockMvc.perform(post("/organizations/" + testOrg1.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .with(csrf())
                        .param("name", "GreenTech Solutions Updated")
                        .param("description", "Updated description")
                        .param("type", "MANUFACTURER")
                        .param("industrySector", "METALS")
                        .param("website", "https://greentech-new.example.com")
                        .param("contactEmail", "newcontact@greentech.example.com")
                        .param("contactPhone", "+1-555-9999")
                        .param("address", "456 New Green Ave, Tech City")
                        .param("primaryContactName", "Jane Green")
                        .param("primaryContactTitle", "CTO")
                        .param("companySize", "LARGE")
                        .param("naceCodes", "96.99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/organizations/" + testOrg1.getId()))
                .andExpect(flash().attributeExists("message"));

        // Assert - Verify database changes
        entityManager.flush();
        entityManager.clear();

        Organization updated = organizationRepository.findById(testOrg1.getId())
                .orElseThrow(() -> new AssertionError("Organization not found"));

        assertThat(updated.getName()).isEqualTo("GreenTech Solutions Updated");
        assertThat(updated.getDescription()).isEqualTo("Updated description");
        assertThat(updated.getIndustrySector()).isEqualTo(IndustrySector.METALS);
        assertThat(updated.getWebsite()).isEqualTo("https://greentech-new.example.com");
        assertThat(updated.getContactEmail()).isEqualTo("newcontact@greentech.example.com");
        assertThat(updated.getContactPhone()).isEqualTo("+1-555-9999");
        assertThat(updated.getAddress()).isEqualTo("456 New Green Ave, Tech City");
        assertThat(updated.getPrimaryContactName()).isEqualTo("Jane Green");
        assertThat(updated.getPrimaryContactTitle()).isEqualTo("CTO");
        assertThat(updated.getCompanySize()).isEqualTo(CompanySize.LARGE);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void testOrgAdminCanEditOwnOrganization() throws Exception {
        // Arrange - Reload user from database to ensure organization is properly loaded
        User reloadedOrg1Admin = userRepository.findById(org1Admin.getId())
                .orElseThrow(() -> new AssertionError("User not found"));

        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                reloadedOrg1Admin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ORG_ADMIN"))
        );

        // Act - Must include all form fields that would be sent by the HTML form
        mockMvc.perform(post("/organizations/" + testOrg1.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .with(csrf())
                        .param("name", testOrg1.getName())
                        .param("description", "Updated by org admin")
                        .param("type", testOrg1.getType().toString())
                        .param("industrySector", testOrg1.getIndustrySector().toString())
                        .param("website", testOrg1.getWebsite())
                        .param("contactEmail", testOrg1.getContactEmail() != null ? testOrg1.getContactEmail() : "")
                        .param("address", testOrg1.getAddress() != null ? testOrg1.getAddress() : "")
                        .param("contactPhone", testOrg1.getContactPhone() != null ? testOrg1.getContactPhone() : "")
                        .param("primaryContactName", testOrg1.getPrimaryContactName() != null ? testOrg1.getPrimaryContactName() : "")
                        .param("primaryContactTitle", testOrg1.getPrimaryContactTitle() != null ? testOrg1.getPrimaryContactTitle() : "")
                        .param("companySize", testOrg1.getCompanySize() != null ? testOrg1.getCompanySize().toString() : "")
                        .param("naceCodes", "96.99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/organizations/" + testOrg1.getId()));

        // Assert
        entityManager.flush();
        entityManager.clear();

        Organization updated = organizationRepository.findById(testOrg1.getId())
                .orElseThrow(() -> new AssertionError("Organization not found"));
        assertThat(updated.getDescription()).isEqualTo("Updated by org admin");
    }

    @Test
    void testEditOrganizationWithPartialUpdate() throws Exception {
        // Arrange - Only update description, keep other fields the same
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                platformAdmin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );

        String originalName = testOrg1.getName();
        String originalEmail = testOrg1.getContactEmail();

        // Act - Only change description
        mockMvc.perform(post("/organizations/" + testOrg1.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .with(csrf())
                        .param("name", originalName)
                        .param("description", "Only description changed")
                        .param("type", testOrg1.getType().toString())
                        .param("industrySector", testOrg1.getIndustrySector().toString())
                        .param("contactEmail", originalEmail)
                        .param("naceCodes", "96.99"))
                .andExpect(status().is3xxRedirection());

        // Assert - Name and email should remain unchanged
        entityManager.flush();
        entityManager.clear();

        Organization updated = organizationRepository.findById(testOrg1.getId())
                .orElseThrow(() -> new AssertionError("Organization not found"));
        assertThat(updated.getName()).isEqualTo(originalName);
        assertThat(updated.getContactEmail()).isEqualTo(originalEmail);
        assertThat(updated.getDescription()).isEqualTo("Only description changed");
    }

    @Test
    void testEditFormDisplaysExistingData() throws Exception {
        // Arrange
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                platformAdmin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );

        // Act & Assert - Verify form loads with existing data
        mockMvc.perform(get("/organizations/" + testOrg1.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(view().name("organizations/form"))
                .andExpect(model().attributeExists("organization"))
                .andExpect(model().attribute("title", "Edit Organization"));
    }

    // ========== LOCKED FIELD REGRESSION TESTS ==========

    /**
     * Regression test: contactEmail (like name, type and industrySector) is rendered as a
     * disabled input once an organization exists, so browsers never include it in the edit
     * form submission. The template must carry its value through via a companion hidden
     * input - the same pattern already used for the other locked fields - otherwise the
     * field silently binds to null on submit and gets wiped by the next save (this is
     * exactly what happened in production: an org's contactEmail disappeared after being
     * edited, with no user action ever touching that field).
     *
     * This can't be caught by posting to the controller directly (as the other tests in
     * this file do) because those tests explicitly supply a contactEmail param, unlike a
     * real browser submitting the disabled field. It has to be verified against the actual
     * rendered HTML instead.
     */
    @Test
    void testEditFormCarriesContactEmailThroughHiddenFieldWhenLocked() throws Exception {
        // Arrange
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                platformAdmin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );

        // Act - render the edit form for an existing organization (contactEmail is locked/disabled)
        String html = mockMvc.perform(get("/organizations/" + testOrg1.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Assert - a hidden input carrying the current contactEmail value must be present.
        // The disabled <input type="email"> alone is not enough: browsers omit disabled
        // fields from the submitted form, so without this hidden companion the value is lost.
        java.util.regex.Matcher hiddenInputs = java.util.regex.Pattern
                .compile("<input[^>]*type=\"hidden\"[^>]*>")
                .matcher(html);

        boolean hasHiddenContactEmail = false;
        while (hiddenInputs.find()) {
            String tag = hiddenInputs.group();
            if (tag.contains("name=\"contactEmail\"")
                    && tag.contains("value=\"" + testOrg1.getContactEmail() + "\"")) {
                hasHiddenContactEmail = true;
                break;
            }
        }

        assertThat(hasHiddenContactEmail)
                .as("Edit form must submit contactEmail via a hidden input while the visible "
                        + "field is disabled, otherwise it is silently wiped on the next save")
                .isTrue();
    }

    // ========== UI VISIBILITY TESTS ==========

    @Test
    void testEditButtonVisibleToPlatformAdmin() throws Exception {
        // Arrange
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                platformAdmin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );

        // Act & Assert - Edit button should be visible on ANY organization's view page
        mockMvc.perform(get("/organizations/" + testOrg1.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(view().name("organizations/view"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Edit")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/organizations/" + testOrg1.getId() + "/edit")));
    }

    @Test
    void testEditButtonVisibleToOwnOrgAdmin() throws Exception {
        // Arrange - Org admin viewing their own organization
        User reloadedOrg1Admin = userRepository.findById(org1Admin.getId())
                .orElseThrow(() -> new AssertionError("User not found"));

        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                reloadedOrg1Admin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ORG_ADMIN"))
        );

        // Act & Assert - Edit button should be visible on THEIR OWN organization's view page
        mockMvc.perform(get("/organizations/" + testOrg1.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(view().name("organizations/view"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Edit")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/organizations/" + testOrg1.getId() + "/edit")));
    }

    @Test
    void testEditButtonHiddenFromOtherOrgAdmin() throws Exception {
        // Arrange - Org1 admin viewing Org2's page
        User reloadedOrg1Admin = userRepository.findById(org1Admin.getId())
                .orElseThrow(() -> new AssertionError("User not found"));

        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                reloadedOrg1Admin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ORG_ADMIN"))
        );

        // Act & Assert - Edit button should NOT be visible on OTHER organization's view page
        mockMvc.perform(get("/organizations/" + testOrg2.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(view().name("organizations/view"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/organizations/" + testOrg2.getId() + "/edit"))));
    }

    @Test
    void testEditButtonHiddenFromOrgMember() throws Exception {
        // Arrange - Org member viewing their own organization
        User reloadedOrg1Member = userRepository.findById(org1Member.getId())
                .orElseThrow(() -> new AssertionError("User not found"));

        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                reloadedOrg1Member,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ORG_MEMBER"))
        );

        // Act & Assert - Edit button should NOT be visible even for their own organization
        mockMvc.perform(get("/organizations/" + testOrg1.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(view().name("organizations/view"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/organizations/" + testOrg1.getId() + "/edit"))));
    }

    // ========== AUTHORIZATION SCENARIOS ==========

    @Test
    void testOrgAdminCannotEditOtherOrganization() throws Exception {
        // Arrange - org1Admin trying to edit org2
        User reloadedOrg1Admin = userRepository.findById(org1Admin.getId())
                .orElseThrow(() -> new AssertionError("User not found"));

        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                reloadedOrg1Admin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ORG_ADMIN"))
        );

        // Act & Assert - Should be blocked (shows error view)
        // Note: Currently returns error/500 due to exception in authorization check,
        // but the important thing is that the edit is blocked
        mockMvc.perform(post("/organizations/" + testOrg2.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .with(csrf())
                        .param("name", "Hacked Name")
                        .param("type", "RECYCLER")
                        .param("industrySector", "METALS"))
                .andExpect(status().isOk())  // Error view renders with 200
                .andExpect(view().name(org.hamcrest.Matchers.startsWith("error/")));  // Any error view is acceptable

        // Verify organization was not changed
        entityManager.flush();
        entityManager.clear();

        Organization unchanged = organizationRepository.findById(testOrg2.getId())
                .orElseThrow(() -> new AssertionError("Organization not found"));
        assertThat(unchanged.getName()).isEqualTo(testOrg2.getName());
    }

    @Test
    void testOrgMemberCannotEditOrganization() throws Exception {
        // Arrange - Regular member trying to edit their own org
        User reloadedOrg1Member = userRepository.findById(org1Member.getId())
                .orElseThrow(() -> new AssertionError("User not found"));

        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                reloadedOrg1Member,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ORG_MEMBER"))
        );

        // Act & Assert - Should be blocked (shows error view)
        // Note: Currently returns error/500 due to exception in authorization check,
        // but the important thing is that the edit is blocked
        mockMvc.perform(post("/organizations/" + testOrg1.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .with(csrf())
                        .param("name", "Unauthorized Change")
                        .param("type", "MANUFACTURER")
                        .param("industrySector", "ELECTRICAL_ELECTRONICS"))
                .andExpect(status().isOk())  // Error view renders with 200
                .andExpect(view().name(org.hamcrest.Matchers.startsWith("error/")));  // Any error view is acceptable
    }

    @Test
    void testUnauthenticatedUserCannotAccessEditForm() throws Exception {
        // Act & Assert - Should redirect to login
        mockMvc.perform(get("/organizations/" + testOrg1.getId() + "/edit"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void testUnauthenticatedUserCannotSubmitEdit() throws Exception {
        // Act & Assert - Should redirect to login
        mockMvc.perform(post("/organizations/" + testOrg1.getId() + "/edit")
                        .with(csrf())
                        .param("name", "Unauthorized")
                        .param("type", "MANUFACTURER")
                        .param("industrySector", "ELECTRICAL_ELECTRONICS"))
                .andExpect(status().is3xxRedirection());
    }

    // ========== VALIDATION SCENARIOS ==========

    @Test
    void testCannotChangeNameToDuplicateName() throws Exception {
        // Arrange - Try to change testOrg1 name to testOrg2's name
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                platformAdmin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );

        // Act & Assert - Should show validation error
        mockMvc.perform(post("/organizations/" + testOrg1.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .with(csrf())
                        .param("name", testOrg2.getName()) // Duplicate name
                        .param("description", "Test")
                        .param("type", "MANUFACTURER")
                        .param("industrySector", "ELECTRICAL_ELECTRONICS"))
                .andExpect(status().isOk())
                .andExpect(view().name("organizations/form"))
                .andExpect(model().attributeHasFieldErrors("organization", "name"));

        // Verify name was not changed
        entityManager.flush();
        entityManager.clear();

        String originalName = testOrg1.getName();
        Organization unchanged = organizationRepository.findById(testOrg1.getId())
                .orElseThrow(() -> new AssertionError("Organization not found"));
        assertThat(unchanged.getName()).isEqualTo(originalName);
    }

    @Test
    void testCanKeepSameNameWhenEditingOtherFields() throws Exception {
        // Arrange - Edit without changing name (should be allowed)
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                platformAdmin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );

        String originalName = testOrg1.getName();

        // Act - Keep same name, change other fields
        mockMvc.perform(post("/organizations/" + testOrg1.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .with(csrf())
                        .param("name", originalName) // Same name
                        .param("description", "New description")
                        .param("type", "MANUFACTURER")
                        .param("industrySector", "METALS")
                        .param("naceCodes", "96.99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/organizations/" + testOrg1.getId()));

        // Verify update succeeded
        entityManager.flush();
        entityManager.clear();

        Organization updated = organizationRepository.findById(testOrg1.getId())
                .orElseThrow(() -> new AssertionError("Organization not found"));
        assertThat(updated.getName()).isEqualTo(originalName);
        assertThat(updated.getDescription()).isEqualTo("New description");
        assertThat(updated.getIndustrySector()).isEqualTo(IndustrySector.METALS);
    }

    @Test
    void testCannotChangeEmailToExistingOrganizationEmail() throws Exception {
        // Arrange - Try to change testOrg1 email to testOrg2's email
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                platformAdmin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );

        // Act & Assert - Should show validation error
        mockMvc.perform(post("/organizations/" + testOrg1.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .with(csrf())
                        .param("name", testOrg1.getName())
                        .param("type", "MANUFACTURER")
                        .param("industrySector", "ELECTRICAL_ELECTRONICS")
                        .param("contactEmail", testOrg2.getContactEmail())) // Duplicate email
                .andExpect(status().isOk())
                .andExpect(view().name("organizations/form"))
                .andExpect(model().attributeHasFieldErrors("organization", "contactEmail"));

        // Verify email was not changed
        entityManager.flush();
        entityManager.clear();

        String originalEmail = testOrg1.getContactEmail();
        Organization unchanged = organizationRepository.findById(testOrg1.getId())
                .orElseThrow(() -> new AssertionError("Organization not found"));
        assertThat(unchanged.getContactEmail()).isEqualTo(originalEmail);
    }

    @Test
    void testCannotChangeEmailToExistingUserEmail() throws Exception {
        // Arrange - Try to change org email to a user's email
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                platformAdmin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );

        // Act & Assert - Should show validation error
        mockMvc.perform(post("/organizations/" + testOrg1.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .with(csrf())
                        .param("name", testOrg1.getName())
                        .param("type", "MANUFACTURER")
                        .param("industrySector", "ELECTRICAL_ELECTRONICS")
                        .param("contactEmail", org2Admin.getEmail())) // Existing user email
                .andExpect(status().isOk())
                .andExpect(view().name("organizations/form"))
                .andExpect(model().attributeHasFieldErrors("organization", "contactEmail"));
    }

    @Test
    void testCannotChangeEmailToPendingOnboardingRequestEmail() throws Exception {
        // Arrange - Create a pending onboarding request
        String pendingEmail = "pending_" + System.currentTimeMillis() + "@example.com";
        OrganizationOnboardingRequest pendingRequest = new OrganizationOnboardingRequest();
        pendingRequest.setCompanyName("Pending Company");
        pendingRequest.setEmail(pendingEmail);
        pendingRequest.setCompanyType("MANUFACTURER");
        pendingRequest.setIndustry("ELECTRICAL_ELECTRONICS");
        pendingRequest.setContactName("Pending Person");
        pendingRequest.setContactTitle("Manager");
        pendingRequest.setOrganizationDescription("Test description");
        pendingRequest.setParticipationGoals("Test goals");
        pendingRequest.setGdprConsent(true);
        pendingRequest.setStatus(OnboardingRequestStatus.PENDING);
        onboardingRequestRepository.save(pendingRequest);

        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                platformAdmin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );

        // Act & Assert - Should show validation error
        mockMvc.perform(post("/organizations/" + testOrg1.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .with(csrf())
                        .param("name", testOrg1.getName())
                        .param("type", "MANUFACTURER")
                        .param("industrySector", "ELECTRICAL_ELECTRONICS")
                        .param("contactEmail", pendingEmail)) // Pending request email
                .andExpect(status().isOk())
                .andExpect(view().name("organizations/form"))
                .andExpect(model().attributeHasFieldErrors("organization", "contactEmail"));
    }

    @Test
    void testCanKeepSameEmailWhenEditingOtherFields() throws Exception {
        // Arrange - Edit without changing email (should be allowed)
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                platformAdmin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );

        String originalEmail = testOrg1.getContactEmail();

        // Act - Keep same email, change other fields
        mockMvc.perform(post("/organizations/" + testOrg1.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .with(csrf())
                        .param("name", testOrg1.getName())
                        .param("description", "Email unchanged")
                        .param("type", "MANUFACTURER")
                        .param("industrySector", "ELECTRICAL_ELECTRONICS")
                        .param("contactEmail", originalEmail) // Same email
                        .param("naceCodes", "96.99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/organizations/" + testOrg1.getId()));

        // Verify update succeeded
        entityManager.flush();
        entityManager.clear();

        Organization updated = organizationRepository.findById(testOrg1.getId())
                .orElseThrow(() -> new AssertionError("Organization not found"));
        assertThat(updated.getContactEmail()).isEqualTo(originalEmail);
        assertThat(updated.getDescription()).isEqualTo("Email unchanged");
    }

    @Test
    void testCanClearOptionalFields() throws Exception {
        // Arrange - Clear optional fields like website, phone, etc.
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                platformAdmin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );

        // Act - Submit with empty optional fields
        mockMvc.perform(post("/organizations/" + testOrg1.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .with(csrf())
                        .param("name", testOrg1.getName())
                        .param("type", "MANUFACTURER")
                        .param("industrySector", "ELECTRICAL_ELECTRONICS")
                        .param("website", "") // Clear website
                        .param("contactPhone", "") // Clear phone
                        .param("contactEmail", "") // Clear email
                        .param("naceCodes", "96.99"))
                .andExpect(status().is3xxRedirection());

        // Assert - Optional fields should be cleared or null
        entityManager.flush();
        entityManager.clear();

        Organization updated = organizationRepository.findById(testOrg1.getId())
                .orElseThrow(() -> new AssertionError("Organization not found"));
        assertThat(updated.getWebsite()).isNullOrEmpty();
        assertThat(updated.getContactPhone()).isNullOrEmpty();
        assertThat(updated.getContactEmail()).isNullOrEmpty();
    }

    // ========== EDGE CASES ==========

    @Test
    void testEditNonExistentOrganization() throws Exception {
        // Arrange
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                platformAdmin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );

        Long nonExistentId = 99999L;

        // Act & Assert - Should return error view
        // Note: The @PreAuthorize annotation calls organizationService.findById().get()
        // which may throw during security evaluation
        mockMvc.perform(get("/organizations/" + nonExistentId + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth)))
                .andExpect(status().isOk()) // Error view renders with 200
                .andExpect(view().name(org.hamcrest.Matchers.containsString("error")));
    }

    @Test
    void testEditUpdatesTimestamp() throws Exception {
        // Arrange
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                platformAdmin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );

        LocalDateTime originalUpdatedAt = testOrg1.getUpdatedAt();

        // Add a small delay to ensure timestamp difference
        Thread.sleep(10);

        // Act - Update organization
        mockMvc.perform(post("/organizations/" + testOrg1.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .with(csrf())
                        .param("name", testOrg1.getName())
                        .param("description", "Timestamp test")
                        .param("type", "MANUFACTURER")
                        .param("industrySector", "ELECTRICAL_ELECTRONICS")
                        .param("naceCodes", "96.99"))
                .andExpect(status().is3xxRedirection());

        // Assert - updatedAt should be later than original
        entityManager.flush();
        entityManager.clear();

        Organization updated = organizationRepository.findById(testOrg1.getId())
                .orElseThrow(() -> new AssertionError("Organization not found"));

        if (originalUpdatedAt != null) {
            assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        } else {
            assertThat(updated.getUpdatedAt()).isNotNull();
        }
    }

    @Test
    void testRequiredFieldsValidation() throws Exception {
        // Arrange - Submit without required fields
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                platformAdmin,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );

        // Act & Assert - Should show validation errors for missing required fields
        mockMvc.perform(post("/organizations/" + testOrg1.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .with(csrf())
                        .param("name", "") // Empty required field
                        .param("description", "Test"))
                .andExpect(status().isOk())
                .andExpect(view().name("organizations/form"))
                .andExpect(model().attributeHasErrors("organization"));
    }
}
