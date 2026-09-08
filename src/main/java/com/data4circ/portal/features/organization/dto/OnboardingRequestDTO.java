package com.data4circ.portal.features.organization.dto;

import com.data4circ.portal.features.organization.entity.CompanySize;
import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class OnboardingRequestDTO {

    @NotBlank(message = "Company name is required")
    @Size(max = 255, message = "Company name must not exceed 255 characters")
    private String companyName;

    @NotBlank(message = "Company type is required")
    private String companyType;

    @NotBlank(message = "Industry is required")
    private String industry;

    private String companySize;

    @NotEmpty(message = "At least one NACE code is required")
    private List<String> naceCodes;

    @Size(max = 1000, message = "Address must not exceed 1000 characters")
    private String address;

    private String country;

    @NotBlank(message = "Organization description is required")
    @Size(max = 250, message = "Organization description must not exceed 250 characters")
    private String organizationDescription;

    private String website;

    @NotBlank(message = "Contact name is required")
    @Size(max = 255, message = "Contact name must not exceed 255 characters")
    private String contactName;

    @NotBlank(message = "Contact title is required")
    @Size(max = 255, message = "Contact title must not exceed 255 characters")
    private String contactTitle;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    private String phone;

    private String phonePrefix;

    @NotBlank(message = "Participation goals are required")
    @Size(max = 2000, message = "Participation goals must not exceed 2000 characters")
    private String participationGoals;

    @Size(max = 2000, message = "Data types must not exceed 2000 characters")
    private String dataTypes;

    @Size(max = 2000, message = "Data needs must not exceed 2000 characters")
    private String dataNeeds;

    @Size(max = 2000, message = "Current systems must not exceed 2000 characters")
    private String currentSystems;

    @NotNull(message = "GDPR consent is required")
    private Boolean gdprConsent;

    // Constructors
    public OnboardingRequestDTO() {}

    // Getters and Setters
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

    public String getCompanySize() {
        return companySize;
    }

    public void setCompanySize(String companySize) {
        this.companySize = companySize;
    }

    public List<String> getNaceCodes() {
        return naceCodes;
    }

    public void setNaceCodes(List<String> naceCodes) {
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
        return gdprConsent;
    }

    public void setGdprConsent(Boolean gdprConsent) {
        this.gdprConsent = gdprConsent;
    }

    // Helper method to convert DTO to entity
    public OrganizationOnboardingRequest toEntity() {
        OrganizationOnboardingRequest entity =
            new OrganizationOnboardingRequest();

        entity.setCompanyName(this.companyName);
        entity.setCompanyType(this.companyType);
        entity.setIndustry(this.industry);
        entity.setAddress(this.address);
        entity.setCountry(this.country);
        entity.setOrganizationDescription(this.organizationDescription);
        entity.setWebsite(this.website);
        entity.setContactName(this.contactName);
        entity.setContactTitle(this.contactTitle);
        entity.setEmail(this.email);
        entity.setPhone(this.phone);
        entity.setPhonePrefix(this.phonePrefix);
        entity.setParticipationGoals(this.participationGoals);
        entity.setDataTypes(this.dataTypes);
        entity.setDataNeeds(this.dataNeeds);
        entity.setCurrentSystems(this.currentSystems);
        entity.setGdprConsent(this.gdprConsent);

        // Store NACE codes as comma-separated string
        if (this.naceCodes != null && !this.naceCodes.isEmpty()) {
            entity.setNaceCodes(String.join(",", this.naceCodes));
        }

        // Convert company size string to enum
        if (this.companySize != null && !this.companySize.isEmpty()) {
            try {
                entity.setCompanySize(CompanySize.valueOf(this.companySize.toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Handle invalid company size gracefully
                entity.setCompanySize(null);
            }
        }

        return entity;
    }

    // Static method to create DTO from entity
    public static OnboardingRequestDTO fromEntity(OrganizationOnboardingRequest entity) {
        OnboardingRequestDTO dto = new OnboardingRequestDTO();
        dto.setCompanyName(entity.getCompanyName());
        dto.setCompanyType(entity.getCompanyType());
        dto.setIndustry(entity.getIndustry());
        dto.setCompanySize(entity.getCompanySize() != null ? entity.getCompanySize().name() : null);
        dto.setAddress(entity.getAddress());
        dto.setCountry(entity.getCountry());
        dto.setOrganizationDescription(entity.getOrganizationDescription());
        dto.setWebsite(entity.getWebsite());
        dto.setContactName(entity.getContactName());
        dto.setContactTitle(entity.getContactTitle());
        dto.setEmail(entity.getEmail());
        dto.setPhone(entity.getPhone());
        dto.setPhonePrefix(entity.getPhonePrefix());
        dto.setParticipationGoals(entity.getParticipationGoals());
        dto.setDataTypes(entity.getDataTypes());
        dto.setDataNeeds(entity.getDataNeeds());
        dto.setCurrentSystems(entity.getCurrentSystems());
        dto.setGdprConsent(entity.getGdprConsent());
        if (entity.getNaceCodes() != null && !entity.getNaceCodes().isEmpty()) {
            dto.setNaceCodes(List.of(entity.getNaceCodes().split(",")));
        }
        return dto;
    }
}