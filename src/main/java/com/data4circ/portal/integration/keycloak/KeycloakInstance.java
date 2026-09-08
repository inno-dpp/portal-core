package com.data4circ.portal.integration.keycloak;

/**
 * Connection descriptor for one Keycloak instance. Every {@code KeycloakApiClient}
 * call takes an instance so the portal can provision different Keycloak servers per
 * organization; the default instance comes from configuration/platform settings.
 */
public class KeycloakInstance {

    private final String baseUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakInstance(String baseUrl, String realm, String clientId, String clientSecret) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Keycloak base URL must not be empty");
        }
        if (realm == null || realm.isBlank()) {
            throw new IllegalArgumentException("Keycloak realm must not be empty");
        }
        this.baseUrl = trimTrailingSlash(baseUrl.trim());
        this.realm = realm.trim();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getRealm() {
        return realm;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String tokenUrl() {
        return baseUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    public String adminUrl(String path) {
        return baseUrl + "/admin/realms/" + realm + path;
    }

    @Override
    public String toString() {
        return baseUrl + " (realm: " + realm + ")";
    }
}
