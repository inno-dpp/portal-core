package com.data4circ.portal.features.category.service;

import com.data4circ.portal.common.exception.EntityNotFoundException;
import com.data4circ.portal.common.util.CkanSlugUtil;
import com.data4circ.portal.features.category.dto.CategoryFormDTO;
import com.data4circ.portal.features.category.dto.CategorySaveResult;
import com.data4circ.portal.features.category.entity.Category;
import com.data4circ.portal.features.category.enums.CategoryStyle;
import com.data4circ.portal.features.category.repository.CategoryRepository;
import com.data4circ.portal.features.onboardingsync.ckan.CkanOnboardingSyncService;
import com.data4circ.portal.features.onboardingsync.ckan.GroupMembershipBackfillResult;
import com.data4circ.portal.integration.ckan.client.CkanApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages portal-side CKAN categories (CKAN groups) and keeps the corresponding CKAN group in
 * sync on every write. The portal DB is authoritative: a CKAN sync failure on create/update/
 * deactivate/reactivate is logged and surfaced to the caller as a warning, but never blocks or
 * rolls back the DB write — see docs/developer/ckan/CKAN-GROUP-MEMBERSHIP-FOR-ONBOARDED-USERS.md
 * for the related decision that the portal, not CKAN, is authoritative for which categories
 * exist. Failing the whole admin action during a CKAN outage would make this screen unusable
 * exactly when an admin might need it; the next edit/save naturally retries the sync instead.
 */
@Service
@Transactional
public class CategoryService {

    private static final Logger logger = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categoryRepository;
    private final CkanApiClient ckanApiClient;
    private final CkanOnboardingSyncService ckanOnboardingSyncService;

