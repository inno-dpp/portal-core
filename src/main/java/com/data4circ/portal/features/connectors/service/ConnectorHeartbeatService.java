package com.data4circ.portal.features.connectors.service;

import com.data4circ.portal.features.connectors.dto.HeartbeatRequest;
import com.data4circ.portal.features.connectors.dto.HeartbeatResponse;
import com.data4circ.portal.features.connectors.entity.Connector;
import com.data4circ.portal.features.connectors.enums.ConnectorStatus;
import com.data4circ.portal.features.connectors.repository.ConnectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for handling connector heartbeat processing and timeout detection.
 */
@Service
public class ConnectorHeartbeatService {

    private static final Logger logger = LoggerFactory.getLogger(ConnectorHeartbeatService.class);

    private final ConnectorRepository connectorRepository;

    @Value("${app.connector.heartbeat.timeout-seconds:180}")
    private int heartbeatTimeoutSeconds;

    public ConnectorHeartbeatService(ConnectorRepository connectorRepository) {
        this.connectorRepository = connectorRepository;
    }

    /**
     * Process an incoming heartbeat from a connector.
     *
     * @param token   The heartbeat token from the Authorization header
     * @param request The heartbeat request payload
     * @return HeartbeatResponse with connector details
     */
    @Transactional
    public Optional<HeartbeatResponse> processHeartbeat(String token, HeartbeatRequest request) {
        if (token == null || token.isBlank()) {
            logger.warn("Heartbeat received with empty token");
            return Optional.empty();
        }

        Optional<Connector> connectorOpt = connectorRepository.findByHeartbeatToken(token);
        if (connectorOpt.isEmpty()) {
            logger.warn("Heartbeat received with invalid token: {}", maskToken(token));
            return Optional.empty();
        }

        Connector connector = connectorOpt.get();
        connector.setLastHeartbeat(LocalDateTime.now());
        connector.setStatus(ConnectorStatus.ONLINE);
        connectorRepository.save(connector);

        logger.debug("Heartbeat processed for connector: {} (ID: {})", connector.getName(), connector.getId());

        return Optional.of(HeartbeatResponse.accepted(connector.getId(), connector.getName()));
    }

    /**
     * Scheduled task to check for heartbeat timeouts and mark stale connectors as OFFLINE.
     * Runs every 30 seconds by default.
     */
    @Scheduled(fixedRateString = "${app.connector.heartbeat.check-interval:30000}", initialDelayString = "${app.connector.heartbeat.initial-delay:60000}")
    @Transactional
    public void checkHeartbeatTimeouts() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusSeconds(heartbeatTimeoutSeconds);

            List<Connector> staleConnectors = connectorRepository
                    .findByHeartbeatTokenIsNotNullAndLastHeartbeatBeforeAndStatusNot(threshold, ConnectorStatus.OFFLINE);

            if (!staleConnectors.isEmpty()) {
                logger.info("Found {} connector(s) with stale heartbeats, marking as OFFLINE", staleConnectors.size());

                for (Connector connector : staleConnectors) {
                    connector.setStatus(ConnectorStatus.OFFLINE);
                    connectorRepository.save(connector);
                    logger.info("Connector '{}' (ID: {}) marked OFFLINE due to heartbeat timeout",
                            connector.getName(), connector.getId());
                }
            }
        } catch (Exception e) {
            logger.warn("Heartbeat check skipped (schema may not be ready yet): {}", e.getMessage());
        }
    }

    /**
     * Generate a new heartbeat token for a connector.
     *
     * @return A new UUID-based token
     */
    public String generateHeartbeatToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Mask token for logging purposes (show first 8 chars only).
     */
    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "****";
        }
        return token.substring(0, 8) + "****";
    }
}
