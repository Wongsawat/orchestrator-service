package com.wpanther.orchestrator.infrastructure.adapter.in.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.Collections;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * API Key authentication filter for admin endpoints.
 * <p>
 * Validates API keys from the X-API-Key header against configured valid keys.
 * This provides simple, stateless authentication suitable for:
 * <ul>
 *   <li>Admin monitoring dashboards</li>
 *   <li>Operational tools</li>
 *   <li>API testing</li>
 *   <li>External system integrations</li>
 * </ul>
 * <p>
 * API keys are configured via the {@code orchestrator.admin.api-keys} property
 * or the {@code ORCHESTRATOR_API_KEYS} environment variable (comma-separated).
 * <p>
 * <b>Security Note:</b> In production, always set API keys via environment variables
 * and rotate them regularly. Never commit API keys to source control.
 */
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    /**
     * List of valid API keys.
     * Loaded from orchestrator.admin.api-keys property or ORCHESTRATOR_API_KEYS env var.
     */
    private final List<String> validApiKeys;

    /**
     * Creates a new API key authentication filter.
     * Reads API keys from the Spring Environment.
     *
     * @param environment the Spring environment for reading properties
     */
    public ApiKeyAuthenticationFilter(Environment environment) {
        String apiKeys = environment.getProperty("orchestrator.admin.api-keys", "");
        if (apiKeys != null && !apiKeys.isBlank()) {
            this.validApiKeys = Arrays.stream(apiKeys.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            log.info("Loaded {} valid API key(s) for admin access", this.validApiKeys.size());
        } else {
            // Development default - should be overridden in production
            this.validApiKeys = List.of("dev-admin-key-12345");
            log.warn("No API keys configured - using development default key! " +
                     "Set orchestrator.admin.api-keys or ORCHESTRATOR_API_KEYS environment variable in production.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip authentication for public endpoints
        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Allow CORS preflight requests without authentication
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check for API key
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (isValidApiKey(apiKey)) {
            // Create authentication with API_USER role (matching controller's @PreAuthorize requirement)
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "api-user",
                    null,
                    AuthorityUtils.createAuthorityList("ROLE_API_USER")
            );
            authentication.setDetails(new WebAuthenticationDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            if (log.isDebugEnabled()) {
                log.debug("API key authentication successful for {} from {}",
                        path, request.getRemoteAddr());
            }

            filterChain.doFilter(request, response);
        } else {
            // Invalid or missing API key
            log.warn("Unauthorized access attempt from {} to {} (API key: {})",
                    request.getRemoteAddr(), path, maskApiKey(apiKey));

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Valid API key required in X-API-Key header\"}");
        }
    }

    /**
     * Checks if the given API key is valid.
     *
     * @param apiKey the API key to validate (may be null)
     * @return true if the API key is valid, false otherwise
     */
    private boolean isValidApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        return validApiKeys.contains(apiKey.trim());
    }

    /**
     * Determines if the given path is a public endpoint that doesn't require authentication.
     *
     * @param path the request path
     * @return true if the path is public, false otherwise
     */
    private boolean isPublicEndpoint(String path) {
        return path.equals("/actuator/health") ||
               path.equals("/actuator/health/") ||
               path.startsWith("/actuator/health/") ||
               path.equals("/actuator/info") ||
               path.equals("/actuator/info/") ||
               path.startsWith("/actuator/info/") ||
               path.equals("/api/saga/health") ||
               path.equals("/error");
    }

    /**
     * Masks an API key for logging purposes (shows first 4 and last 4 characters).
     *
     * @param apiKey the API key to mask
     * @return the masked API key, or "null" if the key is null
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null) {
            return "null";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
