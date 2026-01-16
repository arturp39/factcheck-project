package com.factcheck.collector.controller.admin;

import com.factcheck.collector.dto.SourceCreateRequest;
import com.factcheck.collector.dto.SourceResponse;
import com.factcheck.collector.dto.SourceUpdateRequest;
import com.factcheck.collector.service.catalog.SourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/sources")
@RequiredArgsConstructor
public class SourceAdminController {

    private final SourceService sourceService;

    @GetMapping
    public ResponseEntity<List<SourceResponse>> listSources() {
        return ResponseEntity.ok(sourceService.listSources());
    }

    @PostMapping
    public ResponseEntity<SourceResponse> createSource(@RequestBody @Valid SourceCreateRequest request) {
        return ResponseEntity.ok(sourceService.createSource(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SourceResponse> updateSource(
            @PathVariable("id") Long id,
            @RequestBody @Valid SourceUpdateRequest request
    ) {
        return ResponseEntity.ok(sourceService.updateSource(id, request));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<SourceResponse> disableSource(@PathVariable("id") Long id) {
        return ResponseEntity.ok(sourceService.setEnabled(id, false));
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<SourceResponse> enableSource(@PathVariable("id") Long id) {
        return ResponseEntity.ok(sourceService.setEnabled(id, true));
    }
}
