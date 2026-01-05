package com.factcheck.collector.controller;

import com.factcheck.collector.integration.newsapi.dto.NewsApiSourcesResponse;
import com.factcheck.collector.service.NewsApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/newsapi")
@RequiredArgsConstructor
public class NewsApiAdminController {

    private final NewsApiService newsApiService;

    @GetMapping("/sources")
    public ResponseEntity<NewsApiSourcesResponse> listSources() {
        return ResponseEntity.ok(newsApiService.listEnglishSources());
    }
}
