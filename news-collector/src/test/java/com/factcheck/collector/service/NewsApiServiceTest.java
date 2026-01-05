package com.factcheck.collector.service;

import com.factcheck.collector.integration.newsapi.NewsApiClient;
import com.factcheck.collector.integration.newsapi.dto.NewsApiSource;
import com.factcheck.collector.integration.newsapi.dto.NewsApiSourcesResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsApiServiceTest {

    @Mock
    private NewsApiClient newsApiClient;

    @InjectMocks
    private NewsApiService newsApiService;

    @Test
    void listEnglishSources_delegatesToClient() {
        NewsApiSourcesResponse response = new NewsApiSourcesResponse(
                "ok",
                List.of(new NewsApiSource("abc", "ABC News", "desc", "https://abc.com", "general", "en", "us")),
                null,
                null
        );
        when(newsApiClient.fetchSources("en")).thenReturn(response);

        NewsApiSourcesResponse result = newsApiService.listEnglishSources();

        assertThat(result).isSameAs(response);
        verify(newsApiClient).fetchSources("en");
    }
}