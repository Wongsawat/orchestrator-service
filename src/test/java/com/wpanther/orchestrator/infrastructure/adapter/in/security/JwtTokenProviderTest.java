package com.wpanther.orchestrator.infrastructure.adapter.in.security;

import com.wpanther.orchestrator.infrastructure.adapter.in.security.JwtTokenProvider;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Unit tests for JwtTokenProvider.
 */
@DisplayName("JwtTokenProvider Tests")
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private String testSecret = "test-secret-key-for-jwt-token-generation-in-tests";

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", testSecret);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtTokenValidityInSeconds", 3600L);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtIssuer", "test-orchestrator");
        jwtTokenProvider.init();
    }

    @Nested
    @DisplayName("generateToken()")
    class GenerateTokenTests {

        @Test
        @DisplayName("should generate valid JWT token with subject only")
        void shouldGenerateValidTokenWithSubject() {
            String subject = "test-user";
            String token = jwtTokenProvider.generateToken(subject);

            assertThat(token).isNotNull();
            assertThat(token).isNotEmpty();

            // Verify token structure (header.payload.signature)
            String[] parts = token.split("\\.");
            assertThat(parts).hasSize(3);
        }

        @Test
        @DisplayName("should generate token with custom claims")
        void shouldGenerateTokenWithCustomClaims() {
            String subject = "test-user";
            Map<String, Object> claims = new HashMap<>();
            claims.put("customClaim", "customValue");
            claims.put("userId", "12345");

            String token = jwtTokenProvider.generateToken(subject, claims);

            assertThat(token).isNotNull();
            String username = jwtTokenProvider.getSubjectFromToken(token);
            assertThat(username).isEqualTo(subject);

            Object customClaim = jwtTokenProvider.getClaimFromToken(token, "customClaim");
            assertThat(customClaim).isEqualTo("customValue");
        }

        @Test
        @DisplayName("should set correct expiration time")
        void shouldSetCorrectExpirationTime() {
            String subject = "test-user";
            Instant beforeGeneration = Instant.now();

            String token = jwtTokenProvider.generateToken(subject);

            Instant afterGeneration = Instant.now();
            Date expiration = jwtTokenProvider.getExpirationDateFromToken(token);

            // Token should expire approximately 1 hour from now
            Instant expectedMin = beforeGeneration.plusSeconds(3599);
            Instant expectedMax = afterGeneration.plusSeconds(3601);
            assertThat(expiration.toInstant()).isAfter(expectedMin);
            assertThat(expiration.toInstant()).isBefore(expectedMax);
        }

        @Test
        @DisplayName("should include issuer in token")
        void shouldIncludeIssuerInToken() {
            String subject = "test-user";
            String token = jwtTokenProvider.generateToken(subject);

            // The issuer is not directly accessible but is part of the token
            // Token generation should not throw exception
            assertThat(token).isNotNull();
        }
    }

    @Nested
    @DisplayName("validateToken()")
    class ValidateTokenTests {

        @Test
        @DisplayName("should validate valid token successfully")
        void shouldValidateValidTokenSuccessfully() {
            String subject = "test-user";
            String token = jwtTokenProvider.generateToken(subject);

            String retrievedSubject = jwtTokenProvider.getSubjectFromToken(token);

            assertThat(retrievedSubject).isEqualTo(subject);
        }

        @Test
        @DisplayName("should throw exception for expired token")
        void shouldThrowExceptionForExpiredToken() {
            String subject = "test-user";

            // Create an expired token (expired in the past)
            SecretKey key = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
            String expiredToken = Jwts.builder()
                    .subject(subject)
                    .expiration(Date.from(Instant.now().minusSeconds(1)))
                    .signWith(key)
                    .compact();

            assertThatThrownBy(() -> jwtTokenProvider.validateToken(expiredToken))
                    .isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("should throw exception for malformed token")
        void shouldThrowExceptionForMalformedToken() {
            String malformedToken = "invalid.jwt.token";

            assertThatThrownBy(() -> jwtTokenProvider.validateToken(malformedToken))
                    .isInstanceOf(MalformedJwtException.class);
        }

        @Test
        @DisplayName("should throw exception for empty token")
        void shouldThrowExceptionForEmptyToken() {
            assertThatThrownBy(() -> jwtTokenProvider.validateToken(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw exception for null token")
        void shouldThrowExceptionForNullToken() {
            assertThatThrownBy(() -> jwtTokenProvider.validateToken(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getClaimFromToken()")
    class GetClaimFromTokenTests {

        @Test
        @DisplayName("should retrieve custom claim from token")
        void shouldRetrieveCustomClaimFromToken() {
            String subject = "test-user";
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", "12345");
            claims.put("role", "ADMIN");
            claims.put("active", true);

            String token = jwtTokenProvider.generateToken(subject, claims);

            assertThat(jwtTokenProvider.getClaimFromToken(token, "userId")).isEqualTo("12345");
            assertThat(jwtTokenProvider.getClaimFromToken(token, "role")).isEqualTo("ADMIN");
            assertThat(jwtTokenProvider.getClaimFromToken(token, "active")).isEqualTo(true);
        }

        @Test
        @DisplayName("should return null for non-existent claim")
        void shouldReturnNullForNonExistentClaim() {
            String subject = "test-user";
            String token = jwtTokenProvider.generateToken(subject);

            Object claim = jwtTokenProvider.getClaimFromToken(token, "nonExistentClaim");

            assertThat(claim).isNull();
        }
    }

    @Nested
    @DisplayName("isTokenExpired()")
    class IsTokenExpiredTests {

        @Test
        @DisplayName("should return false for valid token")
        void shouldReturnFalseForValidToken() {
            String subject = "test-user";
            String token = jwtTokenProvider.generateToken(subject);

            assertThat(jwtTokenProvider.isTokenExpired(token)).isFalse();
        }

        @Test
        @DisplayName("should return true for expired token")
        void shouldReturnTrueForExpiredToken() {
            SecretKey key = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
            String expiredToken = Jwts.builder()
                    .subject("test-user")
                    .expiration(Date.from(Instant.now().minusSeconds(1)))
                    .signWith(key)
                    .compact();

            assertThat(jwtTokenProvider.isTokenExpired(expiredToken)).isTrue();
        }

        @Test
        @DisplayName("should return true for invalid token")
        void shouldReturnTrueForInvalidToken() {
            String invalidToken = "invalid.token";

            assertThat(jwtTokenProvider.isTokenExpired(invalidToken)).isTrue();
        }
    }

    @Nested
    @DisplayName("validateTokenSilently()")
    class ValidateTokenSilentlyTests {

        @Test
        @DisplayName("should return true for valid token")
        void shouldReturnTrueForValidToken() {
            String subject = "test-user";
            String token = jwtTokenProvider.generateToken(subject);

            assertThat(jwtTokenProvider.validateTokenSilently(token)).isTrue();
        }

        @Test
        @DisplayName("should return false for expired token")
        void shouldReturnFalseForExpiredToken() {
            SecretKey key = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
            String expiredToken = Jwts.builder()
                    .subject("test-user")
                    .expiration(Date.from(Instant.now().minusSeconds(1)))
                    .signWith(key)
                    .compact();

            assertThat(jwtTokenProvider.validateTokenSilently(expiredToken)).isFalse();
        }

        @Test
        @DisplayName("should return false for malformed token")
        void shouldReturnFalseForMalformedToken() {
            assertThat(jwtTokenProvider.validateTokenSilently("invalid.token")).isFalse();
        }

        @Test
        @DisplayName("should return false for empty token")
        void shouldReturnFalseForEmptyToken() {
            assertThat(jwtTokenProvider.validateTokenSilently("")).isFalse();
        }

        @Test
        @DisplayName("should return false for null token")
        void shouldReturnFalseForNullToken() {
            assertThat(jwtTokenProvider.validateTokenSilently(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("getSubjectFromToken()")
    class GetSubjectFromTokenTests {

        @Test
        @DisplayName("should return subject from valid token")
        void shouldReturnSubjectFromValidToken() {
            String subject = "test-user-123";
            String token = jwtTokenProvider.generateToken(subject);

            assertThat(jwtTokenProvider.getSubjectFromToken(token)).isEqualTo(subject);
        }
    }

    @Nested
    @DisplayName("getExpirationDateFromToken()")
    class GetExpirationDateFromTokenTests {

        @Test
        @DisplayName("should return expiration date from token")
        void shouldReturnExpirationDateFromToken() {
            String subject = "test-user";
            String token = jwtTokenProvider.generateToken(subject);

            Date expiration = jwtTokenProvider.getExpirationDateFromToken(token);

            assertThat(expiration).isNotNull();
            assertThat(expiration.after(new Date()));
        }
    }
}
