package com.data4circ.portal.features.connectors.repository;

import com.data4circ.portal.features.connectors.entity.EdcPortAllocation;
import com.data4circ.portal.features.connectors.entity.EdcPortAllocation.EdcRole;
import com.data4circ.portal.features.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EdcPortAllocationRepository extends JpaRepository<EdcPortAllocation, Long> {

    List<EdcPortAllocation> findAllByOrganization(Organization organization);

    Optional<EdcPortAllocation> findByOrganization(Organization organization);

    Optional<EdcPortAllocation> findByOrganizationAndEdcRole(Organization organization, EdcRole edcRole);

    boolean existsByOrganization(Organization organization);

    @Query("SELECT COALESCE(MAX(e.slotIndex), -1) FROM EdcPortAllocation e")
    int findMaxSlotIndex();
}
