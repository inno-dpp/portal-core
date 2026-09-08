package com.data4circ.portal.features.organization.repository;

import com.data4circ.portal.features.organization.entity.CertificationStatus;
import com.data4circ.portal.features.organization.entity.IndustrySector;
import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.organization.entity.OrganizationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long>, JpaSpecificationExecutor<Organization> {
    
    Optional<Organization> findByName(String name);

    List<Organization> findByType(OrganizationType type);

    List<Organization> findByCertificationStatus(CertificationStatus status);

    List<Organization> findByIndustrySector(IndustrySector sector);
    
    @Query("SELECT o FROM Organization o WHERE o.certificationStatus = 'ACTIVE' ORDER BY o.name")
    List<Organization> findActiveOrganizations();

    @Query("SELECT o FROM Organization o WHERE o.certificationStatus = 'ACTIVE' ORDER BY o.name")
    List<Organization> findApprovedOrganizations(); // For backwards compatibility

    @Query("SELECT o FROM Organization o ORDER BY o.createdAt DESC")
    List<Organization> findRecentOrganizations();

    @Query("SELECT DISTINCT o FROM Organization o LEFT JOIN FETCH o.members WHERE o.id = :id")
    Optional<Organization> findByIdWithMembers(Long id);

    @Query("SELECT DISTINCT o FROM Organization o LEFT JOIN FETCH o.connectors WHERE o.id = :id")
    Optional<Organization> findByIdWithConnectors(Long id);

    @Query("SELECT o FROM Organization o LEFT JOIN FETCH o.spipUser WHERE o.id = :id")
    Optional<Organization> findByIdWithSpipUser(Long id);

    @Query("SELECT DISTINCT o FROM Organization o LEFT JOIN FETCH o.naceCodes WHERE o.id = :id")
    Optional<Organization> findByIdWithNaceCodes(Long id);

    boolean existsByName(String name);

    /**
     * Organization names only (no other columns), used to pre-check SPIP access-policy label
     * collisions before a new onboarding request is created.
     */
    @Query("SELECT o.name FROM Organization o")
    List<String> findAllNames();

    boolean existsByContactEmail(String contactEmail);
}
