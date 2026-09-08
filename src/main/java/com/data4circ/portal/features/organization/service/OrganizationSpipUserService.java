package com.data4circ.portal.features.organization.service;

import com.data4circ.portal.common.util.PasswordGenerator;
import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.organization.entity.OrganizationSpipUser;
import com.data4circ.portal.features.organization.repository.OnboardingRequestRepository;
import com.data4circ.portal.features.organization.repository.OrganizationSpipUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Optional;

/**
 * Service for managing SPIP user credentials for organizations.
 */
@Service
public class OrganizationSpipUserService {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationSpipUserService.class);
    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
    private static final int PASSWORD_LENGTH = 16;

    @Autowired
    private OrganizationSpipUserRepository spipUserRepository;

    @Autowired
    private OnboardingRequestRepository onboardingRequestRepository;

    /**
     * Creates a SPIP user for the given organization with auto-generated credentials.
     * Username is generated from the organization name (lowercase, no spaces).
     * Password is randomly generated and encrypted by JPA EncryptedStringConverter.
     *
     * @param organization The organization to create SPIP credentials for
     * @return The created OrganizationSpipUser
     */
    @Transactional
    public OrganizationSpipUser createSpipUserForOrganization(Organization organization) {
        if (organization == null) {
            throw new IllegalArgumentException("Organization cannot be null");
        }

        // Check if SPIP user already exists for this organization
        Optional<OrganizationSpipUser> existing = spipUserRepository.findByOrganization(organization);
        if (existing.isPresent()) {
            logger.warn("SPIP user already exists for organization: {}", organization.getName());
            return existing.get();
        }

        // Generate username from organization name
        String username = generateUsernameFromOrganizationName(organization.getName());

        // Generate random password (plaintext - encrypted by JPA EncryptedStringConverter)
        String password = generateRandomPassword();

        // Create the SPIP user entity
        OrganizationSpipUser spipUser = new OrganizationSpipUser();
        spipUser.setOrganization(organization);
        spipUser.setSpipUser(username);
        spipUser.setSpipPassword(password); // Plaintext - encrypted by JPA EncryptedStringConverter
        spipUser.setSpipSynchronized(false);

        // Save and return
        OrganizationSpipUser savedSpipUser = spipUserRepository.save(spipUser);
        logger.info("Created SPIP user '{}' for organization '{}'", username, organization.getName());

        return savedSpipUser;
    }

    /**
     * Generates a SPIP username from the organization's full name.
     * Must satisfy BOTH S3 bucket naming rules AND SPIP username rules:
     *
     * S3 bucket rules: lowercase letters, numbers, hyphens; start/end with alphanumeric
     * SPIP rules: letters, numbers, periods, underscores only
     *
     * Intersection (compatible with both): lowercase letters and numbers only
     *
     * Final rules applied:
     * - Only lowercase letters and numbers (no separators)
     * - 3-30 characters length
     * - Defaults to "org" if empty after processing
     *
     * @param organizationName The full name of the organization
     * @return Generated username (3-30 characters, S3 and SPIP compatible)
     */
    public String generateUsernameFromOrganizationName(String organizationName) {
        if (organizationName == null || organizationName.trim().isEmpty()) {
            throw new IllegalArgumentException("Organization name cannot be null or empty");
        }

        // Convert to lowercase first
        String baseUsername = organizationName.toLowerCase();

        // Remove all characters except lowercase letters and numbers
        // This satisfies both S3 (letters, numbers, hyphens) and SPIP (letters, numbers, periods, underscores)
        // by using only the common subset: letters and numbers
        baseUsername = baseUsername.replaceAll("[^a-z0-9]", "");

        // Ensure username is not empty after processing
        if (baseUsername.isEmpty()) {
            baseUsername = "org";
        }

        // Ensure minimum length of 3 characters (S3 requirement)
        while (baseUsername.length() < 3) {
            baseUsername = baseUsername + "0";
        }

        // Limit to 30 characters maximum (reserve space for potential counter suffix)
        final int MAX_USERNAME_LENGTH = 30;
        final int MAX_BASE_LENGTH = 27; // Reserve 3 chars for "999" if needed

        // Initial truncation to max length
        if (baseUsername.length() > MAX_USERNAME_LENGTH) {
            baseUsername = baseUsername.substring(0, MAX_USERNAME_LENGTH);
            logger.debug("Username truncated to {} characters: {}", MAX_USERNAME_LENGTH, baseUsername);
        }

        // Check if username exists, if so append counter
        String username = baseUsername;
        int counter = 1;
        while (spipUserRepository.existsBySpipUser(username)) {
            // Truncate base to make room for counter suffix
            String truncatedBase = baseUsername;
            String counterStr = String.valueOf(counter);

            if (baseUsername.length() + counterStr.length() > MAX_USERNAME_LENGTH) {
                int availableLength = MAX_USERNAME_LENGTH - counterStr.length();
                truncatedBase = baseUsername.substring(0, Math.max(1, availableLength));
            }

            username = truncatedBase + counterStr;

            counter++;
            logger.debug("Username '{}' exists, trying '{}'", baseUsername, username);
        }

        logger.info("Generated SPIP username (S3+SPIP compatible): {} (length: {})", username, username.length());
        return username;
    }

    /**
     * Generates a random password for SPIP user.
     * SPIP requires: at least one numeral, one uppercase, one lowercase, one special character.
     *
     * @return Generated password (plaintext - will be encrypted by JPA EncryptedStringConverter)
     */
    private String generateRandomPassword() {
        return PasswordGenerator.generate(PASSWORD_LENGTH, "!@#$%^&*", false);
    }

    /**
     * Finds SPIP user by organization.
     *
     * @param organization The organization
     * @return Optional containing the SPIP user if found
     */
    public Optional<OrganizationSpipUser> findByOrganization(Organization organization) {
        return spipUserRepository.findByOrganization(organization);
    }

    /**
     * Updates the synchronization status of a SPIP user.
     *
     * @param spipUser The SPIP user to update
     * @param synchronized The new synchronization status
     * @return The updated SPIP user
     */
    @Transactional
    public OrganizationSpipUser updateSynchronizationStatus(OrganizationSpipUser spipUser, boolean _synchronized) {
        spipUser.setSpipSynchronized(_synchronized);
        return spipUserRepository.save(spipUser);
    }

    /**
     * Saves or updates a SPIP user.
     *
     * @param spipUser The SPIP user to save
     * @return The saved SPIP user
     */
    @Transactional
    public OrganizationSpipUser save(OrganizationSpipUser spipUser) {
        return spipUserRepository.save(spipUser);
    }

    /**
     * Creates an OrganizationSpipUser with existing SPIP credentials.
     * This method is used when creating an organization from an existing SPIP user.
     * The password should be plaintext - it will be encrypted by JPA EncryptedStringConverter.
     *
     * @param organization The organization to link SPIP credentials to
     * @param spipUsername The existing SPIP username
     * @param spipPassword The SPIP password (plaintext)
     * @return The created OrganizationSpipUser
     */
    @Transactional
    public OrganizationSpipUser createOrganizationSpipUser(Organization organization, String spipUsername, String spipPassword) {
        if (organization == null) {
            throw new IllegalArgumentException("Organization cannot be null");
        }
        if (spipUsername == null || spipUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("SPIP username cannot be null or empty");
        }
        if (spipPassword == null || spipPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("SPIP password cannot be null or empty");
        }

        // Check if SPIP user already exists for this organization
        Optional<OrganizationSpipUser> existing = spipUserRepository.findByOrganization(organization);
        if (existing.isPresent()) {
            logger.warn("SPIP user already exists for organization: {}", organization.getName());
            return existing.get();
        }

        // Check if username is already taken
        if (spipUserRepository.existsBySpipUser(spipUsername)) {
            throw new IllegalArgumentException("SPIP username '" + spipUsername + "' is already in use");
        }

        // Create the SPIP user entity
        OrganizationSpipUser spipUser = new OrganizationSpipUser();
        spipUser.setOrganization(organization);
        spipUser.setSpipUser(spipUsername);
        spipUser.setSpipPassword(spipPassword); // Plaintext - encrypted by JPA EncryptedStringConverter
        spipUser.setSpipSynchronized(true); // Mark as synchronized since we're using existing SPIP user

        // Save and return
        OrganizationSpipUser savedSpipUser = spipUserRepository.save(spipUser);
        logger.info("Linked existing SPIP user '{}' to organization '{}'", spipUsername, organization.getName());

        return savedSpipUser;
    }

    /**
     * Checks if a SPIP username is already taken in the database.
     * Checks both organization_spip_users and organization_onboarding_requests tables.
     *
     * @param username The SPIP username to check
     * @param excludeRequestId Optional request ID to exclude from the check (for re-synchronization)
     * @return true if the username already exists, false otherwise
     */
    public boolean isUsernameAlreadyTaken(String username, Long excludeRequestId) {
        // Check if username exists in organization_spip_users table
        if (spipUserRepository.existsBySpipUser(username)) {
            return true;
        }

        // Check if username exists in organization_onboarding_requests table
        // excluding the current request being synchronized
        return onboardingRequestRepository.findAll().stream()
                .filter(req -> !req.getId().equals(excludeRequestId))
                .anyMatch(req -> username.equals(req.getSpipUser()));
    }

    /**
     * Checks if a CKAN username is already taken, independently of SPIP. Since a CKAN
     * account may either be a standalone account (an org's own {@code ckanUser}) or a
     * reused SPIP account ({@code spipUser}), both columns share the same CKAN
     * namespace and must both be checked to avoid colliding with either.
     *
     * @param username The CKAN username to check
     * @param excludeRequestId Optional request ID to exclude from the check (for re-synchronization)
     * @return true if the username already exists, false otherwise
     */
    public boolean isCkanUsernameAlreadyTaken(String username, Long excludeRequestId) {
        if (spipUserRepository.existsBySpipUser(username) || spipUserRepository.existsByCkanUser(username)) {
            return true;
        }

        return onboardingRequestRepository.findAll().stream()
                .filter(req -> !req.getId().equals(excludeRequestId))
                .anyMatch(req -> username.equals(req.getSpipUser()) || username.equals(req.getCkanUser()));
    }
}
