package com.factcheck.collector.integration.catalog.newsapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewsApiArticle(
        NewsApiArticleSource source,
        String author,
        String title,
        String description,
        String url,
        String urlToImage,
        String publishedAt,
        String content
) {
}