package com.data4circ.portal.features.connectors.service;

import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.connectors.entity.Connector;
import com.data4circ.portal.features.connectors.enums.ConnectorStatus;
import com.data4circ.portal.features.connectors.enums.ConnectorType;
import com.data4circ.portal.features.connectors.repository.ConnectorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ConnectorService {

    @Autowired
    private ConnectorRepository connectorRepository;

    public Connector save(Connector connector) {
        return connectorRepository.save(connector);
    }

    public Optional<Connector> findById(Long id) {
        return connectorRepository.findByIdWithOrganization(id);
    }

    public List<Connector> findAll() {
        return connectorRepository.findAll();
    }

    public List<Connector> findByOrganization(Organization organization) {
        return connectorRepository.findByOrganization(organization);
    }

    public List<Connector> findByStatus(ConnectorStatus status) {
        return connectorRepository.findByStatus(status);
    }

    public List<Connector> findByType(ConnectorType type) {
        return connectorRepository.findByType(type);
    }

    public Page<Connector> findPage(int page, int size, Sort sort,
                                   Organization organizationFilter,
                                   ConnectorStatus statusFilter,
                                   ConnectorType typeFilter) {
        return findPage(page, size, sort, organizationFilter, statusFilter, typeFilter, null);
    }

    public Page<Connector> findPage(int page, int size, Sort sort,
                                   Organization organizationFilter,
                                   ConnectorStatus statusFilter,
                                   ConnectorType typeFilter,
                                   String nameFilter) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(normalizedPage, normalizedSize, sort);

        Specification<Connector> spec = Specification.where(null);

        if (organizationFilter != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("organization"), organizationFilter));
        }
        if (statusFilter != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), statusFilter));
        }
        if (typeFilter != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), typeFilter));
        }
        if (nameFilter != null && !nameFilter.isBlank()) {
            // Match against both the name and the description columns (case-insensitive LIKE %q%).
            String like = "%" + nameFilter.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("description")), like)
            ));
        }

        return connectorRepository.findAll(spec, pageable);
    }

    public void deleteById(Long id) {
        connectorRepository.deleteById(id);
    }

    public Connector update(Connector connector) {
        return connectorRepository.save(connector);
    }

    public void updateHeartbeat(Long connectorId) {
        Optional<Connector> connector = findById(connectorId);
        if (connector.isPresent()) {
            Connector c = connector.get();
            c.setLastHeartbeat(LocalDateTime.now());
            c.setStatus(ConnectorStatus.ONLINE);
            save(c);
        }
    }

    public long getTotalCount() {
        return connectorRepository.count();
    }

    public long getOnlineCount() {
        return connectorRepository.countByStatus(ConnectorStatus.ONLINE);
    }

    public long getOfflineCount() {
        return connectorRepository.countByStatus(ConnectorStatus.OFFLINE);
    }

    public long getCountByOrganization(Organization organization) {
        return connectorRepository.countByOrganization(organization);
    }
}
