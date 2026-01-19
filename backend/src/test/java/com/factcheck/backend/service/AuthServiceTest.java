package com.factcheck.backend.service;

import com.factcheck.backend.entity.AppUser;
import com.factcheck.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserService userService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authenticationManager, jwtService, userService);
    }

    @Test
    void authenticate_mapsRolesAndGeneratesToken() {
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("USER")
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken("alice", "pw", authorities);

        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(jwtService.generateToken(eq("alice"), eq(List.of("ADMIN", "USER")))).thenReturn("token");

        String token = authService.authenticate("alice", "pw");

        assertThat(token).isEqualTo("token");
        verify(jwtService).generateToken(eq("alice"), eq(List.of("ADMIN", "USER")));
    }

    @Test
    void register_delegatesToUserService() {
        AppUser user = new AppUser();
        when(userService.registerUser("bob", "password123")).thenReturn(user);

        AppUser result = authService.register("bob", "password123");

        assertThat(result).isSameAs(user);
        verify(userService).registerUser("bob", "password123");
    }
}
