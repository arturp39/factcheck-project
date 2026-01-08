package com.factcheck.collector.controller.admin;

import com.factcheck.collector.dto.MbfcSourceCreateRequest;
import com.factcheck.collector.dto.MbfcSourceResponse;
import com.factcheck.collector.dto.MbfcSourceUpdateRequest;
import com.factcheck.collector.dto.MbfcSyncResponse;
import com.factcheck.collector.service.catalog.MbfcSourceService;
import com.factcheck.collector.service.catalog.MbfcSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/mbfc")
@RequiredArgsConstructor
public class MbfcAdminController {

    private final MbfcSourceService mbfcSourceService;
    private final MbfcSyncService mbfcSyncService;

    @PostMapping("/sync")
    public ResponseEntity<MbfcSyncResponse> sync() {
        var result = mbfcSyncService.syncAndMap();
        return ResponseEntity.ok(new MbfcSyncResponse(result.fetched(), result.saved(), result.mapped()));
    }

    @PostMapping("/map-publishers")
    public ResponseEntity<MbfcSyncResponse> mapPublishers() {
        var result = mbfcSyncService.mapExistingSources();
        return ResponseEntity.ok(new MbfcSyncResponse(result.fetched(), result.saved(), result.mapped()));
    }

    @GetMapping("/sources")
    public ResponseEntity<List<MbfcSourceResponse>> listSources() {
        return ResponseEntity.ok(mbfcSourceService.listSources());
    }

    @GetMapping("/sources/{id}")
    public ResponseEntity<MbfcSourceResponse> getSource(@PathVariable("id") Long id) {
        return ResponseEntity.ok(mbfcSourceService.getSource(id));
    }

    @PostMapping("/sources")
    public ResponseEntity<MbfcSourceResponse> createSource(@RequestBody @Valid MbfcSourceCreateRequest request) {
        return ResponseEntity.ok(mbfcSourceService.createSource(request));
    }

    @PatchMapping("/sources/{id}")
    public ResponseEntity<MbfcSourceResponse> updateSource(
            @PathVariable("id") Long id,
            @RequestBody @Valid MbfcSourceUpdateRequest request
    ) {
        return ResponseEntity.ok(mbfcSourceService.updateSource(id, request));
    }

    @DeleteMapping("/sources/{id}")
    public ResponseEntity<Void> deleteSource(@PathVariable("id") Long id) {
        mbfcSourceService.deleteSource(id);
        return ResponseEntity.noContent().build();
    }
}