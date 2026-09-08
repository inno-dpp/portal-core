package com.data4circ.portal.features.organization.entity;

import com.data4circ.portal.features.connectors.entity.Connector;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(unique = true)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @NotNull
    private OrganizationType type;

    @Enumerated(EnumType.STRING)
    @NotNull
    private IndustrySector industrySector;

    private String website;
    private String contactEmail;
    private String contactPhone;
    private String phonePrefix;
    private String address;
    private String logoUrl;

    private String primaryContactName;
    private String primaryContactTitle;

    @Enumerated(EnumType.STRING)
    private CompanySize companySize;

    private String country;

    @Enumerated(EnumType.STRING)
    private CertificationStatus certificationStatus = CertificationStatus.ACTIVE;

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<User> members = new ArrayList<>();

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Connector> connectors = new ArrayList<>();

    @OneToOne(mappedBy = "organization", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private OrganizationSpipUser spipUser;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "organization_nace_codes",
            joinColumns = @JoinColumn(name = "organization_id"),
            inverseJoinColumns = @JoinColumn(name = "nace_code")
    )
    private Set<NaceCode> naceCodes = new HashSet<>();

    @org.hibernate.annotations.Formula("(SELECT COUNT(*) FROM users u WHERE u.organization_id = id)")
    private int memberCount;

    @org.hibernate.annotations.Formula("(SELECT COUNT(*) FROM connectors c WHERE c.organization_id = id)")
    private int connectorCount;

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

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OrganizationType getType() {
        return type;
    }

    public void setType(OrganizationType type) {
        this.type = type;
    }

    public IndustrySector getIndustrySector() {
        return industrySector;
    }

    public void setIndustrySector(IndustrySector industrySector) {
        this.industrySector = industrySector;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getPhonePrefix() {
        return phonePrefix;
    }

    public void setPhonePrefix(String phonePrefix) {
        this.phonePrefix = phonePrefix;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public CertificationStatus getCertificationStatus() {
        return certificationStatus;
    }

    public void setCertificationStatus(CertificationStatus certificationStatus) {
        this.certificationStatus = certificationStatus;
    }

    public List<User> getMembers() {
        return members;
    }

    public void setMembers(List<User> members) {
        this.members = members;
    }

    public List<Connector> getConnectors() {
        return connectors;
    }

    public void setConnectors(List<Connector> connectors) {
        this.connectors = connectors;
    }

    public OrganizationSpipUser getSpipUser() {
        return spipUser;
    }

    public void setSpipUser(OrganizationSpipUser spipUser) {
        this.spipUser = spipUser;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // Helper methods - counts are calculated via @Formula annotations
    public int getMemberCount() {
        return memberCount;
    }

    public int getConnectorCount() {
        return connectorCount;
    }

    public String getPrimaryContactName() {
        return primaryContactName;
    }

    public void setPrimaryContactName(String primaryContactName) {
        this.primaryContactName = primaryContactName;
    }

    public String getPrimaryContactTitle() {
        return primaryContactTitle;
    }

    public void setPrimaryContactTitle(String primaryContactTitle) {
        this.primaryContactTitle = primaryContactTitle;
    }

    public CompanySize getCompanySize() {
        return companySize;
    }

    public void setCompanySize(CompanySize companySize) {
        this.companySize = companySize;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Set<NaceCode> getNaceCodes() {
        return naceCodes;
    }

    public void setNaceCodes(Set<NaceCode> naceCodes) {
        this.naceCodes = naceCodes;
    }

    public boolean hasDefaultNaceCodeOnly() {
        return naceCodes != null && naceCodes.size() == 1
                && naceCodes.iterator().next().getCode().equals("96.99");
    }
}
