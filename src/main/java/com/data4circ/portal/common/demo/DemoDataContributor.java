package com.data4circ.portal.common.demo;

import com.data4circ.portal.features.organization.entity.Organization;

/**
 * Extension point for a module to seed its own demo data for a sample organization,
 * alongside the ones {@code DataInitializer} creates when {@code app.demo-data.enabled=true}.
 *
 * <p>Same "ask by capability, not by tool" shape as {@link com.data4circ.portal.common.nav.NavContribution}
 * and {@link com.data4circ.portal.common.security.SecurityRuleContributor}: {@code DataInitializer}
 * depends on {@code List<DemoDataContributor>} rather than a specific module's facade, so a
 * module's demo-data hook — like SPIP's mock attributes/policies/key statuses — lives entirely
 * in that module's own package and simply isn't in the list when the module is disabled.</p>
 */
public interface DemoDataContributor {

    void seedDemoData(Organization organization);
}
