package com.data4circ.portal.features.platformsettings.controller;

import com.data4circ.portal.features.category.service.CategoryService;
import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.platformsettings.service.CkanSettingsService;
import com.data4circ.portal.features.platformsettings.service.KeycloakSettingsService;
import com.data4circ.portal.features.platformsettings.service.SpipSettingsService;
import com.data4circ.portal.integration.ckan.client.CkanApiClient;
import com.data4circ.portal.integration.keycloak.KeycloakInstance;
import com.data4circ.portal.integration.keycloak.client.KeycloakApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Platform admin settings page. Hosts the CKAN connection settings (default base
 * URL and admin API token), the SPIP platform settings (base URL, customer tag,
 * admin credentials, default role) and the default Keycloak instance settings
 * (base URL, realm, admin client).
 */
@Controller
@RequestMapping("/admin/settings")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformSettingsController {

    private static final Logger logger = LoggerFactory.getLogger(PlatformSettingsController.class);

    private final CkanSettingsService ckanSettingsService;
    private final CkanApiClient ckanApiClient;
    private final SpipSettingsService spipSettingsService;
    private final KeycloakSettingsService keycloakSettingsService;
    private final KeycloakApiClient keycloakApiClient;
    private final CategoryService categoryService;

    public PlatformSettingsController(CkanSettingsService ckanSettingsService,
                                      CkanApiClient ckanApiClient,
                                      SpipSettingsService spipSettingsService,
                                      KeycloakSettingsService keycloakSettingsService,
                                      KeycloakApiClient keycloakApiClient,
                                      CategoryService categoryService) {
        this.ckanSettingsService = ckanSettingsService;
        this.ckanApiClient = ckanApiClient;
        this.spipSettingsService = spipSettingsService;
        this.keycloakSettingsService = keycloakSettingsService;
        this.keycloakApiClient = keycloakApiClient;
        this.categoryService = categoryService;
    }

    @GetMapping
    public String showSettings(Model model) {
        model.addAttribute("ckanBaseUrl", ckanSettingsService.getBaseUrl());
        model.addAttribute("ckanEnvBaseUrl", ckanSettingsService.getEnvironmentBaseUrl());
        model.addAttribute("ckanBaseUrlOverridden", ckanSettingsService.isBaseUrlOverridden());
        model.addAttribute("ckanTokenConfigured", ckanSettingsService.isJwtTokenConfigured());
        model.addAttribute("ckanTokenOverridden", ckanSettingsService.isJwtTokenOverridden());
        model.addAttribute("ckanTokenPreview", ckanSettingsService.getJwtTokenPreview());

        model.addAttribute("spipBaseUrl", spipSettingsService.getBaseUrl());
        model.addAttribute("spipEnvBaseUrl", spipSettingsService.getEnvironmentBaseUrl());
        model.addAttribute("spipBaseUrlOverridden", spipSettingsService.isBaseUrlOverridden());
        model.addAttribute("spipCustomerTag", spipSettingsService.getCustomerTag());
        model.addAttribute("spipCustomerTagOverridden", spipSettingsService.isCustomerTagOverridden());
        model.addAttribute("spipAdminUsername", spipSettingsService.getAdminUsername());
        model.addAttribute("spipAdminUsernameOverridden", spipSettingsService.isAdminUsernameOverridden());
        model.addAttribute("spipAdminPasswordConfigured", spipSettingsService.isAdminPasswordConfigured());
        model.addAttribute("spipAdminPasswordOverridden", spipSettingsService.isAdminPasswordOverridden());
        model.addAttribute("spipDefaultRole", spipSettingsService.getDefaultRole());
        model.addAttribute("spipDefaultRoleOverridden", spipSettingsService.isDefaultRoleOverridden());

        model.addAttribute("keycloakBaseUrl", keycloakSettingsService.getBaseUrl());
        model.addAttribute("keycloakEnvBaseUrl", keycloakSettingsService.getEnvironmentBaseUrl());
        model.addAttribute("keycloakBaseUrlOverridden", keycloakSettingsService.isBaseUrlOverridden());
        model.addAttribute("keycloakRealm", keycloakSettingsService.getRealm());
        model.addAttribute("keycloakRealmOverridden", keycloakSettingsService.isRealmOverridden());
        model.addAttribute("keycloakClientId", keycloakSettingsService.getClientId());
        model.addAttribute("keycloakClientIdOverridden", keycloakSettingsService.isClientIdOverridden());
        model.addAttribute("keycloakSecretConfigured", keycloakSettingsService.isClientSecretConfigured());
        model.addAttribute("keycloakSecretOverridden", keycloakSettingsService.isClientSecretOverridden());

        long activeCategoryCount = categoryService.findAllActive().size();
        long totalCategoryCount = categoryService.findAll().size();
        model.addAttribute("activeCategoryCount", activeCategoryCount);
        model.addAttribute("totalCategoryCount", totalCategoryCount);

        model.addAttribute("title", "Platform Settings");
        return "admin/settings";
    }

    @PostMapping("/ckan")
    public String updateCkanSettings(@RequestParam(required = false) String baseUrl,
                                     @RequestParam(required = false) String jwtToken,
                                     @AuthenticationPrincipal User currentUser,
                                     RedirectAttributes redirectAttributes) {
        try {
            ckanSettingsService.updateSettings(baseUrl, jwtToken, currentUser.getUsername());
            redirectAttributes.addFlashAttribute("message", "CKAN connection settings saved.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/ckan/reset")
    public String resetCkanSettings(@AuthenticationPrincipal User currentUser,
                                    RedirectAttributes redirectAttributes) {
        ckanSettingsService.resetToEnvironmentDefaults(currentUser.getUsername());
        redirectAttributes.addFlashAttribute("message",
                "CKAN connection settings reset to environment defaults.");
        return "redirect:/admin/settings";
    }

    @PostMapping("/ckan/test")
    public String testCkanConnection(RedirectAttributes redirectAttributes) {
        if (!ckanSettingsService.isJwtTokenConfigured()) {
            redirectAttributes.addFlashAttribute("error",
                    "No CKAN API token configured. Save a token before testing the connection.");
            return "redirect:/admin/settings";
        }

        String baseUrl = ckanSettingsService.getBaseUrl();
        Integer count = ckanApiClient.getDatasetCount(ckanSettingsService.getJwtToken());
        if (count != null) {
            redirectAttributes.addFlashAttribute("message",
                    "Connection to " + baseUrl + " succeeded — CKAN reports " + count + " dataset(s).");
        } else {
            logger.warn("CKAN connection test against {} failed", baseUrl);
            redirectAttributes.addFlashAttribute("error",
                    "Connection to " + baseUrl + " failed. Check the base URL and API token (see logs for details).");
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/spip")
    public String updateSpipSettings(@RequestParam(required = false) String baseUrl,
                                     @RequestParam(required = false) String customerTag,
                                     @RequestParam(required = false) String adminUsername,
                                     @RequestParam(required = false) String adminPassword,
                                     @RequestParam(required = false) String defaultRole,
                                     @AuthenticationPrincipal User currentUser,
                                     RedirectAttributes redirectAttributes) {
        try {
            spipSettingsService.updateSettings(baseUrl, customerTag, adminUsername,
                    adminPassword, defaultRole, currentUser.getUsername());
            redirectAttributes.addFlashAttribute("message", "SPIP platform settings saved.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/spip/reset")
    public String resetSpipSettings(@AuthenticationPrincipal User currentUser,
                                    RedirectAttributes redirectAttributes) {
        spipSettingsService.resetToEnvironmentDefaults(currentUser.getUsername());
        redirectAttributes.addFlashAttribute("message",
                "SPIP platform settings reset to environment defaults.");
        return "redirect:/admin/settings";
    }

    @PostMapping("/keycloak")
    public String updateKeycloakSettings(@RequestParam(required = false) String baseUrl,
                                         @RequestParam(required = false) String realm,
                                         @RequestParam(required = false) String clientId,
                                         @RequestParam(required = false) String clientSecret,
                                         @AuthenticationPrincipal User currentUser,
                                         RedirectAttributes redirectAttributes) {
        try {
            keycloakSettingsService.updateSettings(baseUrl, realm, clientId, clientSecret,
                    currentUser.getUsername());
            redirectAttributes.addFlashAttribute("message", "Keycloak connection settings saved.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/keycloak/reset")
    public String resetKeycloakSettings(@AuthenticationPrincipal User currentUser,
                                        RedirectAttributes redirectAttributes) {
        keycloakSettingsService.resetToEnvironmentDefaults(currentUser.getUsername());
        redirectAttributes.addFlashAttribute("message",
                "Keycloak connection settings reset to environment defaults.");
        return "redirect:/admin/settings";
    }

    @PostMapping("/keycloak/test")
    public String testKeycloakConnection(RedirectAttributes redirectAttributes) {
        if (!keycloakSettingsService.isClientSecretConfigured()) {
            redirectAttributes.addFlashAttribute("error",
                    "No Keycloak client secret configured. Save a secret before testing the connection.");
            return "redirect:/admin/settings";
        }

        String baseUrl = keycloakSettingsService.getBaseUrl();
        try {
            KeycloakInstance instance = new KeycloakInstance(
                    baseUrl,
                    keycloakSettingsService.getRealm(),
                    keycloakSettingsService.getClientId(),
                    keycloakSettingsService.getClientSecret());
            keycloakApiClient.obtainAdminToken(instance);
            redirectAttributes.addFlashAttribute("message",
                    "Connection to " + baseUrl + " succeeded — Keycloak admin login OK.");
        } catch (Exception e) {
            logger.warn("Keycloak connection test against {} failed: {}", baseUrl, e.getMessage());
            redirectAttributes.addFlashAttribute("error",
                    "Connection to " + baseUrl + " failed: " + e.getMessage());
        }
        return "redirect:/admin/settings";
    }
}
