package com.factcheck.collector.dto;

import java.time.Instant;

public record ArticleMetadataResponse(
        Long id,
        Long publisherId,
        String publisherName,
        Long sourceEndpointId,
        String sourceEndpointName,
        String canonicalUrl,
        String title,
        Instant publishedDate,
        int chunkCount,
        String status,
        boolean weaviateIndexed
) {
}