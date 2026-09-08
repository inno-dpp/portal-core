package com.data4circ.portal.features.connectors.repository;

import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.connectors.entity.Connector;
import com.data4circ.portal.features.connectors.enums.ConnectorStatus;
import com.data4circ.portal.features.connectors.enums.ConnectorType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConnectorRepository extends JpaRepository<Connector, Long>, JpaSpecificationExecutor<Connector> {

    @Query("SELECT c FROM Connector c LEFT JOIN FETCH c.organization WHERE c.id = :id")
    Optional<Connector> findByIdWithOrganization(@Param("id") Long id);

    List<Connector> findByOrganization(Organization organization);

    List<Connector> findByStatus(ConnectorStatus status);

    List<Connector> findByType(ConnectorType type);

    List<Connector> findByOrganizationAndStatus(Organization organization, ConnectorStatus status);

    Optional<Connector> findByOrganizationAndName(Organization organization, String name);

    Optional<Connector> findByOrganizationAndToolKey(Organization organization, String toolKey);

    List<Connector> findByNameAndToolKeyIsNull(String name);

    long countByStatus(ConnectorStatus status);

    long countByOrganization(Organization organization);

    Optional<Connector> findByHeartbeatToken(String heartbeatToken);

    List<Connector> findByHeartbeatTokenIsNotNullAndLastHeartbeatBeforeAndStatusNot(
            LocalDateTime threshold, ConnectorStatus status);
}
