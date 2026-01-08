package com.factcheck.collector.service.catalog;

import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.SourceKind;
import com.factcheck.collector.integration.catalog.newsapi.NewsApiClient;
import com.factcheck.collector.integration.catalog.newsapi.dto.NewsApiSource;
import com.factcheck.collector.integration.catalog.newsapi.dto.NewsApiSourcesResponse;
import com.factcheck.collector.repository.PublisherRepository;
import com.factcheck.collector.repository.SourceEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsApiSourceSyncService {

    private static final String PROVIDER = "newsapi";
    private static final int DEFAULT_FETCH_INTERVAL_MINUTES = 30;

    private final NewsApiClient newsApiClient;
    private final PublisherRepository publisherRepository;
    private final SourceEndpointRepository sourceEndpointRepository;

    /**
     * Sync NewsAPI sources into publishers and source endpoints.
     */
    public NewsApiSyncResult syncSources(String language) {
        String languageCode = normalizeLanguageForRequest(language);

        NewsApiSourcesResponse response = newsApiClient.fetchSources(languageCode);
        List<NewsApiSource> sources = (response != null) ? response.sources() : null;

        if (sources == null || sources.isEmpty()) {
            return new NewsApiSyncResult(0, 0, 0, 0);
        }

        int createdEndpoints = 0;
        int enrichedPublishers = 0;
        int existingOrConcurrent = 0;

        for (NewsApiSource source : sources) {
            PerSourceResult r = syncOneSource(source);
            createdEndpoints += r.createdEndpoints();
            enrichedPublishers += r.enrichedPublishers();
            existingOrConcurrent += r.existingOrConcurrent();
        }

        log.info("NewsAPI sync: fetched={}, createdEndpoints={}, enrichedPublishers={}, existingOrConcurrent={}",
                sources.size(), createdEndpoints, enrichedPublishers, existingOrConcurrent);

        return new NewsApiSyncResult(sources.size(), createdEndpoints, enrichedPublishers, existingOrConcurrent);
    }

    /**
     * Sync a single source in a small transaction.
     */
    @Transactional
    protected PerSourceResult syncOneSource(NewsApiSource source) {
        String sourceId = normalizeSourceId(source != null ? source.id() : null);
        String name = trimToNull(source != null ? source.name() : null);

        if (sourceId == null || name == null) {
            return new PerSourceResult(0, 0, 1);
        }

        Publisher publisher = publisherRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> Publisher.builder().name(name).build());

        boolean wasNew = (publisher.getId() == null);
        boolean enriched = enrichPublisherIfBlank(publisher, source);

        if (wasNew || enriched) {
            publisher = savePublisherHandlingRaces(publisher);
        }

        boolean endpointCreated = createEndpointIfMissing(publisher, name, sourceId);

        int created = endpointCreated ? 1 : 0;
        int enrichedCount = (!wasNew && enriched) ? 1 : 0;
        int existingOrConcurrent = endpointCreated ? 0 : 1;

        return new PerSourceResult(created, enrichedCount, existingOrConcurrent);
    }

    /**
     * Insert the endpoint and ignore uniqueness races.
     */
    private boolean createEndpointIfMissing(Publisher publisher, String displayName, String sourceId) {
        SourceEndpoint endpoint = SourceEndpoint.builder()
                .publisher(publisher)
                .kind(SourceKind.API)
                .displayName(displayName)
                .apiProvider(PROVIDER)
                .apiQuery(sourceId)
                .enabled(true)
                .fetchIntervalMinutes(DEFAULT_FETCH_INTERVAL_MINUTES)
                .build();

        try {
            sourceEndpointRepository.save(endpoint);
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    /**
     * Fill missing publisher fields from NewsAPI.
     */
    private boolean enrichPublisherIfBlank(Publisher publisher, NewsApiSource source) {
        boolean updated = false;

        String countryCode = normalizeCountry(source != null ? source.country() : null);
        if (countryCode != null && isBlank(publisher.getCountryCode())) {
            publisher.setCountryCode(countryCode);
            updated = true;
        }

        String websiteUrl = trimToNull(source != null ? source.url() : null);
        if (websiteUrl != null && isBlank(publisher.getWebsiteUrl())) {
            publisher.setWebsiteUrl(websiteUrl);
            updated = true;
        }

        return updated;
    }

    private Publisher savePublisherHandlingRaces(Publisher publisher) {
        try {
            return publisherRepository.save(publisher);
        } catch (DataIntegrityViolationException ex) {
            // Likely unique(LOWER(name)) race; re-read the existing publisher.
            String name = publisher.getName();
            return publisherRepository.findByNameIgnoreCase(name).orElseThrow(() -> ex);
        }
    }

    private String normalize(String value, java.util.function.UnaryOperator<String> casing, String defaultValue) {
        String trimmed = trimToNull(value);
        if (trimmed == null) return defaultValue;
        return casing.apply(trimmed);
    }

    private String normalizeLanguageForRequest(String language) {
        return normalize(language, s -> s.toLowerCase(Locale.ROOT), "en");
    }

    private String normalizeCountry(String country) {
        return normalize(country, s -> s.toUpperCase(Locale.ROOT), null);
    }

    private String normalizeSourceId(String sourceId) {
        return normalize(sourceId, s -> s.toLowerCase(Locale.ROOT), null);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record NewsApiSyncResult(int fetched, int createdEndpoints, int enrichedPublishers, int existingOrConcurrent) {}

    record PerSourceResult(int createdEndpoints, int enrichedPublishers, int existingOrConcurrent) {}
}