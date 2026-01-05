package com.factcheck.collector.integration.newsapi;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "newsapi")
public class NewsApiProperties {

    private String apiKey;
    private String baseUrl = "https://newsapi.org/v2";
    private int maxSourcesPerRequest = 20;
    private int maxPagesPerBatch = 5;
    private int maxRequestsPerIngestion = 100;
    private int pageSize = 100;
    private String sortBy = "publishedAt";
}
