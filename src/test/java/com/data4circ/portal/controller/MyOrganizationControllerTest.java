package com.data4circ.portal.controller;

import com.data4circ.portal.features.organization.entity.*;
import com.data4circ.portal.features.organization.repository.OrganizationRepository;
import com.data4circ.portal.features.organization.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for the My Organization endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MyOrganizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private JavaMailSender javaMailSender;

    private Organization testOrganization;
    private User testOrgAdmin;
    private User testOrgMember;

    @BeforeEach
    void setUp() {
        // Clear existing data
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        // Create test organization
        testOrganization = new Organization();
        testOrganization.setName("Test Organization");
        testOrganization.setDescription("Test Description");
        testOrganization.setType(OrganizationType.MANUFACTURER);
        testOrganization.setIndustrySector(IndustrySector.ELECTRICAL_ELECTRONICS);
        testOrganization.setCertificationStatus(CertificationStatus.ACTIVE);
        testOrganization = organizationRepository.save(testOrganization);

        // Create test organization admin
        testOrgAdmin = new User();
        testOrgAdmin.setUsername("orgadmin");
        testOrgAdmin.setEmail("admin@test.com");
        testOrgAdmin.setPassword("password");
        testOrgAdmin.setFirstName("Admin");
        testOrgAdmin.setLastName("User");
        testOrgAdmin.setRole(UserRole.ORG_ADMIN);
        testOrgAdmin.setOrganization(testOrganization);
        testOrgAdmin = userRepository.save(testOrgAdmin);

        // Create test organization member
        testOrgMember = new User();
        testOrgMember.setUsername("orgmember");
        testOrgMember.setEmail("member@test.com");
        testOrgMember.setPassword("password");
        testOrgMember.setFirstName("Member");
        testOrgMember.setLastName("User");
        testOrgMember.setRole(UserRole.ORG_MEMBER);
        testOrgMember.setOrganization(testOrganization);
        testOrgMember = userRepository.save(testOrgMember);
    }

    @Test
    void shouldDenyAccessForUnauthenticatedUser() throws Exception {
        // Unauthenticated users should be redirected to login
        mockMvc.perform(get("/organizations/my-organization"))
                .andExpect(status().is3xxRedirection()); // Redirects to login
    }

    @Test
    void shouldAllowAccessForOrgAdmin() throws Exception {
        // A real ORG_ADMIN with an organization is redirected to their organization page.
        var auth = new UsernamePasswordAuthenticationToken(testOrgAdmin, null,
                List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
        mockMvc.perform(get("/organizations/my-organization").with(authentication(auth)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/organizations/" + testOrganization.getId()));
    }

    @Test
    void shouldAllowAccessForOrgMember() throws Exception {
        // A real ORG_MEMBER with an organization is redirected to their organization page.
        var auth = new UsernamePasswordAuthenticationToken(testOrgMember, null,
                List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
        mockMvc.perform(get("/organizations/my-organization").with(authentication(auth)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/organizations/" + testOrganization.getId()));
    }
}
