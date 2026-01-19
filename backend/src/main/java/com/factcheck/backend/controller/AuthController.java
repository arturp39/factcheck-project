package com.factcheck.backend.controller;

import com.factcheck.backend.config.SecurityProperties;
import com.factcheck.backend.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;

@Controller
public class AuthController {

    private final AuthService authService;
    private final SecurityProperties securityProperties;

    public AuthController(AuthService authService, SecurityProperties securityProperties) {
        this.authService = authService;
        this.securityProperties = securityProperties;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @PostMapping("/auth/login")
    public String handleLogin(@RequestParam String username,
                              @RequestParam String password,
                              HttpServletResponse response,
                              Model model) {
        try {
            String token = authService.authenticate(username, password);
            setJwtCookie(response, token);
            return "redirect:/";
        } catch (AuthenticationException e) {
            model.addAttribute("error", "Invalid username or password.");
            return "login";
        }
    }

    @PostMapping("/auth/register")
    public String handleRegister(@RequestParam String username,
                                 @RequestParam String password,
                                 HttpServletResponse response,
                                 Model model) {
        try {
            authService.register(username, password);
            String token = authService.authenticate(username, password);
            setJwtCookie(response, token);
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        } catch (AuthenticationException e) {
            model.addAttribute("error", "Unable to sign in after registration.");
            return "register";
        }
    }

    @GetMapping("/auth/logout")
    public String logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(securityProperties.getJwtCookieName(), "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .secure(securityProperties.isJwtCookieSecure())
                .sameSite(securityProperties.getJwtCookieSameSite())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return "redirect:/login";
    }

    private void setJwtCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(securityProperties.getJwtCookieName(), token)
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofMinutes(securityProperties.getJwtTtlMinutes()))
                .secure(securityProperties.isJwtCookieSecure())
                .sameSite(securityProperties.getJwtCookieSameSite())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
