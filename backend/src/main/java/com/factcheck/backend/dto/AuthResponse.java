package com.factcheck.backend.dto;

public record AuthResponse(
        String token,
        String tokenType
) {}
