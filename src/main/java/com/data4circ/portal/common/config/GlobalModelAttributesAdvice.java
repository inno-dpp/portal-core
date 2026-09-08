package com.data4circ.portal.common.config;

import com.data4circ.portal.common.nav.NavContribution;
import com.data4circ.portal.features.collaboration.entity.CollaborationStatus;
import com.data4circ.portal.features.collaboration.repository.CollaborationRequestRepository;
import com.data4circ.portal.features.organization.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Comparator;
import java.util.List;

/**
 * Global controller advice to add common model attributes to all views.
 * This makes certain values available to all Thymeleaf templates automatically.
 */
@ControllerAdvice
public class GlobalModelAttributesAdvice {

    @Value("${app.version}")
    private String appVersion;

    @Autowired
    private ThemeProperties themeProperties;

    @Autowired
    private CollaborationRequestRepository collaborationRequestRepository;

    // Pluggable top-level navbar items (e.g. SPIP's) — see NavContribution and
    // fragments/navigation.html's th:each over ${navContributions}. required = false:
    // a required List<T> autowiring still demands at least one matching bean, which
    // fails outright when every contributor (e.g. just SPIP today) is disabled — the
    // list must be able to be genuinely empty.
    @Autowired(required = false)
    private List<NavContribution> navContributions = List.of();

    /**
     * Adds the application version to all model attributes.
     * This makes the version available in all Thymeleaf templates as ${appVersion}.
     *
     * @return The application version string
     */
    @ModelAttribute("appVersion")
    public String addAppVersion() {
        return appVersion;
    }

    /**
     * Adds branding configuration to all model attributes.
     * This makes branding properties available in all Thymeleaf templates as ${branding}.
     *
     * @return The theme properties containing branding configuration
     */
    @ModelAttribute("branding")
    public ThemeProperties addBranding() {
        return themeProperties;
    }

    /**
     * Adds the top-level navbar items contributed by modules the current user is allowed
     * to see, in display order. Available in all templates as ${navContributions}; the nav
     * fragment renders each generically instead of the core hard-coding a per-module item.
     */
    @ModelAttribute("navContributions")
    public List<NavContribution> addNavContributions(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return List.of();
        }
        return navContributions.stream()
                .filter(item -> item.requiredRoles().contains(currentUser.getRole()))
                .sorted(Comparator.comparingInt(NavContribution::order))
                .toList();
    }

    /**
     * Adds the number of pending incoming collaboration requests for the current user's
     * organization. Available in all templates as ${pendingCollabCount}.
     */
    @ModelAttribute("pendingCollabCount")
    public long addPendingCollabCount(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null || currentUser.getOrganization() == null) {
            return 0L;
        }
        try {
            return collaborationRequestRepository.countByTargetOrgIdAndStatus(
                    currentUser.getOrganization().getId(), CollaborationStatus.PENDING);
        } catch (Exception e) {
            return 0L;
        }
    }
}
