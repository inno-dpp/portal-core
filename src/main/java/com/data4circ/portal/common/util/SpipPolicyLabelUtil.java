package com.data4circ.portal.common.util;

/**
 * Builds the SPIP access-policy label for an organization and exposes the same sanitize/cap
 * logic so callers can pre-check for label collisions before an onboarding request is even
 * created (SPIP rejects policy creation - "Failed to add the policy or the attribute list" -
 * when the label is too long, so the organization-name portion is capped well before that call
 * is made; see {@link com.data4circ.portal.features.onboardingsync.spip.SpipOnboardingService}).
 */
public final class SpipPolicyLabelUtil {

    private SpipPolicyLabelUtil() {
    }

    /** Fixed suffix appended to every organization's access-policy label. */
    public static final String ACCESS_POLICY_SUFFIX = "_access_policy";

    /** Hard limit SPIP enforces on policy labels. */
    public static final int MAX_LABEL_LENGTH = 40;

    /** Room left for the sanitized organization name once the suffix is accounted for. */
    public static final int MAX_NAME_LENGTH = MAX_LABEL_LENGTH - ACCESS_POLICY_SUFFIX.length();

    /**
     * Lowercase {@code value} and collapse it to {@code [a-z0-9_]}, matching the sanitization
     * SPIP attribute values already go through elsewhere in the onboarding flow.
     */
    public static String sanitize(String value) {
        if (value == null) return "";
        return value.toLowerCase()
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    /**
     * Sanitized organization-name portion of the access-policy label, capped to
     * {@link #MAX_NAME_LENGTH} characters (with any trailing underscore left by the cut
     * stripped). Two organizations that produce the same value here would collide on the same
     * SPIP policy label.
     */
    public static String sanitizeAndCapName(String companyName) {
        String sanitized = sanitize(companyName);
        if (sanitized.length() <= MAX_NAME_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_NAME_LENGTH).replaceAll("_+$", "");
    }

    /** Full access-policy label as sent to SPIP, e.g. {@code "acme_corp_access_policy"}. */
    public static String generateAccessPolicyLabel(String companyName) {
        return sanitizeAndCapName(companyName) + ACCESS_POLICY_SUFFIX;
    }
}
