package com.factcheck.collector.controller.read;

import com.factcheck.collector.dto.ArticleContentResponse;
import com.factcheck.collector.dto.ArticleMetadataResponse;
import com.factcheck.collector.dto.SearchRequest;
import com.factcheck.collector.dto.SearchResponse;
import com.factcheck.collector.service.read.ArticleContentService;
import com.factcheck.collector.service.read.ArticleListService;
import com.factcheck.collector.service.read.ArticleMetadataService;
import com.factcheck.collector.service.read.ArticleSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalArticleController {

    private final ArticleSearchService articleSearchService;
    private final ArticleContentService articleContentService;
    private final ArticleMetadataService articleMetadataService;
    private final ArticleListService articleListService;

    @GetMapping("/articles")
    public List<ArticleMetadataResponse> listArticles(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit
    ) {
        if (q == null || q.trim().isEmpty()) {
            return articleListService.listLatest(limit);
        }
        return articleListService.searchByTitle(q, limit);
    }

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