package com.factcheck.collector.integration.fetcher;

public interface ArticleContentExtractor {
    ArticleFetchResult fetchAndExtract(String url);
}
