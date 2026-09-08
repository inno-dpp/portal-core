package com.data4circ.portal.features.category.dto;

import com.data4circ.portal.features.category.entity.Category;
import com.data4circ.portal.features.onboardingsync.ckan.GroupMembershipBackfillResult;

import java.util.List;

/**
 * Result of a {@code CategoryService} create/update/deactivate/reactivate call. The portal DB
 * write always succeeds (or throws) independently of CKAN — {@code ckanWarning} is set when the
 * DB write succeeded but the accompanying CKAN sync call failed, so the controller can flash a
 * warning instead of (or in addition to) the normal success message. See {@code CategoryService}
 * for why CKAN sync failures never block the DB write.
 */
public class CategorySaveResult {

    private final Category category;
    private final String ckanWarning;
    private final List<GroupMembershipBackfillResult> existingOrganizationsGranted;

    public CategorySaveResult(Category category, String ckanWarning) {
        this(category, ckanWarning, List.of());
    }

    /**
     * @param existingOrganizationsGranted only populated by {@code create()} — the outcome of
     *                                     granting the brand-new category to every organization
     *                                     that was already onboarded. Empty for update/deactivate/
     *                                     reactivate, where this doesn't apply.
     */
    public CategorySaveResult(Category category, String ckanWarning,
                               List<GroupMembershipBackfillResult> existingOrganizationsGranted) {
        this.category = category;
        this.ckanWarning = ckanWarning;
        this.existingOrganizationsGranted = existingOrganizationsGranted != null
                ? existingOrganizationsGranted : List.of();
    }

    public Category getCategory() {
        return category;
    }

    /** Null when the CKAN sync succeeded (or wasn't needed, e.g. the group already existed). */
    public String getCkanWarning() {
        return ckanWarning;
    }

    public boolean hasCkanWarning() {
        return ckanWarning != null;
    }

    public List<GroupMembershipBackfillResult> getExistingOrganizationsGranted() {
        return existingOrganizationsGranted;
    }
}
