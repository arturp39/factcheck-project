package com.factcheck.backend.controller;

import com.factcheck.backend.config.SecurityProperties;
import com.factcheck.backend.entity.AppUser;
import com.factcheck.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.ui.ExtendedModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private SecurityProperties buildProps() {
        SecurityProperties props = new SecurityProperties();
        props.setJwtCookieName("factcheck_token");
        props.setJwtCookieSameSite("Strict");
        props.setJwtCookieSecure(false);
        props.setJwtTtlMinutes(120);
        return props;
    }

    @Test
    void loginAndRegisterViews() {
        AuthController controller = new AuthController(authService, buildProps());

        assertThat(controller.login()).isEqualTo("login");
        assertThat(controller.register()).isEqualTo("register");
    }

    @Test
    void handleLogin_setsCookieOnSuccess() {
        AuthController controller = new AuthController(authService, buildProps());
        when(authService.authenticate("user", "pw")).thenReturn("token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.handleLogin("user", "pw", response, model);

        assertThat(view).isEqualTo("redirect:/");
        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("factcheck_token=token");
    }

    @Test
    void handleLogin_returnsLoginOnFailure() {
        AuthController controller = new AuthController(authService, buildProps());
        when(authService.authenticate("user", "pw"))
                .thenThrow(new BadCredentialsException("bad creds"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.handleLogin("user", "pw", response, model);

        assertThat(view).isEqualTo("login");
        assertThat(model.get("error")).isEqualTo("Invalid username or password.");
    }

    @Test
    void handleRegister_setsCookieOnSuccess() {
        AuthController controller = new AuthController(authService, buildProps());
        when(authService.register("user", "pw")).thenReturn(new AppUser());
        when(authService.authenticate("user", "pw")).thenReturn("token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.handleRegister("user", "pw", response, model);

        assertThat(view).isEqualTo("redirect:/");
        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("factcheck_token=token");
    }

    @Test
    void handleRegister_returnsRegisterOnFailure() {
        AuthController controller = new AuthController(authService, buildProps());
        when(authService.register("user", "pw"))
                .thenThrow(new IllegalArgumentException("username exists"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.handleRegister("user", "pw", response, model);

        assertThat(view).isEqualTo("register");
        assertThat(model.get("error")).isEqualTo("username exists");
    }

    @Test
    void logout_clearsCookie() {
        AuthController controller = new AuthController(authService, buildProps());
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.logout(response);

        assertThat(view).isEqualTo("redirect:/login");
        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("factcheck_token=");
        assertThat(cookie).contains("Max-Age=0");
    }
}
