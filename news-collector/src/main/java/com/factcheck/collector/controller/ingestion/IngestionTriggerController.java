package com.factcheck.collector.controller.ingestion;

import com.factcheck.collector.dto.IngestionRunRequest;
import com.factcheck.collector.dto.IngestionRunStartResponse;
import com.factcheck.collector.service.ingestion.IngestionJobRunner;
import com.factcheck.collector.exception.IngestionRunAlreadyRunningException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/ingestion")
@RequiredArgsConstructor
public class IngestionTriggerController {

    private final IngestionJobRunner ingestionJobRunner;

    @PostMapping("/run")
    public ResponseEntity<IngestionRunStartResponse> run(
            @RequestBody(required = false) IngestionRunRequest request,
            @RequestParam(name = "correlationId", required = false) String correlationIdParam
    ) {
        String correlationId = correlationIdParam != null ? correlationIdParam
                : (request != null ? request.correlationId() : null);
        validateCorrelationId(correlationId);

        try {
            IngestionRunStartResponse response = ingestionJobRunner.startRun(correlationId);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (IngestionRunAlreadyRunningException ex) {
            log.warn("Ingestion run rejected: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new IngestionRunStartResponse(null, null, 0, "RUNNING"));
        }
    }

    private void validateCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return;
        }
        UUID.fromString(correlationId);
    }
}