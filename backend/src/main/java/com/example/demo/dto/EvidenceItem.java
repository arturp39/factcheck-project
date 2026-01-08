package com.example.demo.dto;

import java.time.LocalDateTime;

public record EvidenceItem(
        String title,
        String source,
        LocalDateTime publishedAt,
        String snippet
) {}
