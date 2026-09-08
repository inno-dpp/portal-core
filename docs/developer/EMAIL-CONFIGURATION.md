# Email Configuration Guide

This document explains the email workflow implementation for the DATA4CIRC Portal onboarding process.

## Overview

The portal sends automated emails at three key points in the onboarding workflow:
1. **Onboarding Request Received** - Confirmation email sent to applicant
2. **Onboarding Request Approved** - Approval email with login credentials
3. **Onboarding Request Rejected** - Rejection notification with reason

## Architecture

### Components

#### EmailService (`com.data4circ.portal.service.EmailService`)
Core service responsible for sending emails using Spring Boot's JavaMailSender and Thymeleaf for HTML templates.

**Key Methods:**
- `sendOnboardingConfirmation()` - Sends confirmation email when request is submitted
- `sendOnboardingApproval()` - Sends the approval email. By default (`app.email.onboarding.secure-link=true`) it contains **no credentials**: only a one-time set-password link and a pointer to retrieve SPIP/CKAN connector credentials in the portal. Set the flag to `false` (dev/test) to render credentials inline (legacy behaviour).
- `sendOnboardingRejection()` - Sends rejection email with admin notes
- `logEmailConfiguration()` - Debug method to inspect email configuration

#### Email Templates
Located in `src/main/resources/templates/email/`:
- `onboarding-confirmation.html` - Confirmation template
- `onboarding-approved.html` - Approval template with credentials
- `onboarding-rejected.html` - Rejection template

#### Integration Points
The `EmailService` is called from `OnboardingRequestService` at:
- **Line 84-88**: After creating onboarding request → sends confirmation
- **Line 137-143**: After approving request → sends approval with credentials
- **Line 171-176**: After rejecting request → sends rejection notice

## Configuration

### Application Profiles

#### Development Profile (`application-dev.yml`)
```yaml
spring:
  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
            required: true

app:
  email:
    enabled: ${EMAIL_ENABLED:true}
```

**Default Behavior:** Email enabled but requires credentials via environment variables.

#### Production Profile (`application-prod.yml`)
```yaml
spring:
  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
            required: true

app:
  email:
    enabled: ${EMAIL_ENABLED:true}
  base-url: ${APP_BASE_URL:https://portal.data4circ.eu}
```

**Default Behavior:** Email enabled by default. Requires mandatory environment variables. Base URL defaults to `https://portal.data4circ.eu`.

#### Test Profile (`application-test.yml`)
```yaml
app:
  email:
    enabled: false
```

**Default Behavior:** Email disabled to prevent sending emails during automated tests.

### Environment Variables

Required environment variables for email functionality:

| Variable | Description | Example | Required |
|----------|-------------|---------|----------|
| `EMAIL_ENABLED` | Enable/disable email sending | `true` or `false` | No (defaults per profile) |
| `MAIL_HOST` | SMTP server hostname | `smtp.gmail.com` | No (defaults to Gmail) |
| `MAIL_PORT` | SMTP server port | `587` | No (defaults to 587) |
| `MAIL_USERNAME` | SMTP username (email address) | `your-email@gmail.com` | Yes |
| `MAIL_PASSWORD` | SMTP password (App Password for Gmail) | `abcd efgh ijkl mnop` | Yes |
| `APP_BASE_URL` | Portal base URL for email links | `https://portal.data4circ.eu` | No (defaults per profile) |

## Setup Instructions

### Gmail Configuration (Recommended for Development)

