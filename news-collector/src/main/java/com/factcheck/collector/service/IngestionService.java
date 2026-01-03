package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.repository.SourceEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final SourceEndpointRepository sourceEndpointRepository;
    private final SourceIngestionService sourceIngestionService;

    @Value("${ingestion.max-parallel-sources:3}")
    private int maxParallelSources;

    public void ingestAllSources(String correlationId) {
        List<SourceEndpoint> endpoints = sourceEndpointRepository.findByEnabledTrue();
        if (endpoints.isEmpty()) {
            log.info("No enabled sources to ingest, correlationId={}", correlationId);
            return;
        }

        log.info("Starting ingestion for {} sources, correlationId={}",
                endpoints.size(), correlationId);

        ExecutorService executor = Executors.newFixedThreadPool(maxParallelSources);
        List<Future<?>> futures = new ArrayList<>();

        for (SourceEndpoint endpoint : endpoints) {
            futures.add(executor.submit(() ->
                    sourceIngestionService.ingestSingleSource(endpoint, correlationId)));
        }

        executor.shutdown();

        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Ingestion interrupted, correlationId={}", correlationId, ie);
            } catch (ExecutionException ee) {
                log.warn("Ingestion task failed, correlationId={}", correlationId, ee.getCause());
            }
        }

        log.info("Ingestion run finished, correlationId={}", correlationId);
    }

    public void ingestSource(Long sourceId, String correlationId) {
        SourceEndpoint endpoint = sourceEndpointRepository.findById(sourceId)
                .orElseThrow(() -> new EmptyResultDataAccessException("Source endpoint not found: " + sourceId, 1));

        log.info("Starting ingestion for sourceEndpointId={} name={} correlationId={}",
                endpoint.getId(), endpoint.getDisplayName(), correlationId);

        sourceIngestionService.ingestSingleSource(endpoint, correlationId);
    }
}