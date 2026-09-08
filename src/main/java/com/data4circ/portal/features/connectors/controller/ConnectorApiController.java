package com.data4circ.portal.features.connectors.controller;

import com.data4circ.portal.common.exception.EntityNotFoundException;
import com.data4circ.portal.common.exception.ValidationException;
import com.data4circ.portal.features.connectors.dto.ConnectorRequest;
import com.data4circ.portal.features.connectors.dto.ConnectorResponse;
import com.data4circ.portal.features.connectors.entity.Connector;
import com.data4circ.portal.features.connectors.entity.EdcPortAllocation;
import com.data4circ.portal.features.connectors.repository.EdcPortAllocationRepository;
import com.data4circ.portal.features.connectors.service.ConnectorHeartbeatService;
import com.data4circ.portal.features.connectors.service.ConnectorService;
import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.organization.service.OrganizationService;
import com.data4circ.portal.features.organization.entity.OrganizationSpipUser;
import com.data4circ.portal.features.organization.repository.OrganizationSpipUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/connectors")
public class ConnectorApiController {

    private final ConnectorService connectorService;
    private final OrganizationService organizationService;
    private final OrganizationSpipUserRepository organizationSpipUserRepository;
    private final ConnectorHeartbeatService connectorHeartbeatService;
    private final EdcPortAllocationRepository edcPortAllocationRepository;

    @Value("${app.api.key}")
    private String apiKey;

    public ConnectorApiController(ConnectorService connectorService,
                                  OrganizationService organizationService,
                                  OrganizationSpipUserRepository organizationSpipUserRepository,
                                  ConnectorHeartbeatService connectorHeartbeatService,
                                  EdcPortAllocationRepository edcPortAllocationRepository) {
        this.connectorService = connectorService;
        this.organizationService = organizationService;
        this.organizationSpipUserRepository = organizationSpipUserRepository;
        this.connectorHeartbeatService = connectorHeartbeatService;
        this.edcPortAllocationRepository = edcPortAllocationRepository;
    }

    @GetMapping
    public ResponseEntity<?> listConnectors(
            @RequestHeader(value = "X-API-Key", required = false) String requestApiKey,
            @RequestParam(required = false) String orgName,
            @RequestParam(required = false) String spipUser) {

        ResponseEntity<?> authError = validateApiKey(requestApiKey);
        if (authError != null) return authError;

        if ((orgName == null || orgName.isBlank()) == (spipUser == null || spipUser.isBlank())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Exactly one of 'orgName' or 'spipUser' must be provided"));
        }

        Organization organization = resolveOrganization(orgName, spipUser);

        List<ConnectorResponse> connectors = connectorService.findByOrganization(organization)
                .stream()
                .map(ConnectorResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(connectors);
    }

    @GetMapping("/status")
    public ResponseEntity<?> getConnectorStatus(
            @RequestHeader(value = "X-API-Key", required = false) String requestApiKey,
            @RequestParam(required = false) String orgName,
            @RequestParam(required = false) String spipUser) {

        ResponseEntity<?> authError = validateApiKey(requestApiKey);
        if (authError != null) return authError;

        if ((orgName == null || orgName.isBlank()) == (spipUser == null || spipUser.isBlank())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Exactly one of 'orgName' or 'spipUser' must be provided"));
        }

        Organization organization = resolveOrganization(orgName, spipUser);

        EdcPortAllocation allocation = edcPortAllocationRepository.findByOrganization(organization)
                .orElseThrow(() -> new EntityNotFoundException("EDC allocation for organization", organization.getName()));

        String backendContainer = allocation.getBackendContainerName();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("organization", organization.getName());
        body.put("participantId", allocation.getComposeProjectName());
        body.put("dspEndpoint", "http://" + backendContainer + ":11003/api/dsp");
        body.put("managementUrl", "http://" + backendContainer + ":11002/api/management");
        body.put("backendContainer", backendContainer);
        body.put("basePort", allocation.getBasePort());
        body.put("provisioningStatus", allocation.getStatus().name());
        body.put("stable", true);

        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getConnector(
            @RequestHeader(value = "X-API-Key", required = false) String requestApiKey,
            @PathVariable Long id) {

        ResponseEntity<?> authError = validateApiKey(requestApiKey);
        if (authError != null) return authError;

        Connector connector = connectorService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Connector", id));

        return ResponseEntity.ok(ConnectorResponse.fromEntity(connector));
    }

    @PostMapping
    public ResponseEntity<?> createConnector(
            @RequestHeader(value = "X-API-Key", required = false) String requestApiKey,
            @RequestParam(required = false) String orgName,
            @RequestParam(required = false) String spipUser,
            @RequestBody ConnectorRequest request) {

        ResponseEntity<?> authError = validateApiKey(requestApiKey);
        if (authError != null) return authError;

        if ((orgName == null || orgName.isBlank()) == (spipUser == null || spipUser.isBlank())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Exactly one of 'orgName' or 'spipUser' must be provided"));
        }

        Map<String, String> errors = request.validateForCreate();
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }

        Organization organization = resolveOrganization(orgName, spipUser);
        Connector connector = request.toEntity(organization);
        connector.setHeartbeatToken(connectorHeartbeatService.generateHeartbeatToken());
        connectorService.save(connector);

        return ResponseEntity.status(HttpStatus.CREATED).body(ConnectorResponse.fromEntity(connector));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateConnector(
            @RequestHeader(value = "X-API-Key", required = false) String requestApiKey,
            @PathVariable Long id,
            @RequestBody ConnectorRequest request) {

        ResponseEntity<?> authError = validateApiKey(requestApiKey);
        if (authError != null) return authError;

        Connector connector = connectorService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Connector", id));

        Map<String, String> errors = request.validateForUpdate();
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }

        request.applyTo(connector);
        connectorService.update(connector);

        return ResponseEntity.ok(ConnectorResponse.fromEntity(connector));
    }

    private Organization resolveOrganization(String orgName, String spipUser) {
        if (orgName != null && !orgName.isBlank()) {
            return organizationService.findByName(orgName)
                    .orElseThrow(() -> new EntityNotFoundException("Organization", orgName));
        }
        OrganizationSpipUser spipUserEntity = organizationSpipUserRepository.findBySpipUser(spipUser)
                .orElseThrow(() -> new EntityNotFoundException("SPIP user", spipUser));
        return spipUserEntity.getOrganization();
    }

    private ResponseEntity<?> validateApiKey(String requestApiKey) {
        if (requestApiKey == null || !requestApiKey.equals(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or missing API key"));
        }
        return null;
    }
}
