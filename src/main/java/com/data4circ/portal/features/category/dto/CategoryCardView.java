package com.data4circ.portal.features.category.dto;

/**
 * Dashboard-ready view of an active category: display fields plus the resolved CKAN "Explore
 * Data" link, built server-side ({@code DashboardController}) from {@code CkanSettingsService}'s
 * public URL rather than string-concatenated in the template.
 */
public class CategoryCardView {

    private final String title;
    private final String description;
    private final String icon;
    private final String styleCssSuffix;
    private final String exploreUrl;

    public CategoryCardView(String title, String description, String icon, String styleCssSuffix, String exploreUrl) {
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.styleCssSuffix = styleCssSuffix;
        this.exploreUrl = exploreUrl;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public String getStyleCssSuffix() {
        return styleCssSuffix;
    }

    public String getExploreUrl() {
        return exploreUrl;
    }
}
