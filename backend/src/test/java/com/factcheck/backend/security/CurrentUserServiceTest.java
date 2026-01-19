package com.factcheck.backend.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentUserServiceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireUsername_throwsWhenNoAuth() {
        CurrentUserService service = new CurrentUserService();

        assertThrows(IllegalStateException.class, service::requireUsername);
    }

    @Test
    void requireUsername_throwsForAnonymous() {
        CurrentUserService service = new CurrentUserService();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated("anonymousUser", "n/a")
        );

        assertThrows(IllegalStateException.class, service::requireUsername);
    }

    @Test
    void requireUsername_returnsAuthenticatedUser() {
        CurrentUserService service = new CurrentUserService();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "user",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        assertThat(service.requireUsername()).isEqualTo("user");
    }

    @Test
    void isAdmin_returnsTrueWhenAdminRolePresent() {
        CurrentUserService service = new CurrentUserService();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "admin",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );

        assertThat(service.isAdmin()).isTrue();
    }

    @Test
    void isAdmin_returnsFalseWhenNoAuth() {
        CurrentUserService service = new CurrentUserService();

        assertThat(service.isAdmin()).isFalse();
    }
}
