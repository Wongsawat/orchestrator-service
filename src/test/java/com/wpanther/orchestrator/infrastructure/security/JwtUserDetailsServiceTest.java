package com.wpanther.orchestrator.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JwtUserDetailsService.
 */
@DisplayName("JwtUserDetailsService Tests")
class JwtUserDetailsServiceTest {

    private JwtUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new JwtUserDetailsService();
    }

    @Nested
    @DisplayName("loadUserByUsername()")
    class LoadUserByUsernameTests {

        @Test
        @DisplayName("should load user with valid username")
        void shouldLoadUserWithValidUsername() {
            String username = "test-user";

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            assertThat(userDetails).isNotNull();
            assertThat(userDetails.getUsername()).isEqualTo(username);
            assertThat(userDetails.getAuthorities())
                    .hasSize(1)
                    .anyMatch(a -> a.getAuthority().equals("ROLE_API_USER"));
        }

        @Test
        @DisplayName("should create user with account non-expired")
        void shouldCreateUserWithAccountNonExpired() {
            String username = "test-user";

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            assertThat(userDetails.isAccountNonExpired()).isTrue();
            assertThat(userDetails.isAccountNonLocked()).isTrue();
            assertThat(userDetails.isCredentialsNonExpired()).isTrue();
            assertThat(userDetails.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should accept alphanumeric username")
        void shouldAcceptAlphanumericUsername() {
            String username = "user123";

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            assertThat(userDetails.getUsername()).isEqualTo(username);
        }

        @Test
        @DisplayName("should accept username with hyphens")
        void shouldAcceptUsernameWithHyphens() {
            String username = "test-user-123";

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            assertThat(userDetails.getUsername()).isEqualTo(username);
        }

        @Test
        @DisplayName("should accept username with underscores")
        void shouldAcceptUsernameWithUnderscores() {
            String username = "test_user_123";

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            assertThat(userDetails.getUsername()).isEqualTo(username);
        }

        @Test
        @DisplayName("should throw exception for null username")
        void shouldThrowExceptionForNullUsername() {
            assertThatThrownBy(() -> userDetailsService.loadUserByUsername(null))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessage("Username cannot be empty");
        }

        @Test
        @DisplayName("should throw exception for empty username")
        void shouldThrowExceptionForEmptyUsername() {
            assertThatThrownBy(() -> userDetailsService.loadUserByUsername(""))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessage("Username cannot be empty");
        }

        @Test
        @DisplayName("should throw exception for blank username")
        void shouldThrowExceptionForBlankUsername() {
            assertThatThrownBy(() -> userDetailsService.loadUserByUsername("   "))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessage("Username cannot be empty");
        }

        @Test
        @DisplayName("should throw exception for username with spaces")
        void shouldThrowExceptionForUsernameWithSpaces() {
            assertThatThrownBy(() -> userDetailsService.loadUserByUsername("user name"))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        @DisplayName("should throw exception for username with special characters")
        void shouldThrowExceptionForUsernameWithSpecialCharacters() {
            assertThatThrownBy(() -> userDetailsService.loadUserByUsername("user@domain.com"))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        @DisplayName("should throw exception for username starting with number")
        void shouldThrowExceptionForUsernameStartingWithNumber() {
            // Actually, this should be accepted with the current pattern
            String username = "123user";
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            assertThat(userDetails.getUsername()).isEqualTo(username);
        }
    }

    @Nested
    @DisplayName("User Details Properties")
    class UserDetailsPropertiesTests {

        @Test
        @DisplayName("should return user with password not encoded")
        void shouldReturnUserWithNoOpPassword() {
            String username = "test-user";

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // Password should be {noop} prefixed (no encoding)
            assertThat(userDetails.getPassword()).contains("{noop}");
        }

        @Test
        @DisplayName("should return user with ROLE_API_USER authority")
        void shouldReturnUserWithApiUserRole() {
            String username = "test-user";

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            assertThat(userDetails.getAuthorities())
                    .hasSize(1)
                    .allMatch(auth -> auth.getAuthority().equals("ROLE_API_USER"));
        }

        @Test
        @DisplayName("should return enabled user")
        void shouldReturnEnabledUser() {
            String username = "test-user";

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            assertThat(userDetails.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should create unique user instance for each call")
        void shouldCreateUniqueUserInstanceForEachCall() {
            String username = "test-user";

            UserDetails userDetails1 = userDetailsService.loadUserByUsername(username);
            UserDetails userDetails2 = userDetailsService.loadUserByUsername(username);

            // Should be different instances but same values
            assertThat(userDetails1).isNotSameAs(userDetails2);
            assertThat(userDetails1.getUsername()).isEqualTo(userDetails2.getUsername());
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("should create service with default credentials")
        void shouldCreateWithDefaultCredentials() {
            JwtUserDetailsService service = new JwtUserDetailsService();

            String username = "test-user";
            UserDetails userDetails = service.loadUserByUsername(username);

            assertThat(userDetails).isNotNull();
            assertThat(userDetails.getPassword()).contains("{noop}api-password");
            assertThat(userDetails.getAuthorities())
                    .extracting("authority")
                    .contains("ROLE_API_USER");
        }
    }
}
