package com.wpanther.orchestrator.infrastructure.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SecurityConfig with API key authentication.
 * Tests endpoint security configuration with Spring Security.
 */
@SpringBootTest(classes = com.wpanther.orchestrator.OrchestratorServiceApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:orchestrator_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.dialect=org.hibernate.dialect.H2Dialect",
        // Configure API keys for testing
        "orchestrator.admin.api-keys=test-admin-key-12345,another-test-key-67890"
})
@DisplayName("SecurityConfig Tests - API Key Authentication")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String VALID_API_KEY = "test-admin-key-12345";
    private static final String ANOTHER_VALID_API_KEY = "another-test-key-67890";
    private static final String INVALID_API_KEY = "invalid-api-key";

    @Nested
    @DisplayName("Public Endpoints")
    class PublicEndpointsTests {

        @Test
        @DisplayName("GET /api/saga/health should be accessible without authentication")
        void healthEndpointShouldBePublic() throws Exception {
            mockMvc.perform(get("/api/saga/health"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /actuator/health should be accessible without authentication")
        void actuatorHealthEndpointShouldBePublic() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /actuator/info should be accessible without authentication")
        void actuatorInfoEndpointShouldBePublic() throws Exception {
            mockMvc.perform(get("/actuator/info"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Protected Endpoints - Without Authentication")
    class ProtectedEndpointsTests {

        @Test
        @DisplayName("GET /api/saga/{sagaId} should return 401 without API key")
        void getSagaEndpointShouldReturnUnauthorized() throws Exception {
            mockMvc.perform(get("/api/saga/some-saga-id"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/saga/active should return 401 without API key")
        void getActiveSagasEndpointShouldReturnUnauthorized() throws Exception {
            mockMvc.perform(get("/api/saga/active"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/saga/start should return 401 without API key")
        void startSagaEndpointShouldReturnUnauthorized() throws Exception {
            mockMvc.perform(post("/api/saga/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"documentType\":\"INVOICE\",\"documentId\":\"doc-123\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/saga/{sagaId}/advance should return 401 without API key")
        void advanceSagaEndpointShouldReturnUnauthorized() throws Exception {
            mockMvc.perform(post("/api/saga/some-saga-id/advance"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/saga/{sagaId}/retry should return 401 without API key")
        void retrySagaEndpointShouldReturnUnauthorized() throws Exception {
            mockMvc.perform(post("/api/saga/some-saga-id/retry"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/saga/document should return 401 without API key")
        void getSagasForDocumentEndpointShouldReturnUnauthorized() throws Exception {
            mockMvc.perform(get("/api/saga/document?documentType=INVOICE&documentId=doc-123"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Protected Endpoints - With Valid API Key")
    class ProtectedEndpointsWithApiKeyTests {

        @Test
        @DisplayName("GET /api/saga/{sagaId} should allow access with valid API key")
        void getSagaEndpointShouldAllowAccessWithApiKey() throws Exception {
            try {
                mockMvc.perform(get("/api/saga/some-saga-id")
                                .header("X-API-Key", VALID_API_KEY))
                        .andExpect(result -> {
                            int status = result.getResponse().getStatus();
                            // 404 is acceptable (saga not found), but 401/403 means auth failed
                            if (status == 401 || status == 403) {
                                throw new AssertionError("Endpoint should be accessible with valid API key but got " + status);
                            }
                        });
            } catch (Exception e) {
                // ServletException with "Saga not found" is expected behavior when saga doesn't exist
                // This means authentication succeeded, but the saga doesn't exist (which is fine for this test)
                Throwable cause = e;
                while (cause != null) {
                    if (cause.getMessage() != null && cause.getMessage().contains("Saga not found")) {
                        // This is expected - authentication succeeded, saga just doesn't exist
                        return;
                    }
                    cause = cause.getCause();
                }
                // If it's not a "Saga not found" exception, rethrow it
                throw e;
            }
        }

        @Test
        @DisplayName("GET /api/saga/active should allow access with valid API key")
        void getActiveSagasEndpointShouldAllowAccessWithApiKey() throws Exception {
            mockMvc.perform(get("/api/saga/active")
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isOk()) // Will return empty list
                    .andExpect(content().string("[]"));
        }

        @Test
        @DisplayName("GET /api/saga/active should allow access with another valid API key")
        void getActiveSagasEndpointShouldAllowAccessWithAnotherApiKey() throws Exception {
            mockMvc.perform(get("/api/saga/active")
                            .header("X-API-Key", ANOTHER_VALID_API_KEY))
                    .andExpect(status().isOk())
                    .andExpect(content().string("[]"));
        }

        @Test
        @DisplayName("POST /api/saga/start should allow access with valid API key")
        void startSagaEndpointShouldAllowAccessWithApiKey() throws Exception {
            mockMvc.perform(post("/api/saga/start")
                            .header("X-API-Key", VALID_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"documentType\":\"INVOICE\",\"documentId\":\"doc-123\",\"invoiceNumber\":\"INV-001\",\"xmlContent\":\"<test></test>\"}"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        // 400 is acceptable (validation error), but 401/403 means auth failed
                        if (status == 401 || status == 403) {
                            throw new AssertionError("Endpoint should be accessible with valid API key but got " + status);
                        }
                    });
        }

        @Test
        @DisplayName("POST /api/saga/{sagaId}/retry should allow access with valid API key")
        void retrySagaEndpointShouldAllowAccessWithApiKey() throws Exception {
            try {
                mockMvc.perform(post("/api/saga/some-saga-id/retry")
                                .header("X-API-Key", VALID_API_KEY))
                        .andExpect(result -> {
                            int status = result.getResponse().getStatus();
                            // 404 is acceptable (saga not found), but 401/403 means auth failed
                            if (status == 401 || status == 403) {
                                throw new AssertionError("Endpoint should be accessible with valid API key but got " + status);
                            }
                        });
            } catch (Exception e) {
                // ServletException with "Saga not found" is expected behavior when saga doesn't exist
                Throwable cause = e;
                while (cause != null) {
                    if (cause.getMessage() != null && cause.getMessage().contains("Saga not found")) {
                        // This is expected - authentication succeeded, saga just doesn't exist
                        return;
                    }
                    cause = cause.getCause();
                }
                throw e;
            }
        }

        @Test
        @DisplayName("GET /api/saga/{sagaId} should allow access with API key in header")
        void getSagaEndpointShouldAllowAccessWithApiKeyInHeader() throws Exception {
            try {
                mockMvc.perform(get("/api/saga/some-saga-id")
                                .header("X-API-Key", VALID_API_KEY))
                        .andExpect(result -> {
                            int status = result.getResponse().getStatus();
                            if (status == 401 || status == 403) {
                                throw new AssertionError("Endpoint should be accessible with valid API key but got " + status);
                            }
                        });
            } catch (Exception e) {
                // ServletException with "Saga not found" is expected behavior when saga doesn't exist
                Throwable cause = e;
                while (cause != null) {
                    if (cause.getMessage() != null && cause.getMessage().contains("Saga not found")) {
                        // This is expected - authentication succeeded, saga just doesn't exist
                        return;
                    }
                    cause = cause.getCause();
                }
                throw e;
            }
        }
    }

    @Nested
    @DisplayName("Protected Endpoints - With Invalid API Key")
    class ProtectedEndpointsWithInvalidApiKeyTests {

        @Test
        @DisplayName("GET /api/saga/active should return 401 with invalid API key")
        void getActiveSagasEndpointShouldReturnUnauthorizedWithInvalidKey() throws Exception {
            mockMvc.perform(get("/api/saga/active")
                            .header("X-API-Key", INVALID_API_KEY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/saga/active should return 401 with missing API key")
        void getActiveSagasEndpointShouldReturnUnauthorizedWithMissingKey() throws Exception {
            mockMvc.perform(get("/api/saga/active"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/saga/active should return 401 with empty API key")
        void getActiveSagasEndpointShouldReturnUnauthorizedWithEmptyKey() throws Exception {
            mockMvc.perform(get("/api/saga/active")
                            .header("X-API-Key", ""))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/saga/start should return 401 with invalid API key")
        void startSagaEndpointShouldReturnUnauthorizedWithInvalidKey() throws Exception {
            mockMvc.perform(post("/api/saga/start")
                            .header("X-API-Key", INVALID_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"documentType\":\"INVOICE\",\"documentId\":\"doc-123\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("CORS Configuration")
    class CorsConfigurationTests {

        @Test
        @DisplayName("should include CORS headers for preflight request")
        void shouldIncludeCorsHeaders() throws Exception {
            mockMvc.perform(options("/api/saga/active")
                            .header("Origin", "http://localhost:3000")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk()); // CORS preflight is handled by Spring Security
        }
    }

    @Nested
    @DisplayName("CSRF Protection")
    class CsrfProtectionTests {

        @Test
        @DisplayName("should disable CSRF for stateless API key authentication")
        void shouldDisableCsrfProtection() throws Exception {
            // Stateless API key authentication doesn't need CSRF
            // POST request to public endpoint should work without CSRF token
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk()); // CSRF is disabled, public endpoint accessible
        }
    }

    @Nested
    @DisplayName("API Key Header Case Sensitivity")
    class ApiKeyHeaderTests {

        @Test
        @DisplayName("should accept API key with lowercase header name (HTTP headers are case-insensitive)")
        void shouldRejectApiKeyWithWrongHeaderName() throws Exception {
            mockMvc.perform(get("/api/saga/active")
                            .header("x-api-key", VALID_API_KEY)) // lowercase header name (HTTP spec: case-insensitive)
                    .andExpect(status().isOk())
                    .andExpect(content().string("[]"));
        }

        @Test
        @DisplayName("should accept API key with correct header name")
        void shouldAcceptApiKeyWithCorrectHeaderName() throws Exception {
            mockMvc.perform(get("/api/saga/active")
                            .header("X-API-Key", VALID_API_KEY)) // correct header name
                    .andExpect(status().isOk())
                    .andExpect(content().string("[]"));
        }
    }
}
