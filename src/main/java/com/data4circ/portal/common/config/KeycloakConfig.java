package com.data4circ.portal.common.config;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;

/**
 * Configuration for Keycloak identity provider integration. The properties describe
 * the platform's <em>default</em> Keycloak instance; the onboarding sync form can
 * override them per organization (multi-instance support).
 */
@Configuration
public class KeycloakConfig {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakConfig.class);

    /**
     * Default Keycloak instance properties loaded from application.yml under 'app.keycloak'.
     */
    @Component
    @ConfigurationProperties(prefix = "app.keycloak")
    public static class KeycloakProperties {

        /** Base URL of the Keycloak server, e.g. http://localhost:8081 */
        private String baseUrl;

        /** Realm provisioned during onboarding. */
        private String realm;

        /** Confidential client with service account used for Admin API calls. */
        private String clientId;

        private String clientSecret;

        /**
         * When true, the portal mirrors the first user's password into Keycloak at
         * first-login activation (best-effort; failures never block the portal flow).
         */
        private boolean mirrorPortalPassword;

        private Ssl ssl = new Ssl();

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getRealm() {
            return realm;
        }

        public void setRealm(String realm) {
            this.realm = realm;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public boolean isMirrorPortalPassword() {
            return mirrorPortalPassword;
        }

        public void setMirrorPortalPassword(boolean mirrorPortalPassword) {
            this.mirrorPortalPassword = mirrorPortalPassword;
        }

        public Ssl getSsl() {
            return ssl;
        }

        public void setSsl(Ssl ssl) {
            this.ssl = ssl;
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
    }

    /**
     * RestTemplate for Keycloak Admin API communication.
     *
     * WARNING: SSL verification can be disabled for development.
     * This should be enabled in production with proper certificates.
     */
    @Bean(name = "keycloakRestTemplate")
    public RestTemplate keycloakRestTemplate(KeycloakProperties keycloakProperties) {
        try {
            // Response timeout is essential: the password mirror runs inside user-facing
            // password flows, and a Keycloak that accepts TCP but never answers must not
            // pin the request thread (connect/read each bounded at 15s).
            HttpClientBuilder httpClientBuilder = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                    .setResponseTimeout(Timeout.ofSeconds(15))
                    .build());

            if (keycloakProperties.getSsl().isDisableVerification()) {
                logger.warn("SSL certificate verification is DISABLED for Keycloak communication. " +
                           "This should only be used in development/testing environments!");

                SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial((chain, authType) -> true)
                    .build();

                SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    (hostname, session) -> true
                );

                HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();

                httpClientBuilder.setConnectionManager(connectionManager);
            }

            HttpClient httpClient = httpClientBuilder.build();
            HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
            requestFactory.setConnectTimeout(15000);
            requestFactory.setConnectionRequestTimeout(15000);

            return new RestTemplate(requestFactory);
        } catch (Exception e) {
            logger.error("Failed to configure RestTemplate for Keycloak: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to configure Keycloak RestTemplate", e);
        }
    }
}
