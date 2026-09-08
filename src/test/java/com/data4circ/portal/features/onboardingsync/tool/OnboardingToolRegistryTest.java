package com.data4circ.portal.features.onboardingsync.tool;

import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the onboarding tool registry: configuration defaults,
 * enable/disable semantics, dependency validation and ordering.
 */
class OnboardingToolRegistryTest {

    private static class StubTool implements OnboardingToolProvisioner {
        private final String key;
        private final List<String> dependencies;

        StubTool(String key, String... dependencies) {
            this.key = key;
            this.dependencies = List.of(dependencies);
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public String getDisplayName() {
            return key.toUpperCase();
        }

        @Override
        public List<String> getDependencies() {
            return dependencies;
        }

        @Override
        public List<OnboardingToolInputField> describeInputs(OrganizationOnboardingRequest request) {
            return List.of(new OnboardingToolInputField(key, key, null, null, null, null, null));
        }

        @Override
        public OnboardingToolSyncOutcome synchronize(OrganizationOnboardingRequest request,
                                                     Map<String, String> params) {
            return OnboardingToolSyncOutcome.empty();
        }
    }

    private OnboardingToolsProperties properties(Map<String, OnboardingToolsProperties.ToolProperties> tools) {
        OnboardingToolsProperties properties = new OnboardingToolsProperties();
        properties.setTools(new java.util.HashMap<>(tools));
        return properties;
    }

    private OnboardingToolsProperties.ToolProperties tool(boolean enabled, boolean required) {
        OnboardingToolsProperties.ToolProperties props = new OnboardingToolsProperties.ToolProperties();
        props.setEnabled(enabled);
        props.setRequired(required);
        return props;
    }

    @Test
    void absentConfigurationDefaultsToEnabledAndRequired() {
        OnboardingToolRegistry registry = new OnboardingToolRegistry(
                List.of(new StubTool("a"), new StubTool("b", "a")), properties(Map.of()));
        registry.validateAndOrder();

        assertThat(registry.isEnabled("a")).isTrue();
        assertThat(registry.isRequired("a")).isTrue();
        assertThat(registry.enabledTools()).hasSize(2);
    }

    @Test
    void enabledToolsAreOrderedDependenciesFirst() {
        OnboardingToolRegistry registry = new OnboardingToolRegistry(
                List.of(new StubTool("b", "a"), new StubTool("a")), properties(Map.of()));
        registry.validateAndOrder();

        assertThat(registry.enabledTools())
                .extracting(OnboardingToolProvisioner::getKey)
                .containsExactly("a", "b");
    }

    @Test
    void disabledToolIsExcludedAndNotRequired() {
        OnboardingToolRegistry registry = new OnboardingToolRegistry(
                List.of(new StubTool("a"), new StubTool("b")),
                properties(Map.of("b", tool(false, true))));
        registry.validateAndOrder();

        assertThat(registry.enabledTools())
                .extracting(OnboardingToolProvisioner::getKey)
                .containsExactly("a");
        assertThat(registry.getEnabled("b")).isEmpty();
        assertThat(registry.isRequired("b")).isFalse();
    }

    @Test
    void optionalToolStaysEnabled() {
        OnboardingToolRegistry registry = new OnboardingToolRegistry(
                List.of(new StubTool("a")), properties(Map.of("a", tool(true, false))));
        registry.validateAndOrder();

        assertThat(registry.getEnabled("a")).isPresent();
        assertThat(registry.isRequired("a")).isFalse();
    }

    @Test
    void enabledToolDependingOnDisabledToolFailsStartup() {
        OnboardingToolRegistry registry = new OnboardingToolRegistry(
                List.of(new StubTool("a"), new StubTool("b", "a")),
                properties(Map.of("a", tool(false, true))));

        assertThatThrownBy(registry::validateAndOrder)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("depends on disabled tool 'a'");
    }

    @Test
    void enabledToolDependingOnUnknownToolFailsStartup() {
        OnboardingToolRegistry registry = new OnboardingToolRegistry(
                List.of(new StubTool("b", "missing")), properties(Map.of()));

        assertThatThrownBy(registry::validateAndOrder)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown tool 'missing'");
    }

    @Test
    void duplicateToolKeysFailStartup() {
        assertThatThrownBy(() -> new OnboardingToolRegistry(
                List.of(new StubTool("a"), new StubTool("a")), properties(Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate onboarding tool key");
    }
}
