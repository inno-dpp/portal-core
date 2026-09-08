package com.data4circ.portal.features.organization.service;

import com.data4circ.portal.features.organization.entity.CertificationStatus;
import com.data4circ.portal.features.organization.entity.IndustrySector;
import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.organization.entity.OrganizationType;
import com.data4circ.portal.features.organization.repository.OrganizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

@Service
public class OrganizationService {

    @Autowired
    private OrganizationRepository organizationRepository;

    public Organization save(Organization organization) {
        return organizationRepository.save(organization);
    }

    public Optional<Organization> findById(Long id) {
        return organizationRepository.findById(id);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<Organization> findByIdWithMembers(Long id) {
        return organizationRepository.findByIdWithMembers(id);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<Organization> findByIdWithMembersAndConnectors(Long id) {
        // Fetch organization with members first
        Optional<Organization> orgWithMembers = organizationRepository.findByIdWithMembers(id);
        if (orgWithMembers.isEmpty()) {
            return Optional.empty();
        }

        // Then fetch connectors separately to avoid MultipleBagFetchException
        organizationRepository.findByIdWithConnectors(id);

        // Also fetch NACE codes in the same session
        organizationRepository.findByIdWithNaceCodes(id);

        // Return the organization (connectors and NACE codes are now loaded in the same session)
        return orgWithMembers;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<Organization> findByIdWithNaceCodes(Long id) {
        return organizationRepository.findByIdWithNaceCodes(id);
    }

    public Optional<Organization> findByName(String name) {
        return organizationRepository.findByName(name);
    }

    public List<Organization> findAll() {
        return organizationRepository.findAll();
    }

    public Page<Organization> findPage(int page, int size, Sort sort,
                                       CertificationStatus certificationStatus,
                                       OrganizationType organizationType) {
        return findPage(page, size, sort, certificationStatus, organizationType, null, null, null);
    }

    public Page<Organization> findPage(int page, int size, Sort sort,
                                       CertificationStatus certificationStatus,
                                       OrganizationType organizationType,
                                       IndustrySector industrySector,
                                       String search) {
        return findPage(page, size, sort, certificationStatus, organizationType, industrySector, search, null);
    }

    public Page<Organization> findPage(int page, int size, Sort sort,
                                       CertificationStatus certificationStatus,
                                       OrganizationType organizationType,
                                       IndustrySector industrySector,
                                       String search,
                                       String country) {
        return findPage(page, size, sort, certificationStatus, organizationType, industrySector, search, country, null);
    }

    public Page<Organization> findPage(int page, int size, Sort sort,
                                       CertificationStatus certificationStatus,
                                       OrganizationType organizationType,
                                       IndustrySector industrySector,
                                       String search,
                                       String country,
                                       String naceCode) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(normalizedPage, normalizedSize, sort);

        Specification<Organization> spec = (root, query, cb) -> cb.conjunction();

        if (certificationStatus != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("certificationStatus"), certificationStatus));
        }

        if (organizationType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), organizationType));
        }

        if (industrySector != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("industrySector"), industrySector));
        }

        if (search != null && !search.isBlank()) {
            String searchPattern = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), searchPattern),
                    cb.like(cb.lower(root.get("description")), searchPattern)
            ));
        }

        if (country != null && !country.isBlank()) {
            final String countryFilter = country;
            spec = spec.and((root, query, cb) -> cb.equal(root.get("country"), countryFilter));
        }

        if (naceCode != null && !naceCode.isBlank()) {
            final String naceCodeFilter = naceCode.trim();
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                return cb.equal(root.join("naceCodes").get("code"), naceCodeFilter);
            });
        }

        return organizationRepository.findAll(spec, pageable);
    }

    public List<Organization> findByType(OrganizationType type) {
        return organizationRepository.findByType(type);
    }

    public List<Organization> findByCertificationStatus(CertificationStatus status) {
        return organizationRepository.findByCertificationStatus(status);
    }

    public List<Organization> findApprovedOrganizations() {
        return organizationRepository.findApprovedOrganizations();
    }

    public List<Organization> findByIndustrySector(IndustrySector sector) {
        return organizationRepository.findByIndustrySector(sector);
    }

    public boolean existsByName(String name) {
        return organizationRepository.existsByName(name);
    }

    public List<String> findAllNames() {
        return organizationRepository.findAllNames();
    }

    public boolean existsByContactEmail(String contactEmail) {
        return organizationRepository.existsByContactEmail(contactEmail);
    }

    public void deleteById(Long id) {
        organizationRepository.findById(id).ifPresent(organization -> {
            organization.setCertificationStatus(CertificationStatus.INACTIVE);
            //TODO disable user login for this organization

            organizationRepository.save(organization);
        });
        //do not delete organisaton, just mark it as inactive
        //organizationRepository.deleteById(id);
    }

    public Organization update(Organization organization) {
        return organizationRepository.save(organization);
    }

    public long getTotalCount() {
        return organizationRepository.count();
    }

    public long getActiveCount() {
        return organizationRepository.findByCertificationStatus(CertificationStatus.ACTIVE).size();
    }

    public long getApprovedCount() {
        // For backwards compatibility, return active organizations
        return getActiveCount();
    }

    public List<Organization> findRecentOrganizations(int limit) {
        return organizationRepository.findRecentOrganizations()
                .stream()
                .limit(limit)
                .toList();
    }
}
