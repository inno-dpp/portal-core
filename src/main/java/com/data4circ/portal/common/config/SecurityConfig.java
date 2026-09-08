package com.data4circ.portal.common.config;

import com.data4circ.portal.common.security.CustomAuthenticationFailureHandler;
import com.data4circ.portal.common.security.SecurityRuleContributor;
import com.data4circ.portal.features.organization.service.UserService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final CorsConfigurationSource corsConfigurationSource;
    private final CustomAuthenticationFailureHandler authenticationFailureHandler;
    private final List<SecurityRuleContributor> securityRuleContributors;

    public SecurityConfig(UserService userService, PasswordEncoder passwordEncoder,
                         @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource,
                         CustomAuthenticationFailureHandler authenticationFailureHandler,
                         List<SecurityRuleContributor> securityRuleContributors) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.corsConfigurationSource = corsConfigurationSource;
        this.authenticationFailureHandler = authenticationFailureHandler;
        this.securityRuleContributors = securityRuleContributors;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
            // CSRF Protection Configuration
            // ENABLED by default for all endpoints except those explicitly ignored below
            // Thymeleaf automatically adds CSRF tokens to forms using th:action
            .csrf(csrf -> csrf
                // H2 Console (development only) - CSRF disabled for embedded console
                .ignoringRequestMatchers("/h2-console/**")
                // Public API endpoints - CSRF disabled for stateless API calls
                .ignoringRequestMatchers("/api/onboarding/**")
                // Notification API endpoints - CSRF disabled for REST API calls
                .ignoringRequestMatchers("/api/notifications/**")
                // Connector API endpoints - CSRF disabled for external calls
                .ignoringRequestMatchers("/api/connectors/**")
                // All other endpoints (including /admin/**) have CSRF protection ENABLED
            )
            .authorizeHttpRequests(authz -> {
                // Public endpoints
                authz.requestMatchers("/login", "/register", "/join", "/forgot-password", "/reset-password", "/css/**", "/js/**", "/images/**", "/assets/**", "/webjars/**").permitAll();
                authz.requestMatchers("/h2-console/**").permitAll();
                authz.requestMatchers("/api/onboarding/**").permitAll();
                authz.requestMatchers("/api/connectors/**").permitAll();
                authz.requestMatchers("/api/nace-codes/**").permitAll();

                // Actuator endpoints - health and info are public, others require admin
                authz.requestMatchers("/actuator/health", "/actuator/info").permitAll();
                authz.requestMatchers("/actuator/**").hasRole("PLATFORM_ADMIN");

                // Admin only endpoints
                authz.requestMatchers("/admin/**").hasRole("PLATFORM_ADMIN");

                // Organization admin endpoints
                authz.requestMatchers("/organization/admin/**").hasAnyRole("PLATFORM_ADMIN", "ORG_ADMIN");

                // Module-contributed rules (e.g. SPIP's /spip/** rule registered from its
                // own package) — applied before the catch-all so a module can narrow what
                // it would otherwise allow. No hard-coded per-module line here: see
                // SecurityRuleContributor.
                securityRuleContributors.forEach(contributor -> contributor.contribute(authz));

                // All other requests require authentication
                authz.anyRequest().authenticated();
            })
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .failureHandler(authenticationFailureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .clearAuthentication(true)
                .permitAll()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .authenticationProvider(authenticationProvider());

        // For H2 Console in development
        http.headers(headers -> headers.frameOptions().disable());

        return http.build();
    }
}