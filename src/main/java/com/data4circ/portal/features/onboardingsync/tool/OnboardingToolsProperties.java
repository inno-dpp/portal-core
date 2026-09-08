package com.data4circ.portal.features.onboardingsync.tool;

import com.data4circ.portal.features.connectors.enums.ConnectorStatus;
import com.data4circ.portal.features.connectors.enums.ConnectorType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-tool onboarding configuration under {@code app.onboarding.tools.<key>}.
 * A tool absent from the configuration defaults to enabled and required with no
 * connector template, so zero-config deployments keep the historical behavior.
 */
@Component
@ConfigurationProperties(prefix = "app.onboarding")
public class OnboardingToolsProperties {

    private Map<String, ToolProperties> tools = new HashMap<>();

    public Map<String, ToolProperties> getTools() {
        return tools;
    }

    public void setTools(Map<String, ToolProperties> tools) {
        this.tools = tools;
    }

    /** Never returns null; unknown keys resolve to the defaults (enabled + required). */
    public ToolProperties forKey(String key) {
        ToolProperties properties = tools.get(key);
        return properties != null ? properties : new ToolProperties();
    }

    public static class ToolProperties {

        /** When false the tool is hidden, not runnable, and ignored by approval. */
        private boolean enabled = true;

        /** When false the tool can still be synchronized but approval does not gate on it. */
        private boolean required = true;

        /**
         * Optional template for the connector ("My Tools" entry) materialized for the
         * organization once the tool is synchronized and the request approved.
         */
        private ConnectorTemplateProperties connector;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public ConnectorTemplateProperties getConnector() {
            return connector;
        }

        public void setConnector(ConnectorTemplateProperties connector) {
            this.connector = connector;
        }
    }

    /** Template for the connector materialized in the organization's "My Tools" section. */
    public static class ConnectorTemplateProperties {
        private boolean enabled = true;
        private String name;
        private String description;
        private ConnectorType type = ConnectorType.PLATFORM_DIGITAL_TOOL;
        private String endpoint;
        private String configuration;
        private ConnectorStatus status = ConnectorStatus.OFFLINE;
        private String healthEndpoint;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public ConnectorType getType() {
            return type;
        }

        public void setType(ConnectorType type) {
            this.type = type;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getConfiguration() {
            return configuration;
        }

        public void setConfiguration(String configuration) {
            this.configuration = configuration;
        }

        public ConnectorStatus getStatus() {
            return status;
        }

        public void setStatus(ConnectorStatus status) {
            this.status = status;
        }

        public String getHealthEndpoint() {
            return healthEndpoint;
        }

        public void setHealthEndpoint(String healthEndpoint) {
            this.healthEndpoint = healthEndpoint;
        }
    }
}
