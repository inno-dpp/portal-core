package com.data4circ.portal.common.security;

import com.data4circ.portal.features.organization.entity.OnboardingRequestStatus;
import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.organization.repository.OnboardingRequestRepository;
import com.data4circ.portal.features.organization.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OnboardingRequestRepository onboardingRequestRepository;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                      AuthenticationException exception) throws IOException, ServletException {

        String errorMessage;

        if (exception instanceof DisabledException) {
            errorMessage = "Account is temporarily unavailable due to organization status.";
        } else if (exception instanceof BadCredentialsException) {
            errorMessage = resolveBadCredentialsMessage(request.getParameter("username"));
        } else {
            errorMessage = "Authentication failed. Please try again.";
        }

        String encodedMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
        setDefaultFailureUrl("/login?error=true&message=" + encodedMessage);

        super.onAuthenticationFailure(request, response, exception);
    }

    /**
     * Bad credentials covers two very different situations besides a simple typo: an account whose
     * password still needs to be set (fresh approval or an admin-triggered reset both go through
     * mustChangePassword) and an email that has no account yet because its onboarding request is
     * still pending. Both are worth naming explicitly instead of a generic "wrong password", since
     * the visitor may have just come from the onboarding flow and not know which state they're in.
     */
    private String resolveBadCredentialsMessage(String username) {
        if (username == null || username.isBlank()) {
            return "Email or password is incorrect.";
        }
        String trimmed = username.trim();

        Optional<User> user = userRepository.findByUsername(trimmed);
        if (user.isPresent()) {
            if (user.get().isMustChangePassword()) {
                return "Your password needs to be set or reset. Check your email for instructions.";
            }
            return "Email or password is incorrect.";
        }

        return onboardingRequestRepository.findByEmail(trimmed)
            .filter(req -> req.getStatus() == OnboardingRequestStatus.PENDING)
            .map(req -> "Your access request is still under review.")
            .orElse("Email or password is incorrect.");
    }
}
