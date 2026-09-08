package com.data4circ.portal.features.organization.controller;

import com.data4circ.portal.features.organization.dto.OnboardingRequestDTO;
import com.data4circ.portal.features.organization.entity.OnboardingRequestStatus;
import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;
import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.common.exception.EntityNotFoundException;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolProvisioner;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolRegistry;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolSyncStateService;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolView;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolsProperties;
import com.data4circ.portal.features.organization.service.OnboardingRequestService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class OnboardingController {

    private static final Logger logger = LoggerFactory.getLogger(OnboardingController.class);

    @Autowired
    private OnboardingRequestService onboardingRequestService;

    @Autowired
    private OnboardingToolRegistry toolRegistry;

    @Autowired
    private OnboardingToolSyncStateService toolSyncState;

    @Autowired
    private OnboardingToolsProperties toolsProperties;

    @org.springframework.beans.factory.annotation.Value("${app.email.support:noreply@localhost}")
    private String supportEmail;

    // REST API Endpoint for form submission
    @PostMapping("/api/onboarding/request")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitOnboardingRequest(@Valid @RequestBody OnboardingRequestDTO dto, BindingResult result) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (result.hasErrors()) {
                response.put("success", false);
                response.put("message", "Validation failed");
                response.put("errors", result.getAllErrors());
                return ResponseEntity.badRequest().body(response);
            }

            // Check for existing requests. Return a machine-readable reason and the existing
            // request's status so the form can render contextual guidance + the right next-step
            // CTAs (sign in / reset password / contact support) instead of a bare error.
            if (onboardingRequestService.existsByEmail(dto.getEmail())) {
                response.put("success", false);
                response.put("reason", "EMAIL_EXISTS");
                onboardingRequestService.findByEmail(dto.getEmail())
                        .ifPresent(existing -> response.put("requestStatus", existing.getStatus().name()));
                response.put("message", "An onboarding request with this email already exists");
                return ResponseEntity.badRequest().body(response);
            }

            if (onboardingRequestService.existsByCompanyName(dto.getCompanyName())) {
                response.put("success", false);
                response.put("reason", "COMPANY_EXISTS");
                response.put("message", "An onboarding request with this company name already exists");
                return ResponseEntity.badRequest().body(response);
            }

            // Create the onboarding request
            OrganizationOnboardingRequest request = dto.toEntity();
            OrganizationOnboardingRequest savedRequest = onboardingRequestService.createOnboardingRequest(request);

            response.put("success", true);
            response.put("message", "Onboarding request submitted successfully");
            response.put("requestId", savedRequest.getId());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "An error occurred while processing your request");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Lightweight existence check used by the join form for early (on-blur) email validation,
    // so a returning applicant is guided before filling in the whole form. Never reveals
    // anything beyond onboarding-request state (no account/personal data).
    @GetMapping("/api/onboarding/check-email")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkEmail(@RequestParam String email) {
        Map<String, Object> response = new HashMap<>();
        java.util.Optional<OrganizationOnboardingRequest> existing =
                onboardingRequestService.findByEmail(email == null ? "" : email.trim());
        response.put("exists", existing.isPresent());
        existing.ifPresent(req -> response.put("requestStatus", req.getStatus().name()));
        return ResponseEntity.ok(response);
    }

    // Admin interface endpoints
    @GetMapping("/admin/onboarding")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public String listOnboardingRequests(Model model) {
        List<OrganizationOnboardingRequest> pendingRequests =
            onboardingRequestService.findByStatus(OnboardingRequestStatus.PENDING);
        List<OrganizationOnboardingRequest> allRequests = onboardingRequestService.findAll();

        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("allRequests", allRequests);
        model.addAttribute("pendingCount", pendingRequests.size());
        model.addAttribute("title", "Onboarding Requests");

        return "admin/onboarding/list";
    }

    @GetMapping("/admin/onboarding/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public String viewOnboardingRequest(@PathVariable Long id, Model model) {
        OrganizationOnboardingRequest request = onboardingRequestService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Onboarding request", id));

        model.addAttribute("request", request);
        List<OnboardingToolView> toolViews = buildToolViews(request);
        model.addAttribute("onboardingTools", toolViews);
        model.addAttribute("skippedOptionalTools", toolViews.stream()
                .filter(OnboardingToolView::isSkippedAtApproval)
                .map(OnboardingToolView::getDisplayName)
                .toList());
        model.addAttribute("rejections", onboardingRequestService.getRejectionHistory(id));
        model.addAttribute("title", "Onboarding Request - " + request.getCompanyName());

        return "admin/onboarding/view";
    }

    /**
     * One card per enabled onboarding tool: provisioner-backed tools first (in
     * dependency order, with a synchronize form), then config-only tools as
     * informational cards so the admin knows which connectors approval will create.
     */
    private List<OnboardingToolView> buildToolViews(OrganizationOnboardingRequest request) {
        Map<String, Boolean> syncMap = toolSyncState.syncMapFor(request.getId());
        List<OnboardingToolView> views = new java.util.ArrayList<>();
        for (OnboardingToolProvisioner tool : toolRegistry.enabledTools()) {
            boolean synced = Boolean.TRUE.equals(syncMap.get(tool.getKey()));
            boolean dependenciesMet = tool.getDependencies().stream()
                    .allMatch(dep -> Boolean.TRUE.equals(syncMap.get(dep)));
            String dependencyNames = tool.getDependencies().stream()
                    .map(dep -> toolRegistry.get(dep)
                            .map(OnboardingToolProvisioner::getDisplayName)
                            .orElse(dep))
                    .collect(java.util.stream.Collectors.joining(", "));
            views.add(new OnboardingToolView(
                    tool.getKey(),
                    tool.getDisplayName(),
                    tool.getIconClass(),
                    synced,
                    dependenciesMet,
                    dependencyNames,
                    tool.describeInputs(request),
                    synced ? tool.getStatusNote(request) : null,
                    false,
                    toolRegistry.isRequired(tool.getKey())));
        }
        for (Map.Entry<String, OnboardingToolsProperties.ToolProperties> entry : toolsProperties.getTools().entrySet()) {
            String toolKey = entry.getKey();
            if (toolRegistry.get(toolKey).isPresent()) {
                continue; // provisioner-backed, already rendered above
            }
            OnboardingToolsProperties.ToolProperties toolProperties = entry.getValue();
            OnboardingToolsProperties.ConnectorTemplateProperties template = toolProperties.getConnector();
            if (!toolProperties.isEnabled() || template == null || !template.isEnabled()) {
                continue;
            }
            String displayName = template.getName() != null && !template.getName().isBlank()
                    ? template.getName()
                    : toolKey.toUpperCase();
            views.add(new OnboardingToolView(
                    toolKey, displayName, "fas fa-plug",
                    false, true, "", null, null, true, false));
        }
        return views;
    }

    @PostMapping("/admin/onboarding/{id}/approve")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public String approveOnboardingRequest(@PathVariable Long id,
                                         @RequestParam(required = false) String adminNotes,
                                         @AuthenticationPrincipal User currentUser,
                                         RedirectAttributes redirectAttributes) {
        try {
            OrganizationOnboardingRequest request = onboardingRequestService.approveRequest(
                id, currentUser.getUsername(), adminNotes);

            redirectAttributes.addFlashAttribute("message",
                String.format("Onboarding request for '%s' has been approved and organization created",
                    request.getCompanyName()));

            return "redirect:/admin/onboarding/" + id;

        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error",
                "Cannot approve request: " + e.getMessage());
            return "redirect:/admin/onboarding/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Error approving request: " + e.getMessage());
            return "redirect:/admin/onboarding/" + id;
        }
    }

    @PostMapping("/admin/onboarding/{id}/reject")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public String rejectOnboardingRequest(@PathVariable Long id,
                                        @RequestParam(required = false) String adminNotes,
                                        @AuthenticationPrincipal User currentUser,
                                        RedirectAttributes redirectAttributes) {
        try {
            OrganizationOnboardingRequest request = onboardingRequestService.rejectRequest(
                id, currentUser.getUsername(), adminNotes);

            redirectAttributes.addFlashAttribute("message",
                String.format("Onboarding request for '%s' has been rejected",
                    request.getCompanyName()));

            return "redirect:/admin/onboarding/" + id;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Error rejecting request: " + e.getMessage());
            return "redirect:/admin/onboarding/" + id;
        }
    }

    @PostMapping("/admin/onboarding/{id}/reopen")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public String reopenOnboardingRequest(@PathVariable Long id,
                                          @AuthenticationPrincipal User currentUser,
                                          RedirectAttributes redirectAttributes) {
        try {
            OrganizationOnboardingRequest request = onboardingRequestService.reopenRequest(
                id, currentUser.getUsername());

            redirectAttributes.addFlashAttribute("message",
                String.format("Onboarding request for '%s' has been re-opened and set back to Pending. "
                    + "You can now re-synchronize and approve or reject it.", request.getCompanyName()));

            return "redirect:/admin/onboarding/" + id;

        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", "Cannot re-open request: " + e.getMessage());
            return "redirect:/admin/onboarding/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error re-opening request: " + e.getMessage());
            return "redirect:/admin/onboarding/" + id;
        }
    }

    @PostMapping("/admin/onboarding/{id}/synchronize/{toolKey}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public String synchronizeTool(@PathVariable Long id,
                                  @PathVariable String toolKey,
                                  @RequestParam Map<String, String> params,
                                  RedirectAttributes redirectAttributes) {
        String toolName = toolRegistry.get(toolKey)
                .map(OnboardingToolProvisioner::getDisplayName)
                .orElse(toolKey);
        try {
            // All validation is performed in the service layer
            onboardingRequestService.synchronizeTool(id, toolKey, params);
            redirectAttributes.addFlashAttribute("message",
                toolName + " synchronization complete.");
            return "redirect:/admin/onboarding/" + id;

        } catch (IllegalArgumentException e) {
            // Validation errors from service layer
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/onboarding/" + id;
        } catch (IllegalStateException e) {
            // Business logic errors from service layer
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/onboarding/" + id;
        } catch (Exception e) {
            // Unexpected errors
            logger.error("Unexpected error during {} synchronization for request {}: {}", toolName, id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                "An unexpected error occurred during " + toolName + " synchronization. Please try again.");
            return "redirect:/admin/onboarding/" + id;
        }
    }

    // REST API endpoint for getting request status
    @GetMapping("/api/onboarding/request/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getOnboardingRequestStatus(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();

        try {
            OrganizationOnboardingRequest request = onboardingRequestService.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Onboarding request", id));

            response.put("success", true);
            response.put("status", request.getStatus().name());
            response.put("statusDisplay", request.getStatus().getDisplayName());
            response.put("companyName", request.getCompanyName());
            response.put("createdAt", request.getCreatedAt());
            response.put("processedAt", request.getProcessedAt());

            if (request.getOrganization() != null) {
                response.put("organizationId", request.getOrganization().getId());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Request not found");
            return ResponseEntity.notFound().build();
        }
    }

    // Public landing page for onboarding (optional). No explicit "title" attribute here —
    // the template falls back to 'Join ' + ${branding.brandName} on its own.
    @GetMapping("/join")
    public String joinPage(Model model) {
        model.addAttribute("supportEmail", supportEmail);
        return "public/join";
    }
}