package com.factcheck.collector.integration.ingestion.fetcher;

public interface ArticleContentExtractor {
    ArticleFetchResult fetchAndExtract(String url);
}