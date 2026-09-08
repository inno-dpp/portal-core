package com.data4circ.portal.features.organization.controller;

import com.data4circ.portal.features.organization.dto.ForgotPasswordDto;
import com.data4circ.portal.features.organization.dto.ResetPasswordDto;
import com.data4circ.portal.features.organization.entity.PasswordResetToken;
import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.organization.entity.UserRole;
import com.data4circ.portal.features.organization.service.OrganizationService;
import com.data4circ.portal.features.organization.service.PasswordResetService;
import com.data4circ.portal.features.organization.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private PasswordResetService passwordResetService;

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                           @RequestParam(value = "message", required = false) String message,
                           @RequestParam(value = "logout", required = false) String logout,
                           Model model) {

        if (error != null) {
            String errorMessage = message != null ? message : "Invalid username or password!";
            model.addAttribute("error", errorMessage);
        }
        
        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully.");
        }
        
        model.addAttribute("title", "Login");
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("organizations", organizationService.findApprovedOrganizations());
        model.addAttribute("userRoles", UserRole.values());
        model.addAttribute("title", "Register");
        return "auth/register";
    }

    //TODO disable temporary register flow
    //@PostMapping("/register")
//    public String registerUser(@Valid @ModelAttribute User user,
//                              BindingResult result,
//                              Model model,
//                              RedirectAttributes redirectAttributes) {
//
//        // Validation
//        if (userService.existsByUsername(user.getUsername())) {
//            result.rejectValue("username", "error.user", "Username already exists");
//        }
//
//        if (userService.existsByEmail(user.getEmail())) {
//            result.rejectValue("email", "error.user", "Email already exists");
//        }
//
//        if (result.hasErrors()) {
//            model.addAttribute("organizations", organizationService.findApprovedOrganizations());
//            model.addAttribute("userRoles", UserRole.values());
//            model.addAttribute("title", "Register");
//            return "auth/register";
//        }
//
//        // Set default role if not specified
//        if (user.getRole() == null) {
//            user.setRole(UserRole.ORG_MEMBER);
//        }
//
//        userService.save(user);
//        redirectAttributes.addFlashAttribute("message", "Registration successful! Please login.");
//        return "redirect:/login";
//    }

    // Logout itself has no handler here — Spring Security's own `.logout(...)` configuration in
    // SecurityConfig owns POST /logout entirely (matches, invalidates the session, deletes the
    // JSESSIONID cookie, redirects to /login?logout=true) and intercepts the request before it
    // would ever reach a @Controller method. A GET /logout handler previously existed here too,
    // but nothing in the UI ever linked to it (the one real logout control, in
    // fragments/navigation.html, is a POST form) — removed as unreachable/unused dead code,
    // verified empirically rather than assumed (see git history).

    // ==================== Forgot Password ====================

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(Model model) {
        model.addAttribute("forgotPasswordDto", new ForgotPasswordDto());
        model.addAttribute("title", "Forgot Password");
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@Valid @ModelAttribute ForgotPasswordDto dto,
                                       BindingResult result,
                                       Model model,
                                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("title", "Forgot Password");
            return "auth/forgot-password";
        }

        // Always returns success to prevent email enumeration
        passwordResetService.initiatePasswordReset(dto.getEmail());

        redirectAttributes.addFlashAttribute("message",
            "If an account exists with this email, you will receive a password reset link shortly.");
        return "redirect:/forgot-password";
    }

    // ==================== Reset Password ====================

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(required = false) String token,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        if (token == null || token.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Invalid password reset link.");
            return "redirect:/forgot-password";
        }

        Optional<PasswordResetToken> resetToken = passwordResetService.validateToken(token);

        if (resetToken.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                "This password reset link is invalid or has expired. Please request a new one.");
            return "redirect:/forgot-password";
        }

        boolean firstTimeSetup = passwordResetService.isFirstTimeSetup(token);

        ResetPasswordDto dto = new ResetPasswordDto();
        dto.setToken(token);
        model.addAttribute("resetPasswordDto", dto);
        model.addAttribute("firstTimeSetup", firstTimeSetup);
        model.addAttribute("title", firstTimeSetup ? "Set Password" : "Reset Password");
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@Valid @ModelAttribute ResetPasswordDto dto,
                                       BindingResult result,
                                       Model model,
                                       RedirectAttributes redirectAttributes) {
        // First-time activation vs password reset (drives page wording on error re-renders)
        boolean firstTimeSetup = passwordResetService.isFirstTimeSetup(dto.getToken());
        model.addAttribute("firstTimeSetup", firstTimeSetup);
        model.addAttribute("title", firstTimeSetup ? "Set Password" : "Reset Password");

        // Check for validation errors
        if (result.hasErrors()) {
            return "auth/reset-password";
        }

        // Check password confirmation
        if (!dto.isPasswordConfirmed()) {
            result.rejectValue("confirmPassword", "error.confirmPassword", "Passwords do not match");
            return "auth/reset-password";
        }

        // Validate token again before processing
        Optional<PasswordResetToken> resetToken = passwordResetService.validateToken(dto.getToken());
        if (resetToken.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                "This password reset link is invalid or has expired. Please request a new one.");
            return "redirect:/forgot-password";
        }

        // Reset the password
        boolean success = passwordResetService.resetPassword(dto);

        if (success) {
            redirectAttributes.addFlashAttribute("message", firstTimeSetup
                ? "Your password has been set successfully. Please log in to access the portal."
                : "Your password has been reset successfully. Please log in with your new password.");
            return "redirect:/login";
        } else {
            redirectAttributes.addFlashAttribute("error",
                "Failed to reset password. Please try again or request a new reset link.");
            return "redirect:/forgot-password";
        }
    }
}