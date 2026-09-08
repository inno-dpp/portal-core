package com.data4circ.portal.common.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Extension point for a module (e.g. SPIP) to contribute its own URL-pattern authorization
 * rule to the core {@code SecurityFilterChain}, instead of {@code SecurityConfig}
 * hard-coding a per-module {@code .requestMatchers(...)} line.
 *
 * <p>Contributions are applied, in Spring's bean-definition order, after the core's own
 * explicit rules and before the final {@code .anyRequest().authenticated()} catch-all —
 * so a contributed rule can narrow what the catch-all would otherwise allow (e.g. restrict
 * a path to a privileged role) but should not be relied on to widen access beyond it.
 * Wrapping the module's contributor bean in {@code @ConditionalOnProperty} means that when
 * the module is disabled, its rule simply isn't registered; requests to its (non-existent)
 * controller then fall through to the catch-all and 404 rather than ever reaching
 * module-specific authorization logic.</p>
 */
@FunctionalInterface
public interface SecurityRuleContributor {

    void contribute(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry);
}
