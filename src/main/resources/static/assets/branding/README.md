# Branding Assets Directory

This directory contains branding assets for white-label deployments of the portal.

## Required Assets

### 1. Logo (Full Size)
- **File**: `logo.png`
- **Recommended Size**: 400x150px or similar aspect ratio
- **Format**: PNG with transparency
- **Usage**: Main logo display (future use)
- **Note**: Currently not used in templates, reserved for future enhancements

### 2. Logo (Small/Navbar)
- **File**: `logo-small.png`
- **Recommended Size**: 120x40px (height: 30px when rendered)
- **Format**: PNG with transparency
- **Usage**: Navbar brand logo
- **Location in code**: `fragments/navigation.html`

### 3. Favicon
- **File**: `favicon.ico`
- **Recommended Size**: 32x32px or 16x16px
- **Format**: ICO format (or PNG)
- **Usage**: Browser tab icon
- **Location in code**: `fragments/head.html`, `auth/login.html`, `auth/register.html`

### 4. Login Background (Optional)
- **File**: Custom path via configuration
- **Format**: JPG or PNG
- **Usage**: Optional background image for login page
- **Configuration**: `app.branding.assets.login-background`

## Deployment Options

### Option 1: Local Files (Development)
1. Place your asset files in this directory
2. Ensure filenames match the configuration in `application.yml`
3. Restart the application

```bash
src/main/resources/static/assets/branding/
├── logo.png
├── logo-small.png
└── favicon.ico
```

### Option 2: Docker Volume Mount (Production)
Mount a host directory containing your branding assets:

```yaml
services:
  app:
    volumes:
      - ./client-branding:/app/static/assets/branding:ro
```

**Host directory structure** (`./client-branding/`):
```
client-branding/
├── logo.png
├── logo-small.png
└── favicon.ico
```

### Option 3: Custom Path via Configuration
Specify custom paths in your environment variables:

```bash
BRANDING_LOGO=/custom/path/to/logo.png
BRANDING_LOGO_SMALL=/custom/path/to/logo-small.png
BRANDING_FAVICON=/custom/path/to/favicon.ico
```

### Option 4: CDN or External URL
Host assets on a CDN and reference them via configuration:

```yaml
app:
  branding:
    assets:
      logo: https://cdn.example.com/logo.png
      logo-small: https://cdn.example.com/logo-small.png
      favicon: https://cdn.example.com/favicon.ico
```

## Asset Specifications

### Logo Design Guidelines
- Use PNG format with transparent background
- Ensure logo is legible at small sizes (navbar display)
- Recommended colors: Match your brand's primary color scheme
- Test logo visibility on both light and dark backgrounds

### Favicon Guidelines
- Keep design simple (icons work better than text at small sizes)
- Use brand colors or a simplified version of your logo
- Test in different browsers (Chrome, Firefox, Safari, Edge)
- Consider providing multiple sizes for different contexts

## File Permissions
Ensure assets are readable by the application:
```bash
chmod 644 logo.png logo-small.png favicon.ico
```

## Troubleshooting

### Logo not showing
1. Check file path matches configuration
2. Verify file permissions (644)
3. Check browser console for 404 errors
4. Ensure file format is supported (PNG recommended)
5. Clear browser cache (Ctrl+F5 or Cmd+Shift+R)

### Favicon not updating
1. Clear browser cache completely
2. Try incognito/private browsing mode
3. Check file path in `<link rel="icon">` tag
4. Wait for browser cache timeout (may take several minutes)
5. Hard refresh: Ctrl+Shift+Delete → Clear cached images

### Broken image icon appears
1. Verify asset file exists at the configured path
2. Check server logs for file access errors
3. Test asset URL directly in browser: `http://localhost:8080/assets/branding/logo.png`

## Testing

After deploying new branding assets:

1. **Visual Check**:
   - Visit `/login` to see navbar logo
   - Check browser tab for favicon
   - Inspect footer branding

2. **Cross-browser Testing**:
   - Chrome, Firefox, Safari, Edge
   - Mobile browsers (responsive design)

3. **Performance Check**:
   - Verify asset file sizes (logos < 100KB, favicon < 50KB)
   - Check page load times

## Default Behavior

If asset files are not provided or paths are invalid:
- Logo will fail gracefully (hidden via `onerror="this.style.display='none'"`)
- Favicon will fall back to browser default
- No application errors will occur

## Examples

### NTT DATA Branding
```
logo-small.png: NTT DATA corporate logo (120x40px, blue/white)
favicon.ico: NTT DATA "N" icon (32x32px)
```

### Eco Green Theme
```
logo-small.png: Green leaf icon + "EcoMaterials" text
favicon.ico: Green circular arrow (recycling symbol)
```

### Corporate Blue Theme
```
logo-small.png: Corporate shield + "CircularTech" text
favicon.ico: Blue circular "C" monogram
```

## Related Documentation

- [White-Label Deployment Guide](../../../../../docs/deployment/WHITE-LABEL-DEPLOYMENT.md) - Complete deployment instructions
- [Application Configuration](../../application.yml) - Branding configuration reference
- [ThemeProperties.java](../../../../java/com/data4circ/portal/common/config/ThemeProperties.java) - Configuration class

## Support

For questions or issues with branding assets:
1. Check this README for troubleshooting steps
2. Review the White-Label Deployment Guide
3. Verify configuration in `application.yml` or environment variables
4. Check application logs for asset loading errors
