package com.data4circ.portal.features.onboardingsync.tool;

import java.util.List;

/**
 * View model for one onboarding tool card on the admin onboarding request detail page.
 * Config-only tools (no provisioner) render as informational cards without a
 * synchronize form.
 */
public class OnboardingToolView {

    private final String key;
    private final String displayName;
    private final String iconClass;
    private final boolean synced;
    private final boolean dependenciesMet;
    private final String dependencyNames;
    private final List<OnboardingToolInputField> fields;
    private final String statusNote;
    private final boolean configOnly;
    private final boolean required;

    public OnboardingToolView(String key, String displayName, String iconClass,
                              boolean synced, boolean dependenciesMet, String dependencyNames,
                              List<OnboardingToolInputField> fields, String statusNote, boolean configOnly,
                              boolean required) {
        this.key = key;
        this.displayName = displayName;
        this.iconClass = iconClass;
        this.synced = synced;
        this.dependenciesMet = dependenciesMet;
        this.dependencyNames = dependencyNames;
        this.fields = fields != null ? fields : List.of();
        this.statusNote = statusNote;
        this.configOnly = configOnly;
        this.required = required;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIconClass() {
        return iconClass;
    }

    public boolean isSynced() {
        return synced;
    }

    public boolean isDependenciesMet() {
        return dependenciesMet;
    }

    /** Human-readable, comma-separated names of unmet/declared dependencies. */
    public String getDependencyNames() {
        return dependencyNames;
    }

    /** Admin input fields in display order; empty for config-only tools. */
    public List<OnboardingToolInputField> getFields() {
        return fields;
    }

    public String getStatusNote() {
        return statusNote;
    }

    /** True for tools without a provisioner: no sync form, connector created at approval. */
    public boolean isConfigOnly() {
        return configOnly;
    }

    /** True when approval is gated on this tool's synchronization (see {@code app.onboarding.tools.<key>.required}). */
    public boolean isRequired() {
        return required;
    }

    /** True for an optional, provisioner-backed tool that has not been synchronized: approval proceeds without it. */
    public boolean isSkippedAtApproval() {
        return !configOnly && !required && !synced;
    }
}
