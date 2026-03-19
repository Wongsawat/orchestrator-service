package com.wpanther.orchestrator.infrastructure.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for API key authentication settings.
 * <p>
 * Provides type-safe configuration for admin API keys used to secure
 * administrative endpoints. API keys can be provided as a comma-separated
 * list via the {@code orchestrator.admin.api-keys} property.
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

    public void setApiKeys(List<String> apiKeys) {
        this.apiKeys = apiKeys;
    }
}
