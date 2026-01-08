package com.factcheck.collector.service.catalog;

import com.factcheck.collector.domain.entity.MbfcSource;
import com.factcheck.collector.dto.MbfcSourceCreateRequest;
import com.factcheck.collector.dto.MbfcSourceResponse;
import com.factcheck.collector.dto.MbfcSourceUpdateRequest;
import com.factcheck.collector.repository.MbfcSourceRepository;
import com.factcheck.collector.repository.PublisherRepository;
import com.factcheck.collector.util.DomainUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MbfcSourceService {

    private final MbfcSourceRepository mbfcSourceRepository;
    private final PublisherRepository publisherRepository;

    public List<MbfcSourceResponse> listSources() {
        return mbfcSourceRepository.findAll(Sort.by(Sort.Direction.ASC, "mbfcSourceId"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public MbfcSourceResponse getSource(Long id) {
        return toResponse(getSourceEntity(id));
    }

    public MbfcSourceResponse createSource(MbfcSourceCreateRequest request) {
        Long id = request.mbfcSourceId();
        if (mbfcSourceRepository.existsById(id)) {
            throw new IllegalArgumentException("MBFC source already exists: " + id);
        }

        String sourceName = requireNotBlank(request.sourceName(), "sourceName");
        String mbfcUrl = requireNotBlank(request.mbfcUrl(), "mbfcUrl");
        String sourceUrl = trimToNull(request.sourceUrl());

        MbfcSource source = MbfcSource.builder()
                .mbfcSourceId(id)
                .sourceName(sourceName)
                .mbfcUrl(mbfcUrl)
                .bias(trimToNull(request.bias()))
                .country(trimToNull(request.country()))
                .factualReporting(trimToNull(request.factualReporting()))
                .mediaType(trimToNull(request.mediaType()))
                .sourceUrl(sourceUrl)
                .sourceUrlDomain(resolveDomain(sourceUrl, request.sourceUrlDomain()))
                .credibility(trimToNull(request.credibility()))
                .syncedAt(Instant.now())
                .build();

        MbfcSource saved = saveSource(source, "MBFC source already exists for mbfcUrl: " + mbfcUrl);
        return toResponse(saved);
    }

    public MbfcSourceResponse updateSource(Long id, MbfcSourceUpdateRequest request) {
        MbfcSource source = getSourceEntity(id);

        if (request.sourceName() != null) {
            source.setSourceName(requireNotBlank(request.sourceName(), "sourceName"));
        }
        if (request.mbfcUrl() != null) {
            source.setMbfcUrl(requireNotBlank(request.mbfcUrl(), "mbfcUrl"));
        }
        if (request.bias() != null) {
            source.setBias(trimToNull(request.bias()));
        }
        if (request.country() != null) {
            source.setCountry(trimToNull(request.country()));
        }
        if (request.factualReporting() != null) {
            source.setFactualReporting(trimToNull(request.factualReporting()));
        }
        if (request.mediaType() != null) {
            source.setMediaType(trimToNull(request.mediaType()));
        }
        if (request.sourceUrl() != null) {
            source.setSourceUrl(trimToNull(request.sourceUrl()));
        }
        if (request.sourceUrlDomain() != null) {
            source.setSourceUrlDomain(resolveDomain(null, request.sourceUrlDomain()));
        } else if (request.sourceUrl() != null) {
            source.setSourceUrlDomain(resolveDomain(source.getSourceUrl(), null));
        }
        if (request.credibility() != null) {
            source.setCredibility(trimToNull(request.credibility()));
        }

        source.setSyncedAt(Instant.now());

        MbfcSource saved = saveSource(source, "MBFC source already exists for mbfcUrl: " + source.getMbfcUrl());
        return toResponse(saved);
    }

    public void deleteSource(Long id) {
        MbfcSource source = getSourceEntity(id);
        if (publisherRepository.existsByMbfcSource(source)) {
            throw new IllegalArgumentException("MBFC source is still referenced by publishers: " + id);
        }
        mbfcSourceRepository.delete(source);
    }

    private MbfcSource getSourceEntity(Long id) {
        return mbfcSourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MBFC source not found: " + id));
    }

    private MbfcSource saveSource(MbfcSource source, String message) {
        try {
            return mbfcSourceRepository.save(source);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException(message, e);
        }
    }

    private MbfcSourceResponse toResponse(MbfcSource source) {
        return new MbfcSourceResponse(
                source.getMbfcSourceId(),
                source.getSourceName(),
                source.getMbfcUrl(),
                source.getBias(),
                source.getCountry(),
                source.getFactualReporting(),
                source.getMediaType(),
                source.getSourceUrl(),
                source.getSourceUrlDomain(),
                source.getCredibility(),
                source.getSyncedAt()
        );
    }

    private String resolveDomain(String sourceUrl, String sourceUrlDomain) {
        if (sourceUrlDomain != null) {
            return DomainUtils.normalizeDomain(sourceUrlDomain);
        }
        return DomainUtils.normalizeDomain(sourceUrl);
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