package com.factcheck.collector.service.catalog;

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
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
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

        normalizeByKind(endpoint);
        validateEndpoint(endpoint);

        return saveEndpoint(endpoint, request.displayName());
    }

    @Transactional
    public SourceResponse updateSource(Long id, SourceUpdateRequest request) {
        SourceEndpoint endpoint = sourceEndpointRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source endpoint not found: " + id));

        boolean publisherTouched =
                request.publisherId() != null
                        || request.publisherName() != null
                        || request.countryCode() != null
                        || request.websiteUrl() != null;

        if (publisherTouched) {
            Publisher publisher = resolvePublisherForUpdate(endpoint, request);
            endpoint.setPublisher(publisher);
        }

        if (request.kind() != null) endpoint.setKind(request.kind());
        if (request.displayName() != null) endpoint.setDisplayName(request.displayName());
        if (request.rssUrl() != null) endpoint.setRssUrl(request.rssUrl());
        if (request.apiProvider() != null) endpoint.setApiProvider(request.apiProvider());
        if (request.apiQuery() != null) endpoint.setApiQuery(request.apiQuery());
        if (request.enabled() != null) endpoint.setEnabled(request.enabled());
        if (request.fetchIntervalMinutes() != null) endpoint.setFetchIntervalMinutes(request.fetchIntervalMinutes());

        normalizeByKind(endpoint);
        validateEndpoint(endpoint);

        return saveEndpoint(endpoint, endpoint.getDisplayName());
    }

    private SourceResponse saveEndpoint(SourceEndpoint endpoint, String nameForError) {
        try {
            return toResponse(sourceEndpointRepository.save(endpoint));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Source endpoint already exists for " + nameForError, e);
        }
    }

    private Publisher resolvePublisherForCreate(SourceCreateRequest request) {
        return resolvePublisher(
                request.publisherId(),
                request.publisherName(),
                null,
                request.countryCode(),
                request.websiteUrl(),
                true
        );
    }

    private Publisher resolvePublisherForUpdate(SourceEndpoint endpoint, SourceUpdateRequest request) {
        return resolvePublisher(
                request.publisherId(),
                request.publisherName(),
                endpoint.getPublisher(),
                request.countryCode(),
                request.websiteUrl(),
                false
        );
    }

    private Publisher resolvePublisher(
            Long publisherId,
            String publisherName,
            Publisher fallbackPublisher,
            String countryCode,
            String websiteUrl,
            boolean requireNameIfNoId
    ) {
        Publisher publisher;

        if (publisherId != null) {
            publisher = publisherRepository.findById(publisherId)
                    .orElseThrow(() -> new IllegalArgumentException("Publisher not found: " + publisherId));
        } else if (publisherName != null && !publisherName.isBlank()) {
            publisher = publisherRepository.findByNameIgnoreCase(publisherName)
                    .orElseGet(() -> Publisher.builder().name(publisherName).build());
        } else {
            if (requireNameIfNoId) {
                throw new IllegalArgumentException("publisherName is required when publisherId is not provided");
            }
            if (fallbackPublisher == null) {
                throw new IllegalArgumentException("Publisher is required");
            }
            publisher = fallbackPublisher;
        }

        applyPublisherFields(publisher, publisherName, countryCode, websiteUrl);
        return publisherRepository.save(publisher);
    }

    private void applyPublisherFields(Publisher publisher, String name, String countryCode, String websiteUrl) {
        if (name != null && !name.isBlank()) {
            publisher.setName(name);
        }
        if (countryCode != null) publisher.setCountryCode(countryCode);
        if (websiteUrl != null) publisher.setWebsiteUrl(websiteUrl);
    }

    private void normalizeByKind(SourceEndpoint endpoint) {
        if (endpoint.getKind() == SourceKind.RSS) {
            endpoint.setApiProvider(null);
            endpoint.setApiQuery(null);
        } else if (endpoint.getKind() == SourceKind.API) {
            endpoint.setRssUrl(null);
        }
    }

    private void validateEndpoint(SourceEndpoint endpoint) {
        if (endpoint.getKind() == null) {
            throw new IllegalArgumentException("kind is required");
        }

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