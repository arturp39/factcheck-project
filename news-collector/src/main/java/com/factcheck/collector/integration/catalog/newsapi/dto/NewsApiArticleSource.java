package com.factcheck.collector.integration.catalog.newsapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewsApiArticleSource(
        String id,
        String name
) {
}