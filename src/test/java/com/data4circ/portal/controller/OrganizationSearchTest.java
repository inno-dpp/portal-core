package com.data4circ.portal.controller;

import com.data4circ.portal.features.organization.entity.CertificationStatus;
import com.data4circ.portal.features.organization.entity.IndustrySector;
import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.organization.entity.OrganizationType;
import com.data4circ.portal.features.organization.entity.User;
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Organizations directory's free-text search.
 *
 * Covers the server-side pieces the search-as-you-type fix in organizations/list.html
 * relies on: the previous implementation resubmitted the whole filter form (a full page
 * reload) after a typing pause, which could navigate the browser away mid-keystroke
 * whenever the user paused for a beat - e.g. right after a space in a multi-word query
 * like "Company A..." - silently dropping whatever they typed next. The fix now fetches
 * GET /organizations/cards (the same fragment endpoint "Load more" already used) and
 * replaces the results in place instead. These tests guard the endpoint itself handles
 * space-containing queries correctly, and that the empty state's wording is filter-aware.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrganizationSearchTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private EmailService emailService;

    private UsernamePasswordAuthenticationToken auth;

    @BeforeEach
    void setUp() {
        User adminUser = userRepository.findByUsername("admin").orElseThrow();
        auth = new UsernamePasswordAuthenticationToken(
                adminUser, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));

        String suffix = "_" + System.currentTimeMillis();
        Organization org = new Organization();
        org.setName("Agricultural Plastics Institute" + suffix);
        org.setType(OrganizationType.RECYCLER);
        org.setIndustrySector(IndustrySector.PLASTIC_AGRICULTURE);
        org.setCertificationStatus(CertificationStatus.ACTIVE);
        organizationRepository.save(org);
    }

    @Test
    void cardsFragmentEndpointHandlesMultiWordSearchWithSpaces() throws Exception {
        // This is the endpoint the live search calls on every debounced keystroke pause.
        // A space in the query (e.g. "Plastics Institute") must be handled the same way
        // the full page's own search already does, not truncated or rejected.
        mockMvc.perform(get("/organizations/cards")
                        .param("search", "Plastics Institute")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Plastics Institute")));
    }

    @Test
    void emptyResultsWithActiveSearchShowFilterAwareMessage() throws Exception {
        // A search that matches nothing should say so via the empty state's <h4>/<p>
        // ("No Matching Organizations" / "...Try broadening them."), not the generic
        // "no organizations at all" message - misleading once live search makes hitting
        // this state a routine part of typing a query. (Both message variants are always
        // present in the response - one visible, one display:none via inline style, plus
        // both string literals also appear in the page's own JS for the client-side
        // toggle - so this asserts the specific rendered element's content precisely
        // rather than a raw substring match anywhere in the page.)
        mockMvc.perform(get("/organizations")
                        .param("search", "NoSuchOrganizationXYZ123")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "id=\"emptyStateTitle\">No Matching Organizations</h4>")))
                .andExpect(content().string(containsString(
                        "id=\"emptyStateText\">No organizations match your current search and filters. "
                                + "Try broadening them.</p>")));
    }
}
