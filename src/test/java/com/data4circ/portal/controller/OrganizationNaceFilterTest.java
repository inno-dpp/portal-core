package com.data4circ.portal.controller;

import com.data4circ.portal.features.organization.entity.*;
import com.data4circ.portal.features.organization.repository.NaceCodeRepository;
import com.data4circ.portal.features.organization.repository.OrganizationRepository;
import com.data4circ.portal.features.organization.repository.UserRepository;
import com.data4circ.portal.features.organization.service.EmailService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for organization list filtering by NACE code.
 * Verifies that the naceCode query param filters results correctly
 * and that pagination links preserve the filter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrganizationNaceFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NaceCodeRepository naceCodeRepository;

    @MockBean
    private EmailService emailService;

    private NaceCode naceAgriculture;
    private NaceCode naceManufacturing;
    private UsernamePasswordAuthenticationToken auth;

    @BeforeEach
    void setUp() {
        // Do not deleteAll — seeded data has FK constraints (e.g. spip_attributes).
        // @Transactional rolls back test-created data automatically.

        // Create a test user with fullName (required by navigation template)
        User adminUser = userRepository.findByUsername("admin").orElseThrow();
        auth = new UsernamePasswordAuthenticationToken(
                adminUser, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));

        // Get NACE codes loaded by NaceCodeDataLoader
        naceAgriculture = naceCodeRepository.findById("01.1").orElseThrow();
        naceManufacturing = naceCodeRepository.findById("10.1").orElseThrow();

        String suffix = "_" + System.currentTimeMillis();

        // Org with agriculture NACE code
        Organization orgA = new Organization();
        orgA.setName("Farm Corp" + suffix);
        orgA.setType(OrganizationType.MANUFACTURER);
        orgA.setIndustrySector(IndustrySector.PLASTIC_AGRICULTURE);
        orgA.setCertificationStatus(CertificationStatus.ACTIVE);
        orgA.setNaceCodes(new HashSet<>(Set.of(naceAgriculture)));
        organizationRepository.save(orgA);

        // Org with manufacturing NACE code
        Organization orgB = new Organization();
        orgB.setName("Factory Inc" + suffix);
        orgB.setType(OrganizationType.MANUFACTURER);
        orgB.setIndustrySector(IndustrySector.ELECTRICAL_ELECTRONICS);
        orgB.setCertificationStatus(CertificationStatus.ACTIVE);
        orgB.setNaceCodes(new HashSet<>(Set.of(naceManufacturing)));
        organizationRepository.save(orgB);

        // Org with both NACE codes
        Organization orgC = new Organization();
        orgC.setName("AgroFactory Ltd" + suffix);
        orgC.setType(OrganizationType.MANUFACTURER);
        orgC.setIndustrySector(IndustrySector.PLASTIC_AGRICULTURE);
        orgC.setCertificationStatus(CertificationStatus.ACTIVE);
        orgC.setNaceCodes(new HashSet<>(Set.of(naceAgriculture, naceManufacturing)));
        organizationRepository.save(orgC);
    }

    @Test
    void filterByNaceCodeShouldReturnOnlyMatchingOrganizations() throws Exception {
        mockMvc.perform(get("/organizations").param("naceCode", "01.1")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("organizationsPage"))
                .andExpect(model().attribute("naceCode", "01.1"))
                .andExpect(model().attribute("organizationsPage",
                        hasProperty("totalElements", is(2L))));  // Farm Corp + AgroFactory
    }

    @Test
    void filterByDifferentNaceCodeShouldReturnDifferentResults() throws Exception {
        mockMvc.perform(get("/organizations").param("naceCode", "10.1")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("naceCode", "10.1"))
                .andExpect(model().attribute("organizationsPage",
                        hasProperty("totalElements", is(2L))));  // Factory Inc + AgroFactory
    }

    @Test
    void noNaceFilterShouldReturnAllOrganizations() throws Exception {
        mockMvc.perform(get("/organizations")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("organizationsPage",
                        hasProperty("totalElements", greaterThanOrEqualTo(3L))));
    }

    @Test
    void naceCodeFilterShouldBePreservedInModel() throws Exception {
        mockMvc.perform(get("/organizations").param("naceCode", "01.1")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("naceCode", "01.1"));
    }

    @Test
    void paginationWithNaceFilterShouldWork() throws Exception {
        // Request page size 1 so we get multiple pages
        mockMvc.perform(get("/organizations")
                        .param("naceCode", "01.1")
                        .param("size", "1")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("naceCode", "01.1"))
                .andExpect(model().attribute("organizationsPage",
                        hasProperty("totalPages", is(2))));  // 2 matching orgs, page size 1
    }

    @Test
    void invalidNaceCodeShouldReturnEmptyResults() throws Exception {
        mockMvc.perform(get("/organizations").param("naceCode", "99.99.99")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("organizationsPage",
                        hasProperty("totalElements", is(0L))));
    }

    @Test
    void naceCodesInUseShouldBePopulated() throws Exception {
        mockMvc.perform(get("/organizations")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("naceCodesInUse"))
                .andExpect(model().attribute("naceCodesInUse", not(empty())));
    }
}
