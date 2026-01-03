package com.factcheck.collector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleMetadataResponse {
    private Long id;
    private Long publisherId;
    private String publisherName;
    private Long sourceEndpointId;
    private String sourceEndpointName;
    private String canonicalUrl;
    private String title;
    private Instant publishedDate;
    private int chunkCount;
    private String status;
    private boolean weaviateIndexed;
}