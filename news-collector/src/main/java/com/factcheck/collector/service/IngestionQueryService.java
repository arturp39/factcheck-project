package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.IngestionLog;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.dto.IngestionLogPageResponse;
import com.factcheck.collector.dto.IngestionRunResponse;
import com.factcheck.collector.repository.IngestionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IngestionQueryService {

    private final IngestionLogRepository ingestionLogRepository;

    public IngestionLogPageResponse listLogs(int page, int size) {
        Page<IngestionLog> result = ingestionLogRepository.findAll(PageRequest.of(page, size));
        var items = result.getContent().stream().map(this::toResponse).toList();
        return new IngestionLogPageResponse(
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages(),
                items
        );
    }

    public IngestionRunResponse getRun(Long id) {
        IngestionLog run = ingestionLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ingestion run not found: " + id));
        return toResponse(run);
    }

    private IngestionRunResponse toResponse(IngestionLog log) {
        SourceEndpoint endpoint = log.getSourceEndpoint();
        Long sourceEndpointId = endpoint != null ? endpoint.getId() : null;
        String sourceEndpointName = endpoint != null ? endpoint.getDisplayName() : null;
        Long publisherId = endpoint != null ? endpoint.getPublisher().getId() : null;
        String publisherName = endpoint != null ? endpoint.getPublisher().getName() : null;
        String status = log.getStatus() != null ? log.getStatus().name() : null;

        return new IngestionRunResponse(
                log.getId(),
                sourceEndpointId,
                sourceEndpointName,
                publisherId,
                publisherName,
                log.getStartedAt(),
                log.getCompletedAt(),
                log.getArticlesFetched(),
                log.getArticlesProcessed(),
                log.getArticlesFailed(),
                status,
                log.getErrorDetails(),
                log.getCorrelationId()
        );
    }
}