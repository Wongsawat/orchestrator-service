package com.wpanther.orchestrator.infrastructure.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration properties for API key authentication settings.
 * <p>
 * Provides type-safe configuration for admin API keys used to secure
 * administrative endpoints. API keys can be provided as a comma-separated
 * string via the {@code orchestrator.admin.api-keys} property.
 * </p>
 * <p>
 * <b>Security Note:</b> In production, always set API keys via environment
 * variables and rotate them regularly. Never commit API keys to source control.
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "orchestrator.admin")
public class ApiKeyProperties {

    /**
     * List of valid API keys for admin access.
     * <p>
     * Maps to {@code orchestrator.admin.api-keys} in application.yml.
     * Can be provided as a comma-separated string, which will be split
     * into individual keys.
     * </p>
     */
    private List<String> apiKeys = new ArrayList<>();

    public List<String> getApiKeys() {
        return apiKeys;
    }

    /**
     * Sets the API keys from a comma-separated string or a list.
     * This allows both formats:
     * <ul>
     *   <li>Comma-separated: {@code orchestrator.admin.api-keys=key1,key2,key3}</li>
     *   <li>Array (YAML): {@code orchestrator.admin.api-keys: [key1, key2, key3]}</li>
     * </ul>
     *
     * @param value either a comma-separated string or a list of API keys
     */
    @SuppressWarnings("unchecked")
    public void setApiKeys(Object value) {
        if (value instanceof String) {
            // Handle comma-separated string format
            String keysString = (String) value;
            if (!keysString.isBlank()) {
                this.apiKeys = Arrays.stream(keysString.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            }
        } else if (value instanceof List) {
            // Handle list format (from YAML array)
            this.apiKeys = (List<String>) value;
        } else if (value != null) {
            throw new IllegalArgumentException(
                "api-keys must be a String (comma-separated) or List<String>, got: " + value.getClass()
            );
        }
    }
}
