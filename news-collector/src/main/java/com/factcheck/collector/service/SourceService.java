package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.SourceKind;
import com.factcheck.collector.dto.SourceCreateRequest;
import com.factcheck.collector.dto.SourceResponse;
import com.factcheck.collector.dto.SourceUpdateRequest;
import com.factcheck.collector.repository.PublisherRepository;
import com.factcheck.collector.repository.SourceEndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SourceService {

    private final SourceEndpointRepository sourceEndpointRepository;
    private final PublisherRepository publisherRepository;

    public List<SourceResponse> listSources() {
        return sourceEndpointRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public SourceResponse createSource(SourceCreateRequest request) {
        Publisher publisher = resolvePublisherForCreate(request);

        SourceEndpoint endpoint = SourceEndpoint.builder()
                .publisher(publisher)
                .kind(request.kind())
                .displayName(request.displayName())
                .rssUrl(request.rssUrl())
                .apiProvider(request.apiProvider())
                .apiQuery(request.apiQuery())
                .enabled(request.enabled() != null ? request.enabled() : true)
                .fetchIntervalMinutes(request.fetchIntervalMinutes() != null ? request.fetchIntervalMinutes() : 30)
                .build();

        validateEndpoint(endpoint);

        try {
            return toResponse(sourceEndpointRepository.save(endpoint));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Source endpoint already exists for " + request.displayName(), e);
        }
    }

    public SourceResponse updateSource(Long id, SourceUpdateRequest request) {
        SourceEndpoint endpoint = sourceEndpointRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source endpoint not found: " + id));

        if (request.publisherId() != null || request.publisherName() != null) {
            Publisher publisher = resolvePublisherForUpdate(request);
            endpoint.setPublisher(publisher);
        }

        if (request.kind() != null) endpoint.setKind(request.kind());
        if (request.displayName() != null) endpoint.setDisplayName(request.displayName());
        if (request.rssUrl() != null) endpoint.setRssUrl(request.rssUrl());
        if (request.apiProvider() != null) endpoint.setApiProvider(request.apiProvider());
        if (request.apiQuery() != null) endpoint.setApiQuery(request.apiQuery());
        if (request.enabled() != null) endpoint.setEnabled(request.enabled());
        if (request.fetchIntervalMinutes() != null) endpoint.setFetchIntervalMinutes(request.fetchIntervalMinutes());

        validateEndpoint(endpoint);

        try {
            return toResponse(sourceEndpointRepository.save(endpoint));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Source endpoint already exists for " + endpoint.getDisplayName(), e);
        }
    }

    private Publisher resolvePublisherForCreate(SourceCreateRequest request) {
        if (request.publisherId() != null) {
            Publisher existing = publisherRepository.findById(request.publisherId())
                    .orElseThrow(() -> new IllegalArgumentException("Publisher not found: " + request.publisherId()));
            return updatePublisherFields(existing, request.publisherName(), request.countryCode(), request.languageCode(),
                    request.biasLabel(), request.reliabilityScore(), request.websiteUrl(), request.mbfcUrl());
        }

        String name = request.publisherName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("publisherName is required when publisherId is not provided");
        }

        Publisher publisher = publisherRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> Publisher.builder().name(name).build());

        return updatePublisherFields(publisher, name, request.countryCode(), request.languageCode(),
                request.biasLabel(), request.reliabilityScore(), request.websiteUrl(), request.mbfcUrl());
    }

    private Publisher resolvePublisherForUpdate(SourceUpdateRequest request) {
        if (request.publisherId() != null) {
            Publisher existing = publisherRepository.findById(request.publisherId())
                    .orElseThrow(() -> new IllegalArgumentException("Publisher not found: " + request.publisherId()));
            return updatePublisherFields(existing, request.publisherName(), request.countryCode(), request.languageCode(),
                    request.biasLabel(), request.reliabilityScore(), request.websiteUrl(), request.mbfcUrl());
        }

        String name = request.publisherName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("publisherName is required when publisherId is not provided");
        }

        Publisher publisher = publisherRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> Publisher.builder().name(name).build());

        return updatePublisherFields(publisher, name, request.countryCode(), request.languageCode(),
                request.biasLabel(), request.reliabilityScore(), request.websiteUrl(), request.mbfcUrl());
    }

    private Publisher updatePublisherFields(
            Publisher publisher,
            String name,
            String countryCode,
            String languageCode,
            String biasLabel,
            Double reliabilityScore,
            String websiteUrl,
            String mbfcUrl
    ) {
        if (name != null && !name.isBlank()) {
            publisher.setName(name);
        }
        if (countryCode != null) publisher.setCountryCode(countryCode);
        if (languageCode != null) publisher.setLanguageCode(languageCode);
        if (biasLabel != null) publisher.setBiasLabel(biasLabel);
        if (reliabilityScore != null) publisher.setReliabilityScore(reliabilityScore);
        if (websiteUrl != null) publisher.setWebsiteUrl(websiteUrl);
        if (mbfcUrl != null) publisher.setMbfcUrl(mbfcUrl);

        return publisherRepository.save(publisher);
    }

    private void validateEndpoint(SourceEndpoint endpoint) {
        if (endpoint.getKind() == SourceKind.RSS) {
            if (endpoint.getRssUrl() == null || endpoint.getRssUrl().isBlank()) {
                throw new IllegalArgumentException("rssUrl is required for RSS endpoints");
            }
        } else if (endpoint.getKind() == SourceKind.API) {
            if (endpoint.getApiProvider() == null || endpoint.getApiProvider().isBlank()) {
                throw new IllegalArgumentException("apiProvider is required for API endpoints");
            }
            if (endpoint.getApiQuery() == null || endpoint.getApiQuery().isBlank()) {
                throw new IllegalArgumentException("apiQuery is required for API endpoints");
            }
        }
    }

    private SourceResponse toResponse(SourceEndpoint s) {
        return new SourceResponse(
                s.getId(),
                s.getPublisher().getId(),
                s.getPublisher().getName(),
                s.getKind(),
                s.getDisplayName(),
                s.getRssUrl(),
                s.getApiProvider(),
                s.getApiQuery(),
                s.isEnabled(),
                s.getFetchIntervalMinutes(),
                s.getLastFetchedAt(),
                s.getLastSuccessAt(),
                s.getFailureCount(),
                s.isRobotsDisallowed(),
                s.getBlockedUntil(),
                s.getBlockReason(),
                s.getBlockCount(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}