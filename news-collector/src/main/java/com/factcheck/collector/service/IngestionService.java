package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.IngestionRun;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.IngestionStatus;
import com.factcheck.collector.repository.SourceEndpointRepository;
import com.factcheck.collector.repository.IngestionRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final SourceEndpointRepository sourceEndpointRepository;
    private final SourceIngestionService sourceIngestionService;
    private final IngestionRunRepository ingestionRunRepository;

    @Value("${ingestion.max-parallel-sources:3}")
    private int maxParallelSources;

    public void ingestAllSources(String correlationId) {
        sourceIngestionService.resetBatchCaches();
        List<SourceEndpoint> endpoints = sourceEndpointRepository.findByEnabledTrue();
        if (endpoints.isEmpty()) {
            log.info("No enabled sources to ingest, correlationId={}", correlationId);
            return;
        }

        UUID correlationUuid = normalizeCorrelationId(correlationId);
        String correlationIdStr = correlationUuid.toString();
        log.info("Starting ingestion for {} sources, correlationId={}",
                endpoints.size(), correlationIdStr);

        IngestionRun run = IngestionRun.builder()
                .status(IngestionStatus.STARTED)
                .correlationId(correlationUuid)
                .build();
        ingestionRunRepository.save(run);

        ExecutorService executor = Executors.newFixedThreadPool(maxParallelSources);
        List<Future<IngestionStatus>> futures = new ArrayList<>();

        for (SourceEndpoint endpoint : endpoints) {
            futures.add(executor.submit(() ->
                    sourceIngestionService.ingestSingleSource(endpoint, correlationUuid, run)));
        }

        executor.shutdown();

        boolean anySuccess = false;
        boolean anyFailure = false;
        boolean anySkipped = false;

        for (Future<IngestionStatus> f : futures) {
            try {
                IngestionStatus status = f.get();
                if (status == IngestionStatus.SUCCESS) {
                    anySuccess = true;
                } else if (status == IngestionStatus.FAILED) {
                    anyFailure = true;
                } else if (status == IngestionStatus.SKIPPED) {
                    anySkipped = true;
                } else if (status == IngestionStatus.PARTIAL) {
                    anySuccess = true;
                    anyFailure = true;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Ingestion interrupted, correlationId={}", correlationIdStr, ie);
                anyFailure = true;
            } catch (ExecutionException ee) {
                log.warn("Ingestion task failed, correlationId={}", correlationIdStr, ee.getCause());
                anyFailure = true;
            }
        }

        if (anyFailure && anySuccess) {
            run.setStatus(IngestionStatus.PARTIAL);
        } else if (anyFailure) {
            run.setStatus(IngestionStatus.FAILED);
        } else if (anySkipped && !anySuccess) {
            run.setStatus(IngestionStatus.SKIPPED);
        } else {
            run.setStatus(IngestionStatus.SUCCESS);
        }
        run.setCompletedAt(java.time.Instant.now());
        ingestionRunRepository.save(run);

        log.info("Ingestion run finished, correlationId={}", correlationIdStr);
    }

    public void ingestSource(Long sourceId, String correlationId) {
        sourceIngestionService.resetBatchCaches();
        SourceEndpoint endpoint = sourceEndpointRepository.findById(sourceId)
                .orElseThrow(() -> new EmptyResultDataAccessException("Source endpoint not found: " + sourceId, 1));

        UUID correlationUuid = normalizeCorrelationId(correlationId);
        String correlationIdStr = correlationUuid.toString();
        log.info("Starting ingestion for sourceEndpointId={} name={} correlationId={}",
                endpoint.getId(), endpoint.getDisplayName(), correlationIdStr);

        sourceIngestionService.ingestSingleSource(endpoint, correlationUuid, null);
    }

    private UUID normalizeCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(correlationId);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid correlationId {}, generating a new UUID", correlationId);
            return UUID.randomUUID();
        }
    }
}