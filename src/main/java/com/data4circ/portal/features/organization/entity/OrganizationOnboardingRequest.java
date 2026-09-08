package com.data4circ.portal.features.organization.entity;

import com.data4circ.portal.common.converter.EncryptedStringConverter;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "organization_onboarding_requests")
public class OrganizationOnboardingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Organization Information
    @NotBlank
    @Column(name = "company_name")
    private String companyName;

    @NotBlank
    @Column(name = "company_type")
    private String companyType;

    @NotBlank
    @Column(name = "industry")
    private String industry;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_size")
    private CompanySize companySize;

    @Column(name = "nace_codes", length = 2000)
    private String naceCodes;

    @Column(name = "address", length = 1000)
    private String address;

    @Column(name = "country")
    private String country;

    @NotBlank
    @Column(name = "organization_description", length = 250)
    private String organizationDescription;

    @Column(name = "website")
    private String website;

    // Contact Information
    @NotBlank
    @Column(name = "contact_name")
    private String contactName;

    @NotBlank
    @Column(name = "contact_title")
    private String contactTitle;

    @NotBlank
    @Email
    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "phone_prefix")
    private String phonePrefix;

    // Dataspace Participation Questions
    @NotBlank
    @Column(name = "participation_goals", length = 2000)
    private String participationGoals;

    @Column(name = "data_types", length = 2000)
    private String dataTypes;

    @Column(name = "data_needs", length = 2000)
    private String dataNeeds;

    @Column(name = "current_systems", length = 2000)
    private String currentSystems;

    // Consent and Status
    @NotNull
    @Column(name = "gdpr_consent", nullable = false)
    private Boolean gdprConsent = false;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "status")
    private OnboardingRequestStatus status = OnboardingRequestStatus.PENDING;

    // Linked Organization (when approved)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    // Admin notes
    @Column(name = "admin_notes", length = 2000)
    private String adminNotes;

    @Column(name = "processed_by")
    private String processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // SPIP Integration Fields
    @Column(name = "spip_user")
    private String spipUser;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "spip_password")
    private String spipPassword;

    /**
     * @deprecated superseded by the generic onboarding_tool_sync table (tool key "spip").
     * Still written for one deprecation release because the production column is NOT NULL
     * under ddl-auto:update; never read. Drop the column in a follow-up release.
     */
    @Deprecated
    @Column(name = "spip_synchronized", nullable = false)
    private Boolean spipSynchronized = false;

    @Column(name = "initialization_setup", columnDefinition = "TEXT")
    private String initializationSetup;

    // CKAN Integration Fields
    @Column(name = "ckan_organization_short_name")
    private String ckanOrganizationShortName;

    /**
     * CKAN username, only populated when CKAN provisions its own account (SPIP not
     * synced for this request). When SPIP is synced, CKAN reuses {@link #spipUser}
     * instead and this stays null.
     */
    @Column(name = "ckan_user")
    private String ckanUser;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "ckan_password")
    private String ckanPassword;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "ckan_api_token", length = 1024)
    private String ckanApiToken;

    /**
     * @deprecated superseded by the generic onboarding_tool_sync table (tool key "ckan").
     * Still written for one deprecation release because the production column is NOT NULL
     * under ddl-auto:update; never read. Drop the column in a follow-up release.
     */
    @Deprecated
    @Column(name = "ckan_synchronized", nullable = false)
    private Boolean ckanSynchronized = false;

    // Keycloak Integration Fields (instance targeted during sync + provisioned identities;
    // the admin client secret used for provisioning is deliberately never persisted here)
    @Column(name = "keycloak_base_url")
    private String keycloakBaseUrl;

    @Column(name = "keycloak_realm")
    private String keycloakRealm;

    @Column(name = "keycloak_group_name")
    private String keycloakGroupName;

    @Column(name = "keycloak_username")
    private String keycloakUsername;

    @Column(name = "keycloak_user_id")
    private String keycloakUserId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "keycloak_temp_password")
    private String keycloakTempPassword;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Full history of rejections (a request can be rejected, re-opened, and rejected again).
    @OneToMany(mappedBy = "onboardingRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rejectedAt DESC")
    private List<OnboardingRejection> rejections = new ArrayList<>();

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
    public OrganizationOnboardingRequest() {}

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getCompanyType() {
        return companyType;
    }

    public void setCompanyType(String companyType) {
        this.companyType = companyType;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public CompanySize getCompanySize() {
        return companySize;
    }

    public void setCompanySize(CompanySize companySize) {
        this.companySize = companySize;
    }

    public String getNaceCodes() {
        return naceCodes;
    }

    public void setNaceCodes(String naceCodes) {
        this.naceCodes = naceCodes;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getOrganizationDescription() {
        return organizationDescription;
    }

    public void setOrganizationDescription(String organizationDescription) {
        this.organizationDescription = organizationDescription;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactTitle() {
        return contactTitle;
    }

    public void setContactTitle(String contactTitle) {
        this.contactTitle = contactTitle;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPhonePrefix() {
        return phonePrefix;
    }

    public void setPhonePrefix(String phonePrefix) {
        this.phonePrefix = phonePrefix;
    }

    public String getParticipationGoals() {
        return participationGoals;
    }

    public void setParticipationGoals(String participationGoals) {
        this.participationGoals = participationGoals;
    }

    public String getDataTypes() {
        return dataTypes;
    }

    public void setDataTypes(String dataTypes) {
        this.dataTypes = dataTypes;
    }

    public String getDataNeeds() {
        return dataNeeds;
    }

    public void setDataNeeds(String dataNeeds) {
        this.dataNeeds = dataNeeds;
    }

    public String getCurrentSystems() {
        return currentSystems;
    }

    public void setCurrentSystems(String currentSystems) {
        this.currentSystems = currentSystems;
    }

    public Boolean getGdprConsent() {
        // Defensive getter: ensure never returns null to prevent NullPointerException
        return gdprConsent != null ? gdprConsent : false;
    }

    public void setGdprConsent(Boolean gdprConsent) {
        this.gdprConsent = gdprConsent;
    }

    public OnboardingRequestStatus getStatus() {
        return status;
    }

    public void setStatus(OnboardingRequestStatus status) {
        this.status = status;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(String processedBy) {
        this.processedBy = processedBy;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
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

    /** @deprecated read the onboarding_tool_sync table instead (tool key "spip"). */
    @Deprecated
    public Boolean getSpipSynchronized() {
        // Defensive getter: ensure never returns null to prevent NullPointerException
        return spipSynchronized != null ? spipSynchronized : false;
    }

    /** @deprecated only kept to mirror the legacy column during the deprecation window. */
    @Deprecated
    public void setSpipSynchronized(Boolean spipSynchronized) {
        this.spipSynchronized = spipSynchronized;
    }

    public String getInitializationSetup() {
        return initializationSetup;
    }

    public void setInitializationSetup(String initializationSetup) {
        this.initializationSetup = initializationSetup;
    }

    public String getCkanOrganizationShortName() {
        return ckanOrganizationShortName;
    }

    public void setCkanOrganizationShortName(String ckanOrganizationShortName) {
        this.ckanOrganizationShortName = ckanOrganizationShortName;
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

    /** @deprecated read the onboarding_tool_sync table instead (tool key "ckan"). */
    @Deprecated
    public Boolean getCkanSynchronized() {
        // Defensive getter: ensure never returns null to prevent NullPointerException
        return ckanSynchronized != null ? ckanSynchronized : false;
    }

    /** @deprecated only kept to mirror the legacy column during the deprecation window. */
    @Deprecated
    public void setCkanSynchronized(Boolean ckanSynchronized) {
        this.ckanSynchronized = ckanSynchronized;
    }

    public String getCkanApiToken() {
        return ckanApiToken;
    }

    public void setCkanApiToken(String ckanApiToken) {
        this.ckanApiToken = ckanApiToken;
    }

    public String getKeycloakBaseUrl() {
        return keycloakBaseUrl;
    }

    public void setKeycloakBaseUrl(String keycloakBaseUrl) {
        this.keycloakBaseUrl = keycloakBaseUrl;
    }

    public String getKeycloakRealm() {
        return keycloakRealm;
    }

    public void setKeycloakRealm(String keycloakRealm) {
        this.keycloakRealm = keycloakRealm;
    }

    public String getKeycloakGroupName() {
        return keycloakGroupName;
    }

    public void setKeycloakGroupName(String keycloakGroupName) {
        this.keycloakGroupName = keycloakGroupName;
    }

    public String getKeycloakUsername() {
        return keycloakUsername;
    }

    public void setKeycloakUsername(String keycloakUsername) {
        this.keycloakUsername = keycloakUsername;
    }

    public String getKeycloakUserId() {
        return keycloakUserId;
    }

    public void setKeycloakUserId(String keycloakUserId) {
        this.keycloakUserId = keycloakUserId;
    }

    public String getKeycloakTempPassword() {
        return keycloakTempPassword;
    }

    public void setKeycloakTempPassword(String keycloakTempPassword) {
        this.keycloakTempPassword = keycloakTempPassword;
    }

    public List<OnboardingRejection> getRejections() {
        return rejections;
    }

    /** Append a rejection to this request's history, keeping both sides of the relation in sync. */
    public void addRejection(OnboardingRejection rejection) {
        rejection.setOnboardingRequest(this);
        this.rejections.add(rejection);
    }

    // Helper method to map company type from form to OrganizationType
    public OrganizationType getOrganizationTypeMapping() {
        return switch (this.companyType.toLowerCase()) {
            case "manufacturer" -> OrganizationType.MANUFACTURER;
            case "recycler" -> OrganizationType.RECYCLER;
            case "remanufacturer" -> OrganizationType.REMANUFACTURER;
            case "technology_provider" -> OrganizationType.TECHNOLOGY_PROVIDER;
            case "research_institution" -> OrganizationType.RESEARCH_INSTITUTION;
            case "association" -> OrganizationType.ASSOCIATION;
            case "other" -> OrganizationType.OTHER;
            default -> OrganizationType.OTHER;
        };
    }
}