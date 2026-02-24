package com.wpanther.orchestrator.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for JWT token generation and validation.
 * Uses JJWT library for JWT creation and parsing.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${app.security.jwt.secret:orchestrator-service-secret-key-for-jwt-token-generation-change-in-production}")
    private String jwtSecret;

    @Value("${app.security.jwt.token-validity-in-seconds:3600}")
    private long jwtTokenValidityInSeconds;

    @Value("${app.security.jwt.issuer:orchestrator-service}")
    private String jwtIssuer;

    private SecretKey secretKey;
    private JwtParser jwtParser;

    @PostConstruct
    public void init() {
        // Initialize secret key
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);

        // Initialize JWT parser with the same secret key
        this.jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .build();
    }

    /**
     * Generates a JWT token for the given subject (username/userId).
     *
     * @param subject The subject of the token (typically username or userId)
     * @return The generated JWT token
     */
    public String generateToken(String subject) {
        return generateToken(subject, Map.of());
    }

    /**
     * Generates a JWT token with additional claims.
     *
     * @param subject The subject of the token (typically username or userId)
     * @param claims Additional claims to include in the token
     * @return The generated JWT token
     */
    public String generateToken(String subject, Map<String, Object> claims) {
        Instant now = Instant.now();
        Instant expirationTime = now.plus(jwtTokenValidityInSeconds, ChronoUnit.SECONDS);

        JwtBuilder builder = Jwts.builder()
                .subject(subject)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expirationTime))
                .issuer(jwtIssuer)
                .signWith(secretKey);

        // Add additional claims
        claims.forEach((key, value) -> builder.claim(key, value));

        return builder.compact();
    }

    /**
     * Validates and parses a JWT token.
     *
     * @param token The JWT token to validate
     * @return The parsed JWT claims
     * @throws JwtException if the token is invalid or expired
     */
    public Jws<Claims> validateToken(String token) {
        try {
            return jwtParser.parseSignedClaims(token);
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
            throw e;
        } catch (MalformedJwtException e) {
            log.warn("JWT token is malformed: {}", e.getMessage());
            throw e;
        } catch (SignatureException e) {
            log.warn("JWT token signature validation failed: {}", e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is empty or null: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Extracts the subject from a JWT token.
     *
     * @param token The JWT token
     * @return The subject (username/userId)
     */
    public String getSubjectFromToken(String token) {
        return validateToken(token).getPayload().getSubject();
    }

    /**
     * Extracts a specific claim from a JWT token.
     *
     * @param token The JWT token
     * @param claimName The name of the claim
     * @return The claim value, or null if not present
     */
    public Object getClaimFromToken(String token, String claimName) {
        return validateToken(token).getPayload().get(claimName);
    }

    /**
     * Gets the token expiration date.
     *
     * @param token The JWT token
     * @return The expiration date
     */
    public Date getExpirationDateFromToken(String token) {
        return validateToken(token).getPayload().getExpiration();
    }

    /**
     * Checks if a token is expired.
     *
     * @param token The JWT token
     * @return true if the token is expired, false otherwise
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            return expiration.before(new Date());
        } catch (JwtException e) {
            return true; // Invalid tokens are considered expired
        }
    }

    /**
     * Validates a token (checks expiration and signature).
     *
     * @param token The JWT token
     * @return true if valid, false otherwise
     */
    public boolean validateTokenSilently(String token) {
        try {
            validateToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
