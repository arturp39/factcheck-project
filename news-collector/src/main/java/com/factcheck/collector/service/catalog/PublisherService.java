package com.factcheck.collector.service.catalog;

import com.factcheck.collector.domain.entity.MbfcSource;
import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.dto.PublisherCreateRequest;
import com.factcheck.collector.dto.PublisherResponse;
import com.factcheck.collector.dto.PublisherUpdateRequest;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.repository.MbfcSourceRepository;
import com.factcheck.collector.repository.PublisherRepository;
import com.factcheck.collector.repository.SourceEndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PublisherService {

    private final PublisherRepository publisherRepository;
    private final MbfcSourceRepository mbfcSourceRepository;
    private final SourceEndpointRepository sourceEndpointRepository;
    private final ArticleRepository articleRepository;

    public List<PublisherResponse> listPublishers() {
        return publisherRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public PublisherResponse getPublisher(Long id) {
        return toResponse(getPublisherEntity(id));
    }

    public PublisherResponse createPublisher(PublisherCreateRequest request) {
        String name = requireNotBlank(request.name(), "name");
        if (publisherRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new IllegalArgumentException("Publisher already exists: " + name);
        }

        Publisher publisher = Publisher.builder()
                .name(name)
                .countryCode(trimToNull(request.countryCode()))
                .websiteUrl(trimToNull(request.websiteUrl()))
                .build();

        if (request.mbfcSourceId() != null) {
            publisher.setMbfcSource(getMbfcSource(request.mbfcSourceId()));
        }

        Publisher saved = savePublisher(publisher, "Publisher already exists: " + name);
        return toResponse(saved);
    }

    public PublisherResponse updatePublisher(Long id, PublisherUpdateRequest request) {
        Publisher publisher = getPublisherEntity(id);

        if (request.name() != null) {
            String name = requireNotBlank(request.name(), "name");
            if (!name.equalsIgnoreCase(publisher.getName())
                    && publisherRepository.findByNameIgnoreCase(name).isPresent()) {
                throw new IllegalArgumentException("Publisher already exists: " + name);
            }
            publisher.setName(name);
        }

        if (request.countryCode() != null) {
            publisher.setCountryCode(trimToNull(request.countryCode()));
        }
        if (request.websiteUrl() != null) {
            publisher.setWebsiteUrl(trimToNull(request.websiteUrl()));
        }
        if (request.mbfcSourceId() != null) {
            publisher.setMbfcSource(getMbfcSource(request.mbfcSourceId()));
        }

        Publisher saved = savePublisher(publisher, "Publisher already exists: " + publisher.getName());
        return toResponse(saved);
    }

    public void deletePublisher(Long id) {
        Publisher publisher = getPublisherEntity(id);
        if (sourceEndpointRepository.existsByPublisher(publisher)) {
            throw new IllegalArgumentException("Publisher has source endpoints and cannot be deleted: " + id);
        }
        if (articleRepository.existsByPublisher(publisher)) {
            throw new IllegalArgumentException("Publisher has articles and cannot be deleted: " + id);
        }
        publisherRepository.delete(publisher);
    }

    private Publisher getPublisherEntity(Long id) {
        return publisherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Publisher not found: " + id));
    }

    private MbfcSource getMbfcSource(Long id) {
        return mbfcSourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MBFC source not found: " + id));
    }

    private Publisher savePublisher(Publisher publisher, String message) {
        try {
            return publisherRepository.save(publisher);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException(message, e);
        }
    }

    private PublisherResponse toResponse(Publisher publisher) {
        Long mbfcSourceId = publisher.getMbfcSource() != null
                ? publisher.getMbfcSource().getMbfcSourceId()
                : null;
        return new PublisherResponse(
                publisher.getId(),
                publisher.getName(),
                publisher.getCountryCode(),
                publisher.getWebsiteUrl(),
                mbfcSourceId,
                publisher.getCreatedAt(),
                publisher.getUpdatedAt()
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String requireNotBlank(String value, String fieldName) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}