    public CategoryService(CategoryRepository categoryRepository, CkanApiClient ckanApiClient,
                            CkanOnboardingSyncService ckanOnboardingSyncService) {
        this.categoryRepository = categoryRepository;
        this.ckanApiClient = ckanApiClient;
        this.ckanOnboardingSyncService = ckanOnboardingSyncService;
    }

    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Category> findAllActive() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();
    }

    @Transactional(readOnly = true)
    public Optional<Category> findById(Long id) {
        return categoryRepository.findById(id);
    }

    /**
     * Create a new category. The slug is generated from the title when the form leaves it
     * blank; either way it's slugified through {@link CkanSlugUtil} and checked for uniqueness
     * in the portal DB before saving.
     *
     * <p>After the CKAN group itself is created, this also grants the new category to every
     * organization that's already onboarded ({@link CkanOnboardingSyncService#grantCategoryToAllOrganizations}) —
     * otherwise only organizations onboarded *after* this point would ever get it, and existing
     * ones would silently need a manual resync every time a category is added.</p>
     *
     * @throws IllegalArgumentException if the resolved slug is already used by another category
     */
    public CategorySaveResult create(CategoryFormDTO form) {
        String requestedSlug = (form.getSlug() != null && !form.getSlug().isBlank())
                ? form.getSlug() : form.getTitle();
        String slug = CkanSlugUtil.slugify(requestedSlug, "category");

        if (categoryRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("A category with slug '" + slug + "' already exists");
        }

        String icon = (form.getIcon() != null && !form.getIcon().isBlank()) ? form.getIcon() : "fas fa-tags";
        CategoryStyle style = form.getStyle() != null ? form.getStyle() : CategoryStyle.PRIMARY;

        Category category = new Category(slug, form.getTitle(), form.getDescription(), icon, style);
        category = categoryRepository.save(category);
        logger.info("Category '{}' ({}) created", category.getTitle(), slug);

        String warning = syncCreateToCkan(category);
        List<GroupMembershipBackfillResult> existingOrgGrants = grantToExistingOrganizations(slug);
        return new CategorySaveResult(category, warning, existingOrgGrants);
    }

    /**
     * Update an existing category's display fields. Copies only title/description/icon/style
     * onto the persisted entity — slug, active state, id, and timestamps are never touched here,
     * mirroring ConnectorController's anti-mass-assignment pattern for the same reason (avoid
     * overwriting server-managed fields from a form-bound object).
     */
    public CategorySaveResult update(Long id, CategoryFormDTO form) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category", id));

        category.setTitle(form.getTitle());
        category.setDescription(form.getDescription());
        if (form.getIcon() != null && !form.getIcon().isBlank()) {
            category.setIcon(form.getIcon());
        }
        if (form.getStyle() != null) {
            category.setStyle(form.getStyle());
        }
        category = categoryRepository.save(category);
        logger.info("Category '{}' ({}) updated", category.getTitle(), category.getSlug());

        String warning = null;
        try {
            Map<String, Object> fields = new HashMap<>();
            fields.put("title", category.getTitle());
            fields.put("description", category.getDescription());
            ckanApiClient.patchGroup(category.getSlug(), fields);
        } catch (CkanApiClient.CkanApiException e) {
            warning = e.getMessage();
            logger.warn("Category '{}' updated in portal DB but CKAN sync failed: {}",
                    category.getSlug(), warning);
        }
        return new CategorySaveResult(category, warning);
    }

    /**
     * Soft-deactivate a category: flips the active flag and soft-deletes the CKAN group
     * (state=deleted, not purged). Hides the category from the dashboard and from onboarding's
     * group-membership grant. Reversible via {@link #reactivate(Long)}.
     */
    public CategorySaveResult deactivate(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category", id));

        category.setActive(false);
        category = categoryRepository.save(category);
        logger.info("Category '{}' ({}) deactivated", category.getTitle(), category.getSlug());

        String warning = null;
        try {
            ckanApiClient.deleteGroup(category.getSlug());
        } catch (CkanApiClient.CkanApiException e) {
            warning = e.getMessage();
            logger.warn("Category '{}' deactivated in portal DB but CKAN sync failed: {}",
                    category.getSlug(), warning);
        }
        return new CategorySaveResult(category, warning);
    }

    /** Reverses {@link #deactivate(Long)}: flips the active flag and restores the CKAN group. */
    public CategorySaveResult reactivate(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category", id));

        category.setActive(true);
        category = categoryRepository.save(category);
        logger.info("Category '{}' ({}) reactivated", category.getTitle(), category.getSlug());

        String warning = null;
        try {
            ckanApiClient.patchGroup(category.getSlug(), Map.of("state", "active"));
        } catch (CkanApiClient.CkanApiException e) {
            warning = e.getMessage();
            logger.warn("Category '{}' reactivated in portal DB but CKAN sync failed: {}",
                    category.getSlug(), warning);
        }
        return new CategorySaveResult(category, warning);
    }

    /**
     * Create the CKAN group for a newly-saved category, unless one with this slug already
     * exists in CKAN independently (in which case the portal simply starts managing it — no
     * need to fail or warn just because CKAN already had it).
     *
     * @return a warning message if the CKAN sync failed, or null on success
     */
    private String syncCreateToCkan(Category category) {
        try {
            if (ckanApiClient.groupExists(category.getSlug())) {
                logger.info("Group '{}' already exists in CKAN independently - portal now manages it, skipping create",
                        category.getSlug());
                return null;
            }
            ckanApiClient.createGroup(category.getSlug(), category.getTitle(), category.getDescription());
            return null;
        } catch (CkanApiClient.CkanApiException e) {
            logger.warn("Category '{}' saved in portal DB but CKAN group sync failed: {}",
                    category.getSlug(), e.getMessage());
            return e.getMessage();
        }
    }

    /**
     * Grants a newly-created category to every already-onboarded organization. Best-effort: any
     * unexpected failure here is logged and swallowed rather than propagated, so a problem
     * granting existing organizations never fails the category-creation request itself — the
     * category is still created either way, and a full resync
     * ({@code CkanOnboardingSyncService#backfillGroupMembershipForAllOrganizations}, e.g. via
     * {@code resync-ckan-categories.sh}) remains available to catch up anything missed.
     */
    private List<GroupMembershipBackfillResult> grantToExistingOrganizations(String slug) {
        try {
            return ckanOnboardingSyncService.grantCategoryToAllOrganizations(slug);
        } catch (Exception e) {
            logger.warn("Failed to grant new category '{}' to existing organizations: {}", slug, e.getMessage(), e);
            return List.of();
        }
    }
}
