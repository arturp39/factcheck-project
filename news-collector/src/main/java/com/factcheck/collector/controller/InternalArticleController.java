package com.factcheck.collector.controller;

import com.factcheck.collector.dto.ArticleContentResponse;
import com.factcheck.collector.dto.ArticleMetadataResponse;
import com.factcheck.collector.dto.SearchRequest;
import com.factcheck.collector.dto.SearchResponse;
import com.factcheck.collector.service.ArticleContentService;
import com.factcheck.collector.service.ArticleMetadataService;
import com.factcheck.collector.service.ArticleSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalArticleController {

    private final ArticleSearchService articleSearchService;
    private final ArticleContentService articleContentService;
    private final ArticleMetadataService articleMetadataService;

    @PostMapping("/articles/search")
    public SearchResponse search(
            @RequestBody @Valid SearchRequest request,
            @RequestHeader(name = "X-Correlation-ID", required = false) String correlationId
    ) {
        return articleSearchService.search(request, correlationId);
    }

    @GetMapping("/articles/{id}")
    public ArticleMetadataResponse getArticle(@PathVariable Long id) {
        return articleMetadataService.getArticleMetadata(id);
    }

    @GetMapping("/articles/{id}/content")
    public ArticleContentResponse getArticleContent(
            @PathVariable Long id,
            @RequestHeader(name = "X-Correlation-ID", required = false) String correlationId
    ) {
        return articleContentService.getArticleContent(id);
    }
}