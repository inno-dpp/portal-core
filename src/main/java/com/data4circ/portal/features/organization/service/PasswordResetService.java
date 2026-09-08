package com.data4circ.portal.features.organization.service;

import com.data4circ.portal.features.onboardingsync.keycloak.KeycloakPasswordMirrorService;
import com.data4circ.portal.features.organization.dto.ResetPasswordDto;
import com.data4circ.portal.features.organization.entity.CertificationStatus;
import com.data4circ.portal.features.organization.entity.PasswordResetToken;
import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.organization.repository.PasswordResetTokenRepository;
import com.data4circ.portal.features.organization.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);

    private static final int TOKEN_EXPIRY_HOURS = 1;
    private static final int MAX_REQUESTS_PER_HOUR = 3;

    /** Onboarding set-password links are valid for 24h, matching the audit recommendation. */
    public static final int ONBOARDING_TOKEN_EXPIRY_HOURS = 24;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final KeycloakPasswordMirrorService keycloakPasswordMirrorService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                EmailService emailService,
                                PasswordEncoder passwordEncoder,
                                KeycloakPasswordMirrorService keycloakPasswordMirrorService) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.keycloakPasswordMirrorService = keycloakPasswordMirrorService;
    }

    /**
     * Initiate password reset for the given email.
     * Always returns success to prevent email enumeration.
     *
     * @param email the email address to send reset link to
     * @return true (always returns success to prevent email enumeration)
     */
    @Transactional
    public boolean initiatePasswordReset(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            logger.info("Password reset requested for non-existent email: {}", email);
            return true; // Don't reveal that email doesn't exist
        }

        User user = userOptional.get();

        // Block reset for users with INACTIVE organization
        if (user.getOrganization() != null &&
            user.getOrganization().getCertificationStatus() == CertificationStatus.INACTIVE) {
            logger.info("Password reset blocked for user with inactive organization: {}", email);
            return true; // Don't reveal organization status
        }

        // Check rate limiting
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long recentRequests = tokenRepository.countRecentRequestsByEmail(email, oneHourAgo);

        if (recentRequests >= MAX_REQUESTS_PER_HOUR) {
            logger.warn("Rate limit exceeded for password reset requests: {}", email);
            return true; // Don't reveal rate limiting
        }

        // Invalidate any existing tokens for this user
        tokenRepository.invalidateAllTokensForUser(user);

        // Create new token (2 UUIDs concatenated for 64 characters)
        String token = UUID.randomUUID().toString().replace("-", "") +
                       UUID.randomUUID().toString().replace("-", "");

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setExpiresAt(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS));

        tokenRepository.save(resetToken);

        // Build reset link
        String resetLink = baseUrl + "/reset-password?token=" + token;

        // Send email
        try {
            emailService.sendForgotPasswordLink(user.getEmail(), user.getFullName(), resetLink);
            logger.info("Password reset email sent to: {}", email);
        } catch (Exception e) {
            logger.error("Failed to send password reset email to {}: {}", email, e.getMessage());
            // Still return true - don't reveal email sending failure
        }

        return true;
    }

    /**
     * Mint a one-time, expiring link that lets a (typically newly created) user set their own
     * password. Reuses the password-reset token infrastructure so the existing {@code /reset-password}
     * page handles it; on completion {@code mustChangePassword} is cleared.
     *
     * <p>Unlike {@link #initiatePasswordReset(String)} this is an admin/system-triggered action, so it
     * skips email-enumeration protection and rate limiting. It does not send an email &mdash; the
     * caller embeds the returned link in the onboarding approval email.</p>
     *
     * @param user        the persisted user the link is for (must have an id)
     * @param expiryHours how long the link stays valid
     * @return the absolute set-password URL
     */
    @Transactional
    public String createSetPasswordLink(User user, int expiryHours) {
        // Invalidate any existing tokens so only the freshest link works.
        tokenRepository.invalidateAllTokensForUser(user);

        // Create new token (2 UUIDs concatenated for 64 characters)
        String token = UUID.randomUUID().toString().replace("-", "") +
                       UUID.randomUUID().toString().replace("-", "");

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setExpiresAt(LocalDateTime.now().plusHours(expiryHours));
        tokenRepository.save(resetToken);

        logger.info("Set-password link created for user {} (valid {}h)", user.getUsername(), expiryHours);
        return baseUrl + "/reset-password?token=" + token;
    }

    /**
     * Whether this token belongs to a user who has never set their own password
     * ({@code mustChangePassword == true}) &mdash; i.e. a first-time activation rather than a
     * password reset. Used to make the reset-password page wording context-aware.
     *
     * <p>Reads the lazily-loaded {@code user} inside the transaction to avoid
     * {@code LazyInitializationException} under {@code open-in-view: false}.</p>
     *
     * @param token the token to inspect
     * @return true if the token is valid and the user must set a password for the first time
     */
    @Transactional(readOnly = true)
    public boolean isFirstTimeSetup(String token) {
        return validateToken(token)
                .map(t -> t.getUser().isMustChangePassword())
                .orElse(false);
    }

    /**
     * Validate a password reset token.
     *
     * @param token the token to validate
     * @return the token if valid, empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<PasswordResetToken> validateToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        Optional<PasswordResetToken> resetToken = tokenRepository.findByTokenAndUsedFalse(token);

        if (resetToken.isEmpty()) {
            logger.debug("Token not found or already used: {}", token.substring(0, Math.min(token.length(), 8)) + "...");
            return Optional.empty();
        }

        if (resetToken.get().isExpired()) {
            logger.debug("Token expired: {}", token.substring(0, Math.min(token.length(), 8)) + "...");
            return Optional.empty();
        }

        return resetToken;
    }

    /**
     * Reset the password using a valid token.
     *
     * @param dto the reset password DTO containing token and new password
     * @return true if password was reset successfully
     */
    @Transactional
    public boolean resetPassword(ResetPasswordDto dto) {
        Optional<PasswordResetToken> tokenOptional = validateToken(dto.getToken());

        if (tokenOptional.isEmpty()) {
            logger.warn("Invalid or expired token used for password reset");
            return false;
        }

        PasswordResetToken resetToken = tokenOptional.get();
        User user = resetToken.getUser();
        boolean firstActivation = user.isMustChangePassword();

        // Update password
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        // Mark token as used
        resetToken.setUsed(true);
        resetToken.setUsedAt(LocalDateTime.now());
        tokenRepository.save(resetToken);

        // Invalidate any other tokens for this user
        tokenRepository.invalidateAllTokensForUser(user);

        // On first-login activation, optionally mirror the password into the user's
        // onboarding-provisioned Keycloak account (best-effort, never throws). Runs
        // after commit so a slow/hung Keycloak cannot hold this transaction open.
        if (firstActivation) {
            String newPassword = dto.getNewPassword();
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        keycloakPasswordMirrorService.mirrorPasswordIfLinked(user, newPassword);
                    }
                });
            } else {
                keycloakPasswordMirrorService.mirrorPasswordIfLinked(user, newPassword);
            }
        }

        logger.info("Password successfully reset for user: {}", user.getUsername());
        return true;
    }

    /**
     * Cleanup expired tokens. Runs daily at 3 AM.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7); // Keep tokens for 7 days for audit
        tokenRepository.deleteExpiredTokens(cutoff);
        logger.info("Cleaned up expired password reset tokens older than {}", cutoff);
    }
}
