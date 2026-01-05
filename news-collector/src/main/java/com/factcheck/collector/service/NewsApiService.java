package com.factcheck.collector.service;

import com.factcheck.collector.integration.newsapi.NewsApiClient;
import com.factcheck.collector.integration.newsapi.dto.NewsApiSourcesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NewsApiService {

    private final NewsApiClient newsApiClient;

    public NewsApiSourcesResponse listEnglishSources() {
        return newsApiClient.fetchSources("en");
    }
}
