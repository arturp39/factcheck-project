package com.factcheck.collector.controller.admin;

import com.factcheck.collector.integration.catalog.newsapi.dto.NewsApiSource;
import com.factcheck.collector.integration.catalog.newsapi.dto.NewsApiSourcesResponse;
import com.factcheck.collector.service.catalog.NewsApiSourceCatalogService;
import com.factcheck.collector.service.catalog.NewsApiSourceSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NewsApiAdminController.class)
class NewsApiAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NewsApiSourceCatalogService newsApiService;

    @MockitoBean
    private NewsApiSourceSyncService newsApiSourceSyncService;

    @Test
    void listSources_returnsResponse() throws Exception {
        NewsApiSourcesResponse response = new NewsApiSourcesResponse(
                "ok",
                List.of(new NewsApiSource("abc", "ABC News", "desc", "https://abc.com", "general", "en", "us")),
                null,
                null
        );
        when(newsApiService.listEnglishSources()).thenReturn(response);

        mockMvc.perform(get("/admin/newsapi/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.sources[0].id").value("abc"))
                .andExpect(jsonPath("$.sources[0].name").value("ABC News"));
    }

    @Test
    void syncSources_returnsSummary() throws Exception {
        when(newsApiSourceSyncService.syncSources(null))
                .thenReturn(new NewsApiSourceSyncService.NewsApiSyncResult(5, 2, 1, 2));

        mockMvc.perform(post("/admin/newsapi/sources/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetched").value(5))
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.updatedPublishers").value(1))
                .andExpect(jsonPath("$.skipped").value(2));
    }
}