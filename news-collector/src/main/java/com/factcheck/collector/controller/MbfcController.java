package com.factcheck.collector.controller;

import com.factcheck.collector.dto.MbfcSyncResponse;
import com.factcheck.collector.service.MbfcSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/mbfc")
@RequiredArgsConstructor
public class MbfcController {

    private final MbfcSyncService mbfcSyncService;

    @PostMapping("/sync")
    public ResponseEntity<MbfcSyncResponse> sync() {
        var result = mbfcSyncService.syncAndMap();
        return ResponseEntity.ok(new MbfcSyncResponse(result.fetched(), result.saved(), result.mapped()));
    }
}
