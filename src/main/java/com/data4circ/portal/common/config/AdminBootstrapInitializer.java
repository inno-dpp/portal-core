package com.data4circ.portal.common.config;

import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.organization.entity.UserRole;
import com.data4circ.portal.features.organization.repository.UserRepository;
import com.data4circ.portal.features.organization.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the initial platform administrator on first startup from
 * environment-provided credentials, so production deployments never rely
 * on demo users.
 *
 * <p>Configuration (app.bootstrap.admin.* / env vars):
 * ADMIN_USERNAME, ADMIN_PASSWORD, ADMIN_EMAIL (optional).</p>
 *
 * <p>Runs before {@link DataInitializer}. If no platform admin exists and
 * no credentials are configured, the application still starts but logs a
 * prominent warning.</p>
 */
@Component
public class AdminBootstrapInitializer {

    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrapInitializer.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Value("${app.bootstrap.admin.username:}")
    private String username;

    @Value("${app.bootstrap.admin.password:}")
    private String password;

    @Value("${app.bootstrap.admin.email:}")
    private String email;

    @Value("${app.demo-data.enabled:false}")
    private boolean demoDataEnabled;

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Transactional
    public void bootstrapAdmin() {
        if (!userRepository.findByRole(UserRole.PLATFORM_ADMIN).isEmpty()) {
            return;
        }

        if (username.isBlank() || password.isBlank()) {
            if (demoDataEnabled) {
                // DataInitializer will seed the demo admin right after this listener
                logger.info("No bootstrap admin configured; demo data is enabled and provides one.");
            } else {
                logger.warn("No PLATFORM_ADMIN user exists and app.bootstrap.admin.username/password "
                        + "(ADMIN_USERNAME/ADMIN_PASSWORD env vars) are not set. Nobody can administer "
                        + "this portal until an admin is created. Set the variables and restart, or "
                        + "enable demo data for local development (app.demo-data.enabled=true).");
            }
            return;
        }

        if (userRepository.existsByUsername(username)) {
            logger.warn("Cannot bootstrap platform admin: username '{}' already exists without "
                    + "the PLATFORM_ADMIN role.", username);
            return;
        }

        User admin = new User();
        admin.setUsername(username);
        admin.setEmail(email.isBlank() ? username + "@localhost" : email);
        admin.setFirstName("Platform");
        admin.setLastName("Administrator");
        admin.setRole(UserRole.PLATFORM_ADMIN);
        admin.setEnabled(true);
        admin.setAccountNonExpired(true);
        admin.setAccountNonLocked(true);
        admin.setCredentialsNonExpired(true);
        admin.setPassword(userService.encodePassword(password));
        userRepository.save(admin);

        logger.info("Bootstrapped initial platform admin '{}'", username);
    }
}
