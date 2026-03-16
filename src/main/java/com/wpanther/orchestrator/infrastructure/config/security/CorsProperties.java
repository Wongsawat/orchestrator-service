package com.wpanther.orchestrator.infrastructure.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration properties for CORS (Cross-Origin Resource Sharing) settings.
 * Provides type-safe configuration for allowed origins and related CORS behavior.
 */
@Component
@ConfigurationProperties(prefix = "app.security.cors")
public class CorsProperties {

    /**
     * Allowed CORS origins.
     * Maps to app.security.cors.allowed-origins in application.yml.
     */
    private List<String> allowedOrigins = List.of("http://localhost:3000", "http://localhost:8080");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
