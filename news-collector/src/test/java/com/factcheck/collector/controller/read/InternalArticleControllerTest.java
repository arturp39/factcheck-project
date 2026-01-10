package com.factcheck.collector.controller.read;

import com.factcheck.collector.dto.ArticleContentResponse;
import com.factcheck.collector.dto.ArticleMetadataResponse;
import com.factcheck.collector.dto.SearchRequest;
import com.factcheck.collector.dto.SearchResponse;
import com.factcheck.collector.service.read.ArticleContentService;
import com.factcheck.collector.service.read.ArticleListService;
import com.factcheck.collector.service.read.ArticleMetadataService;
import com.factcheck.collector.service.read.ArticleSearchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalArticleController.class)
class InternalArticleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleSearchService articleSearchService;

    @MockitoBean
    private ArticleContentService articleContentService;

    @MockitoBean
    private ArticleMetadataService articleMetadataService;
    @MockitoBean
    private ArticleListService articleListService;
    @Test
    void search_passesRequestAndCorrelationIdToService() throws Exception {
        SearchResponse mockResponse = new SearchResponse(List.of(), 0, 5L, "cid-1");
        when(articleSearchService.search(any(), any())).thenReturn(mockResponse);

        String payload = """
                {
                  "embedding": [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
                  %s
                  ],
                  "limit": 5,
                  "minScore": 0.7
                }
                """.formatted(",0.0".repeat(760)); // 768 dims total.

        mockMvc.perform(post("/internal/articles/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("X-Correlation-ID", "cid-header"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFound").value(0));

        ArgumentCaptor<SearchRequest> reqCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        ArgumentCaptor<String> cidCaptor = ArgumentCaptor.forClass(String.class);

        verify(articleSearchService).search(reqCaptor.capture(), cidCaptor.capture());
        assertThat(cidCaptor.getValue()).isEqualTo("cid-header");
        assertThat(reqCaptor.getValue().embedding()).hasSize(768);
    }

    @Test
    void getArticle_returnsMappedMetadataResponse() throws Exception {
        ArticleMetadataResponse response = new ArticleMetadataResponse(
                1L,
                10L,
                "Guardian",
                20L,
                "Guardian RSS",
                "https://example.com",
                "Title",
                Instant.parse("2024-01-01T00:00:00Z"),
                5,
                "INDEXED",
                true
        );

        when(articleMetadataService.getArticleMetadata(1L)).thenReturn(response);

        mockMvc.perform(get("/internal/articles/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.publisherName").value("Guardian"))
                .andExpect(jsonPath("$.sourceEndpointName").value("Guardian RSS"))
                .andExpect(jsonPath("$.canonicalUrl").value("https://example.com"));
    }

    @Test
    void getArticleContent_delegatesToService() throws Exception {
        ArticleContentResponse response = new ArticleContentResponse(
                1L,
                10L,
                "Guardian",
                20L,
                "Guardian RSS",
                "https://example.com",
                "Title",
                null,
                "Some content"
        );

        when(articleContentService.getArticleContent(1L)).thenReturn(response);

        mockMvc.perform(get("/internal/articles/{id}/content", 1L)
                        .header("X-Correlation-ID", "cid-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleId").value(1L))
                .andExpect(jsonPath("$.content").value("Some content"));

        verify(articleContentService).getArticleContent(1L);
    }

    @Test
    void getArticle_returnsBadRequestWhenMissing() throws Exception {
        when(articleMetadataService.getArticleMetadata(99L))
                .thenThrow(new IllegalArgumentException("Article not found: 99"));

        mockMvc.perform(get("/internal/articles/{id}", 99L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void search_returnsBadRequestForWrongEmbeddingSize() throws Exception {
        String payload = """
                {
                  "embedding": [0.1, 0.2],
                  "limit": 5,
                  "minScore": 0.7
                }
                """;

        when(articleSearchService.search(any(), any()))
                .thenThrow(new IllegalArgumentException("Embedding must have dimension"));

        mockMvc.perform(post("/internal/articles/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}