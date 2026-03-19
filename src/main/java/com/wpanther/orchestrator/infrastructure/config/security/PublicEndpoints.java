package com.wpanther.orchestrator.infrastructure.config.security;

/**
 * Constants for public endpoints that do not require authentication.
 * <p>
 * These endpoints are accessible without API key authentication and are used
 * for health checks, monitoring, and error handling.
 * <p>
 * Note: Changes to public endpoints must be reflected in both:
 * <ul>
 *   <li>{@link com.wpanther.orchestrator.infrastructure.adapter.in.security.ApiKeyAuthenticationFilter}</li>
 *   <li>{@link com.wpanther.orchestrator.infrastructure.config.security.SecurityConfig}</li>
 * </ul>
 */
public final class PublicEndpoints {

    private PublicEndpoints() {
        // Prevent instantiation
    }

    /**
     * Base path for actuator health endpoint.
     * Used by ApiKeyAuthenticationFilter for granular path matching.
     */
    public static final String ACTUATOR_HEALTH_BASE = "/actuator/health";

    /**
     * Actuator health endpoint for health checks (with wildcard pattern).
     * Used by SecurityConfig for requestMatchers.
     */
    public static final String ACTUATOR_HEALTH = "/actuator/health/**";

    /**
     * Base path for actuator info endpoint.
     * Used by ApiKeyAuthenticationFilter for granular path matching.
     */
    public static final String ACTUATOR_INFO_BASE = "/actuator/info";

    /**
     * Actuator info endpoint for application information (with wildcard pattern).
     * Used by SecurityConfig for requestMatchers.
     */
    public static final String ACTUATOR_INFO = "/actuator/info/**";

    /**
     * Custom health endpoint for saga service health.
     */
    public static final String SAGA_HEALTH = "/api/saga/health";

    /**
     * Spring Boot error endpoint.
     */
    public static final String ERROR = "/error";

    /**
     * Array of all public endpoint paths for use in SecurityConfig requestMatchers.
     */
    public static final String[] ALL = {
            ACTUATOR_HEALTH,
            ACTUATOR_INFO,
            SAGA_HEALTH,
            ERROR
    };
}
