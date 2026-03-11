package com.wpanther.orchestrator.infrastructure.adapter.in.security;

import com.wpanther.orchestrator.infrastructure.adapter.in.security.JwtAuthenticationFilter;
import com.wpanther.orchestrator.infrastructure.adapter.in.security.JwtTokenProvider;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for JwtAuthenticationFilter.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Tests")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private jakarta.servlet.http.HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("doFilter() - Valid JWT Token")
    class ValidTokenTests {

        @Test
        @DisplayName("should set authentication when valid JWT provided")
        void shouldSetAuthenticationWhenValidJWT() throws Exception {
            String token = "valid.jwt.token";
            String username = "test-user";

            // Mock request headers
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

            // Mock JWT provider
            when(jwtTokenProvider.validateTokenSilently(token)).thenReturn(true);
            when(jwtTokenProvider.getSubjectFromToken(token)).thenReturn(username);

            // Mock user details
            UserDetails userDetails = User.builder()
                    .username(username)
                    .password("{noop}password")
                    .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_API_USER")))
                    .build();
            when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);

            // Execute filter
            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            // Verify authentication was set
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNotNull();
            assertThat(authentication.getName()).isEqualTo(username);
            assertThat(authentication.getAuthorities())
                    .hasSize(1)
                    .allMatch(auth -> auth.getAuthority().equals("ROLE_API_USER"));

            // Verify filter chain continued
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should set WebAuthenticationDetails when valid JWT")
        void shouldSetWebAuthenticationDetails() throws Exception {
            String token = "valid.jwt.token";
            String username = "test-user";

            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtTokenProvider.validateTokenSilently(token)).thenReturn(true);
            when(jwtTokenProvider.getSubjectFromToken(token)).thenReturn(username);

            UserDetails userDetails = User.builder()
                    .username(username)
                    .password("{noop}password")
                    .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_API_USER")))
                    .build();
            when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication.getDetails()).isNotNull();
        }
    }

    @Nested
    @DisplayName("doFilter() - No or Invalid JWT Token")
    class InvalidTokenTests {

        @Test
        @DisplayName("should not set authentication when no Authorization header")
        void shouldNotSetAuthenticationWhenNoAuthHeader() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
            verify(userDetailsService, never()).loadUserByUsername(any());
            verify(jwtTokenProvider, never()).validateTokenSilently(any());
        }

        @Test
        @DisplayName("should not set authentication when Authorization header has no Bearer prefix")
        void shouldNotSetAuthenticationWhenNoBearerPrefix() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("InvalidFormat token");

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
            verify(jwtTokenProvider, never()).validateTokenSilently(any());
        }

        @Test
        @DisplayName("should not set authentication when JWT is invalid")
        void shouldNotSetAuthenticationWhenJWTInvalid() throws Exception {
            String invalidToken = "invalid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + invalidToken);

            when(jwtTokenProvider.validateTokenSilently(invalidToken)).thenReturn(false);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
            verify(userDetailsService, never()).loadUserByUsername(any());
        }

        @Test
        @DisplayName("should clear authentication on JWT validation exception")
        void shouldClearAuthenticationOnException() throws Exception {
            String invalidToken = "expired.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + invalidToken);

            when(jwtTokenProvider.validateTokenSilently(invalidToken)).thenThrow(new RuntimeException("Token validation failed"));

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("extractJwtFromRequest()")
    class ExtractJwtTests {

        @Test
        @DisplayName("should extract token from valid Authorization header")
        void shouldExtractTokenFromValidHeader() throws Exception {
            String token = "valid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            // If token is valid, it should be validated
            verify(jwtTokenProvider).validateTokenSilently(token);
        }

        @Test
        @DisplayName("should return null when Authorization header is null")
        void shouldReturnNullWhenHeaderIsNull() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(jwtTokenProvider, never()).validateTokenSilently(any());
        }

        @Test
        @DisplayName("should return null when Authorization header is empty")
        void shouldReturnNullWhenHeaderIsEmpty() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("");

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(jwtTokenProvider, never()).validateTokenSilently(any());
        }

        @Test
        @DisplayName("should return null when Authorization header lacks Bearer prefix")
        void shouldReturnNullWhenNoBearerPrefix() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("InvalidFormat token");

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(jwtTokenProvider, never()).validateTokenSilently(any());
        }
    }

    @Nested
    @DisplayName("Filter Chain")
    class FilterChainTests {

        @Test
        @DisplayName("should always continue filter chain regardless of authentication")
        void shouldAlwaysContinueFilterChain() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should continue filter chain after successful authentication")
        void shouldContinueAfterSuccessfulAuth() throws Exception {
            String token = "valid.jwt.token";

            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtTokenProvider.validateTokenSilently(token)).thenReturn(true);
            when(jwtTokenProvider.getSubjectFromToken(token)).thenReturn("test-user");

            UserDetails userDetails = User.builder()
                    .username("test-user")
                    .password("{noop}password")
                    .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_API_USER")))
                    .build();
            when(userDetailsService.loadUserByUsername("test-user")).thenReturn(userDetails);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should continue filter chain after authentication failure")
        void shouldContinueAfterFailedAuth() throws Exception {
            String invalidToken = "invalid.jwt.token";

            when(request.getHeader("Authorization")).thenReturn("Bearer " + invalidToken);
            when(jwtTokenProvider.validateTokenSilently(invalidToken)).thenReturn(false);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Security Context")
    class SecurityContextTests {

        @Test
        @DisplayName("should clear existing authentication on exception")
        void shouldClearExistingAuthenticationOnException() throws Exception {
            // Set existing authentication
            UsernamePasswordAuthenticationToken existingAuth =
                    new UsernamePasswordAuthenticationToken("old-user", null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(existingAuth);

            // Mock token that throws exception
            String invalidToken = "invalid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + invalidToken);
            when(jwtTokenProvider.validateTokenSilently(invalidToken)).thenThrow(new RuntimeException("Token validation failed"));

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            // Authentication should be cleared on exception
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("should replace existing authentication on new valid token")
        void shouldReplaceExistingAuthentication() throws Exception {
            // Set existing authentication
            UsernamePasswordAuthenticationToken existingAuth =
                    new UsernamePasswordAuthenticationToken("old-user", null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(existingAuth);

            // Mock valid token for new user
            String token = "valid.jwt.token";
            String newUser = "new-user";

            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtTokenProvider.validateTokenSilently(token)).thenReturn(true);
            when(jwtTokenProvider.getSubjectFromToken(token)).thenReturn(newUser);

            UserDetails newUserDetails = User.builder()
                    .username(newUser)
                    .password("{noop}password")
                    .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_API_USER")))
                    .build();
            when(userDetailsService.loadUserByUsername(newUser)).thenReturn(newUserDetails);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNotNull();
            assertThat(authentication.getName()).isEqualTo(newUser);
            assertThat(authentication).isNotEqualTo(existingAuth);
        }
    }
}
