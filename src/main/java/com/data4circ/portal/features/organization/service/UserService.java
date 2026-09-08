package com.data4circ.portal.features.organization.service;

import com.data4circ.portal.features.notification.repository.NotificationRepository;
import com.data4circ.portal.features.organization.entity.CertificationStatus;
import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.organization.repository.PasswordResetTokenRepository;
import com.data4circ.portal.features.organization.repository.UserRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationRepository notificationRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       NotificationRepository notificationRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationRepository = notificationRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameWithOrganization(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Check if user belongs to an organization with INACTIVE status
        if (user.getOrganization() != null &&
            user.getOrganization().getCertificationStatus() == CertificationStatus.INACTIVE) {
            throw new DisabledException("Account is temporarily unavailable due to organization status: " +
                                       user.getOrganization().getCertificationStatus().getDisplayName());
        }

        return user;
    }

    public User save(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Delete a user, first removing rows that reference the user via non-nullable foreign keys
     * (notifications, password-reset tokens). Without this cleanup the delete fails with a
     * foreign-key constraint violation. Runs in one transaction so a failure rolls the whole
     * thing back.
     */
    @Transactional
    public void deleteById(Long id) {
        notificationRepository.deleteByRecipientId(id);
        passwordResetTokenRepository.deleteByUserId(id);
        userRepository.deleteById(id);
    }

    public User update(User user) {
        return userRepository.save(user);
    }

    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public User saveWithoutEncoding(User user) {
        return userRepository.save(user);
    }

    public Optional<User> findByIdWithOrganization(Long id) {
        return userRepository.findByIdWithOrganization(id);
    }

    /**
     * Changes user password after verifying the current password.
     *
     * @param user The user whose password to change
     * @param currentPassword The current password for verification
     * @param newPassword The new password to set
     * @return true if password was changed successfully, false if current password verification failed
     */
    public boolean changePassword(User user, String currentPassword, String newPassword) {
        // Verify current password matches
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return false;
        }
        // Encode and save new password
        user.setPassword(passwordEncoder.encode(newPassword));
        // Clear the temporary password flag
        user.setMustChangePassword(false);
        userRepository.save(user);
        return true;
    }
}