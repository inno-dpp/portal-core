package com.data4circ.portal.features.organization.repository;

import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.organization.entity.OrganizationSpipUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationSpipUserRepository extends JpaRepository<OrganizationSpipUser, Long> {

    Optional<OrganizationSpipUser> findByOrganization(Organization organization);

    Optional<OrganizationSpipUser> findByOrganizationId(Long organizationId);

    Optional<OrganizationSpipUser> findBySpipUser(String spipUser);

    boolean existsBySpipUser(String spipUser);

    boolean existsByCkanUser(String ckanUser);

    /**
     * Find organization SPIP users by CKAN organization name.
     * Used to check uniqueness of CKAN organization names.
     *
     * @param ckanOrganizationName the CKAN organization name to search for
     * @return list of organization SPIP users with the given CKAN organization name
     */
    List<OrganizationSpipUser> findByCkanOrganizationName(String ckanOrganizationName);
}
