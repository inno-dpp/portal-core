package com.data4circ.portal.features.onboardingsync.tool;

import com.data4circ.portal.features.connectors.entity.Connector;
import com.data4circ.portal.features.connectors.repository.ConnectorRepository;
import com.data4circ.portal.features.onboardingsync.tool.entity.OnboardingToolSync;
import com.data4circ.portal.features.onboardingsync.tool.repository.OnboardingToolSyncRepository;
import com.data4circ.portal.features.organization.entity.OnboardingRequestStatus;
import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;
import com.data4circ.portal.features.organization.repository.OnboardingRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-time, idempotent migration of pre-existing data to the generic tool-sync model:
 * copies the legacy spip_synchronized/ckan_synchronized flags into onboarding_tool_sync
 * rows, tags legacy CKAN connectors with their tool key, and materializes missing tool
 * connectors for organizations approved before this feature existed.
 */
@Component
public class OnboardingToolSyncBackfill implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(OnboardingToolSyncBackfill.class);

    private static final String LEGACY_CKAN_CONNECTOR_NAME = "CKAN Metadata Platform";

    private final OnboardingRequestRepository requestRepository;
    private final OnboardingToolSyncRepository syncRepository;
    private final ConnectorRepository connectorRepository;
    private final OnboardingToolConnectorService toolConnectorService;

    public OnboardingToolSyncBackfill(OnboardingRequestRepository requestRepository,
                                      OnboardingToolSyncRepository syncRepository,
                                      ConnectorRepository connectorRepository,
                                      OnboardingToolConnectorService toolConnectorService) {
        this.requestRepository = requestRepository;
        this.syncRepository = syncRepository;
        this.connectorRepository = connectorRepository;
        this.toolConnectorService = toolConnectorService;
    }

    @Override
    @Transactional
    @SuppressWarnings("deprecation")
    public void run(ApplicationArguments args) {
        List<OrganizationOnboardingRequest> requests = requestRepository.findAll();
        backfillSyncRows(requests);
        tagLegacyCkanConnectors();
        materializeConnectorsForApprovedRequests(requests);
    }

    @SuppressWarnings("deprecation")
    private void backfillSyncRows(List<OrganizationOnboardingRequest> requests) {
        int created = 0;
        for (OrganizationOnboardingRequest request : requests) {
            if (Boolean.TRUE.equals(request.getSpipSynchronized())) {
                created += insertSyncRowIfMissing(request, "spip") ? 1 : 0;
            }
            if (Boolean.TRUE.equals(request.getCkanSynchronized())) {
                created += insertSyncRowIfMissing(request, "ckan") ? 1 : 0;
            }
        }
        if (created > 0) {
            logger.info("Backfilled {} onboarding tool sync rows from legacy flags", created);
        }
    }

    private boolean insertSyncRowIfMissing(OrganizationOnboardingRequest request, String toolKey) {
        if (syncRepository.findByRequestIdAndToolKey(request.getId(), toolKey).isPresent()) {
            return false;
        }
        OnboardingToolSync sync = new OnboardingToolSync();
        sync.setRequest(request);
        sync.setToolKey(toolKey);
        sync.setSynced(true);
        sync.setSyncedAt(request.getUpdatedAt());
        sync.setDetails("Backfilled from legacy " + toolKey + "_synchronized flag");
        syncRepository.save(sync);
        return true;
    }

    private void tagLegacyCkanConnectors() {
        List<Connector> untagged = connectorRepository.findByNameAndToolKeyIsNull(LEGACY_CKAN_CONNECTOR_NAME);
        for (Connector connector : untagged) {
            connector.setToolKey("ckan");
            connectorRepository.save(connector);
        }
        if (!untagged.isEmpty()) {
            logger.info("Tagged {} legacy '{}' connectors with tool key 'ckan'",
                    untagged.size(), LEGACY_CKAN_CONNECTOR_NAME);
        }
    }

    private void materializeConnectorsForApprovedRequests(List<OrganizationOnboardingRequest> requests) {
        for (OrganizationOnboardingRequest request : requests) {
            if (request.getStatus() == OnboardingRequestStatus.ORGANIZATION_CREATED
                    && request.getOrganization() != null) {
                toolConnectorService.materializeConnectors(request.getOrganization(), request);
            }
        }
    }
}
