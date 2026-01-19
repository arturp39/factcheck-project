package com.factcheck.backend.controller;

import com.factcheck.backend.dto.AuthResponse;
import com.factcheck.backend.dto.LoginRequest;
import com.factcheck.backend.dto.RegisterRequest;
import com.factcheck.backend.entity.AppUser;
import com.factcheck.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthApiControllerTest {

    @Mock
    private AuthService authService;

    @Test
    void login_rejectsNullRequest() {
        AuthApiController controller = new AuthApiController(authService);

        assertThrows(IllegalArgumentException.class, () -> controller.login(null));
    }

    @Test
    void login_returnsUnauthorizedOnAuthFailure() {
        AuthApiController controller = new AuthApiController(authService);
        when(authService.authenticate("user", "bad"))
                .thenThrow(new BadCredentialsException("bad"));

        ResponseEntity<AuthResponse> response =
                controller.login(new LoginRequest("user", "bad"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void login_returnsTokenOnSuccess() {
        AuthApiController controller = new AuthApiController(authService);
        when(authService.authenticate("user", "pw")).thenReturn("token");

        ResponseEntity<AuthResponse> response =
                controller.login(new LoginRequest("user", "pw"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isEqualTo("token");
        assertThat(response.getBody().tokenType()).isEqualTo("Bearer");
    }

    @Test
    void register_rejectsNullRequest() {
        AuthApiController controller = new AuthApiController(authService);

        assertThrows(IllegalArgumentException.class, () -> controller.register(null));
    }

    @Test
    void register_returnsUnauthorizedWhenAuthFailsAfterRegister() {
        AuthApiController controller = new AuthApiController(authService);
        when(authService.register("user", "pw")).thenReturn(new AppUser());
        when(authService.authenticate("user", "pw"))
                .thenThrow(new BadCredentialsException("bad"));

        ResponseEntity<AuthResponse> response =
                controller.register(new RegisterRequest("user", "pw"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void register_returnsTokenOnSuccess() {
        AuthApiController controller = new AuthApiController(authService);
        when(authService.register("user", "pw")).thenReturn(new AppUser());
        when(authService.authenticate("user", "pw")).thenReturn("token");

        ResponseEntity<AuthResponse> response =
                controller.register(new RegisterRequest("user", "pw"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isEqualTo("token");
        assertThat(response.getBody().tokenType()).isEqualTo("Bearer");
    }
}
