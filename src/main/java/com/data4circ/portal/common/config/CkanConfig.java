package com.data4circ.portal.common.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Configuration for CKAN platform integration
 */
@Configuration
public class CkanConfig {

    /**
     * Configuration properties for CKAN platform
     */
    @ConfigurationProperties(prefix = "app.ckan")
    public static class CkanProperties {
        private String baseUrl;
        private String publicUrl;
        private String apiKey;
        private String jwtToken;
        private String organization;
        private Map<String, String> endpoints;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /** Browser-facing CKAN address, for outgoing links; falls back to base-url when unset. */
        public String getPublicUrl() {
            return publicUrl;
        }

        public void setPublicUrl(String publicUrl) {
            this.publicUrl = publicUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getJwtToken() {
            return jwtToken;
        }

        public void setJwtToken(String jwtToken) {
            this.jwtToken = jwtToken;
        }

        public String getOrganization() {
            return organization;
        }

        public void setOrganization(String organization) {
            this.organization = organization;
        }

        public Map<String, String> getEndpoints() {
            return endpoints;
        }

        public void setEndpoints(Map<String, String> endpoints) {
            this.endpoints = endpoints;
        }
    }

    /**
     * RestTemplate bean for CKAN API calls
     * Configured with timeouts for reliable HTTP communication
     *
     * @return configured RestTemplate for CKAN
     */
    @Bean(name = "ckanRestTemplate")
    public RestTemplate ckanRestTemplate() {
        // Do not reuse connections: CKAN dev servers (werkzeug) close them
        // between requests, and reusing a closed connection surfaces as
        // "failed to respond" I/O errors on subsequent calls.
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionReuseStrategy((request, response, context) -> false)
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(30000); // 30 seconds
        factory.setConnectionRequestTimeout(30000); // 30 seconds

        // Buffer request bodies so they are sent with a Content-Length header.
        // Without this, bodies go out with Transfer-Encoding: chunked, which
        // CKAN's built-in dev server (werkzeug) reads as an empty payload.
        return new RestTemplate(new BufferingClientHttpRequestFactory(factory));
    }

    /**
     * Bean for CKAN properties
     *
     * @return CKAN configuration properties
     */
    @Bean
    public CkanProperties ckanProperties() {
        return new CkanProperties();
    }
}
