package com.factcheck.collector.integration.newsapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewsApiEverythingResponse(
        String status,
        Integer totalResults,
        List<NewsApiArticle> articles,
        String code,
        String message
) {
}
