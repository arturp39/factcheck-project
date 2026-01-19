package com.factcheck.collector.service.ingestion.admin;

import com.factcheck.collector.domain.entity.IngestionRun;
import com.factcheck.collector.domain.enums.IngestionRunStatus;
import com.factcheck.collector.repository.IngestionLogRepository;
import com.factcheck.collector.repository.IngestionRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionAdminService {

    private final IngestionRunRepository ingestionRunRepository;
    private final IngestionLogRepository ingestionLogRepository;

    @Transactional
    public void abortRun(Long runId, String reason) {
        if (runId == null) {
            throw new IllegalArgumentException("runId is required");
        }

        IngestionRun run = ingestionRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Ingestion run not found: " + runId));
        abortRunInternal(run, reason);
    }

    @Transactional
    public Optional<IngestionRun> abortActiveRun(String reason) {
        Optional<IngestionRun> active =
                ingestionRunRepository.findTopByStatusOrderByStartedAtDesc(IngestionRunStatus.RUNNING);
        if (active.isEmpty()) {
            return Optional.empty();
        }
        IngestionRun run = active.get();
        abortRunInternal(run, reason);
        return Optional.of(run);
    }

    private void abortRunInternal(IngestionRun run, String reason) {
        if (run.getStatus() != IngestionRunStatus.RUNNING) {
            log.info("Abort ignored: ingestion run id={} status={}", run.getId(), run.getStatus());
            return;
        }

        String msg = (reason == null || reason.isBlank())
                ? "Aborted by admin request"
                : reason.trim();

        run.setStatus(IngestionRunStatus.FAILED);
        run.setCompletedAt(Instant.now());
        ingestionRunRepository.save(run);

        int logsUpdated = ingestionLogRepository.failPendingLogsForRun(run.getId(), msg);
        log.warn("Aborted ingestion run id={} pendingLogsUpdated={}", run.getId(), logsUpdated);
    }
}
