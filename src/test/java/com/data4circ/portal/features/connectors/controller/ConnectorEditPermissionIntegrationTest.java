package com.data4circ.portal.features.connectors.controller;

import com.data4circ.portal.features.connectors.entity.Connector;
import com.data4circ.portal.features.connectors.enums.ConnectorStatus;
import com.data4circ.portal.features.connectors.enums.ConnectorType;
import com.data4circ.portal.features.connectors.repository.ConnectorRepository;
import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.organization.entity.OrganizationType;
import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.organization.entity.UserRole;
import com.data4circ.portal.features.organization.repository.OrganizationRepository;
import com.data4circ.portal.features.organization.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * ORG_ADMINs may edit connectors of their own organization (e.g. fill in the EDC
 * configuration after onboarding) but nothing beyond: no other org's connectors,
 * no delete, and unbound fields (tool key, heartbeat token) survive the edit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConnectorEditPermissionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConnectorRepository connectorRepository;

    private User orgAdmin;
    private Connector ownConnector;
    private Connector foreignConnector;

    @BeforeEach
    void setUp() {
        Organization ownOrg = saveOrganization("Edit Perm Own Org");
        Organization otherOrg = saveOrganization("Edit Perm Other Org");

        orgAdmin = new User();
        orgAdmin.setUsername("editperm.orgadmin");
        orgAdmin.setEmail("editperm.orgadmin@example.com");
        orgAdmin.setFirstName("Edit");
        orgAdmin.setLastName("Admin");
        orgAdmin.setPassword("$2a$10$test");
        orgAdmin.setRole(UserRole.ORG_ADMIN);
        orgAdmin.setOrganization(ownOrg);
        orgAdmin.setEnabled(true);
        orgAdmin = userRepository.save(orgAdmin);

        ownConnector = saveConnector(ownOrg, "Own EDC Connector");
        foreignConnector = saveConnector(otherOrg, "Foreign EDC Connector");
    }

    @Test
    void orgAdminCanEditOwnConnectorConfiguration() throws Exception {
        mockMvc.perform(post("/connectors/" + ownConnector.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(orgAdminAuth()))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("name", ownConnector.getName())
                        .param("type", ConnectorType.EDC_CONNECTOR.name())
                        .param("endpoint", "https://edc.own-org.example.com")
                        .param("status", ConnectorStatus.OFFLINE.name())
                        .param("configuration", "{\"participantId\": \"own-org\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/connectors/" + ownConnector.getId()));

        Connector updated = connectorRepository.findById(ownConnector.getId()).orElseThrow();
        assertThat(updated.getConfiguration()).isEqualTo("{\"participantId\": \"own-org\"}");
        assertThat(updated.getEndpoint()).isEqualTo("https://edc.own-org.example.com");
        // Unbound fields survive the edit
        assertThat(updated.getToolKey()).isEqualTo("edc");
        assertThat(updated.getHeartbeatToken()).isEqualTo("hb-" + ownConnector.getId());
    }

    @Test
    void orgAdminCannotEditForeignConnector() throws Exception {
        mockMvc.perform(post("/connectors/" + foreignConnector.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(orgAdminAuth()))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("name", "Hijacked")
                        .param("type", ConnectorType.EDC_CONNECTOR.name())
                        .param("endpoint", "https://evil.example.com")
                        .param("status", ConnectorStatus.OFFLINE.name())
                        .param("configuration", "{}"))
                .andExpect(view().name("error/403"));

        Connector unchanged = connectorRepository.findById(foreignConnector.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Foreign EDC Connector");
    }

    @Test
    void orgAdminCannotDeleteOwnConnector() throws Exception {
        // @PreAuthorize denial: the dedicated Spring AccessDeniedException handler renders
        // error/403 for an authenticated-but-unauthorized user (same as the edit-foreign case).
        mockMvc.perform(post("/connectors/" + ownConnector.getId() + "/delete")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(orgAdminAuth()))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(view().name("error/403"));

        assertThat(connectorRepository.findById(ownConnector.getId())).isPresent();
    }

    @Test
    void invalidEdcJsonConfigurationIsRejected() throws Exception {
        mockMvc.perform(post("/connectors/" + ownConnector.getId() + "/edit")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(orgAdminAuth()))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("name", ownConnector.getName())
                        .param("type", ConnectorType.EDC_CONNECTOR.name())
                        .param("endpoint", "https://edc.own-org.example.com")
                        .param("status", ConnectorStatus.OFFLINE.name())
                        .param("configuration", "{not valid json"))
                .andExpect(view().name("connectors/form"));

        Connector unchanged = connectorRepository.findById(ownConnector.getId()).orElseThrow();
        assertThat(unchanged.getConfiguration()).isNull();
    }

    private UsernamePasswordAuthenticationToken orgAdminAuth() {
        return new UsernamePasswordAuthenticationToken(orgAdmin, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
    }

    private Organization saveOrganization(String name) {
        Organization organization = new Organization();
        organization.setName(name);
        organization.setType(OrganizationType.MANUFACTURER);
        organization.setIndustrySector(com.data4circ.portal.features.organization.entity.IndustrySector.METALS);
        organization.setContactEmail(name.toLowerCase().replace(" ", ".") + "@example.com");
        return organizationRepository.save(organization);
    }

    private Connector saveConnector(Organization organization, String name) {
        Connector connector = new Connector();
        connector.setName(name);
        connector.setType(ConnectorType.EDC_CONNECTOR);
        connector.setEndpoint("https://edc.example.com");
        connector.setOrganization(organization);
        connector.setStatus(ConnectorStatus.OFFLINE);
        connector.setToolKey("edc");
        connector = connectorRepository.save(connector);
        connector.setHeartbeatToken("hb-" + connector.getId());
        return connectorRepository.save(connector);
    }
}
