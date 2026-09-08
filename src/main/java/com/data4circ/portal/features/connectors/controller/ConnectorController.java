package com.data4circ.portal.features.connectors.controller;

import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.organization.service.OrganizationService;
import com.data4circ.portal.common.exception.AccessDeniedException;
import com.data4circ.portal.common.exception.EntityNotFoundException;
import com.data4circ.portal.features.connectors.entity.Connector;
import com.data4circ.portal.features.connectors.enums.ConnectorStatus;
import com.data4circ.portal.features.connectors.enums.ConnectorType;
import com.data4circ.portal.features.connectors.service.ConnectorService;
import com.data4circ.portal.features.connectors.service.ConnectorHeartbeatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Controller
@RequestMapping("/connectors")
@PreAuthorize("isAuthenticated()")
public class ConnectorController {

    @Autowired
    private ConnectorService connectorService;

    @Autowired
    private ConnectorHeartbeatService heartbeatService;

    @Autowired
    private OrganizationService organizationService;

    @GetMapping
    public String listConnectors(@AuthenticationPrincipal User currentUser,
                                 @RequestParam(required = false) Long organizationId,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "6") int size,
                                 @RequestParam(defaultValue = "name") String sort,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(required = false) String type,
                                 @RequestParam(required = false) String name,
                                 Model model) {

        Organization organization = null;
        boolean isPlatformAdmin = currentUser.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_PLATFORM_ADMIN"));

        if (isPlatformAdmin) {
            if (organizationId != null) {
                Optional<Organization> selectedOrg = organizationService.findById(organizationId);
                if (selectedOrg.isPresent()) {
                    organization = selectedOrg.get();
                } else {
                    model.addAttribute("title", "My Connectors");
                    model.addAttribute("error", "Organization not found");
                    model.addAttribute("organizations", organizationService.findAll());
                    model.addAttribute("isPlatformAdmin", true);
                    model.addAttribute("showOrgSelector", true);
                    return "connectors/list";
                }
            } else if (currentUser.getOrganization() != null) {
                organization = currentUser.getOrganization();
            } else {
                model.addAttribute("title", "My Connectors");
                model.addAttribute("organizations", organizationService.findAll());
                model.addAttribute("isPlatformAdmin", true);
                model.addAttribute("showOrgSelector", true);
                return "connectors/list";
            }
        } else {
            organization = currentUser.getOrganization();
            if (organization == null) {
                model.addAttribute("error", "You must belong to an organization to view connectors");
                return "error/403";
            }
        }

        List<String> allowedSorts = List.of("name", "status", "type", "lastHeartbeat");
        String sortProperty = allowedSorts.contains(sort) ? sort : "name";
        Sort sortOrder = Sort.by(Sort.Direction.ASC, sortProperty);

        ConnectorStatus statusFilter = parseStatus(status);
        ConnectorType typeFilter = parseType(type);

        String nameFilter = (name != null && !name.isBlank()) ? name.trim() : null;

        Page<Connector> connectorsPage = connectorService.findPage(page, size, sortOrder,
                organization, statusFilter, typeFilter, nameFilter);

        int totalPages = connectorsPage.getTotalPages();
        List<Integer> pageNumbers = totalPages > 0
                ? IntStream.range(0, totalPages).boxed().toList()
                : List.of();

        model.addAttribute("connectorsPage", connectorsPage);
        model.addAttribute("pageNumbers", pageNumbers);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("sort", sortProperty);
        model.addAttribute("status", statusFilter != null ? statusFilter.name() : null);
        model.addAttribute("type", typeFilter != null ? typeFilter.name() : null);
        model.addAttribute("name", nameFilter);
        model.addAttribute("statusOptions", ConnectorStatus.values());
        model.addAttribute("typeOptions", ConnectorType.values());
        model.addAttribute("organization", organization);
        model.addAttribute("title", "My Connectors");

        if (isPlatformAdmin) {
            model.addAttribute("organizations", organizationService.findAll());
            model.addAttribute("isPlatformAdmin", true);
        }
        return "connectors/list";
    }

    @GetMapping("/{id}")
    public String viewConnector(@PathVariable Long id,
                               @AuthenticationPrincipal User currentUser,
                               Model model) {
        Connector connector = connectorService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Connector", id));

        // Check if user has access to this connector
        if (currentUser.getOrganization() != null &&
            !connector.getOrganization().getId().equals(currentUser.getOrganization().getId()) &&
            !currentUser.getRole().name().equals("PLATFORM_ADMIN")) {
            throw new AccessDeniedException("connector", "view");
        }

        model.addAttribute("connector", connector);
        model.addAttribute("title", connector.getName());
        return "connectors/view";
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public String newConnectorForm(@AuthenticationPrincipal User currentUser,
                                   @RequestParam(required = false) Long organizationId,
                                   Model model) {
        Organization targetOrg = resolveTargetOrganization(currentUser, organizationId);

        model.addAttribute("connector", new Connector());
        model.addAttribute("connectorTypes", ConnectorType.values());
        model.addAttribute("organization", targetOrg);
        model.addAttribute("title", "New Connector");
        return "connectors/form";
    }

    @PostMapping("/new")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public String createConnector(@Valid @ModelAttribute Connector connector,
                                BindingResult result,
                                @AuthenticationPrincipal User currentUser,
                                @RequestParam(required = false) Long organizationId,
                                Model model,
                                RedirectAttributes redirectAttributes) {

        Organization targetOrg = resolveTargetOrganization(currentUser, organizationId);

        validateEdcConnectorConfiguration(connector, result);

        if (result.hasErrors()) {
            model.addAttribute("connectorTypes", ConnectorType.values());
            model.addAttribute("organization", targetOrg);
            model.addAttribute("title", "New Connector");
            return "connectors/form";
        }

        connector.setOrganization(targetOrg);
        connector.setHeartbeatToken(heartbeatService.generateHeartbeatToken());
        connectorService.save(connector);
        redirectAttributes.addFlashAttribute("message", "Connector created successfully");
        return "redirect:/connectors/" + connector.getId();
    }

    /**
     * For EDC Connector types, the configuration field must be valid JSON if non-empty.
     * Empty / null / whitespace-only is allowed (passes validation).
     * Adds a field error on `configuration` if parsing fails.
     */
    private void validateEdcConnectorConfiguration(Connector connector, BindingResult result) {
        if (connector.getType() != ConnectorType.EDC_CONNECTOR) {
            return;
        }
        String config = connector.getConfiguration();
        if (config == null || config.isBlank()) {
            return;
        }
        try {
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(config);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            result.rejectValue("configuration", "configuration.invalidJson",
                    "Configuration must be valid JSON: " + e.getOriginalMessage());
        }
    }

    private Organization resolveTargetOrganization(User currentUser, Long organizationId) {
        boolean isPlatformAdmin = currentUser.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_PLATFORM_ADMIN"));

        if (isPlatformAdmin && organizationId != null) {
            return organizationService.findById(organizationId)
                    .orElseThrow(() -> new EntityNotFoundException("Organization", organizationId));
        }
        if (currentUser.getOrganization() != null) {
            return currentUser.getOrganization();
        }
        throw new AccessDeniedException("connector creation",
                "create - user must belong to an organization or select one");
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORG_ADMIN')")
    public String editConnectorForm(@PathVariable Long id,
                                  @AuthenticationPrincipal User currentUser,
                                  Model model) {
        Connector connector = connectorService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Connector", id));

        checkEditAccess(currentUser, connector, "edit");

        model.addAttribute("connector", connector);
        model.addAttribute("connectorTypes", ConnectorType.values());
        model.addAttribute("organization", connector.getOrganization());
        model.addAttribute("title", "Edit Connector");
        return "connectors/form";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORG_ADMIN')")
    public String updateConnector(@PathVariable Long id,
                                @Valid @ModelAttribute Connector connector,
                                BindingResult result,
                                @AuthenticationPrincipal User currentUser,
                                Model model,
                                RedirectAttributes redirectAttributes) {

        Connector existingConnector = connectorService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Connector", id));

        checkEditAccess(currentUser, existingConnector, "update");

        validateEdcConnectorConfiguration(connector, result);

        if (result.hasErrors()) {
            model.addAttribute("connectorTypes", ConnectorType.values());
            model.addAttribute("organization", existingConnector.getOrganization());
            model.addAttribute("title", "Edit Connector");
            return "connectors/form";
        }

        // Copy only the form-exposed fields onto the persisted entity so unbound
        // fields (heartbeat token, last heartbeat, health endpoint, tool key,
        // organization) survive the edit and cannot be mass-assigned.
        existingConnector.setName(connector.getName());
        existingConnector.setDescription(connector.getDescription());
        existingConnector.setType(connector.getType());
        existingConnector.setEndpoint(connector.getEndpoint());
        existingConnector.setStatus(connector.getStatus());
        existingConnector.setConfiguration(connector.getConfiguration());

        // Preserve existing API token if the submitted value is blank
        if (connector.getApiToken() != null && !connector.getApiToken().isBlank()) {
            existingConnector.setApiToken(connector.getApiToken());
        }

        connectorService.update(existingConnector);
        redirectAttributes.addFlashAttribute("message", "Connector updated successfully");
        return "redirect:/connectors/" + id;
    }

    /**
     * Platform admins can edit any connector; org admins only connectors that belong
     * to their own organization.
     */
    private void checkEditAccess(User currentUser, Connector connector, String action) {
        boolean isPlatformAdmin = currentUser.getRole().name().equals("PLATFORM_ADMIN");
        boolean sameOrganization = currentUser.getOrganization() != null &&
            connector.getOrganization().getId().equals(currentUser.getOrganization().getId());
        if (!isPlatformAdmin && !sameOrganization) {
            throw new AccessDeniedException("connector", action);
        }
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public String deleteConnector(@PathVariable Long id,
                                @AuthenticationPrincipal User currentUser,
                                RedirectAttributes redirectAttributes) {
        Connector connector = connectorService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Connector", id));

        // Check if user has access to this connector
        if (currentUser.getOrganization() != null &&
            !connector.getOrganization().getId().equals(currentUser.getOrganization().getId()) &&
            !currentUser.getRole().name().equals("PLATFORM_ADMIN")) {
            throw new AccessDeniedException("connector", "delete");
        }

        connectorService.deleteById(id);
        redirectAttributes.addFlashAttribute("message", "Connector deleted successfully");
        return "redirect:/connectors";
    }

    @PostMapping("/{id}/regenerate-token")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public String regenerateHeartbeatToken(@PathVariable Long id,
                                           @AuthenticationPrincipal User currentUser,
                                           RedirectAttributes redirectAttributes) {
        Connector connector = connectorService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Connector", id));

        // Check if user has access to this connector
        if (currentUser.getOrganization() != null &&
            !connector.getOrganization().getId().equals(currentUser.getOrganization().getId()) &&
            !currentUser.getRole().name().equals("PLATFORM_ADMIN")) {
            throw new AccessDeniedException("connector", "regenerate token");
        }

        connector.setHeartbeatToken(heartbeatService.generateHeartbeatToken());
        connectorService.save(connector);
        redirectAttributes.addFlashAttribute("message", "Heartbeat token regenerated successfully");
        return "redirect:/connectors/" + id;
    }

    @PostMapping("/{id}/heartbeat")
    @ResponseBody
    public String updateHeartbeat(@PathVariable Long id) {
        connectorService.updateHeartbeat(id);
        return "OK";
    }

    private ConnectorStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return Arrays.stream(ConnectorStatus.values())
                .filter(s -> s.name().equalsIgnoreCase(status))
                .findFirst()
                .orElse(null);
    }

    private ConnectorType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return Arrays.stream(ConnectorType.values())
                .filter(t -> t.name().equalsIgnoreCase(type))
                .findFirst()
                .orElse(null);
    }
}
