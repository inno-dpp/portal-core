package com.data4circ.portal.features.onboardingsync.tool;

/**
 * Result of a successful tool synchronization. Failures are reported by throwing
 * from {@link OnboardingToolProvisioner#synchronize}; this type only carries the
 * diagnostic payload persisted on the sync record.
 */
public class OnboardingToolSyncOutcome {

    private final String details;

    private OnboardingToolSyncOutcome(String details) {
        this.details = details;
    }

    public static OnboardingToolSyncOutcome withDetails(String details) {
        return new OnboardingToolSyncOutcome(details);
    }

    public static OnboardingToolSyncOutcome empty() {
        return new OnboardingToolSyncOutcome(null);
    }

    public String getDetails() {
        return details;
    }
}
