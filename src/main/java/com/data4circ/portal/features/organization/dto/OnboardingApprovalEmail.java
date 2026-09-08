package com.data4circ.portal.features.organization.dto;

/**
 * Payload of the onboarding approval email. Built with a builder so call sites name
 * every value — the previous 12 positional String parameters made transposed
 * arguments compile silently and forced signature churn for every new tool.
 * Credential fields are optional; they are only rendered in legacy
 * (non-secure-link) email mode.
 */
public class OnboardingApprovalEmail {

    private final String toEmail;
    private final String companyName;
    private final String contactName;
    private final String username;
    private final String temporaryPassword;
    private final String spipUsername;
    private final String spipPassword;
    private final String ckanUsername;
    private final String ckanPassword;
    private final String ckanApiToken;
    private final String keycloakUsername;
    private final String keycloakPassword;
    private final String keycloakAccountUrl;
    private final String setPasswordUrl;

    private OnboardingApprovalEmail(Builder builder) {
        this.toEmail = builder.toEmail;
        this.companyName = builder.companyName;
        this.contactName = builder.contactName;
        this.username = builder.username;
        this.temporaryPassword = builder.temporaryPassword;
        this.spipUsername = builder.spipUsername;
        this.spipPassword = builder.spipPassword;
        this.ckanUsername = builder.ckanUsername;
        this.ckanPassword = builder.ckanPassword;
        this.ckanApiToken = builder.ckanApiToken;
        this.keycloakUsername = builder.keycloakUsername;
        this.keycloakPassword = builder.keycloakPassword;
        this.keycloakAccountUrl = builder.keycloakAccountUrl;
        this.setPasswordUrl = builder.setPasswordUrl;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getToEmail() {
        return toEmail;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getContactName() {
        return contactName;
    }

    public String getUsername() {
        return username;
    }

    public String getTemporaryPassword() {
        return temporaryPassword;
    }

    public String getSpipUsername() {
        return spipUsername;
    }

    public String getSpipPassword() {
        return spipPassword;
    }

    /** Only set when CKAN provisioned its own account (SPIP not synced for this request). */
    public String getCkanUsername() {
        return ckanUsername;
    }

    /** @see #getCkanUsername() */
    public String getCkanPassword() {
        return ckanPassword;
    }

    public String getCkanApiToken() {
        return ckanApiToken;
    }

    public String getKeycloakUsername() {
        return keycloakUsername;
    }

    public String getKeycloakPassword() {
        return keycloakPassword;
    }

    public String getKeycloakAccountUrl() {
        return keycloakAccountUrl;
    }

    public String getSetPasswordUrl() {
        return setPasswordUrl;
    }

    public static class Builder {

        private String toEmail;
        private String companyName;
        private String contactName;
        private String username;
        private String temporaryPassword;
        private String spipUsername;
        private String spipPassword;
        private String ckanUsername;
        private String ckanPassword;
        private String ckanApiToken;
        private String keycloakUsername;
        private String keycloakPassword;
        private String keycloakAccountUrl;
        private String setPasswordUrl;

        public Builder toEmail(String toEmail) {
            this.toEmail = toEmail;
            return this;
        }

        public Builder companyName(String companyName) {
            this.companyName = companyName;
            return this;
        }

        public Builder contactName(String contactName) {
            this.contactName = contactName;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder temporaryPassword(String temporaryPassword) {
            this.temporaryPassword = temporaryPassword;
            return this;
        }

        public Builder spipUsername(String spipUsername) {
            this.spipUsername = spipUsername;
            return this;
        }

        public Builder spipPassword(String spipPassword) {
            this.spipPassword = spipPassword;
            return this;
        }

        public Builder ckanUsername(String ckanUsername) {
            this.ckanUsername = ckanUsername;
            return this;
        }

        public Builder ckanPassword(String ckanPassword) {
            this.ckanPassword = ckanPassword;
            return this;
        }

        public Builder ckanApiToken(String ckanApiToken) {
            this.ckanApiToken = ckanApiToken;
            return this;
        }

        public Builder keycloakUsername(String keycloakUsername) {
            this.keycloakUsername = keycloakUsername;
            return this;
        }

        public Builder keycloakPassword(String keycloakPassword) {
            this.keycloakPassword = keycloakPassword;
            return this;
        }

        public Builder keycloakAccountUrl(String keycloakAccountUrl) {
            this.keycloakAccountUrl = keycloakAccountUrl;
            return this;
        }

        public Builder setPasswordUrl(String setPasswordUrl) {
            this.setPasswordUrl = setPasswordUrl;
            return this;
        }

        public OnboardingApprovalEmail build() {
            return new OnboardingApprovalEmail(this);
        }
    }
}
