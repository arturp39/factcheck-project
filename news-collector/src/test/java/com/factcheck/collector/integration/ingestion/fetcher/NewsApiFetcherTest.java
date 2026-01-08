package com.factcheck.collector.integration.ingestion.fetcher;

import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.SourceKind;
import com.factcheck.collector.integration.catalog.newsapi.NewsApiClient;
import com.factcheck.collector.integration.catalog.newsapi.NewsApiProperties;
import com.factcheck.collector.integration.catalog.newsapi.dto.NewsApiArticle;
import com.factcheck.collector.integration.catalog.newsapi.dto.NewsApiArticleSource;
import com.factcheck.collector.integration.catalog.newsapi.dto.NewsApiEverythingResponse;
import com.factcheck.collector.repository.ArticleSourceRepository;
import com.factcheck.collector.repository.SourceEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewsApiFetcherTest {

    @Mock
    private NewsApiClient newsApiClient;

    @Mock
    private SourceEndpointRepository sourceEndpointRepository;

    @Mock
    private ArticleSourceRepository articleSourceRepository;

    private NewsApiProperties properties;

    @BeforeEach
    void setUp() {
        properties = new NewsApiProperties();
        properties.setMaxSourcesPerRequest(20);
        properties.setMaxPagesPerBatch(2);
        properties.setMaxRequestsPerIngestion(100);
        properties.setSortBy("publishedAt");
    }

    @Test
    void fetch_returnsArticlesForEachEndpoint_andDedupeByUrl() throws Exception {
        SourceEndpoint endpointA = endpoint(1L, "source-a");
        SourceEndpoint endpointB = endpoint(2L, "source-b");

        when(sourceEndpointRepository.findByEnabledTrueAndKind(SourceKind.API))
                .thenReturn(List.of(endpointA, endpointB));
        when(articleSourceRepository.countBySourceEndpointIds(anyList()))
                .thenReturn(List.of(new EndpointCount(1L, 5), new EndpointCount(2L, 1)));

        // Include a duplicate URL to validate per-endpoint dedupe.
        List<NewsApiArticle> articles = List.of(
                new NewsApiArticle(new NewsApiArticleSource("source-a", "Source A"), null,
                        "Title A1", "Desc A1", "https://a1", null, "2024-01-01T00:00:00Z", null),
                new NewsApiArticle(new NewsApiArticleSource("source-a", "Source A"), null,
                        "Title A1 DUP", "Desc A1", "https://a1", null, "2024-01-01T00:10:00Z", null),
                new NewsApiArticle(new NewsApiArticleSource("source-a", "Source A"), null,
                        "Title A2", "Desc A2", "https://a2", null, "2024-01-01T01:00:00Z", null),
                new NewsApiArticle(new NewsApiArticleSource("source-b", "Source B"), null,
                        "Title B1", "Desc B1", "https://b1", null, "2024-01-02T00:00:00Z", null)
        );
        when(newsApiClient.fetchEverything(anyString(), anyString(), anyInt()))
                .thenReturn(new NewsApiEverythingResponse("ok", 4, articles, null, null));

        NewsApiFetcher fetcher = new NewsApiFetcher(
                newsApiClient,
                properties,
                sourceEndpointRepository,
                articleSourceRepository
        );

        List<RawArticle> resultA = fetcher.fetch(endpointA);
        List<RawArticle> resultB = fetcher.fetch(endpointB);

        assertThat(resultA).hasSize(2);
        assertThat(resultA.getFirst().getExternalUrl()).isEqualTo("https://a1");
        assertThat(resultA.getFirst().getTitle()).isEqualTo("Title A1");
        assertThat(resultA.getFirst().getPublishedDate()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));

        assertThat(resultB).hasSize(1);
        assertThat(resultB.getFirst().getExternalUrl()).isEqualTo("https://b1");
        assertThat(resultB.getFirst().getTitle()).isEqualTo("Title B1");

        // Single request fits both sources.
        verify(newsApiClient, times(1)).fetchEverything(anyString(), anyString(), anyInt());

        // Second fetch rebuilds and returns articles again.
        List<RawArticle> resultASecond = fetcher.fetch(endpointA);
        assertThat(resultASecond).hasSize(2);
        verify(newsApiClient, times(2)).fetchEverything(anyString(), anyString(), anyInt());
    }

    @Test
    void fetch_respectsRequestLimit_andDefersNextEndpointWithoutRebuildingBatch() throws Exception {
        properties.setMaxRequestsPerIngestion(1);
        properties.setMaxSourcesPerRequest(1);
        properties.setMaxPagesPerBatch(3);

        SourceEndpoint endpointA = endpoint(1L, "source-a");
        SourceEndpoint endpointB = endpoint(2L, "source-b");

        when(sourceEndpointRepository.findByEnabledTrueAndKind(SourceKind.API))
                .thenReturn(List.of(endpointA, endpointB));
        when(articleSourceRepository.countBySourceEndpointIds(anyList()))
                .thenReturn(List.of(new EndpointCount(1L, 10), new EndpointCount(2L, 10)));

        // Return a full page so the limit check stops the loop.
        List<NewsApiArticle> page = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            page.add(new NewsApiArticle(new NewsApiArticleSource("source-a", "Source A"), null,
                    "Title " + i, "Desc", "https://a" + i, null, "2024-01-01T00:00:00Z", null));
        }
        when(newsApiClient.fetchEverything(anyString(), anyString(), anyInt()))
                .thenReturn(new NewsApiEverythingResponse("ok", 100, page, null, null));

        NewsApiFetcher fetcher = new NewsApiFetcher(
                newsApiClient,
                properties,
                sourceEndpointRepository,
                articleSourceRepository
        );

        // First call consumes A and hits the request limit.
        List<RawArticle> a = fetcher.fetch(endpointA);
        assertThat(a).isNotEmpty();

        // Second endpoint is deferred without extra requests.
        List<RawArticle> b = fetcher.fetch(endpointB);
        assertThat(b).isEmpty();

        verify(newsApiClient, times(1)).fetchEverything(anyString(), anyString(), anyInt());
    }

    @Test
    void fetch_prioritizesSourcesByArticleCount_andNormalizesSourceIds() throws Exception {
        properties.setMaxSourcesPerRequest(2);
        properties.setMaxPagesPerBatch(1);

        SourceEndpoint low = endpoint(1L, "source-low");
        SourceEndpoint high = endpoint(2L, "SOURCE-HIGH");
        SourceEndpoint mid = endpoint(3L, "Source-Mid");

        when(sourceEndpointRepository.findByEnabledTrueAndKind(SourceKind.API))
                .thenReturn(List.of(low, high, mid));
        when(articleSourceRepository.countBySourceEndpointIds(anyList()))
                .thenReturn(List.of(
                        new EndpointCount(1L, 1),
                        new EndpointCount(2L, 10),
                        new EndpointCount(3L, 5)
                ));

        when(newsApiClient.fetchEverything(anyString(), anyString(), anyInt()))
                .thenReturn(new NewsApiEverythingResponse("ok", 0, List.of(), null, null));

        NewsApiFetcher fetcher = new NewsApiFetcher(
                newsApiClient,
                properties,
                sourceEndpointRepository,
                articleSourceRepository
        );

        fetcher.fetch(high);

        ArgumentCaptor<String> sourcesCaptor = ArgumentCaptor.forClass(String.class);
        verify(newsApiClient, times(2)).fetchEverything(sourcesCaptor.capture(), anyString(), anyInt());

        // First request includes the top two, normalized to lowercase.
        assertThat(sourcesCaptor.getAllValues().getFirst()).isEqualTo("source-high,source-mid");
    }

    @Test
    void fetch_keepsArticleWhenPublishedAtInvalid_setsNullPublishedDate() throws Exception {
        SourceEndpoint endpoint = endpoint(1L, "source-a");

        when(sourceEndpointRepository.findByEnabledTrueAndKind(SourceKind.API))
                .thenReturn(List.of(endpoint));
        when(articleSourceRepository.countBySourceEndpointIds(anyList()))
                .thenReturn(List.of(new EndpointCount(1L, 1)));

        List<NewsApiArticle> articles = List.of(
                new NewsApiArticle(new NewsApiArticleSource("source-a", "Source A"), null,
                        "Title A1", "Desc A1", "https://a1", null, "invalid-date", null)
        );
        when(newsApiClient.fetchEverything(anyString(), anyString(), anyInt()))
                .thenReturn(new NewsApiEverythingResponse("ok", 1, articles, null, null));

        NewsApiFetcher fetcher = new NewsApiFetcher(
                newsApiClient,
                properties,
                sourceEndpointRepository,
                articleSourceRepository
        );

        List<RawArticle> result = fetcher.fetch(endpoint);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getPublishedDate()).isNull();
    }

    @Test
    void resetBatch_allowsRebuildAndFetchAgain() throws Exception {
        SourceEndpoint endpoint = endpoint(1L, "source-a");

        when(sourceEndpointRepository.findByEnabledTrueAndKind(SourceKind.API))
                .thenReturn(List.of(endpoint));
        when(articleSourceRepository.countBySourceEndpointIds(anyList()))
                .thenReturn(List.of(new EndpointCount(1L, 1)));

        when(newsApiClient.fetchEverything(anyString(), anyString(), anyInt()))
                .thenReturn(new NewsApiEverythingResponse("ok", 0, List.of(), null, null));

        NewsApiFetcher fetcher = new NewsApiFetcher(
                newsApiClient,
                properties,
                sourceEndpointRepository,
                articleSourceRepository
        );

        fetcher.fetch(endpoint);
        fetcher.resetBatch();
        fetcher.fetch(endpoint);

        verify(newsApiClient, times(2)).fetchEverything(anyString(), anyString(), anyInt());
    }

    private SourceEndpoint endpoint(Long id, String sourceId) {
        return SourceEndpoint.builder()
                .id(id)
                .kind(SourceKind.API)
                .displayName("Endpoint " + sourceId)
                .apiProvider("newsapi")
                .apiQuery(sourceId)
                .enabled(true)
                .build();
    }

    private static final class EndpointCount implements ArticleSourceRepository.SourceEndpointCount {
        private final Long sourceEndpointId;
        private final long articleCount;

        private EndpointCount(Long sourceEndpointId, long articleCount) {
            this.sourceEndpointId = sourceEndpointId;
            this.articleCount = articleCount;
        }

        @Override
        public Long getSourceEndpointId() {
            return sourceEndpointId;
        }

        @Override
        public long getArticleCount() {
            return articleCount;
        }
    }
}