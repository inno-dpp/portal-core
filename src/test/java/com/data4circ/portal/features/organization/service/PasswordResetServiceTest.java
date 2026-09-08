package com.data4circ.portal.features.organization.service;

import com.data4circ.portal.features.onboardingsync.keycloak.KeycloakPasswordMirrorService;
import com.data4circ.portal.features.organization.dto.ResetPasswordDto;
import com.data4circ.portal.features.organization.entity.PasswordResetToken;
import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.organization.repository.PasswordResetTokenRepository;
import com.data4circ.portal.features.organization.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PasswordResetService.
 *
 * Tests focus on edge cases and error handling scenarios.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private KeycloakPasswordMirrorService keycloakPasswordMirrorService;

    @InjectMocks
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        // Default mock setup
        when(tokenRepository.findByTokenAndUsedFalse(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("validateToken should handle short tokens without throwing exception")
    void validateToken_shortToken_noException() {
        // Given: tokens shorter than 8 characters
        String shortToken1 = "abc";
        String shortToken2 = "xy";
        String shortToken3 = "";

        // When: validating these tokens
        var result1 = service.validateToken(shortToken1);
        var result2 = service.validateToken(shortToken2);
        var result3 = service.validateToken(shortToken3);

        // Then: no exception should be thrown and all should return empty
        assertThat(result1).isEmpty();
        assertThat(result2).isEmpty();
        assertThat(result3).isEmpty();
    }

    @Test
    @DisplayName("validateToken should handle null token gracefully")
    void validateToken_nullToken_returnsEmpty() {
        // When: validating null token
        var result = service.validateToken(null);

        // Then: should return empty without exception
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("validateToken should handle tokens of exactly 8 characters")
    void validateToken_exactlyEightCharacters_noException() {
        // Given: token with exactly 8 characters
        String token = "12345678";

        // When: validating the token
        var result = service.validateToken(token);

        // Then: no exception should be thrown
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("validateToken should handle tokens longer than 8 characters")
    void validateToken_longToken_noException() {
        // Given: token longer than 8 characters
        String token = "this-is-a-very-long-token-string";

        // When: validating the token
        var result = service.validateToken(token);

        // Then: no exception should be thrown
        assertThat(result).isEmpty();
    }

    private ResetPasswordDto resetDtoForUser(User user) {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("valid-token-12345");
        token.setUser(user);
        token.setExpiresAt(java.time.LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByTokenAndUsedFalse("valid-token-12345")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");

        ResetPasswordDto dto = new ResetPasswordDto();
        dto.setToken("valid-token-12345");
        dto.setNewPassword("New#Password1");
        dto.setConfirmPassword("New#Password1");
        return dto;
    }

    @Test
    @DisplayName("resetPassword mirrors the password to Keycloak on first-login activation")
    void resetPassword_firstActivation_mirrorsToKeycloak() {
        User user = new User();
        user.setUsername("erika");
        user.setMustChangePassword(true);

        boolean result = service.resetPassword(resetDtoForUser(user));

        assertThat(result).isTrue();
        org.mockito.Mockito.verify(keycloakPasswordMirrorService)
                .mirrorPasswordIfLinked(user, "New#Password1");
    }

    @Test
    @DisplayName("resetPassword does not mirror to Keycloak on an ordinary reset")
    void resetPassword_ordinaryReset_doesNotMirror() {
        User user = new User();
        user.setUsername("erika");
        user.setMustChangePassword(false);

        boolean result = service.resetPassword(resetDtoForUser(user));

        assertThat(result).isTrue();
        org.mockito.Mockito.verifyNoInteractions(keycloakPasswordMirrorService);
    }
}
