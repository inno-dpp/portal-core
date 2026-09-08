package com.data4circ.portal.features.organization.dto;

import java.util.List;

/**
 * Summary of how complete an organisation's editable profile is, for the completeness indicator
 * on the edit page. {@code percentage} is 0–100; {@code pendingActions} lists the human-readable
 * actions still needed (empty when the profile is complete).
 */
public class ProfileCompletion {

    private final int percentage;
    private final List<String> pendingActions;

    public ProfileCompletion(int percentage, List<String> pendingActions) {
        this.percentage = percentage;
        this.pendingActions = pendingActions;
    }

    public int getPercentage() {
        return percentage;
    }

    public List<String> getPendingActions() {
        return pendingActions;
    }

    public boolean isComplete() {
        return pendingActions.isEmpty();
    }
}
