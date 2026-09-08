package com.data4circ.portal.features.organization.controller;

import com.data4circ.portal.features.onboardingsync.keycloak.KeycloakPasswordMirrorService;
import com.data4circ.portal.features.organization.dto.PasswordChangeDto;
import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.organization.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfileController {

    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private KeycloakPasswordMirrorService keycloakPasswordMirrorService;

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public String viewProfile(@AuthenticationPrincipal User currentUser, Model model) {
        // Fetch the user with organization to avoid lazy loading issues
        User userWithOrganization = userService.findByIdWithOrganization(currentUser.getId())
                .orElse(currentUser);

        model.addAttribute("user", userWithOrganization);
        model.addAttribute("title", "My Profile");
        return "profile/view";
    }

    @GetMapping("/profile/change-password")
    @PreAuthorize("isAuthenticated()")
    public String showChangePasswordForm(@AuthenticationPrincipal User currentUser, Model model) {
        model.addAttribute("passwordChangeDto", new PasswordChangeDto());
        model.addAttribute("title", "Change Password");
        return "profile/change-password";
    }

    @PostMapping("/profile/change-password")
    @PreAuthorize("isAuthenticated()")
    public String changePassword(
            @AuthenticationPrincipal User currentUser,
            @Valid @ModelAttribute("passwordChangeDto") PasswordChangeDto passwordChangeDto,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        // Check for validation errors
        if (bindingResult.hasErrors()) {
            model.addAttribute("title", "Change Password");
            return "profile/change-password";
        }

        // Check if new password and confirm password match
        if (!passwordChangeDto.isPasswordConfirmed()) {
            bindingResult.rejectValue("confirmPassword", "error.confirmPassword",
                    "New password and confirmation do not match");
            model.addAttribute("title", "Change Password");
            return "profile/change-password";
        }

        // Fetch fresh user data from database
        User user = userService.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        boolean firstActivation = user.isMustChangePassword();

        // Attempt to change password (verifies current password)
        boolean success = userService.changePassword(
                user,
                passwordChangeDto.getCurrentPassword(),
                passwordChangeDto.getNewPassword()
        );

        if (!success) {
            bindingResult.rejectValue("currentPassword", "error.currentPassword",
                    "Current password is incorrect");
            model.addAttribute("title", "Change Password");
            return "profile/change-password";
        }

        // changePassword cleared the flag in the DB, but the banner reads it from the session
        // principal (captured at login). Clear it there too so the "temporary password" banner
        // disappears immediately, without requiring the user to log out and back in.
        currentUser.setMustChangePassword(false);

        // On first-login activation (legacy temp-password mode), optionally mirror the
        // password into the user's onboarding-provisioned Keycloak account (best-effort).
        if (firstActivation) {
            keycloakPasswordMirrorService.mirrorPasswordIfLinked(user, passwordChangeDto.getNewPassword());
        }

        logger.info("Password changed successfully for user: {}", currentUser.getUsername());
        redirectAttributes.addFlashAttribute("successMessage", "Your password has been changed successfully.");
        return "redirect:/profile";
    }
}