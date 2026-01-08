package com.factcheck.collector.controller.admin;

import com.factcheck.collector.dto.NewsApiSyncResponse;
import com.factcheck.collector.integration.catalog.newsapi.dto.NewsApiSourcesResponse;
import com.factcheck.collector.service.catalog.NewsApiSourceCatalogService;
import com.factcheck.collector.service.catalog.NewsApiSourceSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/newsapi")
@RequiredArgsConstructor
public class NewsApiAdminController {

    private final NewsApiSourceCatalogService newsApiService;
    private final NewsApiSourceSyncService newsApiSourceSyncService;

    @GetMapping("/sources")
    public ResponseEntity<NewsApiSourcesResponse> listSources() {
        return ResponseEntity.ok(newsApiService.listEnglishSources());
    }

    @PostMapping("/sources/sync")
    public ResponseEntity<NewsApiSyncResponse> syncSources(
            @RequestParam(name = "language", required = false) String language
    ) {
        var result = newsApiSourceSyncService.syncSources(language);
        return ResponseEntity.ok(new NewsApiSyncResponse(
                result.fetched(),
                result.createdEndpoints(),
                result.enrichedPublishers(),
                result.existingOrConcurrent()
        ));
    }
}