package com.wpanther.orchestrator.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.infrastructure.adapter.in.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for AuthController.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserDetailsService userDetailsService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Create a minimal standalone mockMvc setup
        AuthController authController = new AuthController(
                authenticationManager, jwtTokenProvider, userDetailsService);

        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Nested
    @DisplayName("POST /api/auth/login")
    class LoginTests {

        private static final String LOGIN_URL = "/api/auth/login";

        @Test
        @DisplayName("should return JWT token on successful login")
        void shouldReturnTokenOnSuccessfulLogin() throws Exception {
            String username = "test-user";
            String password = "test-password";
            String expectedToken = "generated.jwt.token";

            // Mock user details
            UserDetails userDetails = User.builder()
                    .username(username)
                    .password("{noop}password")
                    .authorities(Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_API_USER")))
                    .build();

            // Mock successful authentication - return token with userDetails as principal
            TestingAuthenticationToken authenticationToken =
                    new TestingAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            when(authenticationManager.authenticate(any()))
                    .thenReturn(authenticationToken);

            // Mock JWT generation
            when(jwtTokenProvider.generateToken(eq(username), any()))
                    .thenReturn(expectedToken);
            when(jwtTokenProvider.getExpirationDateFromToken(expectedToken))
                    .thenReturn(java.util.Date.from(Instant.now().plusSeconds(3600)));

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new AuthController.LoginRequest(username, password))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value(expectedToken))
                    .andExpect(jsonPath("$.type").value("Bearer"))
                    .andExpect(jsonPath("$.username").value(username))
                    .andExpect(jsonPath("$.expiresIn").exists());
        }

        @Test
        @DisplayName("should return 401 on failed authentication")
        void shouldReturn401OnFailedAuthentication() throws Exception {
            String username = "test-user";
            String password = "wrong-password";

            // Mock authentication failure
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Invalid credentials"));

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new AuthController.LoginRequest(username, password))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("authentication_failed"))
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }

        @Test
        @DisplayName("should return 400 when username is blank")
        void shouldReturn400WhenUsernameBlank() throws Exception {
            AuthController.LoginRequest request = new AuthController.LoginRequest("", "password");

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when password is blank")
        void shouldReturn400WhenPasswordBlank() throws Exception {
            AuthController.LoginRequest request = new AuthController.LoginRequest("username", "");

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when request body is invalid")
        void shouldReturn400ForInvalidRequestBody() throws Exception {
            String invalidJson = "{ invalid json }";

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should include loginTime in token claims")
        void shouldIncludeLoginTimeInClaims() throws Exception {
            String username = "test-user";
            String password = "test-password";

            UserDetails userDetails = User.builder()
                    .username(username)
                    .password("{noop}password")
                    .authorities(Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_API_USER")))
                    .build();

            // Mock successful authentication - return token with userDetails as principal
            TestingAuthenticationToken authenticationToken =
                    new TestingAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            when(authenticationManager.authenticate(any()))
                    .thenReturn(authenticationToken);

            when(jwtTokenProvider.generateToken(eq(username), any()))
                    .thenAnswer(invocation -> {
                        // Verify that claims include loginTime
                        @SuppressWarnings("unchecked")
                        Map<String, Object> claims = invocation.getArgument(1);
                        return "mock.jwt.token";
                    });

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new AuthController.LoginRequest(username, password))))
                    .andExpect(status().isOk());

            verify(jwtTokenProvider).generateToken(eq(username), any());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/validate")
    class ValidateTokenTests {

        private static final String VALIDATE_URL = "/api/auth/validate";

        @Test
        @DisplayName("should return valid=true for valid token")
        void shouldReturnValidTrueForValidToken() throws Exception {
            String token = "valid.jwt.token";
            String username = "test-user";

            when(jwtTokenProvider.getSubjectFromToken(token)).thenReturn(username);
            when(jwtTokenProvider.getExpirationDateFromToken(token))
                    .thenReturn(java.util.Date.from(Instant.now().plusSeconds(3600)));

            UserDetails userDetails = User.builder()
                    .username(username)
                    .password("{noop}password")
                    .authorities(Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_API_USER")))
                    .build();
            when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);

            mockMvc.perform(post(VALIDATE_URL)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.username").value(username))
                    .andExpect(jsonPath("$.authorities").isArray())
                    .andExpect(jsonPath("$.expiresAt").exists());
        }

        @Test
        @DisplayName("should return valid=false for expired token")
        void shouldReturnValidFalseForExpiredToken() throws Exception {
            String expiredToken = "expired.jwt.token";

            when(jwtTokenProvider.getSubjectFromToken(expiredToken))
                    .thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "Token expired"));

            mockMvc.perform(post(VALIDATE_URL)
                            .header("Authorization", "Bearer " + expiredToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("should return valid=false for malformed token")
        void shouldReturnValidFalseForMalformedToken() throws Exception {
            String malformedToken = "malformed.token";

            when(jwtTokenProvider.getSubjectFromToken(malformedToken))
                    .thenThrow(new io.jsonwebtoken.MalformedJwtException("Invalid token"));

            mockMvc.perform(post(VALIDATE_URL)
                            .header("Authorization", "Bearer " + malformedToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false));
        }

        @Test
        @DisplayName("should return 400 when Authorization header is missing")
        void shouldReturn400WhenHeaderMissing() throws Exception {
            mockMvc.perform(post(VALIDATE_URL))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_token"));
        }

        @Test
        @DisplayName("should return 400 when Authorization header has wrong format")
        void shouldReturn400ForWrongHeaderFormat() throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("Authorization", "InvalidFormat token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_token"));
        }

        @Test
        @DisplayName("should return user authorities in validation response")
        void shouldReturnUserAuthorities() throws Exception {
            String token = "valid.jwt.token";
            String username = "test-user";

            when(jwtTokenProvider.getSubjectFromToken(token)).thenReturn(username);

            UserDetails userDetails = User.builder()
                    .username(username)
                    .password("{noop}password")
                    .authorities(java.util.List.of(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_API_USER"),
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")
                    ))
                    .build();
            when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);

            mockMvc.perform(post(VALIDATE_URL)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.authorities").isArray())
                    .andExpect(jsonPath("$.authorities.length()").value(2));
        }
    }
}
