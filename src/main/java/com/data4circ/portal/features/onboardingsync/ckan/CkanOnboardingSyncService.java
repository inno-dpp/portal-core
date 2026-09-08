package com.data4circ.portal.features.onboardingsync.ckan;

import com.data4circ.portal.common.util.CkanSlugUtil;
import com.data4circ.portal.features.category.entity.Category;
import com.data4circ.portal.features.category.repository.CategoryRepository;
import com.data4circ.portal.features.organization.entity.NaceCode;
import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;
import com.data4circ.portal.features.organization.repository.OnboardingRequestRepository;
import com.data4circ.portal.features.organization.entity.OrganizationSpipUser;
import com.data4circ.portal.features.organization.repository.OrganizationSpipUserRepository;
import com.data4circ.portal.integration.ckan.client.CkanApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for synchronizing onboarding requests with CKAN platform.
 * Performs 5 sequential operations:
 * 1. Create organization in CKAN
 * 2. Create user in CKAN
 * 3. Add user as member to the organization
 * 3b. Add user as member to each shared use-case category group (non-fatal if fails)
 * 4. Create API token for the user (non-fatal if fails)
 *
 * <h2>Idempotency and Error Recovery</h2>
 * This service implements idempotent synchronization, meaning the operation can be
 * safely retried multiple times without side effects. If any step fails:
 * <ul>
 *   <li>Already created resources are detected via CONFLICT (409) responses</li>
 *   <li>The operation continues to the next step instead of failing</li>
 *   <li>Safe to retry after partial failures without manual cleanup</li>
 * </ul>
 *
 * <h3>Example: Retry After Partial Failure</h3>
 * <pre>
 * First attempt:
 *   Step 1: Create org → SUCCESS ✓
 *   Step 2: Create user → NETWORK ERROR ✗
 *   Step 3: Not reached
 *
 * Second attempt (retry):
 *   Step 1: Create org → CONFLICT (already exists) → Continue ✓
 *   Step 2: Create user → SUCCESS ✓
 *   Step 3: Add membership → SUCCESS ✓
 * </pre>
 *
 * <h3>Non-Transactional Operations</h3>
 * Note: CKAN API calls are not part of a distributed transaction. Partial state
 * may exist in CKAN if the process is interrupted. However, the idempotent design
 * ensures that retrying will complete the synchronization successfully.
 *
 * @see CkanOnboardingSyncService#synchronizeOnboardingRequest(OrganizationOnboardingRequest, String)
 */
@Service
public class CkanOnboardingSyncService {

    private static final Logger logger = LoggerFactory.getLogger(CkanOnboardingSyncService.class);

    private final CkanApiClient ckanApiClient;
    private final OnboardingRequestRepository onboardingRequestRepository;
    private final OrganizationSpipUserRepository organizationSpipUserRepository;
    private final CategoryRepository categoryRepository;

