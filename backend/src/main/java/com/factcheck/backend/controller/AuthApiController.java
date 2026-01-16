package com.factcheck.backend.controller;

import com.factcheck.backend.dto.AuthResponse;
import com.factcheck.backend.dto.LoginRequest;
import com.factcheck.backend.dto.RegisterRequest;
import com.factcheck.backend.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final AuthService authService;

    public AuthApiController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Login request must not be empty.");
        }
        try {
            String token = authService.authenticate(request.username(), request.password());
            return ResponseEntity.ok(new AuthResponse(token, "Bearer"));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Register request must not be empty.");
        }
        authService.register(request.username(), request.password());
        try {
            String token = authService.authenticate(request.username(), request.password());
            return ResponseEntity.ok(new AuthResponse(token, "Bearer"));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).build();
        }
    }
}
