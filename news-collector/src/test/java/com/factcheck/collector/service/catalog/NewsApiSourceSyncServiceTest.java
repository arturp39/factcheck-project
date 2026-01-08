package com.factcheck.collector.service.catalog;

import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.SourceKind;
import com.factcheck.collector.integration.catalog.newsapi.NewsApiClient;
import com.factcheck.collector.integration.catalog.newsapi.dto.NewsApiSource;
import com.factcheck.collector.repository.PublisherRepository;
import com.factcheck.collector.repository.SourceEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for NewsApiSourceSyncService.syncOneSource().
 */
@ExtendWith(MockitoExtension.class)
class NewsApiSourceSyncServiceTest {

    @Mock
    private NewsApiClient newsApiClient;

    @Mock
    private PublisherRepository publisherRepository;

    @Mock
    private SourceEndpointRepository sourceEndpointRepository;

    @InjectMocks
    private NewsApiSourceSyncService service;

    @BeforeEach
    void setup() {
    }

    @Test
    void syncOneSource_skips_whenSourceIdMissing() {
        NewsApiSource source = new NewsApiSource(
                null,
                "BBC News",
                null,
                "https://www.bbc.co.uk",
                null,
                "en",
                "gb"
        );

        var result = invokeSyncOneSource(source);

        assertEquals(0, result.createdEndpoints());
        assertEquals(0, result.enrichedPublishers());
        assertEquals(1, result.existingOrConcurrent());

        verifyNoInteractions(publisherRepository);
        verifyNoInteractions(sourceEndpointRepository);
    }

    @Test
    void syncOneSource_skips_whenNameMissing() {
        NewsApiSource source = new NewsApiSource(
                "bbc-news",
                "   ",
                null,
                "https://www.bbc.co.uk",
                null,
                "en",
                "gb"
        );

        var result = invokeSyncOneSource(source);

        assertEquals(0, result.createdEndpoints());
        assertEquals(0, result.enrichedPublishers());
        assertEquals(1, result.existingOrConcurrent());

        verifyNoInteractions(publisherRepository);
        verifyNoInteractions(sourceEndpointRepository);
    }

    @Test
    void syncOneSource_createsNewPublisher_andCreatesEndpoint() {
        NewsApiSource source = mockSource("bbc-news", "BBC News", "gb", "en", "https://www.bbc.co.uk");

        when(publisherRepository.findByNameIgnoreCase("BBC News"))
                .thenReturn(Optional.empty());

        Publisher saved = Publisher.builder()
                .id(1L)
                .name("BBC News")
                .countryCode("GB")
                .websiteUrl("https://www.bbc.co.uk")
                .build();

        when(publisherRepository.save(any(Publisher.class))).thenReturn(saved);

        when(sourceEndpointRepository.save(any(SourceEndpoint.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = invokeSyncOneSource(source);

        assertEquals(1, result.createdEndpoints());
        assertEquals(0, result.enrichedPublishers());
        assertEquals(0, result.existingOrConcurrent());

        verify(publisherRepository, times(1)).findByNameIgnoreCase("BBC News");
        verify(publisherRepository, times(1)).save(any(Publisher.class));
        verify(sourceEndpointRepository, times(1)).save(argThat(ep ->
                ep.getPublisher() != null
                        && ep.getPublisher().getId().equals(1L)
                        && ep.getKind() == SourceKind.API
                        && "newsapi".equals(ep.getApiProvider())
                        && "bbc-news".equals(ep.getApiQuery())
                        && ep.isEnabled()
                        && ep.getFetchIntervalMinutes() == 30
        ));
    }

    @Test
    void syncOneSource_enrichesExistingPublisher_whenFieldsBlank_andCreatesEndpoint() {
        NewsApiSource source = mockSource("montreal-gazette", "Montreal Gazette", "ca", "en", "https://montrealgazette.com");

        Publisher existing = Publisher.builder()
                .id(10L)
                .name("Montreal Gazette")
                .countryCode(null)
                .websiteUrl(null)
                .build();

        when(publisherRepository.findByNameIgnoreCase("Montreal Gazette"))
                .thenReturn(Optional.of(existing));

        when(publisherRepository.save(any(Publisher.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(sourceEndpointRepository.save(any(SourceEndpoint.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = invokeSyncOneSource(source);

        assertEquals(1, result.createdEndpoints());
        assertEquals(1, result.enrichedPublishers());
        assertEquals(0, result.existingOrConcurrent());

        verify(publisherRepository, times(1)).save(argThat(p ->
                p.getId().equals(10L)
                        && "CA".equals(p.getCountryCode())
                        && "https://montrealgazette.com".equals(p.getWebsiteUrl())
        ));
        verify(sourceEndpointRepository, times(1)).save(any(SourceEndpoint.class));
    }

    @Test
    void syncOneSource_doesNotSavePublisher_whenNoEnrichment_andEndpointAlreadyExists() {
        NewsApiSource source = mockSource("wwbt-richmond", "WWBT - Richmond News", "us", "en", "https://nbc12.com");

        Publisher existing = Publisher.builder()
                .id(20L)
                .name("WWBT - Richmond News")
                .countryCode("US")
                .websiteUrl("https://nbc12.com")
                .build();

        when(publisherRepository.findByNameIgnoreCase("WWBT - Richmond News"))
                .thenReturn(Optional.of(existing));

        // Simulate a unique constraint on endpoint insert.
        when(sourceEndpointRepository.save(any(SourceEndpoint.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        var result = invokeSyncOneSource(source);

        assertEquals(0, result.createdEndpoints());
        assertEquals(0, result.enrichedPublishers());
        assertEquals(1, result.existingOrConcurrent());

        verify(publisherRepository, never()).save(any(Publisher.class));
        verify(sourceEndpointRepository, times(1)).save(any(SourceEndpoint.class));
    }

    @Test
    void syncOneSource_handlesPublisherInsertRace_byRereadingExisting() {
        NewsApiSource source = mockSource("cnn", "CNN", "us", "en", "https://cnn.com");

        when(publisherRepository.findByNameIgnoreCase("CNN"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(Publisher.builder().id(33L).name("CNN").build()));

        when(publisherRepository.save(any(Publisher.class)))
                .thenThrow(new DataIntegrityViolationException("unique lower(name) violation"));

        when(sourceEndpointRepository.save(any(SourceEndpoint.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = invokeSyncOneSource(source);

        assertEquals(1, result.createdEndpoints());
        assertEquals(0, result.enrichedPublishers());
        assertEquals(0, result.existingOrConcurrent());

        verify(publisherRepository, times(2)).findByNameIgnoreCase("CNN");
        verify(publisherRepository, times(1)).save(any(Publisher.class));
        verify(sourceEndpointRepository, times(1)).save(argThat(ep ->
                ep.getPublisher() != null
                        && ep.getPublisher().getId().equals(33L)
                        && "newsapi".equals(ep.getApiProvider())
                        && "cnn".equals(ep.getApiQuery())
        ));
    }

    // Helpers.

    private NewsApiSourceSyncService.PerSourceResult invokeSyncOneSource(NewsApiSource source) {
        // Protected access is allowed in the same package.
        return service.syncOneSource(source);
    }

    private NewsApiSource mockSource(String id, String name, String country, String language, String url) {
        return new NewsApiSource(
                id,
                name,
                null,
                url,
                null,
                language,
                country
        );
    }
}