package com.data4circ.portal.common.util;

/**
 * Turns a free-text name into a CKAN-compatible slug (organization short name, group name,
 * etc). CKAN requires lowercase alphanumeric characters plus hyphens/underscores, no leading
 * or trailing separator, and a length CKAN itself caps at 100 characters.
 *
 * <p>Extracted from {@code CkanOnboardingSyncService.generateBaseShortName()}, which was the
 * original (and until now, only) caller of this exact regex chain. Kept here so a second
 * caller (category slugs) doesn't have to duplicate it.</p>
 */
public final class CkanSlugUtil {

    private CkanSlugUtil() {
    }

    /**
     * Slugify {@code input}, falling back to {@code fallback} if nothing usable survives the
     * character stripping (e.g. an input that's entirely punctuation/whitespace).
     *
     * @param input    free-text name to slugify (e.g. a company or category title)
     * @param fallback slug to use when {@code input} slugifies to an empty string
     * @return a lowercase, CKAN-compatible slug, truncated to 96 characters to leave room for
     *         a numeric uniqueness suffix (e.g. {@code "-2"}) up to CKAN's 100-char limit
     */
    public static String slugify(String input, String fallback) {
        // Replace whitespace runs with a single hyphen and lowercase everything
        String slug = input == null ? "" : input.replaceAll("\\s+", "-").toLowerCase();

        // Remove special characters - keep only letters, numbers, hyphens, and underscores
        slug = slug.replaceAll("[^a-z0-9_-]", "");

        // Ensure it doesn't start or end with hyphen/underscore
        slug = slug.replaceAll("^[-_]+|[-_]+$", "");

        // Remove consecutive hyphens/underscores
        slug = slug.replaceAll("[-_]{2,}", "-");

        // Ensure the slug is not empty after processing
        if (slug.isEmpty()) {
            slug = fallback;
        }

        // Reserve space for a potential uniqueness suffix (e.g. "-999")
        if (slug.length() > 96) {
            slug = slug.substring(0, 96);
            // Ensure it doesn't end with a hyphen after truncation
            slug = slug.replaceAll("[-_]+$", "");
        }

        return slug;
    }
}
