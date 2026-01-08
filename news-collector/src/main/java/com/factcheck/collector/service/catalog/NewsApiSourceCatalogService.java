package com.factcheck.collector.service.catalog;

import com.factcheck.collector.integration.catalog.newsapi.NewsApiClient;
import com.factcheck.collector.integration.catalog.newsapi.dto.NewsApiSourcesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NewsApiSourceCatalogService {

    private final NewsApiClient newsApiClient;

    public NewsApiSourcesResponse listEnglishSources() {
        return newsApiClient.fetchSources("en");
    }
}