package com.data4circ.portal.features.organization.service;

import com.data4circ.portal.features.organization.dto.OnboardingApprovalEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.data4circ.portal.common.config.ThemeProperties;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Locale;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private ThemeProperties themeProperties;

    @Value("${app.email.from:noreply@localhost}")
    private String fromEmail;

    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.email.onboarding-reviewers:}")
    private String onboardingReviewersCsv;

    /**
     * Create a template context pre-populated with branding variables so all
     * email templates can render brand name, team signature, and copyright.
     */
    private Context newBrandedContext() {
        // English-only emails: pin the locale so date month names never render in the server's
        // default (e.g. Portuguese) locale.
        Context context = new Context(Locale.UK);
        context.setVariable("branding", themeProperties);
        context.setVariable("year", Year.now().getValue());
        return context;
    }

    private String subject(String suffix) {
        return themeProperties.getBrandName() + " - " + suffix;
    }

    @Value("${app.email.support:noreply@localhost}")
    private String supportEmail;

    /**
     * When true (default, recommended for production) the approval email exposes NO credentials:
     * it carries only a one-time set-password link and a pointer to the portal for SPIP/CKAN
     * connector credentials. When false (dev/test convenience) the legacy inline-credentials
     * layout is kept. Configured via {@code app.email.onboarding.secure-link}.
     */
    @Value("${app.email.onboarding.secure-link:true}")
    private boolean onboardingSecureLink;


    /**
     * Send onboarding request confirmation email
     */
    public void sendOnboardingConfirmation(String toEmail, String companyName, String contactName,
                                           Long requestId, LocalDateTime submittedAt) {
        if (!emailEnabled) {
            logger.info("Email sending is disabled. Skipping confirmation email to {}", toEmail);
            return;
        }

        // Log configuration before sending (for debugging)
        logEmailConfiguration();

        try {
            Context context = newBrandedContext();
            context.setVariable("companyName", companyName);
            context.setVariable("contactName", contactName);
            context.setVariable("requestId", requestId);
            context.setVariable("submittedAt", submittedAt);
            context.setVariable("supportEmail", supportEmail);
            context.setVariable("portalUrl", baseUrl);

            String htmlContent = templateEngine.process("email/onboarding-confirmation", context);

            sendHtmlEmail(
                toEmail,
                subject("Your access request is under review"),
                htmlContent
            );

            logger.info("Onboarding confirmation email sent to {}", toEmail);
        } catch (Exception e) {
            logger.error("Failed to send onboarding confirmation email to {}: {}", toEmail, e.getMessage(), e);
            // Don't throw exception - email failure shouldn't break the onboarding flow
        }
    }

    /**
     * Notify the configured onboarding reviewers that a new onboarding request is ready for review.
     * Recipients come from app.email.onboarding-reviewers (comma-separated). Silent no-op when empty.
     */
    public void sendOnboardingReviewNotification(Long requestId, String companyName,
                                                 String contactName, String contactEmail) {
        if (!emailEnabled) {
            logger.info("Email sending is disabled. Skipping onboarding review notification for request {}", requestId);
            return;
        }

        List<String> recipients = parseRecipients(onboardingReviewersCsv);
        if (recipients.isEmpty()) {
            logger.info("No onboarding reviewers configured (app.email.onboarding-reviewers). Skipping review notification for request {}", requestId);
            return;
        }

        Context context = newBrandedContext();
        context.setVariable("companyName", companyName);
        context.setVariable("contactName", contactName);
        context.setVariable("contactEmail", contactEmail);
        context.setVariable("reviewUrl", baseUrl + "/admin/onboarding/" + requestId);

        String htmlContent;
        try {
            htmlContent = templateEngine.process("email/onboarding-review-notification", context);
        } catch (Exception e) {
            logger.error("Failed to render onboarding review notification template for request {}: {}", requestId, e.getMessage(), e);
            return;
        }

        String subject = subject("New Onboarding Request to Review: " + companyName);

        for (String recipient : recipients) {
            try {
                sendHtmlEmail(recipient, subject, htmlContent);
                logger.info("Onboarding review notification sent to {} for request {}", recipient, requestId);
            } catch (Exception e) {
                logger.error("Failed to send onboarding review notification to {} for request {}: {}", recipient, requestId, e.getMessage(), e);
                // Swallow: a single bad recipient must not break the onboarding flow or block other reviewers.
            }
        }
    }

    private List<String> parseRecipients(String csv) {
        if (csv == null || csv.isBlank()) {
            return java.util.Collections.emptyList();
        }
        List<String> result = new java.util.ArrayList<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Send the onboarding approval email.
     *
     * <p>Behaviour depends on {@code app.email.onboarding.secure-link}:</p>
     * <ul>
     *   <li><b>true</b> (default / production): no credentials are placed in the email. The user
     *       receives a one-time {@code setPasswordUrl} to set their own password and a note that
     *       SPIP/CKAN connector credentials are available in the portal after login.</li>
     *   <li><b>false</b> (dev/test): legacy behaviour &mdash; the temporary password, SPIP
     *       credentials and CKAN API token are rendered inline.</li>
     * </ul>
     *
     * The payload's {@code setPasswordUrl} is the one-time secure link where the user
     * sets their password (secure mode).
     */
    public void sendOnboardingApproval(OnboardingApprovalEmail email) {
        if (!emailEnabled) {
            logger.info("Email sending is disabled. Skipping approval email to {}", email.getToEmail());
            return;
        }

        try {
            Context context = newBrandedContext();
            context.setVariable("secureMode", onboardingSecureLink);
            context.setVariable("companyName", email.getCompanyName());
            context.setVariable("contactName", email.getContactName());
            context.setVariable("username", email.getUsername());
            context.setVariable("loginUrl", baseUrl + "/login");
            context.setVariable("supportEmail", supportEmail);

            if (onboardingSecureLink) {
                // Secure mode: never put credentials in the email body.
                context.setVariable("setPasswordUrl", email.getSetPasswordUrl());
            } else {
                // Legacy mode (dev/test only): keep credentials inline.
                context.setVariable("temporaryPassword", email.getTemporaryPassword());
                context.setVariable("spipUsername", email.getSpipUsername());
                context.setVariable("spipPassword", email.getSpipPassword());
                context.setVariable("ckanUsername", email.getCkanUsername());
                context.setVariable("ckanPassword", email.getCkanPassword());
                context.setVariable("ckanApiToken", email.getCkanApiToken());
                context.setVariable("keycloakUsername", email.getKeycloakUsername());
                context.setVariable("keycloakPassword", email.getKeycloakPassword());
                context.setVariable("keycloakAccountUrl", email.getKeycloakAccountUrl());
            }

            String htmlContent = templateEngine.process("email/onboarding-approved", context);

            sendHtmlEmail(
                email.getToEmail(),
                subject("Onboarding Request Approved"),
                htmlContent
            );

            logger.info("Onboarding approval email sent to {} (secureMode={})",
                    email.getToEmail(), onboardingSecureLink);
        } catch (Exception e) {
            logger.error("Failed to send onboarding approval email to {}: {}",
                    email.getToEmail(), e.getMessage(), e);
            // Don't throw exception - email failure shouldn't break the onboarding flow
        }
    }

    /**
     * Send onboarding rejection email
     */
    public void sendOnboardingRejection(String toEmail, String companyName, String contactName, String reason) {
        if (!emailEnabled) {
            logger.info("Email sending is disabled. Skipping rejection email to {}", toEmail);
            return;
        }

        try {
            Context context = newBrandedContext();
            context.setVariable("companyName", companyName);
            context.setVariable("contactName", contactName);
            context.setVariable("reason", reason != null ? reason : "Please contact support for more information.");

            String htmlContent = templateEngine.process("email/onboarding-rejected", context);

            sendHtmlEmail(
                toEmail,
                subject("Onboarding Request Update"),
                htmlContent
            );

            logger.info("Onboarding rejection email sent to {}", toEmail);
        } catch (Exception e) {
            logger.error("Failed to send onboarding rejection email to {}: {}", toEmail, e.getMessage(), e);
            // Don't throw exception - email failure shouldn't break the onboarding flow
        }
    }

    /**
     * Helper method to send HTML email
     */
    private void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }

    /**
     * Send member invitation email when added to organization
     */
    public void sendMemberInvitation(String toEmail, String organizationName, String memberName,
                                    String username, String temporaryPassword) {
        if (!emailEnabled) {
            logger.info("Email sending is disabled. Skipping member invitation email to {}", toEmail);
            return;
        }

        try {
            Context context = newBrandedContext();
            context.setVariable("organizationName", organizationName);
            context.setVariable("memberName", memberName);
            context.setVariable("username", username);
            context.setVariable("temporaryPassword", temporaryPassword);
            context.setVariable("loginUrl", baseUrl + "/login");

            String htmlContent = templateEngine.process("email/member-invitation", context);

            sendHtmlEmail(
                toEmail,
                subject("Welcome to " + organizationName),
                htmlContent
            );

            logger.info("Member invitation email sent to {}", toEmail);
        } catch (Exception e) {
            logger.error("Failed to send member invitation email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send member invitation email", e);
        }
    }

    /**
     * Send password reset email
     */
    public void sendPasswordReset(String toEmail, String organizationName, String memberName,
                                 String username, String newPassword) {
        if (!emailEnabled) {
            logger.info("Email sending is disabled. Skipping password reset email to {}", toEmail);
            return;
        }

        try {
            Context context = newBrandedContext();
            context.setVariable("organizationName", organizationName);
            context.setVariable("memberName", memberName);
            context.setVariable("username", username);
            context.setVariable("newPassword", newPassword);
            context.setVariable("loginUrl", baseUrl + "/login");

            String htmlContent = templateEngine.process("email/password-reset", context);

            sendHtmlEmail(
                toEmail,
                subject("Password Reset"),
                htmlContent
            );

            logger.info("Password reset email sent to {}", toEmail);
        } catch (Exception e) {
            logger.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    /**
     * Send forgot password link email for self-service password reset.
     */
    public void sendForgotPasswordLink(String toEmail, String memberName, String resetLink) {
        if (!emailEnabled) {
            logger.info("Email sending is disabled. Skipping forgot password email to {}", toEmail);
            return;
        }

        try {
            Context context = newBrandedContext();
            context.setVariable("memberName", memberName);
            context.setVariable("resetLink", resetLink);
            context.setVariable("expiryHours", 1);

            String htmlContent = templateEngine.process("email/forgot-password", context);

            sendHtmlEmail(
                toEmail,
                subject("Password Reset Request"),
                htmlContent
            );

            logger.info("Forgot password email sent to {}", toEmail);
        } catch (Exception e) {
            logger.error("Failed to send forgot password email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send forgot password email", e);
        }
    }

    /**
     * Maximum number of characters of a collaboration request message shown in the
     * notification email. Longer messages are truncated with an ellipsis; the recipient
     * can read the full message in the app via the review link.
     */
    private static final int COLLABORATION_MESSAGE_PREVIEW_LENGTH = 300;

    /**
     * Truncates free-text to at most {@code maxLength} characters, breaking on a word
     * boundary where possible and appending an ellipsis when text was cut.
     */
    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        int cut = trimmed.lastIndexOf(' ', maxLength);
        if (cut <= 0) {
            cut = maxLength;
        }
        return trimmed.substring(0, cut).trim() + "…";
    }

    /**
     * Send collaboration request received email to target organization.
     */
    public void sendCollaborationRequestReceived(
            com.data4circ.portal.features.organization.entity.Organization targetOrg,
            com.data4circ.portal.features.organization.entity.Organization requesterOrg,
            String requestMessage) {
        if (!emailEnabled) {
            logger.info("Email sending is disabled. Skipping collaboration request received email.");
            return;
        }
        if (targetOrg.getContactEmail() == null) {
            logger.warn("Target org {} has no contact email, skipping collaboration request received email.", targetOrg.getName());
            return;
        }

        try {
            Context context = newBrandedContext();
            context.setVariable("targetOrgName", targetOrg.getName());
            context.setVariable("requesterOrgName", requesterOrg.getName());
            // Link directly to the collaboration-requests section of the edit page, not just the page.
            context.setVariable("reviewUrl",
                    baseUrl + "/organizations/" + targetOrg.getId() + "/edit#collaboration-requests-section");
            context.setVariable("receivedDate",
                    java.time.LocalDate.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH)));
            // Show a short, escaped preview of the requester's message so the recipient has
            // enough context to decide whether to review the request; full text stays in-app.
            String messagePreview = (requestMessage != null && !requestMessage.isBlank())
                    ? truncate(requestMessage, COLLABORATION_MESSAGE_PREVIEW_LENGTH)
                    : null;
            context.setVariable("requestMessagePreview", messagePreview);

            String htmlContent = templateEngine.process("email/collaboration-request-received", context);

            sendHtmlEmail(
                targetOrg.getContactEmail(),
                subject("New Collaboration Request from " + requesterOrg.getName()),
                htmlContent
            );

            logger.info("Collaboration request received email sent to org {} ({})", targetOrg.getName(), targetOrg.getContactEmail());
        } catch (Exception e) {
            logger.error("Failed to send collaboration request received email: {}", e.getMessage(), e);
            // Don't throw - email failure shouldn't break the collaboration flow
        }
    }

    /**
     * Send collaboration request approved email to requester organization.
     */
    public void sendCollaborationRequestApproved(
            com.data4circ.portal.features.organization.entity.Organization requesterOrg,
            com.data4circ.portal.features.organization.entity.Organization targetOrg,
            String reviewerNotes) {
        if (!emailEnabled) {
            logger.info("Email sending is disabled. Skipping collaboration request approved email.");
            return;
        }
        if (requesterOrg.getContactEmail() == null) {
            logger.warn("Requester org {} has no contact email, skipping collaboration approved email.", requesterOrg.getName());
            return;
        }

        try {
            Context context = newBrandedContext();
            context.setVariable("requesterOrgName", requesterOrg.getName());
            context.setVariable("targetOrgName", targetOrg.getName());
            context.setVariable("partnerProfileUrl", baseUrl + "/organizations/" + targetOrg.getId());
            context.setVariable("approvedDate",
                    java.time.LocalDate.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH)));
            context.setVariable("reviewerNotes",
                    reviewerNotes != null && !reviewerNotes.isBlank() ? reviewerNotes.trim() : null);

            String htmlContent = templateEngine.process("email/collaboration-request-approved", context);

            sendHtmlEmail(
                requesterOrg.getContactEmail(),
                subject("Collaboration Request Approved by " + targetOrg.getName()),
                htmlContent
            );

            logger.info("Collaboration approved email sent to org {} ({})", requesterOrg.getName(), requesterOrg.getContactEmail());
        } catch (Exception e) {
            logger.error("Failed to send collaboration request approved email: {}", e.getMessage(), e);
            // Don't throw - email failure shouldn't break the collaboration flow
        }
    }

    /**
     * Send collaboration request rejected email to requester organization.
     */
    public void sendCollaborationRequestRejected(
            com.data4circ.portal.features.organization.entity.Organization requesterOrg,
            com.data4circ.portal.features.organization.entity.Organization targetOrg,
            String reason) {
        if (!emailEnabled) {
            logger.info("Email sending is disabled. Skipping collaboration request rejected email.");
            return;
        }
        if (requesterOrg.getContactEmail() == null) {
            logger.warn("Requester org {} has no contact email, skipping collaboration rejected email.", requesterOrg.getName());
            return;
        }

        try {
            Context context = newBrandedContext();
            context.setVariable("requesterOrgName", requesterOrg.getName());
            context.setVariable("targetOrgName", targetOrg.getName());
            context.setVariable("reason", reason != null && !reason.isBlank() ? reason.trim() : null);
            context.setVariable("organizationsUrl", baseUrl + "/organizations");

            String htmlContent = templateEngine.process("email/collaboration-request-rejected", context);

            sendHtmlEmail(
                requesterOrg.getContactEmail(),
                subject("Collaboration Request Update from " + targetOrg.getName()),
                htmlContent
            );

            logger.info("Collaboration rejected email sent to org {} ({})", requesterOrg.getName(), requesterOrg.getContactEmail());
        } catch (Exception e) {
            logger.error("Failed to send collaboration request rejected email: {}", e.getMessage(), e);
            // Don't throw - email failure shouldn't break the collaboration flow
        }
    }

    /**
     * Send collaboration cancelled email to the organisation that did NOT initiate the cancellation.
     *
     * @param recipientOrg    the organisation receiving the email (the partner who did not cancel)
     * @param cancellingOrg   the organisation whose admin initiated the cancellation
     * @param reason          the cancellation reason supplied by the cancelling admin
     */
    public void sendCollaborationCancelled(
            com.data4circ.portal.features.organization.entity.Organization recipientOrg,
            com.data4circ.portal.features.organization.entity.Organization cancellingOrg,
            String reason) {
        if (!emailEnabled) {
            logger.info("Email sending is disabled. Skipping collaboration cancelled email.");
            return;
        }
        if (recipientOrg.getContactEmail() == null) {
            logger.warn("Recipient org {} has no contact email, skipping collaboration cancelled email.", recipientOrg.getName());
            return;
        }

        try {
            Context context = newBrandedContext();
            context.setVariable("recipientOrgName", recipientOrg.getName());
            context.setVariable("cancellingOrgName", cancellingOrg.getName());
            context.setVariable("reason", reason);
            context.setVariable("organizationsUrl", baseUrl + "/organizations");

            String htmlContent = templateEngine.process("email/collaboration-cancelled", context);

            sendHtmlEmail(
                recipientOrg.getContactEmail(),
                subject("Collaboration Cancelled by " + cancellingOrg.getName()),
                htmlContent
            );

            logger.info("Collaboration cancelled email sent to org {} ({})", recipientOrg.getName(), recipientOrg.getContactEmail());
        } catch (Exception e) {
            logger.error("Failed to send collaboration cancelled email: {}", e.getMessage(), e);
        }
    }

    /**
     * Log current email configuration for debugging purposes.
     */
    private void logEmailConfiguration() {
        logger.debug("Email configuration: fromEmail={}, emailEnabled={}, baseUrl={}", fromEmail, emailEnabled, baseUrl);
    }
}
