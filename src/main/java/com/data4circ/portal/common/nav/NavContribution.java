package com.data4circ.portal.common.nav;

import com.data4circ.portal.features.organization.entity.UserRole;

import java.util.Set;

/**
 * A pluggable top-level navbar item. A module (e.g. SPIP) registers a bean of this type
 * instead of {@code fragments/navigation.html} hard-coding a per-module {@code <li>} —
 * the fragment renders whatever is contributed generically via
 * {@link com.data4circ.portal.common.config.GlobalModelAttributesAdvice#addNavContributions}.
 * Wrapping the module's contribution in {@code @ConditionalOnProperty} makes the nav item
 * disappear exactly when the module's other beans do — module-gated rather than only
 * role-gated.
 *
 * @param activePageKey  matches the nav fragment's {@code activePage} parameter so the
 *                       item is highlighted "active" on its own pages
 * @param href           link target, e.g. {@code "/spip"}
 * @param label          visible text, e.g. {@code "SPIP"}
 * @param iconClass      Font Awesome class, e.g. {@code "fas fa-shield-alt"}
 * @param requiredRoles  roles that can see this item; checked against the current user's
 *                       single {@link UserRole} server-side (no client-visible markup for
 *                       roles the user doesn't have, unlike a CSS-hidden approach)
 * @param order          ascending display order among contributed items (ties broken by
 *                       registration order)
 */
public record NavContribution(
        String activePageKey,
        String href,
        String label,
        String iconClass,
        Set<UserRole> requiredRoles,
        int order) {
}
