package com.data4circ.portal.features.connectors.controller;

import com.data4circ.portal.features.connectors.dto.HeartbeatRequest;
import com.data4circ.portal.features.connectors.dto.HeartbeatResponse;
import com.data4circ.portal.features.connectors.service.ConnectorHeartbeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * REST API controller for receiving heartbeats from connectors.
 */
@RestController
@RequestMapping("/api/connectors")
public class ConnectorHeartbeatApiController {

    private static final Logger logger = LoggerFactory.getLogger(ConnectorHeartbeatApiController.class);

    private final ConnectorHeartbeatService heartbeatService;

    public ConnectorHeartbeatApiController(ConnectorHeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    /**
     * Receive heartbeat from a connector.
     *
     * @param authHeader The Authorization header containing Bearer token
     * @param request    The heartbeat request payload
     * @return HeartbeatResponse on success, error on failure
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<?> receiveHeartbeat(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) HeartbeatRequest request) {

        // Extract token from Bearer header
        String token = extractBearerToken(authHeader);
        if (token == null) {
            logger.warn("Heartbeat request missing or invalid Authorization header");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid Authorization header"));
        }

        // Process heartbeat
        Optional<HeartbeatResponse> response = heartbeatService.processHeartbeat(token, request != null ? request : new HeartbeatRequest());

        if (response.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Connector not found for the provided token"));
        }

        return ResponseEntity.ok(response.get());
    }

    /**
     * Extract Bearer token from Authorization header.
     */
    private String extractBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7).trim();
    }
}
