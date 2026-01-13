package com.factcheck.backend.dto;

import java.time.LocalDateTime;

public record ArticleDto(
        Long id,
        String title,
        String content,
        String source,
        LocalDateTime publishedAt,
        String mbfcBias,
        String mbfcFactualReporting,
        String mbfcCredibility
) {
}