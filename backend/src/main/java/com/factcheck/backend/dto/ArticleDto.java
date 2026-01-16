package com.factcheck.backend.dto;

import java.time.LocalDateTime;

public record ArticleDto(
        Long articleId,
        String title,
        String content,
        String source,
        LocalDateTime publishedAt,
        String url,
        String mbfcBias,
        String mbfcFactualReporting,
        String mbfcCredibility
) {
}