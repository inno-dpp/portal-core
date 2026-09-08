package com.data4circ.portal.features.connectors.service;

import com.data4circ.portal.features.connectors.entity.Connector;
import com.data4circ.portal.features.connectors.enums.ConnectorStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service that periodically checks the health of connectors that have a configured health endpoint.
 * Updates connector status based on the health check response.
 */
@Service
public class ConnectorHealthCheckService {

    private static final Logger logger = LoggerFactory.getLogger(ConnectorHealthCheckService.class);

    private final ConnectorService connectorService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ConnectorHealthCheckService(ConnectorService connectorService) {
        this.connectorService = connectorService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Scheduled task that runs every 60 seconds to check health endpoints of all connectors.
     */
    @Scheduled(fixedRateString = "${app.connector.health-check.interval:60000}", initialDelayString = "${app.connector.health-check.initial-delay:60000}")
    public void checkConnectorHealth() {
        try {
            List<Connector> connectors = connectorService.findAll();

            for (Connector connector : connectors) {
                if (connector.getHealthEndpoint() != null && !connector.getHealthEndpoint().isBlank()) {
                    checkHealth(connector);
                }
            }
        } catch (Exception e) {
            logger.warn("Health check skipped (schema may not be ready yet): {}", e.getMessage());
        }
    }

    /**
     * Check the health of a single connector and update its status.
     */
    private void checkHealth(Connector connector) {
        String healthEndpoint = connector.getHealthEndpoint();

        try {
            logger.debug("Checking health for connector '{}' at endpoint: {}",
                connector.getName(), healthEndpoint);

            String response = restTemplate.getForObject(healthEndpoint, String.class);

            if (response != null) {
                JsonNode jsonNode = objectMapper.readTree(response);
                String status = jsonNode.has("status") ? jsonNode.get("status").asText() : null;

                if ("UP".equalsIgnoreCase(status)) {
                    updateConnectorStatus(connector, ConnectorStatus.ONLINE);
                    logger.debug("Connector '{}' is ONLINE", connector.getName());
                } else if ("DOWN".equalsIgnoreCase(status)) {
                    updateConnectorStatus(connector, ConnectorStatus.OFFLINE);
                    logger.warn("Connector '{}' is OFFLINE (status: {})", connector.getName(), status);
                } else {
                    // Unknown status, mark as online if we got a response
                    updateConnectorStatus(connector, ConnectorStatus.ONLINE);
                    logger.debug("Connector '{}' responded with unknown status: {}",
                        connector.getName(), status);
                }
            } else {
                updateConnectorStatus(connector, ConnectorStatus.OFFLINE);
                logger.warn("Connector '{}' returned null response", connector.getName());
            }
        } catch (Exception e) {
            updateConnectorStatus(connector, ConnectorStatus.OFFLINE);
            logger.warn("Health check failed for connector '{}': {}",
                connector.getName(), e.getMessage());
        }
    }

    private void updateConnectorStatus(Connector connector, ConnectorStatus status) {
        if (connector.getStatus() != status) {
            logger.info("Connector '{}' status changed from {} to {}",
                connector.getName(), connector.getStatus(), status);
        }
        connector.setStatus(status);
        connector.setLastHeartbeat(LocalDateTime.now());
        connectorService.save(connector);
    }
}