1. **Enable 2-Factor Authentication**
   - Go to [Google Account Security](https://myaccount.google.com/security)
   - Enable 2-Step Verification

2. **Generate App Password**
   - Go to [App Passwords](https://myaccount.google.com/apppasswords)
   - Select "Mail" and your device
   - Copy the 16-character password (format: `xxxx xxxx xxxx xxxx`)

3. **Set Environment Variables**

#### Linux/Mac (Terminal)
```bash
export EMAIL_ENABLED=true
export MAIL_USERNAME=your-email@gmail.com
export MAIL_PASSWORD="xxxx xxxx xxxx xxxx"
```

#### Windows (Command Prompt)
```cmd
set EMAIL_ENABLED=true
set MAIL_USERNAME=your-email@gmail.com
set MAIL_PASSWORD=xxxx xxxx xxxx xxxx
```

#### Windows (PowerShell)
```powershell
$env:EMAIL_ENABLED="true"
$env:MAIL_USERNAME="your-email@gmail.com"
$env:MAIL_PASSWORD="xxxx xxxx xxxx xxxx"
```

### Docker Configuration

Add to the compose file (`docker-compose.local.yml` or `docker-compose.prod.yml`) or set in `.env`:
```yaml
services:
  app:
    environment:
      - EMAIL_ENABLED=true
      - MAIL_USERNAME=${MAIL_USERNAME}
      - MAIL_PASSWORD=${MAIL_PASSWORD}
```

Create `.env` file in project root (DO NOT commit to Git):
```env
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=xxxx xxxx xxxx xxxx
```

## Email Workflow Details

### 1. Onboarding Request Confirmation

**Triggered:** When user submits onboarding request via `/api/onboarding/request`

**Workflow:**
```
User submits form
  → OnboardingController.submitOnboardingRequest()
  → OnboardingRequestService.createOnboardingRequest()
  → EmailService.sendOnboardingConfirmation()
  → Email sent to applicant
```

**Email Content:**
- Company name and contact person
- Status: "Under Review"
- Expected timeline: 2-3 business days
- Next steps information

**Template:** `email/onboarding-confirmation.html`

### 2. Onboarding Request Approval

**Triggered:** When admin approves request via `/admin/onboarding/{id}/approve`

**Workflow:**
```
Admin clicks "Approve"
  → OnboardingController.approveOnboardingRequest()
  → OnboardingRequestService.approveRequest()
  → Create Organization
  → Create Admin User with temporary password
  → PasswordResetService.createSetPasswordLink() (24h, one-time)
  → EmailService.sendOnboardingApproval()
  → Secure mode: email sent with set-password link (no credentials)
    Legacy mode: email sent with credentials inline
```

**Email Content:**
- Congratulations message
- Organization name
- **Username** (auto-generated from email)
- **Temporary Password** (12-character random password)
- Login URL
- Next steps as Organization Admin

**Security Features:**
- Password generated using `SecureRandom`
- Password complexity: uppercase, lowercase, numbers, special characters
- Password only sent via email (not stored in plaintext)
- User entity has transient field `temporaryPasswordForEmail` (not persisted)

**Template:** `email/onboarding-approved.html`

### 3. Onboarding Request Rejection

**Triggered:** When admin rejects request via `/admin/onboarding/{id}/reject`

**Workflow:**
```
Admin clicks "Reject" with optional notes
  → OnboardingController.rejectOnboardingRequest()
  → OnboardingRequestService.rejectRequest()
  → EmailService.sendOnboardingRejection()
  → Email sent with reason
```

**Email Content:**
- Company name and contact person
- Rejection reason (admin notes)
- Support contact information
- Option to reapply

**Template:** `email/onboarding-rejected.html`

## Password Generation

The `OnboardingRequestService.generateTemporaryPassword()` method creates secure temporary passwords:

**Characteristics:**
- **Length:** 12 characters
- **Character sets:**
  - Uppercase letters (A-Z)
  - Lowercase letters (a-z)
  - Numbers (0-9)
  - Special characters (!@#$%)
- **Algorithm:** Ensures at least one character from each category
- **Randomization:** Uses `java.security.SecureRandom`
- **Shuffling:** Password characters are shuffled after generation

**Example:** `T9r@mK5pL2w#`

## Debugging

### Enable Debug Logging

The `EmailService.logEmailConfiguration()` method is automatically called before sending the first email in each session.

**Log Output:**
```
=== EMAIL CONFIGURATION DEBUG ===
Email Enabled: true
From Email: your-email@gmail.com
From Email Length: 20
Mail Sender: org.springframework.mail.javamail.JavaMailSenderImpl
SMTP Host: smtp.gmail.com
SMTP Port: 587
Username: your-email@gmail.com
Password Set: true
Password Length: 16
=================================
```

### Test Endpoint (Temporary)

Visit as Platform Admin: `http://localhost:8080/admin/test-email-config`

This endpoint triggers the debug logging without sending an actual email.

**Note:** Delete `EmailTestController.java` after debugging is complete.

### Common Issues

#### Email Sending Disabled
```
Email sending is disabled. Skipping confirmation email to...
```
**Solution:** Set `EMAIL_ENABLED=true` environment variable

#### Authentication Failed
```
jakarta.mail.AuthenticationFailedException: 535-5.7.8 Username and Password not accepted
```
**Solutions:**
- Verify you're using Gmail App Password (not regular password)
- Ensure 2-Factor Authentication is enabled on Gmail account
- Check username and password environment variables are set correctly
- Verify no extra spaces in password

#### Connection Timeout
```
jakarta.mail.MessagingException: Could not connect to SMTP host
```
**Solutions:**
- Check SMTP host and port configuration
- Verify firewall allows outbound connections on port 587
- Ensure network connectivity

#### Template Not Found
```
org.thymeleaf.exceptions.TemplateInputException: Error resolving template
```
**Solutions:**
- Verify template files exist in `src/main/resources/templates/email/`
- Check template names match exactly (case-sensitive)
- Rebuild project (`mvn clean compile`)

## Security Best Practices

### Development
- ✅ Use environment variables for credentials
- ✅ Never commit credentials to Git
- ✅ Use `.gitignore` for `.env` files
- ✅ Enable email only when needed for testing

### Production
- ✅ Use dedicated email account for application
- ✅ Store credentials in secrets management service
- ✅ Rotate App Passwords periodically
- ✅ Monitor email sending rates and failures
- ✅ Implement rate limiting to prevent abuse
- ✅ Use TLS/STARTTLS for encryption

### Email Content
- ✅ Passwords sent only once via email
- ✅ Force password change on first login (recommended)
- ✅ Include login URL but avoid clickable links in sensitive emails
- ✅ Use HTTPS for all portal URLs
- ✅ Don't include sensitive organization data in emails

## Testing

### Manual Testing

1. **Test Confirmation Email:**
   ```bash
   curl -X POST http://localhost:8080/api/onboarding/request \
     -H "Content-Type: application/json" \
     -d '{
       "companyName": "Test Company",
       "contactName": "John Doe",
       "email": "test@example.com",
       ...
     }'
   ```

2. **Test Approval Email:**
   - Login as admin
   - Navigate to `/admin/onboarding`
   - Approve a pending request
   - Check email inbox

3. **Test Rejection Email:**
   - Login as admin
   - Navigate to `/admin/onboarding`
   - Reject a pending request with notes
   - Check email inbox

### Automated Testing

Email is disabled by default in test profile. To test email functionality:

```java
@SpringBootTest
@ActiveProfiles("test")
class EmailServiceTest {

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private EmailService emailService;

    @Test
    void testOnboardingConfirmationEmail() {
        // Mock email sending
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(null));

        // Test method
        emailService.sendOnboardingConfirmation(
            "test@example.com",
            "Test Company",
            "John Doe"
        );

        // Verify
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }
}
```

## Customization

### Email Templates

Templates use Thymeleaf and Bootstrap 5 styling. To customize:

1. Edit HTML files in `src/main/resources/templates/email/`
2. Available variables per template:

**onboarding-confirmation.html:**
- `${companyName}` - Company name
- `${contactName}` - Contact person name

**onboarding-approved.html:**
- `${companyName}` - Company name
- `${contactName}` - Contact person name
- `${username}` - Generated username
- `${temporaryPassword}` - Temporary password
- `${loginUrl}` - Portal login URL

**onboarding-rejected.html:**
- `${companyName}` - Company name
- `${contactName}` - Contact person name
- `${reason}` - Rejection reason (admin notes)

### SMTP Server

To use a different SMTP server (e.g., SendGrid, AWS SES, Mailgun):

```yaml
spring:
  mail:
    host: smtp.sendgrid.net  # or smtp.eu-west-1.amazonaws.com
    port: 587
    username: apikey  # SendGrid requires "apikey" as username
    password: ${SENDGRID_API_KEY}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

## Monitoring

### Log Levels

Adjust in `application.yml`:
```yaml
logging:
  level:
    com.data4circ.portal.service.EmailService: DEBUG
    org.springframework.mail: DEBUG
```

### Metrics

Monitor these indicators:
- Email send success rate
- Email send failures and reasons
- Average time to send email
- Number of emails sent per onboarding workflow

### Alerting

Consider implementing alerts for:
- High email failure rate (>5%)
- Authentication failures
- Connection timeouts
- Unusual email volume

## Future Enhancements

Potential improvements:
- [ ] Email templates with multiple language support
- [ ] Email queue with retry logic for failed sends
- [ ] Email verification before onboarding approval
- [ ] Email notification preferences for users
- [ ] HTML email preview in admin interface
- [ ] Send test email from admin panel
- [ ] Email delivery tracking (opened/clicked)
- [ ] Attachment support for onboarding documents

## References

- [Spring Boot Mail Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/io.html#io.email)
- [Thymeleaf Documentation](https://www.thymeleaf.org/documentation.html)
- [Gmail App Passwords](https://support.google.com/accounts/answer/185833)
- [JavaMail API](https://javaee.github.io/javamail/)
