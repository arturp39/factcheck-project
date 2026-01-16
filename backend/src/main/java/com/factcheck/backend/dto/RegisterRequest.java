package com.factcheck.backend.dto;

public record RegisterRequest(
        String username,
        String password
) {}
