package com.factcheck.collector.controller.ingestion;

import com.factcheck.collector.dto.IngestionTaskRequest;
import com.factcheck.collector.service.ingestion.IngestionTaskHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/ingestion")
@RequiredArgsConstructor
public class IngestionTaskController {

    private final IngestionTaskHandler ingestionTaskHandler;

    @PostMapping("/task")
    public ResponseEntity<Void> handleTask(@RequestBody(required = false) IngestionTaskRequest request) {
        try {
            ingestionTaskHandler.handle(request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            log.warn("Acknowledging invalid ingestion task payload: {}", ex.getMessage());
            return ResponseEntity.noContent().build();
        }
    }
}