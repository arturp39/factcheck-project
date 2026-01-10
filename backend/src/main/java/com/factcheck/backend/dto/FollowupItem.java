package com.factcheck.backend.dto;

import java.time.Instant;

public record FollowupItem(
        String question,
        String answer,
        Instant createdAt
) {}
