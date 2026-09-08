package com.data4circ.portal.common.config;

import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.organization.entity.UserRole;
import com.data4circ.portal.features.organization.repository.UserRepository;
import com.data4circ.portal.features.organization.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private AdminBootstrapInitializer initializer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(initializer, "username", "");
        ReflectionTestUtils.setField(initializer, "password", "");
        ReflectionTestUtils.setField(initializer, "email", "");
    }

    @Test
    void doesNothingWhenPlatformAdminAlreadyExists() {
        when(userRepository.findByRole(UserRole.PLATFORM_ADMIN)).thenReturn(List.of(new User()));

        initializer.bootstrapAdmin();

        verify(userRepository, never()).save(any());
    }

    @Test
    void warnsButDoesNotFailWhenCredentialsUnset() {
        when(userRepository.findByRole(UserRole.PLATFORM_ADMIN)).thenReturn(Collections.emptyList());

        initializer.bootstrapAdmin();

        verify(userRepository, never()).save(any());
    }

    @Test
    void createsAdminWithEncodedPasswordWhenCredentialsSet() {
        when(userRepository.findByRole(UserRole.PLATFORM_ADMIN)).thenReturn(Collections.emptyList());
        when(userRepository.existsByUsername("root")).thenReturn(false);
        when(userService.encodePassword("s3cret!")).thenReturn("{bcrypt}encoded");
        ReflectionTestUtils.setField(initializer, "username", "root");
        ReflectionTestUtils.setField(initializer, "password", "s3cret!");
        ReflectionTestUtils.setField(initializer, "email", "root@example.com");

        initializer.bootstrapAdmin();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("root", saved.getUsername());
        assertEquals("root@example.com", saved.getEmail());
        assertEquals(UserRole.PLATFORM_ADMIN, saved.getRole());
        assertEquals("{bcrypt}encoded", saved.getPassword());
        assertTrue(saved.isEnabled());
    }

    @Test
    void skipsWhenUsernameTakenByNonAdminUser() {
        when(userRepository.findByRole(UserRole.PLATFORM_ADMIN)).thenReturn(Collections.emptyList());
        when(userRepository.existsByUsername("root")).thenReturn(true);
        ReflectionTestUtils.setField(initializer, "username", "root");
        ReflectionTestUtils.setField(initializer, "password", "s3cret!");

        initializer.bootstrapAdmin();

        verify(userRepository, never()).save(any());
    }
}
