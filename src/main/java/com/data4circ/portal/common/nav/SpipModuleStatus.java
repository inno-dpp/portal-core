package com.data4circ.portal.common.nav;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Whether the SPIP module is present in this deployment at all, independent of the
 * current user's role or which page is asking — inferred from the same (unfiltered)
 * contributed nav item list {@link NavContribution} beans register into, rather than a
 * second, SPIP-specific way to answer the same question. A module-disabled build (e.g.
 * portal-core with no spip-plugin) contributes no {@code /spip} nav item, so this is
 * {@code false} there with no SPIP-specific wiring needed here either.
 *
 * <p>Used to hide SPIP-only UI instead of showing it and letting it silently fail or
 * crash — see SPIP-PLUGIN-DECOUPLING-PLAN.md's testing guide, and
 * {@code GlobalModelAttributesAdvice#addSpipModuleEnabled} for the Thymeleaf-facing
 * {@code ${spipModuleEnabled}} attribute that delegates here.</p>
 */
@Component
public class SpipModuleStatus {

    // required = false: a required List<T> autowiring still demands at least one
    // matching bean, which fails outright when every contributor (e.g. just SPIP
    // today) is disabled — the list must be able to be genuinely empty.
    @Autowired(required = false)
    private List<NavContribution> navContributions = List.of();

    public boolean isEnabled() {
        return navContributions.stream().anyMatch(nav -> "/spip".equals(nav.href()));
    }
}
