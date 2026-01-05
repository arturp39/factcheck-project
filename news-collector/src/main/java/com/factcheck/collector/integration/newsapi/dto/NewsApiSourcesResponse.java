package com.factcheck.collector.integration.newsapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewsApiSourcesResponse(
        String status,
        List<NewsApiSource> sources,
        String code,
        String message
) {
}