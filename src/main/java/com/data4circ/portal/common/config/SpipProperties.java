package com.data4circ.portal.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SPIP configuration properties loaded from application.yml, prefixed {@code app.spip}.
 *
 * <p>Pure, side-effect-free config data — no live behavior — so unlike the rest of SPIP's
 * integration it lives in core, unconditionally: {@code SpipSettingsService} (used by the
 * always-present platform settings page, alongside CKAN and Keycloak) needs it regardless
 * of whether the SPIP module is on the classpath at all. The module's own live pieces
 * (the SPIP-calling {@code SpipConfig.spipRestTemplate} bean, {@code SpipApiClient}, etc.)
 * live in the spip-plugin module and depend on this class from core — never the reverse.
 * See docs/developer/SPIP-PLUGIN-DECOUPLING-PLAN.md.</p>
 */
@Component
@ConfigurationProperties(prefix = "app.spip")
public class SpipProperties {

    // SPIP Platform Connection Properties
    private String baseUrl;
    private String customerTag;

    // Admin credentials
    private Admin admin = new Admin();

    // SSL configuration
    private Ssl ssl = new Ssl();

    // API endpoints
    private Endpoints endpoints = new Endpoints();

    // Resource types
    private Resources resources = new Resources();

    // Default role
    private String defaultRole;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getCustomerTag() {
        return customerTag;
    }

    public void setCustomerTag(String customerTag) {
        this.customerTag = customerTag;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public Ssl getSsl() {
        return ssl;
    }

    public void setSsl(Ssl ssl) {
        this.ssl = ssl;
    }

    public Endpoints getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(Endpoints endpoints) {
        this.endpoints = endpoints;
    }

    public Resources getResources() {
        return resources;
    }

    public void setResources(Resources resources) {
        this.resources = resources;
    }

    public String getDefaultRole() {
        return defaultRole;
    }

    public void setDefaultRole(String defaultRole) {
        this.defaultRole = defaultRole;
    }

    public static class Admin {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Ssl {
        private boolean disableVerification;

        public boolean isDisableVerification() {
            return disableVerification;
        }

        public void setDisableVerification(boolean disableVerification) {
            this.disableVerification = disableVerification;
        }
    }

    public static class Endpoints {
        private String login;
        private String createUser;
        private String assignRole;
        private String addAttribute;
        private String getAttribute;
        private String deleteAttribute;
        private String createPolicy;
        private String getPolicy;
        private String deletePolicy;

        public String getLogin() {
            return login;
        }

        public void setLogin(String login) {
            this.login = login;
        }

        public String getCreateUser() {
            return createUser;
        }

        public void setCreateUser(String createUser) {
            this.createUser = createUser;
        }

        public String getAssignRole() {
            return assignRole;
        }

        public void setAssignRole(String assignRole) {
            this.assignRole = assignRole;
        }

        public String getAddAttribute() {
            return addAttribute;
        }

        public void setAddAttribute(String addAttribute) {
            this.addAttribute = addAttribute;
        }

        public String getGetAttribute() {
            return getAttribute;
        }

        public void setGetAttribute(String getAttribute) {
            this.getAttribute = getAttribute;
        }

        public String getCreatePolicy() {
            return createPolicy;
        }

        public void setCreatePolicy(String createPolicy) {
            this.createPolicy = createPolicy;
        }

        public String getGetPolicy() {
            return getPolicy;
        }

        public void setGetPolicy(String getPolicy) {
            this.getPolicy = getPolicy;
        }

        public String getDeleteAttribute() {
            return deleteAttribute;
        }

        public void setDeleteAttribute(String deleteAttribute) {
            this.deleteAttribute = deleteAttribute;
        }

        public String getDeletePolicy() {
            return deletePolicy;
        }

        public void setDeletePolicy(String deletePolicy) {
            this.deletePolicy = deletePolicy;
        }
    }

    public static class Resources {
        private String encryptAttributes;
        private String decryptAttributes;

        public String getEncryptAttributes() {
            return encryptAttributes;
        }

        public void setEncryptAttributes(String encryptAttributes) {
            this.encryptAttributes = encryptAttributes;
        }

        public String getDecryptAttributes() {
            return decryptAttributes;
        }

        public void setDecryptAttributes(String decryptAttributes) {
            this.decryptAttributes = decryptAttributes;
        }
    }
}
