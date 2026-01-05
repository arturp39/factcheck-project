package com.factcheck.collector.integration.newsapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewsApiSource(
        String id,
        String name,
        String description,
        String url,
        String category,
        String language,
        String country
) {
}