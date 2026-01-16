package com.factcheck.backend.dto;

public record LoginRequest(
        String username,
        String password
) {}
