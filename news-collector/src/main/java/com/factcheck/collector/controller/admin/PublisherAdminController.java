package com.factcheck.collector.controller.admin;

import com.factcheck.collector.dto.PublisherCreateRequest;
import com.factcheck.collector.dto.PublisherResponse;
import com.factcheck.collector.dto.PublisherUpdateRequest;
import com.factcheck.collector.service.catalog.PublisherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/publishers")
@RequiredArgsConstructor
public class PublisherAdminController {

    private final PublisherService publisherService;

    @GetMapping
    public ResponseEntity<List<PublisherResponse>> listPublishers() {
        return ResponseEntity.ok(publisherService.listPublishers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublisherResponse> getPublisher(@PathVariable("id") Long id) {
        return ResponseEntity.ok(publisherService.getPublisher(id));
    }

    @PostMapping
    public ResponseEntity<PublisherResponse> createPublisher(
            @RequestBody @Valid PublisherCreateRequest request
    ) {
        return ResponseEntity.ok(publisherService.createPublisher(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PublisherResponse> updatePublisher(
            @PathVariable("id") Long id,
            @RequestBody @Valid PublisherUpdateRequest request
    ) {
        return ResponseEntity.ok(publisherService.updatePublisher(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePublisher(@PathVariable("id") Long id) {
        publisherService.deletePublisher(id);
        return ResponseEntity.noContent().build();
    }
}