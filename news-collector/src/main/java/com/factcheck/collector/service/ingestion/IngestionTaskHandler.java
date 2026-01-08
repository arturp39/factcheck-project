package com.factcheck.collector.service.ingestion;

import com.factcheck.collector.domain.entity.IngestionLog;
import com.factcheck.collector.domain.entity.IngestionRun;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.IngestionRunStatus;
import com.factcheck.collector.domain.enums.IngestionStatus;
import com.factcheck.collector.dto.IngestionTaskRequest;
import com.factcheck.collector.repository.IngestionLogRepository;
import com.factcheck.collector.repository.IngestionRunRepository;
import com.factcheck.collector.repository.SourceEndpointRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionTaskHandler {

    private final IngestionRunRepository ingestionRunRepository;
    private final IngestionLogRepository ingestionLogRepository;
    private final SourceEndpointRepository sourceEndpointRepository;
    private final EndpointIngestionJob endpointIngestionJob;
    private final TransactionTemplate txTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${ingestion.task-lease-seconds:1800}")
    private long taskLeaseSeconds;

    public void handle(IngestionTaskRequest request) {
        if (request == null || request.runId() == null || request.sourceEndpointId() == null) {
            throw new IllegalArgumentException("runId and sourceEndpointId are required");
        }

        TaskContext ctx = txTemplate.execute(status -> claimOrSkip(request));
        if (ctx == null) {
            return;
        }

        try {
            endpointIngestionJob.ingestSingleSource(
                    ctx.endpoint(),
                    ctx.effectiveCorrelationId(),
                    ctx.run(),
                    ctx.logEntry()
            );
        } catch (Exception e) {
            log.error("Task failed for runId={} sourceEndpointId={}", ctx.run().getId(), ctx.endpoint().getId(), e);


            txTemplate.executeWithoutResult(status -> markLogFailed(ctx.run().getId(), ctx.endpoint().getId(), ctx.effectiveCorrelationId(), e));
        }


        txTemplate.executeWithoutResult(status -> tryCompleteRun(ctx.run().getId()));
    }

    private TaskContext claimOrSkip(IngestionTaskRequest request) {
        IngestionRun run = ingestionRunRepository.findById(request.runId()).orElse(null);
        if (run == null) {
            log.warn("Skipping task because run not found: {}", request.runId());
            return null;
        }

        SourceEndpoint endpoint = sourceEndpointRepository.findById(request.sourceEndpointId()).orElse(null);
        if (endpoint == null) {
            log.warn("Skipping task because source endpoint not found: {}", request.sourceEndpointId());
            return null;
        }

        UUID incomingCorrelationId = normalizeCorrelationId(
                (request.correlationId() != null && !request.correlationId().isBlank())
                        ? request.correlationId()
                        : (run.getCorrelationId() != null ? run.getCorrelationId().toString() : null)
        );

        IngestionLog logEntry = findOrCreateLog(run, endpoint, incomingCorrelationId);

        if (run.getStatus() != IngestionRunStatus.RUNNING) {
            completeLogAsSkippedIfPending(logEntry, "Run is not RUNNING (status=" + run.getStatus() + ")");
            return null;
        }

        if (logEntry.getCompletedAt() != null) {
            log.info("Skipping task because log already completed for runId={} sourceEndpointId={}",
                    run.getId(), endpoint.getId());
            return null;
        }

        UUID effectiveCorrelationId = incomingCorrelationId;
        if (logEntry.getCorrelationId() == null) {
            logEntry.setCorrelationId(incomingCorrelationId);
            ingestionLogRepository.save(logEntry);
        } else if (!logEntry.getCorrelationId().equals(incomingCorrelationId)) {
            log.warn("CorrelationId mismatch for runId={} sourceEndpointId={} existing={} incoming={}",
                    run.getId(), endpoint.getId(), logEntry.getCorrelationId(), incomingCorrelationId);
            effectiveCorrelationId = logEntry.getCorrelationId();
        }

        int claimed = ingestionLogRepository.claimLog(run.getId(), endpoint.getId(), taskLeaseSeconds);
        if (claimed != 1) {
            log.info("Skipping task because it is already being processed or completed for runId={} sourceEndpointId={}",
                    run.getId(), endpoint.getId());
            return null;
        }

        entityManager.refresh(logEntry);

        return new TaskContext(run, endpoint, logEntry, effectiveCorrelationId);
    }

    private IngestionLog findOrCreateLog(IngestionRun run, SourceEndpoint endpoint, UUID correlationId) {
        IngestionLog existing = ingestionLogRepository
                .findByRunIdAndSourceEndpointId(run.getId(), endpoint.getId())
                .orElse(null);

        if (existing != null) {
            return existing;
        }

        IngestionLog newEntry = IngestionLog.builder()
                .run(run)
                .sourceEndpoint(endpoint)
                .correlationId(correlationId)
                .status(IngestionStatus.STARTED)
                .startedAt(Instant.now())
                .build();

        try {
            return ingestionLogRepository.save(newEntry);
        } catch (DataIntegrityViolationException ex) {
            return ingestionLogRepository
                    .findByRunIdAndSourceEndpointId(run.getId(), endpoint.getId())
                    .orElseThrow(() -> ex);
        }
    }

    private void completeLogAsSkippedIfPending(IngestionLog logEntry, String reason) {
        if (logEntry.getCompletedAt() != null) {
            return;
        }
        logEntry.setStatus(IngestionStatus.SKIPPED);
        logEntry.setErrorDetails(reason);
        logEntry.setCompletedAt(Instant.now());
        ingestionLogRepository.save(logEntry);
    }

    private void markLogFailed(Long runId, Long endpointId, UUID correlationId, Exception e) {
        IngestionLog target = ingestionLogRepository
                .findByRunIdAndSourceEndpointId(runId, endpointId)
                .orElse(null);

        if (target == null) {
            log.warn("Missing log row while marking failed runId={} endpointId={}", runId, endpointId);
            return;
        }

        if (target.getCompletedAt() != null) {
            return;
        }

        target.setStatus(IngestionStatus.FAILED);
        target.setErrorDetails(e.getMessage());
        target.setCompletedAt(Instant.now());
        if (target.getCorrelationId() == null) {
            target.setCorrelationId(correlationId);
        }
        ingestionLogRepository.save(target);
    }

    private void tryCompleteRun(Long runId) {
        if (ingestionLogRepository.existsByRunIdAndCompletedAtIsNull(runId)) {
            return;
        }

        List<IngestionLog> logs = ingestionLogRepository.findByRunId(runId);
        if (logs.isEmpty()) {
            return;
        }

        int successCount = 0;
        int partialCount = 0;
        int failureCount = 0;

        for (IngestionLog logEntry : logs) {
            IngestionStatus status = logEntry.getStatus();
            if (status == IngestionStatus.SUCCESS) {
                successCount++;
                continue;
            }
            if (status == IngestionStatus.PARTIAL) {
                partialCount++;
                continue;
            }
            if (status == IngestionStatus.FAILED) {
                if (!isIgnoredFailure(logEntry.getErrorDetails())) {
                    failureCount++;
                }
            }
        }

        IngestionRun run = ingestionRunRepository.findById(runId).orElse(null);
        if (run == null || run.getStatus() != IngestionRunStatus.RUNNING) {
            return;
        }

        IngestionRunStatus finalStatus;
        if (successCount == 0 && partialCount == 0) {
            finalStatus = IngestionRunStatus.FAILED;
        } else if (failureCount == 0 && partialCount == 0) {
            finalStatus = IngestionRunStatus.COMPLETED;
        } else {
            finalStatus = IngestionRunStatus.PARTIAL;
        }

        run.setStatus(finalStatus);
        run.setCompletedAt(Instant.now());
        ingestionRunRepository.save(run);
        log.info("Finalize run {} runId={} success={} partial={} failed={}",
                finalStatus, runId, successCount, partialCount, failureCount);
    }

    private boolean isIgnoredFailure(String errorDetails) {
        if (errorDetails == null || errorDetails.isBlank()) {
            return false;
        }
        String msg = errorDetails.toLowerCase(java.util.Locale.ROOT);
        for (String token : IGNORED_ERROR_TOKENS) {
            if (msg.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static final List<String> IGNORED_ERROR_TOKENS = List.of(
            "robots.txt disallows",
            "source blocked until",
            "too many chunks for embedding",
            "too many texts in one request",
            "unprocessable entity",
            "nlp embed failed"
    );

    private UUID normalizeCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(correlationId);
        } catch (IllegalArgumentException ex) {
            return UUID.randomUUID();
        }
    }

    private record TaskContext(IngestionRun run, SourceEndpoint endpoint, IngestionLog logEntry, UUID effectiveCorrelationId) {
    }
}