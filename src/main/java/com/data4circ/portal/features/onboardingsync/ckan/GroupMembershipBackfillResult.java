package com.data4circ.portal.features.onboardingsync.ckan;

import java.util.List;

/**
 * Outcome of the group-membership backfill for one organization — see
 * {@code CkanOnboardingSyncService#backfillGroupMembershipForAllOrganizations()}.
 */
public record GroupMembershipBackfillResult(String organizationName, String username,
                                             List<String> categoriesGranted, String errorMessage) {

    public boolean isSuccess() {
        return errorMessage == null;
    }
}
