package com.factcheck.collector.controller;

import com.factcheck.collector.integration.newsapi.dto.NewsApiSource;
import com.factcheck.collector.integration.newsapi.dto.NewsApiSourcesResponse;
import com.factcheck.collector.service.NewsApiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NewsApiAdminController.class)
class NewsApiAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NewsApiService newsApiService;

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
}
