package com.data4circ.portal.features.collaboration.controller;

import com.data4circ.portal.features.collaboration.entity.CollaborationCancellation;
import com.data4circ.portal.features.collaboration.entity.CollaborationRejection;
import com.data4circ.portal.features.collaboration.entity.CollaborationRequest;
import com.data4circ.portal.features.collaboration.service.CollaborationService;
import com.data4circ.portal.features.organization.entity.User;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/organizations")
public class CollaborationController {

    private static final Logger logger = LoggerFactory.getLogger(CollaborationController.class);

    @Autowired
    private CollaborationService collaborationService;

    /**
     * Send a collaboration request to the target organization.
     * Returns JSON for inline button-state update.
     */
    @PostMapping("/{targetOrgId}/collaboration-requests")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendCollaborationRequest(
            @PathVariable Long targetOrgId,
            @RequestParam(required = false) String requestMessage,
            @AuthenticationPrincipal User currentUser) {

        Map<String, Object> response = new HashMap<>();
        try {
            CollaborationRequest request = collaborationService.sendRequest(currentUser, targetOrgId, requestMessage);
            response.put("success", true);
            response.put("status", request.getStatus().name());
            response.put("message", "Collaboration request sent successfully.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.warn("Failed to send collaboration request to org {}: {}", targetOrgId, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Approve a pending collaboration request.
     */
    @PostMapping("/{targetOrgId}/collaboration-requests/{requestId}/approve")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public String approveCollaborationRequest(
            @PathVariable Long targetOrgId,
            @PathVariable Long requestId,
            @RequestParam(required = false) String reviewerNotes,
            @AuthenticationPrincipal User currentUser,
            RedirectAttributes redirectAttributes) {

        try {
            collaborationService.approveRequest(requestId, currentUser, reviewerNotes);
            redirectAttributes.addFlashAttribute("message", "Collaboration request approved successfully.");
        } catch (Exception e) {
            logger.warn("Failed to approve collaboration request {}: {}", requestId, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to approve request: " + e.getMessage());
        }
        return "redirect:/organizations/" + targetOrgId + "/edit";
    }

    /**
     * Cancel an approved collaboration. Either side of the relationship may cancel.
     * Returns JSON for inline button-state update.
     */
    @PostMapping("/{otherOrgId}/collaboration-requests/{requestId}/cancel")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelCollaborationRequest(
            @PathVariable Long otherOrgId,
            @PathVariable Long requestId,
            @RequestParam String reason,
            @AuthenticationPrincipal User currentUser) {

        Map<String, Object> response = new HashMap<>();
        try {
            collaborationService.cancelCollaboration(requestId, currentUser, reason);
            response.put("success", true);
            response.put("message", "Collaboration cancelled.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.warn("Failed to cancel collaboration {}: {}", requestId, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Return the list of prior cancellations between the current user's org and the target org,
     * newest first. Consumed by the JS warning popup before a new collaboration is requested.
     */
    @GetMapping("/{otherOrgId}/collaboration-cancellations")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listCancellations(
            @PathVariable Long otherOrgId,
            @AuthenticationPrincipal User currentUser) {

        Map<String, Object> response = new HashMap<>();
        if (currentUser.getOrganization() == null) {
            response.put("success", false);
            response.put("error", "Current user has no organisation.");
            return ResponseEntity.badRequest().body(response);
        }

        List<CollaborationCancellation> cancellations =
                collaborationService.findCancellationsBetween(currentUser.getOrganization().getId(), otherOrgId);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<Map<String, Object>> items = new ArrayList<>();
        for (CollaborationCancellation c : cancellations) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", c.getId());
            item.put("cancelledAt", c.getCancelledAt().format(fmt));
            item.put("initiatingOrgName", c.getInitiatingOrg().getName());
            item.put("initiatingOrgId", c.getInitiatingOrg().getId());
            item.put("reason", c.getReason());
            items.add(item);
        }
        response.put("success", true);
        response.put("cancellations", items);
        return ResponseEntity.ok(response);
    }

    /**
     * Return the list of prior rejections between the current user's org and the target org,
     * newest first. Consumed by the JS warning popup before a new collaboration is requested.
     */
    @GetMapping("/{otherOrgId}/collaboration-rejections")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listRejections(
            @PathVariable Long otherOrgId,
            @AuthenticationPrincipal User currentUser) {

        Map<String, Object> response = new HashMap<>();
        if (currentUser.getOrganization() == null) {
            response.put("success", false);
            response.put("error", "Current user has no organisation.");
            return ResponseEntity.badRequest().body(response);
        }

        List<CollaborationRejection> rejections =
                collaborationService.findRejectionsBetween(currentUser.getOrganization().getId(), otherOrgId);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<Map<String, Object>> items = new ArrayList<>();
        for (CollaborationRejection r : rejections) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", r.getId());
            item.put("rejectedAt", r.getRejectedAt().format(fmt));
            item.put("requesterOrgName", r.getRequesterOrg().getName());
            item.put("targetOrgName", r.getTargetOrg().getName());
            item.put("reason", r.getReason());
            items.add(item);
        }
        response.put("success", true);
        response.put("rejections", items);
        return ResponseEntity.ok(response);
    }

    /**
     * Reject a pending collaboration request.
     */
    @PostMapping("/{targetOrgId}/collaboration-requests/{requestId}/reject")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public String rejectCollaborationRequest(
            @PathVariable Long targetOrgId,
            @PathVariable Long requestId,
            @RequestParam(required = false) String reviewerNotes,
            @AuthenticationPrincipal User currentUser,
            RedirectAttributes redirectAttributes) {

        try {
            collaborationService.rejectRequest(requestId, currentUser, reviewerNotes);
            redirectAttributes.addFlashAttribute("message",
                    "Collaboration request rejected. The requesting organisation has been notified that "
                    + "your organisation declined the request. They may send a new request later.");
        } catch (Exception e) {
            logger.warn("Failed to reject collaboration request {}: {}", requestId, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to reject request: " + e.getMessage());
        }
        return "redirect:/organizations/" + targetOrgId + "/edit";
    }
}
