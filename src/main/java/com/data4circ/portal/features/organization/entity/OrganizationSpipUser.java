package com.data4circ.portal.features.organization.entity;

import com.data4circ.portal.common.converter.EncryptedStringConverter;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Per-organization tool credentials: SPIP, CKAN and (for directly-created orgs) Keycloak
 * all land here. Despite the name — kept as-is to avoid a data migration; see
 * docs/developer/SPIP-PLUGIN-DECOUPLING-PLAN.md — this is a core entity, not SPIP's: SPIP
 * is optional, but every organization that ever syncs any tool ends up with a row here.
 * Credentials are automatically generated when an organization is onboarded.
 */
@Entity
@Table(name = "organization_spip_users")
public class OrganizationSpipUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", unique = true, nullable = false)
    private Organization organization;

    /**
     * Null when the organization was onboarded with SPIP disabled/not synced — this
     * row still gets created for its CKAN fields in that case, see {@link #ckanUser}.
     */
    @Column(name = "spip_user", unique = true)
    private String spipUser;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "spip_password")
    private String spipPassword;

    @Column(name = "spip_synchronized", nullable = false)
    private Boolean spipSynchronized = false;

    @Column(name = "ckan_organization_name")
    private String ckanOrganizationName;

    @Column(name = "ckan_synchronized", nullable = false)
    private Boolean ckanSynchronized = false;

    /**
     * The organization's CKAN login. Equals {@link #spipUser}/{@link #spipPassword}
     * when CKAN reused the SPIP account (the common case); holds its own independently
     * generated credentials when CKAN was synced without SPIP. Always read this pair
     * for "what does this org log into CKAN with", never spipUser/spipPassword directly.
     */
    @Column(name = "ckan_user")
    private String ckanUser;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "ckan_password")
    private String ckanPassword;

    // Keycloak first-user credentials for organizations created directly by a platform
    // admin (no onboarding request to carry them). Onboarding-approved organizations
    // keep these on the OrganizationOnboardingRequest instead.
    @Column(name = "keycloak_username")
    private String keycloakUsername;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "keycloak_temp_password")
    private String keycloakTempPassword;

    @Column(name = "keycloak_account_url")
    private String keycloakAccountUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Constructors
    public OrganizationSpipUser() {}

    public OrganizationSpipUser(Organization organization, String spipUser, String spipPassword) {
        this.organization = organization;
        this.spipUser = spipUser;
        this.spipPassword = spipPassword;
        this.spipSynchronized = false;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public String getSpipUser() {
        return spipUser;
    }

    public void setSpipUser(String spipUser) {
        this.spipUser = spipUser;
    }

    public String getSpipPassword() {
        return spipPassword;
    }

    public void setSpipPassword(String spipPassword) {
        this.spipPassword = spipPassword;
    }

    public Boolean getSpipSynchronized() {
        // Defensive getter: ensure never returns null to prevent NullPointerException
        return spipSynchronized != null ? spipSynchronized : false;
    }

    public void setSpipSynchronized(Boolean spipSynchronized) {
        this.spipSynchronized = spipSynchronized;
    }

    public String getCkanOrganizationName() {
        return ckanOrganizationName;
    }

    public void setCkanOrganizationName(String ckanOrganizationName) {
        this.ckanOrganizationName = ckanOrganizationName;
    }

    public Boolean getCkanSynchronized() {
        // Defensive getter: ensure never returns null to prevent NullPointerException
        return ckanSynchronized != null ? ckanSynchronized : false;
    }

    public void setCkanSynchronized(Boolean ckanSynchronized) {
        this.ckanSynchronized = ckanSynchronized;
    }

    public String getCkanUser() {
        return ckanUser;
    }

    public void setCkanUser(String ckanUser) {
        this.ckanUser = ckanUser;
    }

    public String getCkanPassword() {
        return ckanPassword;
    }

    public void setCkanPassword(String ckanPassword) {
        this.ckanPassword = ckanPassword;
    }

    public String getKeycloakUsername() {
        return keycloakUsername;
    }

    public void setKeycloakUsername(String keycloakUsername) {
        this.keycloakUsername = keycloakUsername;
    }

    public String getKeycloakTempPassword() {
        return keycloakTempPassword;
    }

    public void setKeycloakTempPassword(String keycloakTempPassword) {
        this.keycloakTempPassword = keycloakTempPassword;
    }

    public String getKeycloakAccountUrl() {
        return keycloakAccountUrl;
    }

    public void setKeycloakAccountUrl(String keycloakAccountUrl) {
        this.keycloakAccountUrl = keycloakAccountUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
