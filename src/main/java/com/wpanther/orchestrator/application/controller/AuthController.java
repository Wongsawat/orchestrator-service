package com.wpanther.orchestrator.application.controller;

import com.wpanther.orchestrator.infrastructure.security.JwtTokenProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for authentication.
 * Provides login endpoint that returns a JWT token.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    /**
     * Login endpoint that authenticates the user and returns a JWT token.
     *
     * @param request The login request containing username and password
     * @return Response containing the JWT token
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsername());

        try {
            // Authenticate the user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // If authentication successful, generate JWT token
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            Map<String, Object> claims = new HashMap<>();
            claims.put("username", userDetails.getUsername());
            claims.put("authorities", userDetails.getAuthorities());
            claims.put("loginTime", Instant.now().toString());

            String token = jwtTokenProvider.generateToken(userDetails.getUsername(), claims);

            log.info("User {} logged in successfully", request.getUsername());

            return ResponseEntity.ok(AuthResponse.builder()
                    .token(token)
                    .type("Bearer")
                    .username(userDetails.getUsername())
                    .expiresIn(jwtTokenProvider.getExpirationDateFromToken(token))
                    .build());

        } catch (AuthenticationException e) {
            log.warn("Authentication failed for user {}: {}", request.getUsername(), e.getMessage());
            return ResponseEntity.status(401)
                    .body(ErrorResponse.builder()
                            .error("authentication_failed")
                            .message("Invalid username or password")
                            .timestamp(Instant.now())
                            .build());
        }
    }

    /**
     * Validates a JWT token and returns the user information.
     *
     * @param authorizationHeader The Authorization header containing the JWT token
     * @return Response containing the user information
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.builder()
                            .error("invalid_token")
                            .message("Authorization header must be in format: Bearer <token>")
                            .timestamp(Instant.now())
                            .build());
        }

        String token = authorizationHeader.substring(7);

        try {
            String username = jwtTokenProvider.getSubjectFromToken(token);

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            return ResponseEntity.ok(ValidationResponse.builder()
                    .valid(true)
                    .username(userDetails.getUsername())
                    .authorities(userDetails.getAuthorities())
                    .expiresAt(jwtTokenProvider.getExpirationDateFromToken(token))
                    .build());

        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return ResponseEntity.ok(ValidationResponse.builder()
                    .valid(false)
                    .error(e.getMessage())
                    .build());
        }
    }

    /**
     * Login request DTO.
     */
    @Data
    public static class LoginRequest {
        @NotBlank(message = "Username is required")
        private String username;

        @NotBlank(message = "Password is required")
        private String password;
    }

    /**
     * Authentication response DTO.
     */
    @Data
    @AllArgsConstructor
    public static class AuthResponse {
        private String token;
        private String type;
        private String username;
        private java.util.Date expiresIn;

        public static AuthResponseBuilder builder() {
            return new AuthResponseBuilder();
        }

        public static class AuthResponseBuilder {
            private String token;
            private String type;
            private String username;
            private java.util.Date expiresIn;

            public AuthResponseBuilder token(String token) {
                this.token = token;
                return this;
            }

            public AuthResponseBuilder type(String type) {
                this.type = type;
                return this;
            }

            public AuthResponseBuilder username(String username) {
                this.username = username;
                return this;
            }

            public AuthResponseBuilder expiresIn(java.util.Date expiresIn) {
                this.expiresIn = expiresIn;
                return this;
            }

            public AuthResponse build() {
                return new AuthResponse(token, type, username, expiresIn);
            }
        }
    }

    /**
     * Token validation response DTO.
     */
    @Data
    @AllArgsConstructor
    public static class ValidationResponse {
        private boolean valid;
        private String username;
        private java.util.List<String> authorities;
        private java.util.Date expiresAt;
        private String error;

        public static ValidationResponseBuilder builder() {
            return new ValidationResponseBuilder();
        }

        public static class ValidationResponseBuilder {
            private boolean valid;
            private String username;
            private java.util.List<String> authorities;
            private java.util.Date expiresAt;
            private String error;

            public ValidationResponseBuilder valid(boolean valid) {
                this.valid = valid;
                return this;
            }

            public ValidationResponseBuilder username(String username) {
                this.username = username;
                return this;
            }

            public ValidationResponseBuilder authorities(java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities) {
                this.authorities = authorities.stream()
                        .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                        .toList();
                return this;
            }

            public ValidationResponseBuilder expiresAt(java.util.Date expiresAt) {
                this.expiresAt = expiresAt;
                return this;
            }

            public ValidationResponseBuilder error(String error) {
                this.error = error;
                return this;
            }

            public ValidationResponse build() {
                return new ValidationResponse(valid, username, authorities, expiresAt, error);
            }
        }
    }

    /**
     * Error response DTO.
     */
    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
        private Instant timestamp;

        public static ErrorResponseBuilder builder() {
            return new ErrorResponseBuilder();
        }

        public static class ErrorResponseBuilder {
            private String error;
            private String message;
            private Instant timestamp;

            public ErrorResponseBuilder error(String error) {
                this.error = error;
                return this;
            }

            public ErrorResponseBuilder message(String message) {
                this.message = message;
                return this;
            }

            public ErrorResponseBuilder timestamp(Instant timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public ErrorResponse build() {
                return new ErrorResponse(error, message, timestamp);
            }
        }
    }
}
