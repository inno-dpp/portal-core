package com.data4circ.portal.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for white-label theming.
 * Allows deployment-level customization of branding, colors, typography, and assets.
 *
 * <p>Configuration prefix: app.branding</p>
 *
 * <p>Example configuration in application.yml:</p>
 * <pre>
 * app:
 *   branding:
 *     brand-name: "Your Company Portal"
 *     brand-tagline: "Circular Economy Data Platform"
 *     colors:
 *       primary: "#00AA78"
 *       secondary: "#2B463C"
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "app.branding")
public class ThemeProperties {

    private String brandName = "Portal for Circularity";
    private String brandTagline = "Circular Economy Data Platform";
    private String brandSubtagline = "Integrate data catalog functionality with SPIP capabilities for circular economy data management.";

    /** Support contact shown in outgoing emails; hidden when empty. */
    private String supportEmail = "";

    /** Privacy policy URL linked from consent checkboxes; link is unstyled text when empty. */
    private String privacyPolicyUrl = "";

    /** Email sign-off name; derived from brandName when not set. */
    private String teamName;

    /** Copyright line owner; derived from brandName when not set. */
    private String copyrightHolder;

    private Colors colors = new Colors();
    private Typography typography = new Typography();
    private Assets assets = new Assets();
    private Navbar navbar = new Navbar();

    // Getters and Setters

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public String getBrandTagline() {
        return brandTagline;
    }

    public void setBrandTagline(String brandTagline) {
        this.brandTagline = brandTagline;
    }

    public String getBrandSubtagline() {
        return brandSubtagline;
    }

    public void setBrandSubtagline(String brandSubtagline) {
        this.brandSubtagline = brandSubtagline;
    }

    public String getSupportEmail() {
        return supportEmail;
    }

    public void setSupportEmail(String supportEmail) {
        this.supportEmail = supportEmail;
    }

    public String getPrivacyPolicyUrl() {
        return privacyPolicyUrl;
    }

    public void setPrivacyPolicyUrl(String privacyPolicyUrl) {
        this.privacyPolicyUrl = privacyPolicyUrl;
    }

    public String getTeamName() {
        return teamName != null && !teamName.isBlank() ? teamName : "The " + brandName + " Team";
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getCopyrightHolder() {
        return copyrightHolder != null && !copyrightHolder.isBlank() ? copyrightHolder : brandName;
    }

    public void setCopyrightHolder(String copyrightHolder) {
        this.copyrightHolder = copyrightHolder;
    }

    public Colors getColors() {
        return colors;
    }

    public void setColors(Colors colors) {
        this.colors = colors;
    }

    public Typography getTypography() {
        return typography;
    }

    public void setTypography(Typography typography) {
        this.typography = typography;
    }

    public Assets getAssets() {
        return assets;
    }

    public void setAssets(Assets assets) {
        this.assets = assets;
    }

    public Navbar getNavbar() {
        return navbar;
    }

    public void setNavbar(Navbar navbar) {
        this.navbar = navbar;
    }

    /**
     * Color scheme configuration for the theme.
     * All colors should be provided in hex format (e.g., #1E5AA8).
     */
    public static class Colors {
        private String primary = "#1E5AA8";
        private String secondary = "#2F3E4E";
        private String accent = "#A8C6E8";
        private String info = "#0E7490";
        private String success = "#2E7D32";
        private String warning = "#ED6C02";
        private String danger = "#C62828";
        private String text = "#1F2933";
        private String lightText = "#6D7485";

        public String getPrimary() {
            return primary;
        }

        public void setPrimary(String primary) {
            this.primary = primary;
        }

        public String getSecondary() {
            return secondary;
        }

        public void setSecondary(String secondary) {
            this.secondary = secondary;
        }

        public String getAccent() {
            return accent;
        }

        public void setAccent(String accent) {
            this.accent = accent;
        }

        public String getInfo() {
            return info;
        }

        public void setInfo(String info) {
            this.info = info;
        }

        public String getSuccess() {
            return success;
        }

        public void setSuccess(String success) {
            this.success = success;
        }

        public String getWarning() {
            return warning;
        }

        public void setWarning(String warning) {
            this.warning = warning;
        }

        public String getDanger() {
            return danger;
        }

        public void setDanger(String danger) {
            this.danger = danger;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getLightText() {
            return lightText;
        }

        public void setLightText(String lightText) {
            this.lightText = lightText;
        }
    }

    /**
     * Typography configuration for the theme.
     */
    public static class Typography {
        private String fontFamily = "'Open Sans', Arial, sans-serif";
        private String headingFontFamily;

        public String getFontFamily() {
            return fontFamily;
        }

        public void setFontFamily(String fontFamily) {
            this.fontFamily = fontFamily;
        }

        public String getHeadingFontFamily() {
            return headingFontFamily != null ? headingFontFamily : fontFamily;
        }

        public void setHeadingFontFamily(String headingFontFamily) {
            this.headingFontFamily = headingFontFamily;
        }
    }

    /**
     * Asset paths configuration (logos, favicon, etc.).
     */
    public static class Assets {
        private String logo = "/assets/branding/logo.png";
        private String logoSmall = "/assets/branding/logo-small.png";
        private String favicon = "/assets/branding/favicon.ico";
        private String loginBackground;

        public String getLogo() {
            return logo;
        }

        public void setLogo(String logo) {
            this.logo = logo;
        }

        public String getLogoSmall() {
            return logoSmall;
        }

        public void setLogoSmall(String logoSmall) {
            this.logoSmall = logoSmall;
        }

        public String getFavicon() {
            return favicon;
        }

        public void setFavicon(String favicon) {
            this.favicon = favicon;
        }

        public String getLoginBackground() {
            return loginBackground;
        }

        public void setLoginBackground(String loginBackground) {
            this.loginBackground = loginBackground;
        }
    }

    /**
     * Navbar styling configuration.
     */
    public static class Navbar {
        private String background = "#2F3E4E";
        private String textColor = "#FFFFFF";

        public String getBackground() {
            return background;
        }

        public void setBackground(String background) {
            this.background = background;
        }

        public String getTextColor() {
            return textColor;
        }

        public void setTextColor(String textColor) {
            this.textColor = textColor;
        }
    }
}
