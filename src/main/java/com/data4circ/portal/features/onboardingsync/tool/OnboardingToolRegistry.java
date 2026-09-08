package com.data4circ.portal.features.onboardingsync.tool;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registry of all {@link OnboardingToolProvisioner} beans combined with their
 * {@code app.onboarding.tools} configuration. Validates the tool graph at startup
 * and exposes the enabled tools in dependency order.
 */
@Component
public class OnboardingToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(OnboardingToolRegistry.class);

    private final Map<String, OnboardingToolProvisioner> toolsByKey = new LinkedHashMap<>();
    private final OnboardingToolsProperties properties;
    private List<OnboardingToolProvisioner> enabledToolsInOrder;

    public OnboardingToolRegistry(List<OnboardingToolProvisioner> provisioners,
                                  OnboardingToolsProperties properties) {
        this.properties = properties;
        for (OnboardingToolProvisioner provisioner : provisioners) {
            OnboardingToolProvisioner duplicate = toolsByKey.put(provisioner.getKey(), provisioner);
            if (duplicate != null) {
                throw new IllegalStateException("Duplicate onboarding tool key '" + provisioner.getKey()
                        + "' provided by " + duplicate.getClass().getName()
                        + " and " + provisioner.getClass().getName());
            }
        }
    }

    @PostConstruct
    void validateAndOrder() {
        for (OnboardingToolProvisioner tool : toolsByKey.values()) {
            if (!isEnabled(tool.getKey())) {
                continue;
            }
            for (String dependencyKey : tool.getDependencies()) {
                OnboardingToolProvisioner dependency = toolsByKey.get(dependencyKey);
                if (dependency == null) {
                    throw new IllegalStateException("Onboarding tool '" + tool.getKey()
                            + "' depends on unknown tool '" + dependencyKey + "'");
                }
                if (!isEnabled(dependencyKey)) {
                    throw new IllegalStateException("Onboarding tool '" + tool.getKey()
                            + "' is enabled but depends on disabled tool '" + dependencyKey
                            + "'. Enable '" + dependencyKey + "' or disable '" + tool.getKey()
                            + "' via app.onboarding.tools.*.enabled.");
                }
                if (isRequired(tool.getKey()) && !isRequired(dependencyKey)) {
                    logger.warn("Onboarding tool '{}' is required but depends on optional tool '{}' — "
                            + "the dependency becomes required in practice.", tool.getKey(), dependencyKey);
                }
            }
        }
        this.enabledToolsInOrder = sortByDependencies();
        logger.info("Onboarding tools active: {}", enabledToolsInOrder.stream()
                .map(t -> t.getKey() + (isRequired(t.getKey()) ? " (required)" : " (optional)"))
                .toList());
    }

    /** Enabled tools, dependencies first. */
    public List<OnboardingToolProvisioner> enabledTools() {
        return enabledToolsInOrder;
    }

    public Optional<OnboardingToolProvisioner> getEnabled(String key) {
        OnboardingToolProvisioner tool = toolsByKey.get(key);
        return tool != null && isEnabled(key) ? Optional.of(tool) : Optional.empty();
    }

    public Optional<OnboardingToolProvisioner> get(String key) {
        return Optional.ofNullable(toolsByKey.get(key));
    }

    /**
     * The enabled tool providing identity credentials (e.g. SPIP), if any. Looked up
     * by capability ({@link OnboardingToolProvisioner#providesIdentity()}) rather than
     * by tool key, so dependents (CKAN, Keycloak) never need a compile-time reference
     * to a specific provisioner's class.
     */
    public Optional<OnboardingToolProvisioner> identityProvider() {
        return findIdentityProvider(toolsByKey.values(), properties);
    }

    /**
     * Same lookup as {@link #identityProvider()}, usable without a live registry bean.
     *
     * <p>A dependent's {@link OnboardingToolProvisioner#getDependencies()} is called by
     * {@link #validateAndOrder()} itself while this registry is still being constructed
     * — a dependent can't ask the not-yet-built registry bean at that point without a
     * circular reference. Dependents that need the identity provider from
     * {@code getDependencies()} (CKAN, Keycloak) instead take a {@code @Lazy
     * List<OnboardingToolProvisioner>} (Spring excludes the requesting bean itself from
     * a same-typed collection injection) and call this static method directly — no bean
     * resolution involved. Once the registry exists, later calls (rendering input
     * fields, synchronizing) can use the instance method instead.</p>
     */
    public static Optional<OnboardingToolProvisioner> findIdentityProvider(
            Collection<OnboardingToolProvisioner> tools, OnboardingToolsProperties properties) {
        return tools.stream()
                .filter(tool -> properties.forKey(tool.getKey()).isEnabled() && tool.providesIdentity())
                .findFirst();
    }

    public boolean isEnabled(String key) {
        return properties.forKey(key).isEnabled();
    }

    public boolean isRequired(String key) {
        return isEnabled(key) && properties.forKey(key).isRequired();
    }

    private List<OnboardingToolProvisioner> sortByDependencies() {
        List<OnboardingToolProvisioner> ordered = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Set<String> visiting = new LinkedHashSet<>();
        for (OnboardingToolProvisioner tool : toolsByKey.values()) {
            if (isEnabled(tool.getKey())) {
                visit(tool, ordered, visited, visiting);
            }
        }
        return List.copyOf(ordered);
    }

    private void visit(OnboardingToolProvisioner tool, List<OnboardingToolProvisioner> ordered,
                       Set<String> visited, Set<String> visiting) {
        if (visited.contains(tool.getKey())) {
            return;
        }
        if (!visiting.add(tool.getKey())) {
            throw new IllegalStateException("Circular dependency between onboarding tools: " + visiting);
        }
        for (String dependencyKey : tool.getDependencies()) {
            visit(toolsByKey.get(dependencyKey), ordered, visited, visiting);
        }
        visiting.remove(tool.getKey());
        visited.add(tool.getKey());
        ordered.add(tool);
    }
}
