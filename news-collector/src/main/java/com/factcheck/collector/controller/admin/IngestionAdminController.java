package com.factcheck.collector.controller.admin;

import com.factcheck.collector.dto.IngestionLogPageResponse;
import com.factcheck.collector.dto.IngestionRunDetailResponse;
import com.factcheck.collector.service.ingestion.admin.IngestionAdminService;
import com.factcheck.collector.service.ingestion.query.IngestionQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admin/ingestion")
@RequiredArgsConstructor
public class IngestionAdminController {

    private final IngestionQueryService ingestionQueryService;
    private final IngestionAdminService ingestionAdminService;

    @GetMapping("/logs")
    public ResponseEntity<IngestionLogPageResponse> listLogs(
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > 200) {
            throw new IllegalArgumentException("size must be between 1 and 200");
        }

        return ResponseEntity.ok(ingestionQueryService.listLogs(page, size));
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<IngestionRunDetailResponse> getRun(@PathVariable("id") Long runId) {
        return ResponseEntity.ok(ingestionQueryService.getRun(runId));
    }

    @PostMapping("/runs/abort-active")
    public ResponseEntity<IngestionRunDetailResponse> abortActiveRun(
            @RequestParam(name = "reason", required = false) String reason
    ) {
        return ingestionAdminService.abortActiveRun(reason)
                .map(run -> ResponseEntity.ok(ingestionQueryService.getRun(run.getId())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
