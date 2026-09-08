package com.data4circ.portal.features.category.enums;

/**
 * Fixed set of Bootstrap color variants a category card can be rendered in. Kept as an enum
 * (not a free-text field) so the admin form is a dropdown, matching the app's existing style
 * conventions (e.g. {@code ConnectorType}/{@code ConnectorStatus}), rather than the free-text
 * "style" string {@code DashboardProperties.UseCaseCard} used to have.
 */
public enum CategoryStyle {
    PRIMARY("primary", "Primary (green)"),
    SUCCESS("success", "Success (blue-green)"),
    WARNING("warning", "Warning (amber)"),
    INFO("info", "Info (blue)"),
    DANGER("danger", "Danger (red)"),
    SECONDARY("secondary", "Secondary (grey)");

    private final String cssSuffix;
    private final String displayName;

    CategoryStyle(String cssSuffix, String displayName) {
        this.cssSuffix = cssSuffix;
        this.displayName = displayName;
    }

    /** Bootstrap class suffix, e.g. {@code "primary"} for {@code btn-outline-primary}/{@code text-primary}. */
    public String getCssSuffix() {
        return cssSuffix;
    }

    public String getDisplayName() {
        return displayName;
    }
}
