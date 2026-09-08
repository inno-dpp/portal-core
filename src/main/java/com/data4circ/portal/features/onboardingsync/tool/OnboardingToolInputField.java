package com.data4circ.portal.features.onboardingsync.tool;

/**
 * Describes one admin-supplied input field a tool needs for synchronization
 * (e.g. the SPIP username or the CKAN organization short name). Rendered generically
 * by the onboarding request detail view.
 */
public class OnboardingToolInputField {

    private final String name;
    private final String label;
    private final String proposedValue;
    private final String htmlPattern;
    private final Integer minLength;
    private final Integer maxLength;
    private final String helpText;
    private final String type;
    private final boolean required;
    private final boolean readOnly;

    /** Creates a required text field (the historical single-field shape). */
    public OnboardingToolInputField(String name, String label, String proposedValue,
                                    String htmlPattern, Integer minLength, Integer maxLength,
                                    String helpText) {
        this(name, label, proposedValue, htmlPattern, minLength, maxLength, helpText, "text", true);
    }

    public OnboardingToolInputField(String name, String label, String proposedValue,
                                    String htmlPattern, Integer minLength, Integer maxLength,
                                    String helpText, String type, boolean required) {
        this(name, label, proposedValue, htmlPattern, minLength, maxLength, helpText, type, required, false);
    }

    public OnboardingToolInputField(String name, String label, String proposedValue,
                                    String htmlPattern, Integer minLength, Integer maxLength,
                                    String helpText, String type, boolean required, boolean readOnly) {
        this.name = name;
        this.label = label;
        this.proposedValue = proposedValue;
        this.htmlPattern = htmlPattern;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.helpText = helpText;
        this.type = type;
        this.required = required;
        this.readOnly = readOnly;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public String getProposedValue() {
        return proposedValue;
    }

    public String getHtmlPattern() {
        return htmlPattern;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public String getHelpText() {
        return helpText;
    }

    /** HTML input type: {@code text}, {@code password} or {@code url}. */
    public String getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    /**
     * Informative field whose value is fixed by the system (rendered read-only).
     * Client-side only — implementations must not trust the submitted value.
     */
    public boolean isReadOnly() {
        return readOnly;
    }
}
