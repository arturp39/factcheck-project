package com.factcheck.collector.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ArticleContentResponse {

    private Long articleId;
    private Long publisherId;
    private String publisherName;
    private Long sourceEndpointId;
    private String sourceEndpointName;

    private String canonicalUrl;
    private String title;
    private Instant publishedDate;
    private String content;
}