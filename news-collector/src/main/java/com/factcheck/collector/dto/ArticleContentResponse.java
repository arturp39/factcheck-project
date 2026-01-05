package com.factcheck.collector.dto;

import java.time.Instant;

public record ArticleContentResponse(
        Long articleId,
        Long publisherId,
        String publisherName,
        Long sourceEndpointId,
        String sourceEndpointName,
        String canonicalUrl,
        String title,
        Instant publishedDate,
        String content
) {
}
