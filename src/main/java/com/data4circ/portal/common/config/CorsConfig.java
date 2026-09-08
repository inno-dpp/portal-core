package com.data4circ.portal.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${app.cors.additional-origins:}")
    private String additionalOriginsCsv;

    private List<String> getAllowedOrigins() {
        List<String> origins = new ArrayList<>();

        //TODO  Review bellow list we do not need all of them

        // Same-origin requests (Spring Boot server)
        origins.add("http://localhost:" + serverPort);
        origins.add("http://127.0.0.1:" + serverPort);
        origins.add("https://localhost:" + serverPort);
        origins.add("https://127.0.0.1:" + serverPort);

        // Demo server port (9099)
        origins.add("http://localhost:9099");
        origins.add("http://127.0.0.1:9099");
        origins.add("https://localhost:9099");
        origins.add("https://127.0.0.1:9099");

        // Deployment-specific origins (e.g. the public portal host, so the join form
        // can call the API backend for submitting onboarding requests)
        for (String origin : additionalOriginsCsv.split(",")) {
            if (!origin.isBlank()) {
                origins.add(origin.trim());
            }
        }

        // External development origins
        origins.add("http://localhost:5500");
        origins.add("http://127.0.0.1:5500");
        origins.add("http://localhost:3000");
        origins.add("http://127.0.0.1:3000");

        return origins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(getAllowedOrigins().toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allow same-origin and external development origins
        configuration.setAllowedOrigins(getAllowedOrigins());

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        return source;
    }
}