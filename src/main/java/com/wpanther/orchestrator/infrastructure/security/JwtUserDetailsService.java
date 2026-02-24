package com.wpanther.orchestrator.infrastructure.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * UserDetailsService implementation for JWT authentication.
 * <p>
 * This service loads user details for authentication. In a production environment,
 * this would typically load users from a database. For now, it returns a default
 * user with valid authentication.
 * </p>
 */
@Service("jwtUserDetailsService")
public class JwtUserDetailsService implements UserDetailsService {

    /**
     * Default username for API authentication.
     * Can be overridden via app.security.jwt.default-username property.
     */
    private static final String DEFAULT_USERNAME = "api-user";

    /**
     * Default password for API authentication (not used in JWT flow, but required by UserDetails).
     * Can be overridden via app.security.jwt.default-password property.
     */
    private static final String DEFAULT_PASSWORD = "{noop}api-password";

    /**
     * Default role for API authentication.
     * Can be overridden via app.security.jwt.default-role property.
     */
    private static final String DEFAULT_ROLE = "ROLE_API_USER";

    private final String username;
    private final String password;
    private final String role;

    public JwtUserDetailsService() {
        this(DEFAULT_USERNAME, DEFAULT_PASSWORD, DEFAULT_ROLE);
    }

    public JwtUserDetailsService(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // In a production environment, load user from database
        // For now, we accept any username that matches a basic pattern
        // and return a user with API_USER role

        if (username == null || username.trim().isEmpty()) {
            throw new UsernameNotFoundException("Username cannot be empty");
        }

        // Simple validation: username must be alphanumeric with optional hyphens/underscores
        if (!username.matches("^[a-zA-Z0-9_-]+$")) {
            throw new UsernameNotFoundException("Invalid username format: " + username);
        }

        return User.builder()
                .username(username)
                .password(this.password) // {noop} prefix means no encoding
                .authorities(Collections.singletonList(new SimpleGrantedAuthority(this.role)))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}
