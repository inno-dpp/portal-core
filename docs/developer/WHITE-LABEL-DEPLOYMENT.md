# White-Label Deployment Guide

Complete guide for deploying client-specific branded instances of the DATA4CIRC Portal.

## Table of Contents
- [Overview](#overview)
- [Quick Start](#quick-start)
- [Configuration Reference](#configuration-reference)
- [Deployment Methods](#deployment-methods)
- [Example Themes](#example-themes)
- [Asset Management](#asset-management)
- [Troubleshooting](#troubleshooting)
- [Production Checklist](#production-checklist)

---

## Overview

The white-label branding system allows deployment-level customization of the portal's appearance without code changes. All branding is configured at deployment time through:

- Configuration files (`application.yml`)
- Environment variables
- Docker environment configuration
- Asset file replacement

### What Can Be Customized

| Element | Configuration Property | Examples |
|---------|----------------------|----------|
| Brand Name | `app.branding.brand-name` | "NTT DATA", "CircularTech Hub" |
| Tagline | `app.branding.brand-tagline` | "Circular Economy Data Platform" |
| Subtagline | `app.branding.brand-subtagline` | Detailed description text |
| Primary Color | `app.branding.colors.primary` | #1E5AA8 (corporate blue) |
| Secondary Color | `app.branding.colors.secondary` | #2F3E4E (slate) |
| Accent Color | `app.branding.colors.accent` | #F2B705 (amber) |
| Logo (Full) | `app.branding.assets.logo` | `/assets/branding/logo.png` |
| Logo (Small) | `app.branding.assets.logo-small` | Navbar logo path |
| Favicon | `app.branding.assets.favicon` | Browser tab icon |
| Typography | `app.branding.typography.font-family` | Font stack |
| Navbar Background | `app.branding.navbar.background` | #1F2A37 |

### Key Features

✅ **No Code Changes Required** - All customization via configuration
✅ **Docker-Friendly** - Environment variable support
✅ **Asset Flexibility** - Local files, volumes, or CDN
✅ **CSS Variables** - Dynamic theming at runtime
✅ **Backward Compatible** - Defaults work without configuration

---

## Quick Start

### Method 1: Application Configuration (Development)

Edit `src/main/resources/application.yml`:

```yaml
app:
  branding:
    brand-name: "Your Company Name"
    brand-tagline: "Your Tagline"
    colors:
      primary: "#1E5AA8"
      secondary: "#2F3E4E"
      accent: "#F2B705"
```

Restart the application:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Method 2: Environment Variables (Production)

Set environment variables before starting:

```bash
export BRANDING_NAME="Your Company Name"
export BRANDING_COLOR_PRIMARY="#1E5AA8"
export BRANDING_COLOR_SECONDARY="#2F3E4E"
export BRANDING_COLOR_ACCENT="#F2B705"

java -jar d4c-portal.jar
```

### Method 3: Docker Compose

Add to your `docker-compose.override.yml`:

```yaml
services:
  app:
    environment:
      BRANDING_NAME: "Your Company Name"
      BRANDING_TAGLINE: "Your Tagline"
      BRANDING_COLOR_PRIMARY: "#1E5AA8"
      BRANDING_COLOR_SECONDARY: "#2F3E4E"
      BRANDING_COLOR_ACCENT: "#F2B705"
```

Restart containers:
```bash
docker-compose up -d
```

---

## Configuration Reference

### Brand Identity

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `brand-name` | `BRANDING_NAME` | DATA4CIRC Portal | Organization or product name |
| `brand-tagline` | `BRANDING_TAGLINE` | Circular Economy Data Platform | Short tagline (1-5 words) |
| `brand-subtagline` | `BRANDING_SUBTAGLINE` | Long description | Detailed description (1-2 sentences) |

### Color Scheme

All colors must be in hex format (e.g., `#1E5AA8`).

| Property | Environment Variable | Default | Usage |
|----------|---------------------|---------|-------|
| `colors.primary` | `BRANDING_COLOR_PRIMARY` | #1E5AA8 | Primary brand color (corporate blue), login box |
| `colors.secondary` | `BRANDING_COLOR_SECONDARY` | #2F3E4E | Secondary color (dark slate), backgrounds |
| `colors.accent` | `BRANDING_COLOR_ACCENT` | #A8C6E8 | Accent (light blue), buttons, highlights |
| `colors.info` | `BRANDING_COLOR_INFO` | #0E7490 | Info color (teal-cyan), data/technology elements |
| `colors.success` | `BRANDING_COLOR_SUCCESS` | #2E7D32 | Success messages, alerts |
| `colors.warning` | `BRANDING_COLOR_WARNING` | #ED6C02 | Warning messages |
| `colors.danger` | `BRANDING_COLOR_DANGER` | #C62828 | Error messages, alerts |
| `colors.text` | `BRANDING_COLOR_TEXT` | #1F2933 | Main text color |
| `colors.light-text` | `BRANDING_COLOR_LIGHT_TEXT` | #6D7485 | Secondary text (cool gray-blue) |

### Typography

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `typography.font-family` | `BRANDING_FONT_FAMILY` | 'Open Sans', Arial, sans-serif | Body font stack |
| `typography.heading-font-family` | `BRANDING_HEADING_FONT_FAMILY` | 'Montserrat', 'Open Sans', Arial, sans-serif | Heading font |

### Assets

| Property | Environment Variable | Default | Specifications |
|----------|---------------------|---------|----------------|
| `assets.logo` | `BRANDING_LOGO` | /assets/branding/logo.png | 400x150px recommended |
| `assets.logo-small` | `BRANDING_LOGO_SMALL` | /assets/branding/logo-small.png | 120x40px, navbar use |
| `assets.favicon` | `BRANDING_FAVICON` | /assets/branding/favicon.png | 32x32px PNG format |
| `assets.login-background` | `BRANDING_LOGIN_BACKGROUND` | (none) | Optional background image |

### Navbar Styling

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `navbar.background` | `BRANDING_NAVBAR_BACKGROUND` | #2F3E4E | Navbar background color |
| `navbar.text-color` | `BRANDING_NAVBAR_TEXT_COLOR` | #FFFFFF | Navbar text/link color |

---

## Deployment Methods

### Branding Overlay Directory (Recommended)

Keep an entire brand — texts, colors, fonts, logo files, dashboard cards — in
one directory outside the application, and attach it at runtime. No fork, no
rebuild, and the portal repository stays brand-free. The reserved
`deploy-branding/` directory name is git-ignored for this purpose, so the
overlay can live directly in a checkout (e.g. as a symlink into a separate,
private branding repository shared by your team).

**Layout**:

```
deploy-branding/
├── branding.yml              # all app.branding.* properties + use-case cards
└── assets/branding/          # logo.png, logo-small.png, favicon.png, ...
```

**branding.yml**:

```yaml
spring:
  web:
    resources:
      # Serve the logo files from this directory, falling back to the assets
      # bundled in the jar. Overriding static-locations replaces the Spring
      # Boot defaults, so the classpath entries must be repeated.
      static-locations: file:./deploy-branding/,classpath:/META-INF/resources/,classpath:/resources/,classpath:/static/,classpath:/public/

app:
  branding:
    brand-name: "Your Company Portal"
    colors:
      primary: "#2E7D32"
    assets:
      logo-small: /assets/branding/your-logo.png
    # ... any property from the Configuration Reference above
  dashboard:
    use-case-cards:            # list-valued config that env vars cannot express
      - title: Your Use Case
        description: Category description shown on the dashboard.
        url: https://catalog.example.com/group/your-use-case
        icon: fas fa-tags
        style: primary
```

**Attach it** — one environment variable (works for `java -jar`, Maven, or
compose `env_file`):

```bash
SPRING_CONFIG_IMPORT=optional:file:./deploy-branding/branding.yml
```

Imported properties override the `application.yml` defaults; `optional:`
means a deployment without the overlay simply starts with the neutral
defaults. Environment variables (`BRANDING_*`) still take precedence over the
imported file if both are set.

**Docker**: add a second compose file (the git-ignored name
`docker-compose.branding.yml` is reserved for this) that mounts the overlay
at the same relative location inside the container — the container's working
directory is `/app`, so the repo-relative paths above resolve identically on
the host and in Docker:

```yaml
services:
  app:
    volumes:
      - ./deploy-branding:/app/deploy-branding:ro
```

```bash
docker compose -f docker-compose.local.yml -f docker-compose.branding.yml up -d
```

Note: bind mounts resolve when a container is created — after swapping the
overlay directory (or re-pointing its symlink), recreate the container
(`docker compose up -d --force-recreate app`) rather than restarting it.

---

### Local Development Deployment

**Step 1**: Edit `application.yml`

```yaml
app:
  branding:
    brand-name: "NTT DATA"
    brand-tagline: "Circular Economy Data Platform"
    brand-subtagline: "Federated data sharing with cryptographic capabilities."
    colors:
      primary: "#1E5AA8"
      secondary: "#2F3E4E"
      accent: "#F2B705"
    navbar:
      background: "#1F2A37"
```

**Step 2**: Add branding assets

```bash
cp your-logo.png src/main/resources/static/assets/branding/logo-small.png
cp your-favicon.ico src/main/resources/static/assets/branding/favicon.ico
```

**Step 3**: Restart application

```bash
mvn spring-boot:run
```

**Step 4**: Verify at `http://localhost:8080/login`

---

### Docker Compose Deployment

**Option A: Environment Variables in the compose file**

```yaml
services:
  app:
    image: data4circ-portal:latest
    environment:
      # Brand Identity
      BRANDING_NAME: "NTT DATA"
      BRANDING_TAGLINE: "Circular Economy Data Platform"
      BRANDING_SUBTAGLINE: "Federated data sharing with cryptographic capabilities."

      # Colors
      BRANDING_COLOR_PRIMARY: "#1E5AA8"
      BRANDING_COLOR_SECONDARY: "#2F3E4E"
      BRANDING_COLOR_ACCENT: "#F2B705"
      BRANDING_COLOR_SUCCESS: "#2E7D32"
      BRANDING_COLOR_WARNING: "#ED6C02"
      BRANDING_COLOR_DANGER: "#C62828"

      # Navbar
      BRANDING_NAVBAR_BACKGROUND: "#1F2A37"
      BRANDING_NAVBAR_TEXT_COLOR: "#FFFFFF"
    volumes:
      - ./branding-assets:/app/static/assets/branding:ro
```

**Option B: Using .env File**

Create `.env` file:
```bash
BRANDING_NAME=NTT DATA
BRANDING_TAGLINE=Circular Economy Data Platform
BRANDING_COLOR_PRIMARY=#1E5AA8
BRANDING_COLOR_SECONDARY=#2F3E4E
BRANDING_COLOR_ACCENT=#F2B705
```

Reference in your compose file:
```yaml
services:
  app:
    env_file: .env
```

**Option C: docker-compose.override.yml (Recommended for Client-Specific)**

```bash
# Copy example override file
cp docker-compose.override.yml.example docker-compose.override.yml

# Edit with client-specific values
nano docker-compose.override.yml

# Deploy
docker-compose up -d
```

---

### Kubernetes Deployment

**ConfigMap for Branding**:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: portal-branding
data:
  BRANDING_NAME: "NTT DATA"
  BRANDING_TAGLINE: "Circular Economy Data Platform"
  BRANDING_COLOR_PRIMARY: "#1E5AA8"
  BRANDING_COLOR_SECONDARY: "#2F3E4E"
  BRANDING_COLOR_ACCENT: "#F2B705"
  BRANDING_NAVBAR_BACKGROUND: "#1F2A37"
```

**Deployment**:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: portal
spec:
  template:
    spec:
      containers:
      - name: portal
        image: data4circ-portal:latest
        envFrom:
        - configMapRef:
            name: portal-branding
        volumeMounts:
        - name: branding-assets
          mountPath: /app/static/assets/branding
          readOnly: true
      volumes:
      - name: branding-assets
        persistentVolumeClaim:
          claimName: branding-pvc
```

---

## Example Themes

### Theme 1: NTT DATA (Corporate Blue)

```yaml
app:
  branding:
    brand-name: "NTT DATA"
    brand-tagline: "Circular Economy Data Platform"
    brand-subtagline: "Federated data sharing with cryptographic capabilities for circular economy data management."
    colors:
      primary: "#1E5AA8"        # Deep corporate blue
      secondary: "#2F3E4E"      # Slate / blue-gray
      accent: "#F2B705"         # Amber accent
      success: "#2E7D32"
      warning: "#ED6C02"
      danger: "#C62828"
      text: "#1F2933"
      light-text: "#6B7280"
    navbar:
      background: "#1F2A37"     # Dark blue-gray
      text-color: "#FFFFFF"
```

**Visual Impact**:
- Professional corporate appearance
- Blue-dominated color scheme
- High contrast for accessibility

---

### Theme 2: Eco Green

```yaml
app:
  branding:
    brand-name: "EcoMaterials Exchange"
    brand-tagline: "Sustainable Resource Sharing"
    brand-subtagline: "Connect, share, and trade circular economy materials in a secure, federated platform."
    colors:
      primary: "#2E7D32"        # Forest green
      secondary: "#1B5E20"      # Dark green
      accent: "#81C784"         # Light green
      success: "#4CAF50"
      warning: "#FF9800"
      danger: "#D32F2F"
      text: "#263238"
      light-text: "#546E7A"
    navbar:
      background: "#1B5E20"
      text-color: "#FFFFFF"
```

**Visual Impact**:
- Environmental, sustainability-focused
- Green-dominated palette
- Natural, organic feel

---

### Theme 3: Industrial Gray

```yaml
app:
  branding:
    brand-name: "Industrial Materials Hub"
    brand-tagline: "B2B Resource Marketplace"
    brand-subtagline: "Enterprise-grade material exchange platform for industrial circular economy."
    colors:
      primary: "#455A64"        # Blue-gray
      secondary: "#263238"      # Dark gray
      accent: "#FF6F00"         # Deep orange
      success: "#388E3C"
      warning: "#F57C00"
      danger: "#C62828"
      text: "#212121"
      light-text: "#757575"
    navbar:
      background: "#263238"
      text-color: "#ECEFF1"
```

**Visual Impact**:
- Professional, industrial aesthetic
- Neutral color scheme with orange accents
- Enterprise-focused design

---

## Asset Management

### Asset File Specifications

| Asset | Size | Format | Usage |
|-------|------|--------|-------|
| `logo.png` | 400x150px | PNG with transparency | Future use (not currently displayed) |
| `logo-small.png` | 120x40px | PNG with transparency | Navbar (rendered at 30px height) |
| `favicon.ico` | 32x32px | ICO or PNG | Browser tab icon |

### Deployment Strategies

**Strategy 1: Embedded in Application (Build Time)**

1. Place assets in `src/main/resources/static/assets/branding/`
2. Rebuild application
3. Deploy new JAR/Docker image

**Pros**: Simple, no runtime dependencies
**Cons**: Requires rebuild for changes

**Strategy 2: Docker Volume Mount (Runtime)**

```yaml
volumes:
  - ./client-branding:/app/static/assets/branding:ro
```

**Pros**: Change without rebuild, client-specific per deployment
**Cons**: Requires file management on host

**Strategy 3: CDN Hosting**

```yaml
assets:
  logo: "https://cdn.clientname.com/portal/logo.png"
  logo-small: "https://cdn.clientname.com/portal/logo-small.png"
  favicon: "https://cdn.clientname.com/portal/favicon.ico"
```

**Pros**: Fast delivery, centralized management
**Cons**: External dependency, requires CDN setup

---

## Troubleshooting

### Colors Not Applying

**Symptom**: Page shows old green colors instead of configured colors.

**Solutions**:
1. Clear browser cache (Ctrl+F5 or Cmd+Shift+R)
2. Try incognito/private browsing mode
3. Verify configuration:
   ```bash
   # Check if environment variables are set
   echo $BRANDING_COLOR_PRIMARY

   # Or check application logs on startup
   grep "branding" logs/application.log
   ```
4. Ensure colors are in correct hex format: `#1E5AA8` (not `1E5AA8`)

---

### Logo Not Displaying

**Symptom**: Broken image icon or no logo in navbar.

**Solutions**:
1. Verify file exists:
   ```bash
   ls -la src/main/resources/static/assets/branding/logo-small.png
   # OR for Docker
   docker exec portal ls -la /app/static/assets/branding/
   ```

2. Check file permissions:
   ```bash
   chmod 644 logo-small.png
   ```

3. Test URL directly:
   ```
   http://localhost:8080/assets/branding/logo-small.png
   ```

4. Check configuration path matches actual file location

5. Review application logs for 404 errors

---

### Favicon Not Updating

**Symptom**: Old favicon persists despite configuration change.

**Solutions**:
1. Favicon caching is aggressive. Try:
   - Clear all browser data (not just cache)
   - Close and reopen browser completely
   - Wait 5-10 minutes for cache expiry
   - Use incognito mode for testing

2. Verify HTML source includes new favicon path:
   ```html
   <link rel="icon" type="image/x-icon" href="/assets/branding/favicon.ico">
   ```

3. Test favicon URL directly:
   ```
   http://localhost:8080/assets/branding/favicon.ico
   ```

---

### Environment Variables Not Taking Effect

**Symptom**: Configuration from `application.yml` used instead of environment variables.

**Solutions**:
1. Ensure Spring profile is `prod`:
   ```bash
   export SPRING_PROFILES_ACTIVE=prod
   ```

2. Verify variable names exactly match (case-sensitive):
   ```bash
   # Correct
   BRANDING_NAME="My Company"

   # Wrong
   branding_name="My Company"
   ```

3. Check variable scope:
   ```bash
   # For systemd services
   cat /etc/systemd/system/portal.service

   # For Docker
   docker inspect portal | grep BRANDING
   ```

---

### Login Page Still Shows Default Green

**Symptom**: Login/register pages don't use configured colors.

**Root Cause**: CSS variables not being injected or custom.css cached.

**Solutions**:
1. Verify CSS variable injection in page source:
   ```html
   <style>
     :root {
       --primary-color: #1E5AA8;
       --secondary-color: #2F3E4E;
       ...
     }
   </style>
   ```

2. Clear CSS cache:
   - Hard refresh (Ctrl+F5)
   - Clear browser cache for site
   - Restart browser

3. Verify `custom.css` uses CSS variables (not hardcoded colors):
   ```css
   body.login-page {
     background: var(--secondary-color) !important;
   }
   ```

---

## Production Checklist

Before deploying to production:

### Configuration Review
- [ ] Brand name, tagline, and subtagline are client-approved
- [ ] All colors are in hex format (`#RRGGBB`)
- [ ] Color scheme meets accessibility standards (WCAG AA contrast ratios)
- [ ] Typography fonts are web-safe or properly loaded
- [ ] Navbar colors provide sufficient contrast for readability

### Asset Verification
- [ ] Logo files are optimized for web (< 100KB)
- [ ] Favicon is in ICO format or compatible PNG
- [ ] All asset paths are correct and accessible
- [ ] Assets are deployed to correct location (local/volume/CDN)
- [ ] File permissions are set correctly (644 recommended)

### Testing
- [ ] Login page displays correct branding
- [ ] Register page displays correct branding
- [ ] Dashboard shows correct brand name and tagline
- [ ] Navbar shows logo and brand name
- [ ] Footer displays brand information
- [ ] Favicon appears in browser tabs
- [ ] All pages tested in Chrome, Firefox, Safari, Edge
- [ ] Mobile responsive design verified

### Deployment
- [ ] Environment variables are set (if using)
- [ ] docker-compose.override.yml configured (if using Docker)
- [ ] Asset volume mounts work correctly (if using)
- [ ] Application starts without errors
- [ ] Logs show no branding-related warnings
- [ ] SSL/TLS certificates valid
- [ ] CDN configured (if using external assets)

### Documentation
- [ ] Branding configuration documented for client
- [ ] Asset replacement procedures documented
- [ ] Rollback procedures defined
- [ ] Monitoring/alerting configured

---

## Additional Resources

### Related Documentation
- [Asset README](../../src/main/resources/static/assets/branding/README.md) - Asset file specifications
- [ThemeProperties.java](../../src/main/java/com/data4circ/portal/common/config/ThemeProperties.java) - Configuration class reference
- [docker-compose.override.yml.example](../../docker-compose.override.yml.example) - Example configurations

### Configuration Files
- `application.yml` - Default branding configuration
- `application-prod.yml` - Production environment variable mapping
- `custom.css` - CSS variable implementation

### Support
For issues or questions:
1. Review this guide's troubleshooting section
2. Check application logs for errors
3. Verify configuration syntax
4. Test in isolation (single variable change)
5. Contact support with logs and configuration details

---

## Changelog

### Version 1.0 (2026-01-20)
- Initial white-label branding system
- Full deployment-level customization
- 18 configuration properties
- 3 example themes
- Docker and Kubernetes support
- Comprehensive documentation

---

**Last Updated**: 2026-01-20
**Version**: 1.0
**Maintained by**: DATA4CIRC Development Team
