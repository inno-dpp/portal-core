package com.data4circ.portal.features.platformsettings.controller;

import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.organization.entity.UserRole;
import com.data4circ.portal.features.organization.repository.UserRepository;
import com.data4circ.portal.features.platformsettings.repository.PlatformSettingRepository;
import com.data4circ.portal.features.platformsettings.service.CkanSettingsService;
import com.data4circ.portal.features.platformsettings.service.SpipSettingsService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Platform admins can manage the CKAN connection overrides at /admin/settings;
 * everyone else is denied.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlatformSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformSettingRepository platformSettingRepository;

    @Autowired
    private CkanSettingsService ckanSettingsService;

    @Autowired
    private SpipSettingsService spipSettingsService;

    private User platformAdmin;
    private User orgMember;

    @BeforeEach
    void setUp() {
        platformAdmin = saveUser("settings.admin", UserRole.PLATFORM_ADMIN);
        orgMember = saveUser("settings.member", UserRole.ORG_MEMBER);
    }

    @Test
    void platformAdminCanViewSettingsPage() throws Exception {
        mockMvc.perform(get("/admin/settings")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(platformAdmin, "PLATFORM_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings"));
    }

    @Test
    void nonAdminIsDenied() throws Exception {
        mockMvc.perform(get("/admin/settings")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(orgMember, "ORG_MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/admin/settings"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void platformAdminCanSaveOverrides() throws Exception {
        mockMvc.perform(post("/admin/settings/ckan")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(platformAdmin, "PLATFORM_ADMIN")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("baseUrl", "https://ckan.example.com/")
                        .param("jwtToken", "new-admin-token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attributeExists("message"));

        assertThat(ckanSettingsService.getBaseUrl()).isEqualTo("https://ckan.example.com");
        assertThat(ckanSettingsService.getJwtToken()).isEqualTo("new-admin-token");
        assertThat(ckanSettingsService.isBaseUrlOverridden()).isTrue();
        assertThat(ckanSettingsService.isJwtTokenOverridden()).isTrue();
    }

    @Test
    void invalidBaseUrlIsRejectedWithoutSaving() throws Exception {
        mockMvc.perform(post("/admin/settings/ckan")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(platformAdmin, "PLATFORM_ADMIN")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("baseUrl", "not-a-url")
                        .param("jwtToken", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        assertThat(ckanSettingsService.isBaseUrlOverridden()).isFalse();
    }

    @Test
    void resetRemovesAllOverrides() throws Exception {
        ckanSettingsService.updateSettings("https://ckan.example.com", "override-token", "settings.admin");
        assertThat(platformSettingRepository.count()).isEqualTo(2);

        mockMvc.perform(post("/admin/settings/ckan/reset")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(platformAdmin, "PLATFORM_ADMIN")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"));

        assertThat(platformSettingRepository.count()).isZero();
        assertThat(ckanSettingsService.isBaseUrlOverridden()).isFalse();
        assertThat(ckanSettingsService.isJwtTokenOverridden()).isFalse();
    }

    @Test
    void nonAdminCannotSaveOverrides() throws Exception {
        mockMvc.perform(post("/admin/settings/ckan")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(orgMember, "ORG_MEMBER")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("baseUrl", "https://evil.example.com")
                        .param("jwtToken", "stolen"))
                .andExpect(status().isForbidden());

        assertThat(ckanSettingsService.isBaseUrlOverridden()).isFalse();
    }

    @Test
    void platformAdminCanSaveSpipOverrides() throws Exception {
        mockMvc.perform(post("/admin/settings/spip")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(platformAdmin, "PLATFORM_ADMIN")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("baseUrl", "https://spip.example.com/")
                        .param("customerTag", "test-tag")
                        .param("adminUsername", "spip-admin")
                        .param("adminPassword", "spip-secret")
                        .param("defaultRole", "new_data_owner"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attributeExists("message"));

        assertThat(spipSettingsService.getBaseUrl()).isEqualTo("https://spip.example.com");
        assertThat(spipSettingsService.getCustomerTag()).isEqualTo("test-tag");
        assertThat(spipSettingsService.getAdminUsername()).isEqualTo("spip-admin");
        assertThat(spipSettingsService.getAdminPassword()).isEqualTo("spip-secret");
        assertThat(spipSettingsService.getDefaultRole()).isEqualTo("new_data_owner");
    }

    @Test
    void spipResetRemovesAllSpipOverrides() throws Exception {
        spipSettingsService.updateSettings("https://spip.example.com", "tag", "user", "pass", "role", "settings.admin");
        assertThat(platformSettingRepository.count()).isEqualTo(5);

        mockMvc.perform(post("/admin/settings/spip/reset")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(platformAdmin, "PLATFORM_ADMIN")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"));

        assertThat(platformSettingRepository.count()).isZero();
    }

    @Test
    void nonAdminCannotSaveSpipOverrides() throws Exception {
        mockMvc.perform(post("/admin/settings/spip")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(orgMember, "ORG_MEMBER")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("baseUrl", "https://evil.example.com"))
                .andExpect(status().isForbidden());

        assertThat(spipSettingsService.isBaseUrlOverridden()).isFalse();
    }

    private UsernamePasswordAuthenticationToken auth(User user, String role) {
        return new UsernamePasswordAuthenticationToken(user, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private User saveUser(String username, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFirstName("Settings");
        user.setLastName("Tester");
        user.setPassword("$2a$10$test");
        user.setRole(role);
        user.setEnabled(true);
        return userRepository.save(user);
    }
}