    public CkanOnboardingSyncService(CkanApiClient ckanApiClient,
                                    OnboardingRequestRepository onboardingRequestRepository,
                                    OrganizationSpipUserRepository organizationSpipUserRepository,
                                    CategoryRepository categoryRepository) {
        this.ckanApiClient = ckanApiClient;
        this.onboardingRequestRepository = onboardingRequestRepository;
        this.organizationSpipUserRepository = organizationSpipUserRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Synchronizes an onboarding request with CKAN by creating organization, user, and membership.
     * This operation is idempotent - can be safely retried if any step fails.
     * If resources already exist (409 CONFLICT), the operation continues gracefully.
     *
     * @param request The onboarding request with organization and user details
     * @param ckanOrgShortName The short name for the organization in CKAN
     * @return CkanSyncResult with status of organization, user, and membership synchronization
     */
    public CkanSyncResult synchronizeOnboardingRequest(OrganizationOnboardingRequest request, String ckanOrgShortName) {
        String username = resolveCkanUsername(request);
        String password = resolveCkanPassword(request);

        logger.info("=== Starting CKAN Synchronization for: {} ===", request.getCompanyName());
        logger.info("Organization: {}, User: {}", ckanOrgShortName, username);

        CkanSyncResult result = new CkanSyncResult();
        result.setOrganizationName(request.getCompanyName());
        result.setCkanOrgShortName(ckanOrgShortName);
        result.setUsername(username);

        try {
            // Step 1: Create Organization (idempotent - handles existing org)
            logger.info("Step 1/4: Creating organization in CKAN");
            boolean orgCreated = createOrganizationIdempotent(ckanOrgShortName, request.getCompanyName());
            result.setOrganizationCreated(orgCreated);
            result.setOrganizationExists(!orgCreated);
            if (orgCreated) {
                logger.info("✓ Organization '{}' created successfully", ckanOrgShortName);
            } else {
                logger.info("✓ Organization '{}' already exists (continuing)", ckanOrgShortName);
            }

            // Step 2: Create User (idempotent - handles existing user)
            logger.info("Step 2/4: Creating user in CKAN");
            boolean userCreated = createUserIdempotent(username, request.getEmail(), password);
            result.setUserCreated(userCreated);
            result.setUserExists(!userCreated);
            if (userCreated) {
                logger.info("✓ User '{}' created successfully", username);
            } else {
                logger.info("✓ User '{}' already exists (continuing)", username);
            }

            // Step 3: Add User to Organization (idempotent - handles existing membership)
            logger.info("Step 3/4: Adding user to organization");
            boolean membershipCreated = addUserToOrganizationIdempotent(ckanOrgShortName, username);
            result.setMembershipCreated(membershipCreated);
            if (membershipCreated) {
                logger.info("✓ User '{}' added to organization '{}'", username, ckanOrgShortName);
            } else {
                logger.info("✓ User '{}' already member of organization '{}' (continuing)", username, ckanOrgShortName);
            }

            // Step 3b: Add user to shared use-case category groups (non-fatal - see method doc)
            logger.info("Step 3b: Adding user to use-case category groups");
            List<String> groupsGranted = addUserToUseCaseGroupsIdempotent(username);
            result.setGroupMembershipsGranted(groupsGranted);
            logger.info("✓ User '{}' has editor membership on group(s): {}", username, groupsGranted);

            // Step 4: Create API token for user (non-fatal - admin can assign manually later)
            logger.info("Step 4/4: Creating API token for user");
            String apiToken = createApiTokenIdempotent(username, ckanOrgShortName);
            result.setApiToken(apiToken);
            result.setApiTokenCreated(apiToken != null);
            if (apiToken != null) {
                logger.info("✓ API token created for user '{}'", username);
            } else {
                logger.warn("⚠ API token not created for user '{}' - admin can assign manually later", username);
            }

            // Step 5: Sync NACE codes (non-fatal)
            if (request.getNaceCodes() != null && !request.getNaceCodes().isEmpty()) {
                logger.info("Step 5: Syncing NACE codes to CKAN organization");
                syncNaceCodesFromString(ckanOrgShortName, request.getNaceCodes());
            }

            result.setSuccess(true);
            logger.info("=== CKAN Synchronization Complete Successfully ===");
            return result;

        } catch (Exception e) {
            logger.error("CKAN synchronization failed: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    /**
     * The CKAN username for this request: its own independently generated
     * {@code ckanUser} when CKAN was synced without SPIP, otherwise the shared
     * {@code spipUser} (the common case, when SPIP ran first and CKAN reuses its
     * account). See {@link com.data4circ.portal.features.onboardingsync.ckan.CkanOnboardingToolProvisioner}.
     */
    public String resolveCkanUsername(OrganizationOnboardingRequest request) {
        String ckanUser = request.getCkanUser();
        return (ckanUser != null && !ckanUser.isBlank()) ? ckanUser : request.getSpipUser();
    }

    /** @see #resolveCkanUsername(OrganizationOnboardingRequest) */
    public String resolveCkanPassword(OrganizationOnboardingRequest request) {
        String ckanUser = request.getCkanUser();
        return (ckanUser != null && !ckanUser.isBlank()) ? request.getCkanPassword() : request.getSpipPassword();
    }

    /**
     * Sync NACE codes from a comma-separated string (used during onboarding flow).
     * Pushes extras and sets description in "nace=CODE" format for searchability.
     */
    /**
     * @throws CkanApiClient.CkanApiException if CKAN sync fails
     */
    private void syncNaceCodesFromString(String ckanOrgShortName, String naceCodesStr) {
        Map<String, Object> fields = new HashMap<>();

        // Extras for structured access
        List<Map<String, String>> extras = new ArrayList<>();
        extras.add(Map.of("key", "nace_codes", "value", naceCodesStr));
        fields.put("extras", extras);

        // Description in "nace=CODE" format for CKAN search
        String[] codes = naceCodesStr.split(",");
        StringBuilder description = new StringBuilder();
        for (int i = 0; i < codes.length; i++) {
            String trimmed = codes[i].trim();
            if (!trimmed.isEmpty()) {
                if (description.length() > 0) {
                    description.append(", ");
                }
                description.append("nace=").append(trimmed);
            }
        }
        fields.put("description", description.toString());

        ckanApiClient.patchOrganization(ckanOrgShortName, fields);
        logger.info("NACE codes synced to CKAN organization '{}' from onboarding request",
                ckanOrgShortName);
    }

    /**
     * Synchronize organization and user with CKAN when creating directly from SPIP credentials.
     * This is used when admin creates organization from SPIP user (not through onboarding flow).
     *
     * @param organizationName Full organization name
     * @param spipUsername SPIP username
     * @param spipPassword SPIP password (plaintext)
     * @param contactEmail Contact email for the organization
     * @return CkanSyncResult with status of organization and user synchronization
     */
    public CkanSyncResult synchronizeDirectCreation(String organizationName, String spipUsername,
                                                     String spipPassword, String contactEmail) {
        logger.info("=== Starting Direct CKAN Synchronization for: {} ===", organizationName);

        CkanSyncResult result = new CkanSyncResult();
        result.setOrganizationName(organizationName);
        result.setUsername(spipUsername);

        try {
            // Generate CKAN-compatible organization short name
            String ckanOrgShortName = generateCkanOrgShortName(organizationName);
            result.setCkanOrgShortName(ckanOrgShortName);

            // Step 1: Create/Check Organization
            logger.info("Step 1/4: Creating/checking organization in CKAN");
            boolean orgCreated = createOrganizationIdempotent(ckanOrgShortName, organizationName);
            result.setOrganizationCreated(orgCreated);
            result.setOrganizationExists(!orgCreated);

            if (orgCreated) {
                logger.info("✓ Organization '{}' created in CKAN", ckanOrgShortName);
            } else {
                logger.info("✓ Organization '{}' already exists in CKAN", ckanOrgShortName);
            }

            // Step 2: Create/Check User
            logger.info("Step 2/4: Creating/checking user in CKAN");
            boolean userCreated = createUserIdempotent(spipUsername, contactEmail, spipPassword);
            result.setUserCreated(userCreated);
            result.setUserExists(!userCreated);

            if (userCreated) {
                logger.info("✓ User '{}' created in CKAN", spipUsername);
            } else {
                logger.info("✓ User '{}' already exists in CKAN", spipUsername);
            }

            // Step 3: Add User to Organization
            logger.info("Step 3/4: Adding user to organization");
            boolean membershipCreated = addUserToOrganizationIdempotent(ckanOrgShortName, spipUsername);
            result.setMembershipCreated(membershipCreated);

            if (membershipCreated) {
                logger.info("✓ User '{}' added to organization '{}'", spipUsername, ckanOrgShortName);
            } else {
                logger.info("✓ User '{}' already member of organization '{}'", spipUsername, ckanOrgShortName);
            }

            // Step 3b: Add user to shared use-case category groups (non-fatal - see method doc)
            logger.info("Step 3b: Adding user to use-case category groups");
            List<String> groupsGranted = addUserToUseCaseGroupsIdempotent(spipUsername);
            result.setGroupMembershipsGranted(groupsGranted);
            logger.info("✓ User '{}' has editor membership on group(s): {}", spipUsername, groupsGranted);

            // Step 4: Create API token for user (non-fatal)
            logger.info("Step 4/4: Creating API token for user");
            String apiToken = createApiTokenIdempotent(spipUsername, ckanOrgShortName);
            result.setApiToken(apiToken);
            result.setApiTokenCreated(apiToken != null);
            if (apiToken != null) {
                logger.info("✓ API token created for user '{}'", spipUsername);
            } else {
                logger.warn("⚠ API token not created for user '{}' - admin can assign manually later", spipUsername);
            }

            result.setSuccess(true);
            logger.info("=== Direct CKAN Synchronization Complete ===");
            return result;

        } catch (Exception e) {
            logger.error("Direct CKAN synchronization failed: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    /**
     * Step 1: Create organization in CKAN (idempotent wrapper)
     * Handles CONFLICT errors gracefully - if organization already exists, continues without error
     *
     * @return true if created, false if already exists
     */
    private boolean createOrganizationIdempotent(String shortName, String fullName) {
        try {
            ckanApiClient.createOrganization(shortName, fullName);
            return true; // Successfully created
        } catch (CkanApiClient.CkanApiException e) {
            // Check if error is due to existing organization (idempotent behavior)
            // CKAN may return either "Organization already exists" or "Group name already exists"
            if (e.getMessage() != null &&
                (e.getMessage().contains("Organization already exists in CKAN") ||
                 e.getMessage().contains("Group name already exists"))) {
                logger.warn("Organization '{}' already exists in CKAN - continuing with synchronization", shortName);
                return false; // Already exists
            }
            // Re-throw other errors wrapped in CkanSyncException
            throw new CkanSyncException(e.getMessage(), e);
        }
    }

    /**
     * Step 2: Create user in CKAN (idempotent wrapper)
     * Handles CONFLICT errors gracefully - if user already exists, continues without error
     *
     * @return true if created, false if already exists
     */
    private boolean createUserIdempotent(String username, String email, String password) {
        try {
            // Password is now passed as plaintext (decrypted by JPA EncryptedStringConverter)
            ckanApiClient.createUser(username, email, password);
            return true; // Successfully created
        } catch (CkanApiClient.CkanApiException e) {
            // Only treat username conflicts as idempotent, NOT email conflicts
            if (e.getMessage() != null && e.getMessage().contains("User already exists in CKAN")) {
                logger.warn("User '{}' already exists in CKAN - continuing with synchronization", username);
                return false; // Already exists
            }
            if (e.getMessage() != null && e.getMessage().contains("Email already registered")) {
                throw new CkanSyncException(
                        "Cannot create CKAN user '" + username + "': the email '" + email +
                        "' is already used by another CKAN user. Please use a different email address.", e);
            }
            // Re-throw other errors wrapped in CkanSyncException
            throw new CkanSyncException(e.getMessage(), e);
        }
    }

    /**
     * Step 3: Add user as member to organization (idempotent wrapper)
     * Handles CONFLICT errors gracefully - if user already a member, continues without error
     *
     * @return true if added, false if already member
     */
    private boolean addUserToOrganizationIdempotent(String orgShortName, String username) {
        try {
            ckanApiClient.addUserToOrganization(orgShortName, username, "editor");
            return true; // Successfully added
        } catch (CkanApiClient.CkanApiException e) {
            // Check if error is due to existing membership (idempotent behavior)
            if (e.getMessage() != null && e.getMessage().contains("User is already a member")) {
                logger.warn("User '{}' is already a member of organization '{}' - synchronization complete", username, orgShortName);
                return false; // Already member
            }
            // Re-throw other errors wrapped in CkanSyncException
            throw new CkanSyncException(e.getMessage(), e);
        }
    }

    /**
     * Step 3b: Add user as member to every active portal-managed category (idempotent,
     * non-fatal). Unlike organization membership, a failure here does not fail the whole sync —
     * group categorization is a nice-to-have on top of a working CKAN account, not a
     * precondition for one. Mirrors the non-fatal handling already used for API token creation
     * (step 4).
     *
     * <p>The category list comes from the portal's own {@link CategoryRepository} (platform-admin
     * managed via {@code /admin/categories}), not a live CKAN {@code group_list} call — the
     * portal DB is the source of truth for which groups are real "categories", so a group
     * created directly in CKAN outside the portal isn't auto-granted, and a deactivated category
     * stops being granted immediately rather than waiting for it to actually disappear from
     * CKAN. Grants "editor" membership so the org's own CKAN token (not just the platform
     * sysadmin token) can attach datasets it publishes to these groups later — see
     * docs/developer/ckan/CKAN-GROUP-MEMBERSHIP-FOR-ONBOARDED-USERS.md.</p>
     *
     * @param username the CKAN username to grant group membership to
     * @return the slugs of categories the user ended up a member of (newly granted or already there)
     */
    private List<String> addUserToUseCaseGroupsIdempotent(String username) {
        List<Category> categories = categoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();
        if (categories.isEmpty()) {
            logger.debug("No active portal categories configured - skipping group membership step");
            return List.of();
        }

        List<String> succeeded = new ArrayList<>();
        for (Category category : categories) {
            if (grantCategoryMembership(category.getSlug(), username)) {
                succeeded.add(category.getSlug());
            }
        }
        return succeeded;
    }

    /**
     * Grants "editor" membership on one category to one CKAN username, treating CKAN's
     * "already a member" response as success (idempotent). Shared by
     * {@link #addUserToUseCaseGroupsIdempotent} (all categories, one user),
     * {@link #backfillGroupMembershipForAllOrganizations} (all categories, all users), and
     * {@link #grantCategoryToAllOrganizations} (one category, all users).
     *
     * @return true if the user ended up a member (newly granted or already there), false if the
     *         grant failed for any other reason (logged as a warning, never thrown)
     */
    private boolean grantCategoryMembership(String categorySlug, String username) {
        try {
            ckanApiClient.addUserToGroup(categorySlug, username, "editor");
            return true;
        } catch (CkanApiClient.CkanApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("already a member")) {
                return true; // already granted — still counts as "in place"
            }
            logger.warn("Failed to add user '{}' to group '{}': {} - a dataset published under "
                    + "this account may not be able to attach to this category later", username, categorySlug, e.getMessage());
            return false;
        }
    }

    /**
     * One-off backfill: grants CKAN group membership on every active portal category to every
     * organization that already has a working CKAN account, for organizations onboarded before
     * the group-membership step (or before a given category) existed.
     *
     * <p>New onboardings already get this automatically via {@link #addUserToUseCaseGroupsIdempotent}
     * (step 3b of {@link #synchronizeOnboardingRequest}); this method exists for everyone else.
     * Only organizations with {@code OrganizationSpipUser.ckanSynchronized == true} are processed
     * (i.e. ones that actually have a CKAN account to grant membership on). Idempotent and safe
     * to re-run any time — e.g. after adding a new category, to grant it to existing orgs too —
     * since CKAN's own "already a member" response is treated as success, same as the per-user
     * onboarding step. See docs/developer/ckan/CKAN-GROUP-MEMBERSHIP-FOR-ONBOARDED-USERS.md,
     * "Rollout for already-onboarded organizations".</p>
     *
     * @return one result per processed organization, with the granted category slugs or an error
     */
    @Transactional(readOnly = true)
    public List<GroupMembershipBackfillResult> backfillGroupMembershipForAllOrganizations() {
        List<GroupMembershipBackfillResult> results = new ArrayList<>();

        for (OrganizationSpipUser spipUser : organizationsWithCkanAccount()) {
            String username = resolveCkanUsername(spipUser);
            String organizationName = organizationNameOf(spipUser);

            try {
                List<String> granted = addUserToUseCaseGroupsIdempotent(username);
                results.add(new GroupMembershipBackfillResult(organizationName, username, granted, null));
            } catch (Exception e) {
                logger.error("Group membership backfill failed for organization '{}' (user '{}'): {}",
                        organizationName, username, e.getMessage(), e);
                results.add(new GroupMembershipBackfillResult(organizationName, username, List.of(), e.getMessage()));
            }
        }

        logger.info("Group membership backfill processed {} organization(s)", results.size());
        return results;
    }

    /**
     * Grants one specific category to every organization that already has a working CKAN
     * account. Called right after a platform admin creates a new category
     * ({@code CategoryService#create}), so existing organizations don't have to wait for a full
     * {@link #backfillGroupMembershipForAllOrganizations} run (or a new onboarding) to get access
     * to a category that was added after they were onboarded. Same idempotent, non-fatal-per-org
     * handling as the other two grant methods.
     *
     * @param categorySlug the CKAN group name of the newly created category
     * @return one result per processed organization, with the category slug granted or an error
     */
    @Transactional(readOnly = true)
    public List<GroupMembershipBackfillResult> grantCategoryToAllOrganizations(String categorySlug) {
        List<GroupMembershipBackfillResult> results = new ArrayList<>();

        for (OrganizationSpipUser spipUser : organizationsWithCkanAccount()) {
            String username = resolveCkanUsername(spipUser);
            String organizationName = organizationNameOf(spipUser);

            try {
                boolean granted = grantCategoryMembership(categorySlug, username);
                results.add(new GroupMembershipBackfillResult(organizationName, username,
                        granted ? List.of(categorySlug) : List.of(), null));
            } catch (Exception e) {
                logger.error("Granting category '{}' failed for organization '{}' (user '{}'): {}",
                        categorySlug, organizationName, username, e.getMessage(), e);
                results.add(new GroupMembershipBackfillResult(organizationName, username, List.of(), e.getMessage()));
            }
        }

        logger.info("Granted category '{}' to {} organization(s)", categorySlug, results.size());
        return results;
    }

    /** Organizations with a working CKAN account — the only ones there's anything to grant. */
    private List<OrganizationSpipUser> organizationsWithCkanAccount() {
        return organizationSpipUserRepository.findAll().stream()
                .filter(spipUser -> Boolean.TRUE.equals(spipUser.getCkanSynchronized()))
                .toList();
    }

    /** @see #resolveCkanUsername(OrganizationOnboardingRequest) */
    private static String resolveCkanUsername(OrganizationSpipUser spipUser) {
        String ckanUser = spipUser.getCkanUser();
        return (ckanUser != null && !ckanUser.isBlank()) ? ckanUser : spipUser.getSpipUser();
    }

    private static String organizationNameOf(OrganizationSpipUser spipUser) {
        return spipUser.getOrganization() != null ? spipUser.getOrganization().getName() : spipUser.getSpipUser();
    }

    /**
     * Step 4: Create API token for user (non-fatal wrapper)
     * If token creation fails, logs a warning and returns null instead of failing the entire sync.
     * The token name follows the pattern "d4c-portal-{orgShortName}".
     *
     * @param username the CKAN username
     * @param orgShortName the organization short name (used in token name)
     * @return the API token string, or null if creation failed
     */
    private String createApiTokenIdempotent(String username, String orgShortName) {
        try {
            String tokenName = "d4c-portal-" + orgShortName;
            return ckanApiClient.createApiToken(username, tokenName);
        } catch (CkanApiClient.CkanApiException e) {
            logger.warn("Failed to create API token for user '{}': {} - admin can assign manually later", username, e.getMessage());
            return null;
        }
    }

    /**
     * Retry CKAN API token creation for cases where it was not created during initial sync.
     * Called during approval if the token is missing.
     */
    public String retryCkanApiTokenCreation(String spipUsername, String ckanOrgShortName) {
        logger.info("Retrying CKAN API token creation for user '{}', org '{}'", spipUsername, ckanOrgShortName);
        return createApiTokenIdempotent(spipUsername, ckanOrgShortName);
    }

    /**
     * Generate a unique CKAN-compatible organization short name from company name.
     * Ensures uniqueness by checking existing names in database and appending counter if needed.
     *
     * @param companyName The full company name
     * @return A unique CKAN-compatible organization short name
     * @throws IllegalArgumentException if company name is null or empty
     */
    public String generateCkanOrgShortName(String companyName) {
        if (companyName == null || companyName.trim().isEmpty()) {
            throw new IllegalArgumentException("Company name cannot be null or empty");
        }

        // Generate base short name
        String baseShortName = generateBaseShortName(companyName);

        // Ensure uniqueness by checking database and appending counter if needed
        String uniqueShortName = ensureUniqueCkanOrgName(baseShortName);

        logger.debug("Generated CKAN org short name: '{}' from company name: '{}'", uniqueShortName, companyName);
        return uniqueShortName;
    }

    /**
     * Generate base short name from company name (without uniqueness check).
     * Delegates to {@link CkanSlugUtil}, which holds the actual CKAN-name regex chain shared
     * with the category feature's slug generation.
     */
    private String generateBaseShortName(String companyName) {
        return CkanSlugUtil.slugify(companyName, "organization");
    }

    /**
     * Ensure the CKAN organization name is unique by checking database.
     * If name already exists, appends a counter (e.g., "-2", "-3") until unique.
     *
     * Example:
     * - "abc-corp" exists → returns "abc-corp-2"
     * - "abc-corp-2" exists → returns "abc-corp-3"
     *
     * @param baseName The base short name to check
     * @return A unique organization short name
     */
    private String ensureUniqueCkanOrgName(String baseName) {
        String candidateName = baseName;
        int counter = 2;

        // Check both onboarding requests and existing organizations
        while (isCkanOrgNameTaken(candidateName)) {
            candidateName = baseName + "-" + counter;
            counter++;

            // Safety limit to prevent infinite loop (very unlikely to reach)
            if (counter > 1000) {
                logger.error("Unable to generate unique CKAN org name after 1000 attempts for base: {}", baseName);
                throw new IllegalStateException("Unable to generate unique CKAN organization name");
            }

            logger.debug("Name '{}' already taken, trying '{}'", baseName, candidateName);
        }

        // Uniqueness ensured - return the candidate name
        return candidateName;
    }

    /**
     * Check if a CKAN organization name is already taken in the database.
     * Checks both:
     * 1. Onboarding requests (pending/approved)
     * 2. Organization SPIP users (already created organizations)
     *
     * @param orgName The organization short name to check
     * @return true if name is already in use
     */
    private boolean isCkanOrgNameTaken(String orgName) {
        // Check onboarding requests
        List<OrganizationOnboardingRequest> existingRequests =
            onboardingRequestRepository.findByCkanOrganizationShortName(orgName);

        if (!existingRequests.isEmpty()) {
            logger.debug("CKAN org name '{}' found in {} onboarding request(s)", orgName, existingRequests.size());
            return true;
        }

        // Check organization SPIP users (approved organizations)
        List<OrganizationSpipUser> existingOrgs =
            organizationSpipUserRepository.findByCkanOrganizationName(orgName);

        if (!existingOrgs.isEmpty()) {
            logger.debug("CKAN org name '{}' found in {} organization(s)", orgName, existingOrgs.size());
            return true;
        }

        return false;
    }

    /**
     * Sync NACE codes to a CKAN organization using extras and description.
     * This is a non-fatal operation - if it fails, the error is logged but not propagated.
     *
     * Extras store structured data for programmatic access:
     *   - "nace_codes": comma-separated codes (e.g., "A01.1,C10.1")
     *   - "nace_descriptions": human-readable descriptions
     *
     * Description is set to "nace=CODE" format (e.g., "nace=11.02, nace=11.03"),
     * making NACE codes searchable via CKAN's organization_list?q= API.
     *
     * @param ckanOrgShortName The CKAN organization short name
     * @param naceCodes The set of NACE codes to sync
     */
    /**
     * @throws CkanApiClient.CkanApiException if CKAN sync fails
     */
    public void syncNaceCodes(String ckanOrgShortName, Set<NaceCode> naceCodes) {
        if (ckanOrgShortName == null || ckanOrgShortName.isEmpty()) {
            throw new CkanApiClient.CkanApiException("Cannot sync NACE codes: CKAN organization short name is null or empty");
        }

        logger.info("Syncing NACE codes to CKAN organization '{}': {} codes", ckanOrgShortName,
                naceCodes != null ? naceCodes.size() : 0);

        Map<String, Object> fields = new HashMap<>();
        List<Map<String, String>> extras = new ArrayList<>();

        if (naceCodes != null && !naceCodes.isEmpty()) {
            String codes = naceCodes.stream()
                    .map(NaceCode::getCode)
                    .sorted()
                    .collect(Collectors.joining(","));

            String descriptions = naceCodes.stream()
                    .sorted((a, b) -> a.getCode().compareTo(b.getCode()))
                    .map(nc -> nc.getCode() + " - " + nc.getDescription())
                    .collect(Collectors.joining("; "));

            extras.add(Map.of("key", "nace_codes", "value", codes));
            extras.add(Map.of("key", "nace_descriptions", "value", descriptions));

            // Description in "nace=CODE" format for CKAN search
            String description = naceCodes.stream()
                    .map(NaceCode::getCode)
                    .sorted()
                    .map(code -> "nace=" + code)
                    .collect(Collectors.joining(", "));
            fields.put("description", description);
        } else {
            extras.add(Map.of("key", "nace_codes", "value", ""));
            extras.add(Map.of("key", "nace_descriptions", "value", ""));
            fields.put("description", "");
        }

        fields.put("extras", extras);

        ckanApiClient.patchOrganization(ckanOrgShortName, fields);
        logger.info("NACE codes synced successfully to CKAN organization '{}'", ckanOrgShortName);
    }

    /**
     * Check if an organization already exists in CKAN.
     * This is used to prevent duplicate organization creation.
     *
     * @param organizationName The full organization name
     * @return true if organization exists in CKAN, false otherwise
     */
    public boolean checkOrganizationExists(String organizationName) {
        String ckanOrgShortName = generateCkanOrgShortName(organizationName);
        logger.debug("Checking if organization exists in CKAN: {}", ckanOrgShortName);

        return ckanApiClient.organizationExists(ckanOrgShortName);
    }

    /**
     * Custom exception for CKAN synchronization errors
     */
    public static class CkanSyncException extends RuntimeException {
        public CkanSyncException(String message) {
            super(message);
        }

        public CkanSyncException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
