package com.data4circ.portal.features.onboardingsync.ckan;

import java.util.Collections;
import java.util.List;

/**
 * Result object for CKAN synchronization operations.
 * Contains status of organization and user creation/checks.
 */
public class CkanSyncResult {
    private boolean success;
    private String errorMessage;

    private String organizationName;
    private String ckanOrgShortName;
    private boolean organizationCreated;
    private boolean organizationExists;

    private String username;
    private boolean userCreated;
    private boolean userExists;

    private boolean membershipCreated;

    private String apiToken;
    private boolean apiTokenCreated;

    private List<String> groupMembershipsGranted = Collections.emptyList();

    public CkanSyncResult() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getCkanOrgShortName() {
        return ckanOrgShortName;
    }

    public void setCkanOrgShortName(String ckanOrgShortName) {
        this.ckanOrgShortName = ckanOrgShortName;
    }

    public boolean isOrganizationCreated() {
        return organizationCreated;
    }

    public void setOrganizationCreated(boolean organizationCreated) {
        this.organizationCreated = organizationCreated;
    }

    public boolean isOrganizationExists() {
        return organizationExists;
    }

    public void setOrganizationExists(boolean organizationExists) {
        this.organizationExists = organizationExists;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isUserCreated() {
        return userCreated;
    }

    public void setUserCreated(boolean userCreated) {
        this.userCreated = userCreated;
    }

    public boolean isUserExists() {
        return userExists;
    }

    public void setUserExists(boolean userExists) {
        this.userExists = userExists;
    }

    public boolean isMembershipCreated() {
        return membershipCreated;
    }

    public void setMembershipCreated(boolean membershipCreated) {
        this.membershipCreated = membershipCreated;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public boolean isApiTokenCreated() {
        return apiTokenCreated;
    }

    public void setApiTokenCreated(boolean apiTokenCreated) {
        this.apiTokenCreated = apiTokenCreated;
    }

    /**
     * Slugs of the shared use-case category groups the user was successfully granted (or already
     * held) editor membership on. Group membership is a non-fatal, best-effort step — a group
     * missing from this list means that category will 403 for this org's own CKAN token later,
     * not that the whole sync failed.
     */
    public List<String> getGroupMembershipsGranted() {
        return groupMembershipsGranted;
    }

    public void setGroupMembershipsGranted(List<String> groupMembershipsGranted) {
        this.groupMembershipsGranted = groupMembershipsGranted != null
                ? groupMembershipsGranted : Collections.emptyList();
    }

    /**
     * Build a user-friendly message summarizing the sync results
     */
    public String buildSummaryMessage() {
        if (!success) {
            return "CKAN synchronization failed: " + errorMessage;
        }

        StringBuilder message = new StringBuilder("CKAN Synchronization: ");

        // Organization status
        if (organizationCreated) {
            message.append("Organization '").append(ckanOrgShortName).append("' created. ");
        } else if (organizationExists) {
            message.append("Organization '").append(ckanOrgShortName).append("' already exists. ");
        }

        // User status
        if (userCreated) {
            message.append("User '").append(username).append("' created. ");
        } else if (userExists) {
            message.append("User '").append(username).append("' already exists. ");
        }

        // Membership status
        if (membershipCreated) {
            message.append("User added to organization. ");
        } else {
            message.append("User already member of organization. ");
        }

        // API token status
        if (apiTokenCreated) {
            message.append("API token created. ");
        } else if (apiToken == null) {
            message.append("API token not generated. ");
        }

        // Group membership status (non-fatal — see getGroupMembershipsGranted())
        if (!groupMembershipsGranted.isEmpty()) {
            message.append("Added to group(s): ").append(String.join(", ", groupMembershipsGranted)).append(".");
        } else {
            message.append("No group memberships granted.");
        }

        return message.toString();
    }
}
