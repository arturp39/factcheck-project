package com.factcheck.collector.service.ingestion;

import com.factcheck.collector.domain.entity.IngestionLog;
import com.factcheck.collector.domain.entity.IngestionRun;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.IngestionRunStatus;
import com.factcheck.collector.domain.enums.IngestionStatus;
import com.factcheck.collector.dto.IngestionRunStartResponse;
import com.factcheck.collector.dto.IngestionTaskRequest;
import com.factcheck.collector.exception.IngestionRunAlreadyRunningException;
import com.factcheck.collector.integration.tasks.TaskPublisher;
import com.factcheck.collector.repository.IngestionLogRepository;
import com.factcheck.collector.repository.IngestionRunRepository;
import com.factcheck.collector.repository.SourceEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionJobRunner {

    private static final Duration MAX_RUN_TIMEOUT = Duration.ofHours(24);

    private final IngestionRunRepository ingestionRunRepository;
    private final IngestionLogRepository ingestionLogRepository;
    private final SourceEndpointRepository sourceEndpointRepository;
    private final TaskPublisher taskPublisher;
    private final PlatformTransactionManager transactionManager;

    @Value("${ingestion.run-timeout:PT6H}")
    private Duration runTimeout;

    public IngestionRunStartResponse startRun(String correlationId) {
        return startRunInternal(correlationId, null);
    }

    public IngestionRunStartResponse startRunForEndpoint(String correlationId, Long sourceEndpointId) {
        return startRunInternal(correlationId, sourceEndpointId);
    }

    private IngestionRunStartResponse startRunInternal(String correlationId, Long sourceEndpointId) {
        Duration timeout = clampRunTimeout(runTimeout);
        Instant now = Instant.now();
        markStaleRunsFailed(now.minus(timeout));

        UUID correlationUuid = normalizeCorrelationId(correlationId);
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        RunPlan plan = txTemplate.execute(status -> {
            IngestionRun run = IngestionRun.builder()
                    .status(IngestionRunStatus.RUNNING)
                    .correlationId(correlationUuid)
                    .build();

            final IngestionRun savedRun;
            try {
                savedRun = ingestionRunRepository.save(run);
            } catch (DataIntegrityViolationException ex) {
                throw new IngestionRunAlreadyRunningException("Another ingestion run is already RUNNING", ex);
            }

            List<SourceEndpoint> endpoints = selectEndpoints(now, sourceEndpointId);
            endpoints = dedupeById(endpoints);

            if (endpoints.isEmpty()) {
                savedRun.setStatus(IngestionRunStatus.COMPLETED);
                savedRun.setCompletedAt(Instant.now());
                ingestionRunRepository.save(savedRun);
                return new RunPlan(savedRun, List.of(), correlationUuid);
            }

            Instant startedAt = Instant.now();
            ingestionLogRepository.saveAll(
                    endpoints.stream()
                            .map(ep -> IngestionLog.builder()
                                    .run(savedRun)
                                    .sourceEndpoint(ep)
                                    .status(IngestionStatus.STARTED)
                                    .correlationId(correlationUuid)
                                    .startedAt(startedAt)
                                    .build())
                            .toList()
            );

            return new RunPlan(savedRun, endpoints, correlationUuid);
        });

        IngestionRun run = plan.run();
        List<SourceEndpoint> endpoints = plan.endpoints();

        if (endpoints.isEmpty()) {
            return new IngestionRunStartResponse(run.getId(), correlationUuid.toString(), 0, run.getStatus().name());
        }

        int enqueued = 0;
        Set<Long> enqueuedEndpointIds = new HashSet<>();

        try {
            for (SourceEndpoint endpoint : endpoints) {
                taskPublisher.enqueueIngestionTask(
                        new IngestionTaskRequest(run.getId(), endpoint.getId(), correlationUuid.toString())
                );
                enqueued++;
                enqueuedEndpointIds.add(endpoint.getId());
            }
        } catch (Exception enqueueFailure) {
            log.error("Failed to enqueue ingestion tasks for runId={} after enqueued={}",
                    run.getId(), enqueued, enqueueFailure);

            txTemplate.executeWithoutResult(status ->
                    markNotEnqueuedLogsFailed(run.getId(), endpoints, enqueuedEndpointIds, enqueueFailure)
            );

            if (enqueued == 0) {
                txTemplate.executeWithoutResult(status -> {
                    IngestionRun managed = ingestionRunRepository.findById(run.getId()).orElse(null);
                    if (managed != null && managed.getStatus() == IngestionRunStatus.RUNNING) {
                        managed.setStatus(IngestionRunStatus.FAILED);
                        managed.setCompletedAt(Instant.now());
                        ingestionRunRepository.save(managed);
                    }
                });
                return new IngestionRunStartResponse(run.getId(), correlationUuid.toString(), 0, IngestionRunStatus.FAILED.name());
            }

            return new IngestionRunStartResponse(run.getId(), correlationUuid.toString(), enqueued, IngestionRunStatus.RUNNING.name());
        }

        return new IngestionRunStartResponse(run.getId(), correlationUuid.toString(), enqueued, IngestionRunStatus.RUNNING.name());
    }

    private void markNotEnqueuedLogsFailed(
            Long runId,
            List<SourceEndpoint> endpoints,
            Set<Long> enqueuedEndpointIds,
            Exception enqueueFailure
    ) {
        Instant completedAt = Instant.now();
        String msg = "Enqueue failed before dispatch: " + safeMessage(enqueueFailure);

        for (SourceEndpoint endpoint : endpoints) {
            if (enqueuedEndpointIds.contains(endpoint.getId())) {
                continue;
            }

            IngestionLog logEntry = ingestionLogRepository
                    .findByRunIdAndSourceEndpointId(runId, endpoint.getId())
                    .orElse(null);

            if (logEntry == null) {
                log.warn("Missing log row for runId={} endpointId={} while marking NOT-enqueued failed", runId, endpoint.getId());
                continue;
            }

            if (logEntry.getCompletedAt() != null) {
                continue;
            }

            logEntry.setStatus(IngestionStatus.FAILED);
            logEntry.setErrorDetails(msg);
            logEntry.setCompletedAt(completedAt);
            ingestionLogRepository.save(logEntry);
        }
    }

    private void markStaleRunsFailed(Instant cutoff) {
        String msg = "Run exceeded timeout and was marked stale/FAILED";
        var staleRuns = ingestionRunRepository.findByStatusAndStartedAtBefore(IngestionRunStatus.RUNNING, cutoff);

        for (IngestionRun run : staleRuns) {
            run.setStatus(IngestionRunStatus.FAILED);
            run.setCompletedAt(Instant.now());
            ingestionRunRepository.save(run);

            // Fail pending logs so runs don't linger.
            int logsUpdated = ingestionLogRepository.failPendingLogsForRun(run.getId(), msg);
            log.warn("Marked stale ingestion run id={} as FAILED; failedPendingLogsUpdated={}", run.getId(), logsUpdated);
        }
    }

    private List<SourceEndpoint> selectEndpoints(Instant now, Long sourceEndpointId) {
        if (sourceEndpointId == null) {
            return sourceEndpointRepository.findEligibleForIngestion(now);
        }

        SourceEndpoint endpoint = sourceEndpointRepository.findById(sourceEndpointId)
                .orElseThrow(() -> new IllegalArgumentException("Source endpoint not found: " + sourceEndpointId));

        if (!isEligible(endpoint, now)) {
            return List.of();
        }
        return List.of(endpoint);
    }

    private List<SourceEndpoint> dedupeById(List<SourceEndpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return List.of();
        }
        Map<Long, SourceEndpoint> map = new LinkedHashMap<>();
        for (SourceEndpoint e : endpoints) {
            if (e != null && e.getId() != null) {
                map.putIfAbsent(e.getId(), e);
            }
        }
        return new ArrayList<>(map.values());
    }

    private boolean isEligible(SourceEndpoint endpoint, Instant now) {
        if (!endpoint.isEnabled()) {
            return false;
        }
        Instant blockedUntil = endpoint.getBlockedUntil();
        if (blockedUntil != null && blockedUntil.isAfter(now)) {
            return false;
        }
        Instant lastFetched = endpoint.getLastFetchedAt();
        if (lastFetched == null) {
            return true;
        }
        Duration interval = Duration.ofMinutes(endpoint.getFetchIntervalMinutes());
        return lastFetched.isBefore(now.minus(interval));
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

    private Duration clampRunTimeout(Duration configured) {
        if (configured == null || configured.isZero() || configured.isNegative()) {
            return Duration.ofHours(6);
        }
        if (configured.compareTo(MAX_RUN_TIMEOUT) > 0) {
            return MAX_RUN_TIMEOUT;
        }
        return configured;
    }

    private String safeMessage(Throwable t) {
        if (t == null) {
            return "unknown error";
        }
        String msg = t.getMessage();
        return (msg == null || msg.isBlank()) ? t.getClass().getSimpleName() : msg;
    }

    private record RunPlan(IngestionRun run, List<SourceEndpoint> endpoints, UUID correlationId) {
    }
}