package com.factcheck.collector.service.ingestion.query;

import com.factcheck.collector.domain.entity.IngestionLog;
import com.factcheck.collector.domain.entity.IngestionRun;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.dto.IngestionLogPageResponse;
import com.factcheck.collector.dto.IngestionRunDetailResponse;
import com.factcheck.collector.dto.IngestionRunResponse;
import com.factcheck.collector.repository.IngestionLogRepository;
import com.factcheck.collector.repository.IngestionRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IngestionQueryService {

    private final IngestionLogRepository ingestionLogRepository;
    private final IngestionRunRepository ingestionRunRepository;

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

    public IngestionRunDetailResponse getRun(Long id) {
        IngestionRun run = ingestionRunRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ingestion run not found: " + id));

        String status = run.getStatus() != null ? run.getStatus().name() : null;
        String correlationId = run.getCorrelationId() != null ? run.getCorrelationId().toString() : null;

        return new IngestionRunDetailResponse(
                run.getId(),
                run.getStartedAt(),
                run.getCompletedAt(),
                status,
                correlationId
        );
    }

    private IngestionRunResponse toResponse(IngestionLog log) {
        SourceEndpoint endpoint = log.getSourceEndpoint();
        Long sourceEndpointId = endpoint != null ? endpoint.getId() : null;
        String sourceEndpointName = endpoint != null ? endpoint.getDisplayName() : null;
        Long publisherId = endpoint != null ? endpoint.getPublisher().getId() : null;
        String publisherName = endpoint != null ? endpoint.getPublisher().getName() : null;
        String status = log.getStatus() != null ? log.getStatus().name() : null;

        String correlationId = log.getCorrelationId() != null ? log.getCorrelationId().toString() : null;

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
                correlationId
        );
    }
}