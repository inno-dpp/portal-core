package com.data4circ.portal.features.organization.service;

import com.data4circ.portal.common.util.PasswordGenerator;
import com.data4circ.portal.features.organization.dto.OnboardingApprovalEmail;
import com.data4circ.portal.features.organization.entity.*;
import com.data4circ.portal.features.onboardingsync.ckan.CkanOnboardingSyncService;
import com.data4circ.portal.features.onboardingsync.ckan.CkanOnboardingToolProvisioner;
import com.data4circ.portal.features.onboardingsync.keycloak.KeycloakOnboardingToolProvisioner;
import com.data4circ.portal.features.platformsettings.service.KeycloakSettingsService;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolProvisioner;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolRegistry;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolSyncOutcome;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolSyncStateService;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolConnectorService;
import com.data4circ.portal.features.notification.dto.NotificationEvent;
import com.data4circ.portal.features.notification.entity.NotificationType;
import com.data4circ.portal.features.notification.service.NotificationService;
import com.data4circ.portal.features.organization.repository.OnboardingRequestRepository;
import com.data4circ.portal.features.organization.repository.OnboardingRejectionRepository;
import com.data4circ.portal.features.organization.entity.OrganizationSpipUser;
import com.data4circ.portal.features.organization.repository.NaceCodeRepository;
import com.data4circ.portal.common.config.ThemeProperties;
import com.data4circ.portal.common.util.SpipPolicyLabelUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class OnboardingRequestService {

    private static final Logger logger = LoggerFactory.getLogger(OnboardingRequestService.class);

    /**
     * Mirrors {@code SpipOnboardingToolProvisioner.TOOL_KEY}. Declared as a literal here
     * rather than imported: SPIP is an optional plugin (see
     * docs/developer/SPIP-PLUGIN-DECOUPLING-PLAN.md) and this core service must compile
     * and run with no SPIP classes on the classpath at all. The two legacy-flag /
     * required-tool checks below only need the key string, never the provisioner type.
     */
    private static final String SPIP_TOOL_KEY = "spip";

    @Autowired
    private OnboardingRequestRepository onboardingRequestRepository;

    @Autowired
    private OnboardingRejectionRepository onboardingRejectionRepository;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private OrganizationSpipUserService spipUserService;

    @Autowired
    private NaceCodeRepository naceCodeRepository;

    @Autowired
    private CkanOnboardingSyncService ckanOnboardingSyncService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private OnboardingToolRegistry toolRegistry;

    @Autowired
    private OnboardingToolSyncStateService toolSyncState;

    @Autowired
    private OnboardingToolConnectorService toolConnectorService;

    @Autowired
    private ThemeProperties themeProperties;

    @Autowired
    private KeycloakSettingsService keycloakSettingsService;

    @Transactional
    public OrganizationOnboardingRequest save(OrganizationOnboardingRequest request) {
        return onboardingRequestRepository.save(request);
    }

    public List<OrganizationOnboardingRequest> findAll() {
        return onboardingRequestRepository.findAll();
    }

    public Optional<OrganizationOnboardingRequest> findById(Long id) {
        return onboardingRequestRepository.findById(id);
    }

    public List<OrganizationOnboardingRequest> findByStatus(OnboardingRequestStatus status) {
        return onboardingRequestRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public List<OrganizationOnboardingRequest> findPendingRequests() {
        return findByStatus(OnboardingRequestStatus.PENDING);
    }

    public boolean existsByEmail(String email) {
        return onboardingRequestRepository.existsByEmail(email);
    }

    public Optional<OrganizationOnboardingRequest> findByEmail(String email) {
        return onboardingRequestRepository.findByEmail(email);
    }

    /**
     * The onboarding request that originated the given organization, if any. Organizations
     * created directly by a platform admin (bypassing the public /join flow) have none; the
     * Organization Detail page falls back to an empty state in that case.
     */
    public Optional<OrganizationOnboardingRequest> findByOrganizationId(Long organizationId) {
        return onboardingRequestRepository.findFirstByOrganizationIdOrderByProcessedAtDesc(organizationId);
    }

    public boolean existsByCompanyName(String companyName) {
        return onboardingRequestRepository.existsByCompanyName(companyName);
    }

    public long countByStatus(OnboardingRequestStatus status) {
        return onboardingRequestRepository.countByStatus(status);
    }

    /**
     * Validates a new onboarding request before it's persisted: unique email, unique company
     * name (against both other onboarding requests and existing organizations), no SPIP
     * access-policy label collision, and well-formed NACE codes.
     *
     * <p>Called by {@link #createOnboardingRequest} (the public /join flow) and must also be
     * called by any other path that creates an {@link OrganizationOnboardingRequest} directly
     * (e.g. an admin creating an organization "from scratch" in the portal) - skipping it lets a
     * request through that later fails, or silently collides, at SPIP sync time.
     *
     * @throws IllegalArgumentException if any check fails
     */
    public void validateForCreation(OrganizationOnboardingRequest request) {
        // Validate that email and company name are unique
        if (existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("An onboarding request with this email already exists");
        }

        if (existsByCompanyName(request.getCompanyName())) {
            throw new IllegalArgumentException("An onboarding request with this company name already exists");
        }

        // Check if organization already exists
        if (organizationService.existsByName(request.getCompanyName())) {
            throw new IllegalArgumentException("An organization with this name already exists in the system");
        }

        // SPIP truncates the access-policy label to its sanitized, 26-character-capped company
        // name (see SpipPolicyLabelUtil). Two distinctly-named companies that collide once
        // truncated would silently end up sharing one SPIP policy, so reject the newcomer here
        // rather than let it fail later at SPIP sync time.
        String newLabelPrefix = SpipPolicyLabelUtil.sanitizeAndCapName(request.getCompanyName());
        boolean labelCollision = Stream.concat(
                        onboardingRequestRepository.findAllCompanyNames().stream(),
                        organizationService.findAllNames().stream())
                .anyMatch(existingName -> newLabelPrefix.equals(SpipPolicyLabelUtil.sanitizeAndCapName(existingName)));
        if (labelCollision) {
            throw new IllegalArgumentException(
                    "This company name is too similar to an existing one (same first "
                    + SpipPolicyLabelUtil.MAX_NAME_LENGTH
                    + " characters once normalized) and would collide with its SPIP access policy. "
                    + "Please use a more distinct company name.");
        }

        // Reject 2- and 3-digit NACE codes: only 4-digit class codes denote a specific economic activity
        if (request.getNaceCodes() != null && !request.getNaceCodes().isBlank()) {
            for (String code : request.getNaceCodes().split(",")) {
                String trimmed = code.trim();
                if (!trimmed.isEmpty() && !NaceCode.isClassCode(trimmed)) {
                    throw new IllegalArgumentException(
                        "Only 4-digit NACE class codes are allowed (e.g. '01.11'). " +
                        "2- and 3-digit codes denote broader families and cannot be selected. Invalid code: '" + trimmed + "'");
                }
            }
        }
    }

    @Transactional
    public OrganizationOnboardingRequest createOnboardingRequest(OrganizationOnboardingRequest request) {
        validateForCreation(request);

        request.setStatus(OnboardingRequestStatus.PENDING);
        OrganizationOnboardingRequest savedRequest = save(request);

        // Send confirmation email
        emailService.sendOnboardingConfirmation(
            savedRequest.getEmail(),
            savedRequest.getCompanyName(),
            savedRequest.getContactName(),
            savedRequest.getId(),
            savedRequest.getCreatedAt()
        );

        // Email configured reviewers so they can start the review workflow
        emailService.sendOnboardingReviewNotification(
            savedRequest.getId(),
            savedRequest.getCompanyName(),
            savedRequest.getContactName(),
            savedRequest.getEmail()
        );

        // Notify platform admins about new onboarding request
        notificationService.sendToRole(UserRole.PLATFORM_ADMIN, NotificationEvent.builder()
            .type(NotificationType.ONBOARDING_SUBMITTED)
            .title("New Onboarding Request")
            .message("New organization '" + savedRequest.getCompanyName() + "' has requested to join the platform.")
            .actionUrl("/admin/onboarding/" + savedRequest.getId())
            .actionLabel("Review Request")
            .build());

        return savedRequest;
    }

    @Transactional
    public OrganizationOnboardingRequest approveRequest(Long requestId, String adminUsername, String adminNotes) {
        OrganizationOnboardingRequest request = findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Onboarding request not found"));

        if (request.getStatus() != OnboardingRequestStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be approved");
        }

        // Ensure every enabled tool marked as required has been synchronized
        for (OnboardingToolProvisioner tool : toolRegistry.enabledTools()) {
            if (toolRegistry.isRequired(tool.getKey()) && !toolSyncState.isSynced(requestId, tool.getKey())) {
                throw new IllegalStateException(tool.getDisplayName()
                        + " synchronization must be completed before approving the request");
            }
        }

        boolean spipSynced = toolSyncState.isSynced(requestId, SPIP_TOOL_KEY);
        boolean ckanSynced = toolSyncState.isSynced(requestId, CkanOnboardingToolProvisioner.TOOL_KEY);
        boolean keycloakSynced = toolSyncState.isSynced(requestId, KeycloakOnboardingToolProvisioner.TOOL_KEY);

        // Check if organization already exists
        if (organizationService.existsByName(request.getCompanyName())) {
            throw new IllegalStateException("An organization with name '" + request.getCompanyName() + "' already exists");
        }

        // Check if user with this email already exists
        if (userService.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("A user with email '" + request.getEmail() + "' already exists");
        }

        // Check if this request was already processed (double-click protection)
        if (request.getOrganization() != null) {
            throw new IllegalStateException("This request has already been processed");
        }

        try {
            // Create the organization
            Organization organization = createOrganizationFromRequest(request);
            organization = organizationService.save(organization);

            // Ensure CKAN API token is available before initializing connectors.
            // If sync didn't produce one, retry now so the CKAN connector receives it.
            String ckanApiToken = request.getCkanApiToken();
            if (ckanSynced && (ckanApiToken == null || ckanApiToken.isBlank())) {
                logger.warn("CKAN API token missing for request {}. Attempting to create it now.", requestId);
                try {
                    ckanApiToken = ckanOnboardingSyncService.retryCkanApiTokenCreation(
                            ckanOnboardingSyncService.resolveCkanUsername(request), request.getCkanOrganizationShortName());
                    if (ckanApiToken != null) {
                        request.setCkanApiToken(ckanApiToken);
                        logger.info("CKAN API token created on retry for request {}", requestId);
                    } else {
                        logger.warn("CKAN API token still unavailable for request {} - connector and email will be sent without it", requestId);
                    }
                } catch (Exception tokenEx) {
                    logger.warn("Failed to retry CKAN API token creation for request {}: {}", requestId, tokenEx.getMessage());
                }
            }

            // Materialize the organization's tools in "My Tools"
            toolConnectorService.materializeConnectors(organization, request);

            // Create the organization's credential record from whichever tools were synchronized.
            // SPIP and CKAN each contribute their own half; either can be present without the
            // other (e.g. CKAN provisions its own login when SPIP is disabled).
            if (spipSynced || ckanSynced) {
                OrganizationSpipUser spipUser = new OrganizationSpipUser();
                spipUser.setOrganization(organization);

                if (spipSynced) {
                    spipUser.setSpipUser(request.getSpipUser());
                    spipUser.setSpipPassword(request.getSpipPassword());
                    spipUser.setSpipSynchronized(true);
                }

                if (ckanSynced && request.getCkanOrganizationShortName() != null) {
                    spipUser.setCkanOrganizationName(request.getCkanOrganizationShortName());
                    spipUser.setCkanSynchronized(true);
                    // Only stored when CKAN provisioned its own account (SPIP not synced);
                    // when reused, spipUser/spipPassword above already are the CKAN login —
                    // see OrganizationSpipUser.ckanUser javadoc.
                    if (request.getCkanUser() != null && !request.getCkanUser().isBlank()) {
                        spipUser.setCkanUser(request.getCkanUser());
                        spipUser.setCkanPassword(request.getCkanPassword());
                    }
                    logger.info("CKAN organization '{}' linked to organization credentials", request.getCkanOrganizationShortName());
                }

                spipUserService.save(spipUser);
                logger.info("Organization credentials created for '{}' (spip synced: {}, ckan synced: {})",
                        organization.getName(), spipSynced, ckanSynced);
            }

            // Sync NACE codes to CKAN organization (blocks approval if sync fails)
            if (ckanSynced && request.getCkanOrganizationShortName() != null
                    && organization.getNaceCodes() != null && !organization.getNaceCodes().isEmpty()) {
                try {
                    ckanOnboardingSyncService.syncNaceCodes(request.getCkanOrganizationShortName(), organization.getNaceCodes());
                } catch (Exception e) {
                    logger.error("NACE code sync to CKAN failed during approval: {}", e.getMessage());
                    throw new IllegalStateException(
                            "Cannot approve: NACE code synchronization with CKAN failed. " +
                            "Please verify that CKAN is available and try again. Error: " + e.getMessage());
                }
            }

            // Create initial admin user for the organization
            User adminUser = createAdminUserFromRequest(request, organization);
            // Stamp the Keycloak identity link used by the optional first-login password
            // mirror. Only default-instance identities are stamped: the mirror can never
            // authenticate against other instances (their admin secrets are not persisted).
            if (keycloakSynced && request.getKeycloakUserId() != null
                    && keycloakSettingsService.isDefaultInstance(
                            request.getKeycloakBaseUrl(), request.getKeycloakRealm())) {
                adminUser.setKeycloakUserId(request.getKeycloakUserId());
            }
            User savedAdminUser = userService.saveWithoutEncoding(adminUser);
            String temporaryPassword = adminUser.getTemporaryPasswordForEmail();

            // Mint a one-time secure link for the user to set their own password (secure-email mode).
            String setPasswordUrl = passwordResetService.createSetPasswordLink(
                    savedAdminUser, PasswordResetService.ONBOARDING_TOKEN_EXPIRY_HOURS);

            // Update request status
            request.setStatus(OnboardingRequestStatus.ORGANIZATION_CREATED);
            request.setOrganization(organization);
            request.setProcessedBy(adminUsername);
            request.setProcessedAt(LocalDateTime.now());
            request.setAdminNotes(adminNotes);

            OrganizationOnboardingRequest savedRequest = save(request);

            // Passwords are automatically decrypted by JPA EncryptedStringConverter
            String spipPassword = request.getSpipPassword();
            String keycloakAccountUrl = request.getKeycloakBaseUrl() != null && request.getKeycloakRealm() != null
                    ? request.getKeycloakBaseUrl() + "/realms/" + request.getKeycloakRealm() + "/account"
                    : null;

            // Send approval email. In secure mode (default/prod) only setPasswordUrl is used and no
            // credentials are rendered; in legacy mode (dev/test) the credentials below are inlined.
            emailService.sendOnboardingApproval(OnboardingApprovalEmail.builder()
                .toEmail(savedRequest.getEmail())
                .companyName(savedRequest.getCompanyName())
                .contactName(savedRequest.getContactName())
                .username(adminUser.getUsername())
                .temporaryPassword(temporaryPassword)
                .spipUsername(request.getSpipUser())
                .spipPassword(spipPassword)
                .ckanUsername(request.getCkanUser())
                .ckanPassword(request.getCkanPassword())
                .ckanApiToken(ckanApiToken)
                .keycloakUsername(request.getKeycloakUsername())
                .keycloakPassword(request.getKeycloakTempPassword())
                .keycloakAccountUrl(keycloakAccountUrl)
                .setPasswordUrl(setPasswordUrl)
                .build());

            // Send real-time notification to the new admin user
            notificationService.sendToUser(adminUser, NotificationEvent.builder()
                .type(NotificationType.ONBOARDING_APPROVED)
                .title("Welcome to " + themeProperties.getBrandName() + "!")
                .message("Your organization '" + savedRequest.getCompanyName() + "' has been approved. You can now start using the portal.")
                .actionUrl("/organizations/my-organization")
                .actionLabel("View Organization")
                .build());

            return savedRequest;

        } catch (Exception e) {
            // Log the error and re-throw with more context
            logger.error("Error approving onboarding request ID {}: {}", requestId, e.getMessage(), e);
            throw new IllegalStateException("Failed to approve onboarding request: " + e.getMessage(), e);
        }
    }

    @Transactional
    public OrganizationOnboardingRequest rejectRequest(Long requestId, String adminUsername, String adminNotes) {
        OrganizationOnboardingRequest request = findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Onboarding request not found"));

        if (request.getStatus() != OnboardingRequestStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be rejected");
        }

        // Rejection intentionally requires no tool synchronization: a request must be
        // rejectable without provisioning anything in external systems.

        LocalDateTime now = LocalDateTime.now();

        // Record this rejection in the history so re-open/re-reject cycles are preserved.
        request.addRejection(new OnboardingRejection(request, adminNotes, adminUsername, now));

        request.setStatus(OnboardingRequestStatus.REJECTED);
        request.setProcessedBy(adminUsername);
        request.setProcessedAt(now);
        request.setAdminNotes(adminNotes);

        OrganizationOnboardingRequest savedRequest = save(request);

        // Send rejection email
        emailService.sendOnboardingRejection(
            savedRequest.getEmail(),
            savedRequest.getCompanyName(),
            savedRequest.getContactName(),
            adminNotes
        );

        // Note: Cannot send SSE notification for rejection since user doesn't have an account yet
        // The rejection is communicated via email only

        return savedRequest;
    }

    /**
     * Re-open a previously rejected request, returning it to {@link OnboardingRequestStatus#PENDING}
     * so it can be re-synchronised and approved (or rejected again). The rejection history is kept
     * intact; only the current status and last-processed markers are reset.
     */
    @Transactional
    public OrganizationOnboardingRequest reopenRequest(Long requestId, String adminUsername) {
        OrganizationOnboardingRequest request = findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Onboarding request not found"));

        if (request.getStatus() != OnboardingRequestStatus.REJECTED) {
            throw new IllegalStateException("Only rejected requests can be re-opened");
        }

        request.setStatus(OnboardingRequestStatus.PENDING);
        // Clear the last-processed markers so the request presents as pending again. The full
        // rejection history remains available via getRejectionHistory(...).
        request.setProcessedBy(null);
        request.setProcessedAt(null);
        request.setAdminNotes(null);

        logger.info("Onboarding request {} re-opened to PENDING by {}", requestId, adminUsername);
        return save(request);
    }

    /** Full rejection history for a request, most recent first. */
    @Transactional(readOnly = true)
    public List<OnboardingRejection> getRejectionHistory(Long requestId) {
        return onboardingRejectionRepository.findByOnboardingRequestIdOrderByRejectedAtDesc(requestId);
    }

    private Organization createOrganizationFromRequest(OrganizationOnboardingRequest request) {
        Organization organization = new Organization();
        organization.setName(request.getCompanyName());
        organization.setType(request.getOrganizationTypeMapping());
        // Convert String industry to IndustrySector enum
        organization.setIndustrySector(parseIndustrySector(request.getIndustry()));
        organization.setWebsite(request.getWebsite());
        organization.setAddress(request.getAddress());
        organization.setCountry(request.getCountry());
        organization.setContactEmail(request.getEmail());
        organization.setContactPhone(request.getPhone());
        organization.setPhonePrefix(request.getPhonePrefix());
        organization.setPrimaryContactName(request.getContactName());
        organization.setPrimaryContactTitle(request.getContactTitle());
        organization.setCompanySize(request.getCompanySize());
        organization.setCertificationStatus(CertificationStatus.ACTIVE);

        // Use the user-provided organization description
        organization.setDescription(request.getOrganizationDescription());

        // Resolve NACE codes from the onboarding request
        if (request.getNaceCodes() != null && !request.getNaceCodes().isEmpty()) {
            List<String> codes = Arrays.asList(request.getNaceCodes().split(","));
            java.util.List<NaceCode> resolvedCodes = naceCodeRepository.findByCodeIn(codes);
            organization.setNaceCodes(new HashSet<>(resolvedCodes));
        }

        return organization;
    }

    private User createAdminUserFromRequest(OrganizationOnboardingRequest request, Organization organization) {
        User adminUser = new User();
        adminUser.setUsername(request.getEmail());
        adminUser.setEmail(request.getEmail());
        adminUser.setFirstName(extractFirstName(request.getContactName()));
        adminUser.setLastName(extractLastName(request.getContactName()));
        adminUser.setRole(UserRole.ORG_ADMIN);
        adminUser.setOrganization(organization);
        adminUser.setEnabled(true);

        // Generate temporary password
        String temporaryPassword = generateTemporaryPassword();
        adminUser.setPassword(userService.encodePassword(temporaryPassword));
        adminUser.setMustChangePassword(true);

        // Store the temporary password in a transient field for email sending
        adminUser.setTemporaryPasswordForEmail(temporaryPassword);

        return adminUser;
    }

    private String generateTemporaryPassword() {
        // 12 characters; specials exclude problematic chars: ()[]{}|;:,.<> and spaces
        return PasswordGenerator.generate(12, "!@#$%^&*_+-=?", false);
    }

    private String generateUsernameFromEmail(String email) {
        String localPart = email.substring(0, email.indexOf('@'));
        String baseUsername = localPart.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();

        // Ensure username is unique
        String username = baseUsername;
        int counter = 1;
        while (userService.existsByUsername(username)) {
            username = baseUsername + counter;
            counter++;
        }

        return username;
    }

    private String extractFirstName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "User";
        }
        String[] parts = fullName.trim().split("\\s+");
        return parts[0];
    }

    private String extractLastName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "User";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length > 1) {
            return String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
        }
        return parts[0]; // Use the same name if only one word provided
    }

    /**
     * Parse industry sector string from form submission to IndustrySector enum
     * Handles lowercase form values (e.g., "electronics", "automotive")
     */
    private IndustrySector parseIndustrySector(String industry) {
        if (industry == null || industry.trim().isEmpty()) {
            return IndustrySector.OTHERS;
        }

        // Convert lowercase form values to uppercase enum names
        String normalizedIndustry = industry.trim().toUpperCase().replace(" ", "_");

        try {
            return IndustrySector.valueOf(normalizedIndustry);
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown industry sector '{}', defaulting to OTHER", industry);
            return IndustrySector.OTHERS;
        }
    }

    public List<OrganizationOnboardingRequest> findRecentRequests(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return onboardingRequestRepository.findRecentRequests(since);
    }

    @Transactional
    public void deleteById(Long id) {
        onboardingRequestRepository.deleteById(id);
    }

    /**
     * Synchronizes the onboarding request with an external onboarding tool
     * (e.g. "spip" or "ckan"). The tool must be enabled and all its declared
     * dependencies must already be synchronized.
     *
     * @param requestId The ID of the onboarding request
     * @param toolKey   The key of the tool to synchronize with
     * @param params    Raw form parameters forwarded to the tool provisioner
     * @return The updated onboarding request
     * @throws IllegalArgumentException if the request or tool is unknown, or input is invalid
     * @throws IllegalStateException if the request is not PENDING, a dependency is
     *         missing, or remote provisioning fails
     */
    @Transactional
    public OrganizationOnboardingRequest synchronizeTool(Long requestId, String toolKey, Map<String, String> params) {
        if (requestId == null) {
            throw new IllegalArgumentException("Request ID cannot be null");
        }

        OrganizationOnboardingRequest request = findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Onboarding request not found with ID: " + requestId));

        OnboardingToolProvisioner tool = toolRegistry.getEnabled(toolKey)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or disabled onboarding tool: " + toolKey));

        if (request.getStatus() != OnboardingRequestStatus.PENDING) {
            throw new IllegalStateException("Cannot synchronize with " + tool.getDisplayName()
                    + ": request status is " + request.getStatus().getDisplayName()
                    + ". Only pending requests can be synchronized.");
        }

        if (toolSyncState.isSynced(requestId, toolKey)) {
            logger.info("Onboarding request {} is already synchronized with {}", requestId, tool.getDisplayName());
            return request;
        }

        for (String dependencyKey : tool.getDependencies()) {
            if (!toolSyncState.isSynced(requestId, dependencyKey)) {
                String dependencyName = toolRegistry.get(dependencyKey)
                        .map(OnboardingToolProvisioner::getDisplayName)
                        .orElse(dependencyKey);
                throw new IllegalStateException("Please synchronize with " + dependencyName
                        + " first before synchronizing with " + tool.getDisplayName());
            }
        }

        OnboardingToolSyncOutcome outcome = tool.synchronize(request, params);

        toolSyncState.markSynced(request, toolKey, outcome.getDetails());
        mirrorLegacySyncFlag(request, toolKey);

        OrganizationOnboardingRequest savedRequest = save(request);
        logger.info("Onboarding request {} successfully synchronized with {}", requestId, tool.getDisplayName());
        return savedRequest;
    }

    /**
     * Keeps the deprecated NOT NULL columns spip_synchronized/ckan_synchronized in sync
     * during the deprecation window; they are never read anymore.
     */
    @SuppressWarnings("deprecation")
    private void mirrorLegacySyncFlag(OrganizationOnboardingRequest request, String toolKey) {
        if (SPIP_TOOL_KEY.equals(toolKey)) {
            request.setSpipSynchronized(true);
        } else if (CkanOnboardingToolProvisioner.TOOL_KEY.equals(toolKey)) {
            request.setCkanSynchronized(true);
        }
    }
}
