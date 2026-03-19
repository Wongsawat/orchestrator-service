package com.wpanther.orchestrator.infrastructure.config.security;

import com.wpanther.orchestrator.infrastructure.adapter.in.security.ApiKeyAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * Security configuration for the orchestrator service.
 * <p>
 * Configures API key-based authentication for REST endpoints.
 * <p>
 * Public endpoints (actuator, health) are accessible without authentication.
 * All other endpoints require a valid API key in the {@code X-API-Key} header.
 * <p>
 * API keys are configured via {@code orchestrator.admin.api-keys} property or
 * {@code ORCHESTRATOR_API_KEYS} environment variable (comma-separated).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties
public class SecurityConfig {

    private final CorsProperties corsProperties;
    private final ApiKeyProperties apiKeyProperties;

    public SecurityConfig(CorsProperties corsProperties, ApiKeyProperties apiKeyProperties) {
        this.corsProperties = corsProperties;
        this.apiKeyProperties = apiKeyProperties;
    }

    /**
     * API key authentication filter bean.
     */
    @Bean
    public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter() {
        return new ApiKeyAuthenticationFilter(apiKeyProperties);
    }

    /**
     * Configures the security filter chain.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        ApiKeyAuthenticationFilter apiKeyFilter = apiKeyAuthenticationFilter();

        http
                // Disable CSRF (not needed for stateless API key authentication)
                .csrf(AbstractHttpConfigurer::disable)

                // Disable HTTP Basic - we only use API key authentication
                .httpBasic(AbstractHttpConfigurer::disable)

                // Disable form login - we only use API key authentication
                .formLogin(AbstractHttpConfigurer::disable)

                // Disable logout - stateless API doesn't need session management
                .logout(AbstractHttpConfigurer::disable)

                // Configure CORS
                .cors(cors -> cors.configurationSource(request -> {
                    var corsConfig = new org.springframework.web.cors.CorsConfiguration();
                    corsConfig.setAllowedOrigins(corsProperties.getAllowedOrigins());
                    corsConfig.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    corsConfig.setAllowedHeaders(java.util.List.of("*"));
                    corsConfig.setAllowCredentials(true);
                    corsConfig.setMaxAge(3600L);
                    return corsConfig;
                }))

                // Configure session management (stateless)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Configure authorization rules - use requestMatchers for path-based authorization
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - use constants from PublicEndpoints class
                        .requestMatchers(PublicEndpoints.ALL).permitAll()

                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )

                // Configure exception handling to return 401 (not 403) for missing authentication
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Valid API key required in X-API-Key header\"}");
                        })
                )

                // Add API key filter AFTER SecurityContextHolderFilter but BEFORE UsernamePasswordAuthenticationFilter
                // This ensures the security context is properly initialized before we set authentication
                .addFilterAfter(apiKeyFilter, org.springframework.security.web.context.SecurityContextHolderFilter.class);

        return http.build();
    }
}
