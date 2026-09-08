package com.data4circ.portal.features.onboardingsync.tool;

import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolsProperties.ConnectorTemplateProperties;
import com.data4circ.portal.features.connectors.entity.Connector;
import com.data4circ.portal.features.connectors.enums.ConnectorStatus;
import com.data4circ.portal.features.connectors.enums.ConnectorType;
import com.data4circ.portal.features.connectors.repository.ConnectorRepository;
import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Materializes synchronized onboarding tools as connectors in the organization's
 * "My Tools" section. Each tool with a configured connector template becomes one
 * connector row tagged with the tool key, carrying the tool's credential (encrypted)
 * when the provisioner supplies one.
 */
@Service
public class OnboardingToolConnectorService {

    private static final Logger logger = LoggerFactory.getLogger(OnboardingToolConnectorService.class);

    private final OnboardingToolRegistry registry;
    private final OnboardingToolsProperties properties;
    private final OnboardingToolSyncStateService syncState;
    private final ConnectorRepository connectorRepository;

    public OnboardingToolConnectorService(OnboardingToolRegistry registry,
                                          OnboardingToolsProperties properties,
                                          OnboardingToolSyncStateService syncState,
                                          ConnectorRepository connectorRepository) {
        this.registry = registry;
        this.properties = properties;
        this.syncState = syncState;
        this.connectorRepository = connectorRepository;
    }

    /**
     * Create the missing tool connectors for an organization. Idempotent: tools whose
     * connector already exists (matched by tool key) are skipped, as are tools that
     * were not synchronized or have no connector template configured.
     *
     * <p>Two kinds of tools are handled:
     * <ul>
     *   <li>Provisioner-backed tools (SPIP, CKAN): materialized only when synchronized,
     *       carrying the credential the provisioner supplies.</li>
     *   <li>Config-only tools (e.g. EDC): a {@code app.onboarding.tools.<key>.connector}
     *       template with no registered provisioner. Nothing to synchronize, so the
     *       connector is created unconditionally at approval.</li>
     * </ul>
     */
    public void materializeConnectors(Organization organization, OrganizationOnboardingRequest request) {
        for (OnboardingToolProvisioner tool : registry.enabledTools()) {
            ConnectorTemplateProperties template = properties.forKey(tool.getKey()).getConnector();
            if (template == null || !template.isEnabled()) {
                continue;
            }
            if (!syncState.isSynced(request.getId(), tool.getKey())) {
                logger.debug("Tool '{}' not synchronized for request {}; skipping connector creation",
                        tool.getKey(), request.getId());
                continue;
            }
            createConnectorIfMissing(organization, tool.getKey(), template,
                    tool.getConnectorApiToken(request), tool.getDisplayName(),
                    tool.getConnectorEndpoint(request), tool.getConnectorConfiguration(request));
        }

        materializeConfigOnlyConnectors(organization);
    }

    /**
     * Create the connectors for config-only tools (no provisioner, nothing to
     * synchronize). Also used for organizations created directly by a platform
     * admin, which have no onboarding request.
     */
    public void materializeConfigOnlyConnectors(Organization organization) {
        for (Map.Entry<String, OnboardingToolsProperties.ToolProperties> entry : properties.getTools().entrySet()) {
            String toolKey = entry.getKey();
            if (registry.get(toolKey).isPresent()) {
                continue; // provisioner-backed, requires a synchronized onboarding request
            }
            OnboardingToolsProperties.ToolProperties toolProperties = entry.getValue();
            ConnectorTemplateProperties template = toolProperties.getConnector();
            if (!toolProperties.isEnabled() || template == null || !template.isEnabled()) {
                continue;
            }
            createConnectorIfMissing(organization, toolKey, template, null, toolKey, null, null);
        }
    }

    /**
     * Create one provisioner-backed tool's connector for an organization created
     * outside the onboarding flow (direct admin creation) — there is no onboarding
     * request or sync record to consult, the caller vouches that the tool was
     * provisioned. No-op when the tool has no enabled connector template.
     */
    public void materializeDirectToolConnector(Organization organization, String toolKey,
                                               String apiToken, String endpointOverride,
                                               String configurationOverride) {
        ConnectorTemplateProperties template = properties.forKey(toolKey).getConnector();
        if (template == null || !template.isEnabled()) {
            return;
        }
        String fallbackName = registry.get(toolKey)
                .map(OnboardingToolProvisioner::getDisplayName)
                .orElse(toolKey);
        createConnectorIfMissing(organization, toolKey, template, apiToken, fallbackName,
                endpointOverride, configurationOverride);
    }

    private void createConnectorIfMissing(Organization organization, String toolKey,
                                          ConnectorTemplateProperties template,
                                          String apiToken, String fallbackName,
                                          String endpointOverride, String configurationOverride) {
        if (connectorRepository.findByOrganizationAndToolKey(organization, toolKey).isPresent()) {
            logger.info("Connector for tool '{}' already exists for organization '{}'",
                    toolKey, organization.getName());
            return;
        }
        Connector connector = new Connector();
        connector.setName(template.getName() != null && !template.getName().isBlank()
                ? template.getName()
                : fallbackName);
        connector.setDescription(template.getDescription());
        connector.setType(template.getType() != null ? template.getType() : ConnectorType.PLATFORM_DIGITAL_TOOL);
        connector.setEndpoint(endpointOverride != null ? endpointOverride : template.getEndpoint());
        connector.setOrganization(organization);
        connector.setConfiguration(configurationOverride != null ? configurationOverride : template.getConfiguration());
        connector.setStatus(template.getStatus() != null ? template.getStatus() : ConnectorStatus.OFFLINE);
        connector.setHealthEndpoint(template.getHealthEndpoint());
        connector.setToolKey(toolKey);
        connector.setApiToken(apiToken);
        connectorRepository.save(connector);
        logger.info("Created '{}' connector (tool '{}') for organization '{}'",
                connector.getName(), toolKey, organization.getName());
    }
}
