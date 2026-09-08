package com.data4circ.portal.controller;

import com.data4circ.portal.features.organization.entity.NaceCode;
import com.data4circ.portal.features.organization.repository.NaceCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the NACE code search endpoint (/api/nace-codes/search).
 * Verifies response shape, limit, and edge cases used by the Tom Select UI component.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NaceCodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NaceCodeRepository naceCodeRepository;

    @MockBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void setUp() {
        // NACE codes are loaded by NaceCodeDataLoader at startup,
        // so they should already be present. Verify at least some exist.
        if (naceCodeRepository.count() == 0) {
            // Insert test data if loader didn't run
            naceCodeRepository.save(new NaceCode("01.1", "Growing of non-perennial crops", "A", "Agriculture, forestry and fishing"));
            naceCodeRepository.save(new NaceCode("01.2", "Growing of perennial crops", "A", "Agriculture, forestry and fishing"));
            naceCodeRepository.save(new NaceCode("10.1", "Processing and preserving of meat", "C", "Manufacturing"));
            naceCodeRepository.save(new NaceCode("62.01", "Computer programming activities", "J", "Information and communication"));
        }
    }

    @Test
    @WithMockUser
    void searchShouldReturnMatchingCodesWithExpectedShape() throws Exception {
        mockMvc.perform(get("/api/nace-codes/search").param("q", "01.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].code").exists())
                .andExpect(jsonPath("$[0].description").exists())
                .andExpect(jsonPath("$[0].section").exists())
                .andExpect(jsonPath("$[0].sectionLabel").exists())
                .andExpect(jsonPath("$[0].label").exists())
                .andExpect(jsonPath("$[0].label", containsString(" - ")));
    }

    @Test
    @WithMockUser
    void searchShouldReturnAtMost50Results() throws Exception {
        // Search for a broad term that matches many codes
        mockMvc.perform(get("/api/nace-codes/search").param("q", "a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", lessThanOrEqualTo(50)));
    }

    @Test
    @WithMockUser
    void searchWithEmptyQueryShouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/api/nace-codes/search").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser
    void searchWithBlankQueryShouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/api/nace-codes/search").param("q", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser
    void searchWithNoMatchShouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/api/nace-codes/search").param("q", "zzzznonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser
    void searchByDescriptionShouldWork() throws Exception {
        mockMvc.perform(get("/api/nace-codes/search").param("q", "Computer programming activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].description", containsString("Computer programming")));
    }

    @Test
    void searchWithoutAuthShouldStillWork() throws Exception {
        // The API endpoint is accessible without authentication
        // as it's used by public-facing forms (join/onboarding)
        mockMvc.perform(get("/api/nace-codes/search").param("q", "01"))
                .andExpect(status().isOk());
    }
}
