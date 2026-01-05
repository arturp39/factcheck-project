package com.factcheck.collector.controller;

import com.factcheck.collector.domain.enums.SourceKind;
import com.factcheck.collector.dto.SourceResponse;
import com.factcheck.collector.service.SourceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SourceAdminController.class)
class SourceAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SourceService sourceService;

    @Test
    void listSources_returnsSources() throws Exception {
        SourceResponse s = new SourceResponse(
                1L, 10L, "BBC News", SourceKind.RSS, "BBC - Top", "https://example.com/rss",
                null, null, true, 15, Instant.now(), Instant.now(), 0,
                false, null, null, 0,
                Instant.now(), Instant.now()
        );

        when(sourceService.listSources()).thenReturn(List.of(s));

        mockMvc.perform(get("/admin/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].kind").value("RSS"));
    }

    @Test
    void createSource_createsSource() throws Exception {
        SourceResponse saved = new SourceResponse(
                2L, 20L, "NPR", SourceKind.RSS, "NPR - Top Stories", "https://npr.org/rss",
                null, null, true, 15, Instant.now(), Instant.now(), 0,
                false, null, null, 0,
                Instant.now(), Instant.now()
        );

        when(sourceService.createSource(any())).thenReturn(saved);

        String payload = """
                {
                  "publisherName": "NPR",
                  "kind": "RSS",
                  "displayName": "NPR - Top Stories",
                  "rssUrl": "https://npr.org/rss",
                  "enabled": true,
                  "fetchIntervalMinutes": 15
                }
                """;

        mockMvc.perform(post("/admin/sources")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.publisherName").value("NPR"));
    }

    @Test
    void updateSource_updatesFields() throws Exception {
        SourceResponse updated = new SourceResponse(
                3L, 30L, "Old", SourceKind.RSS, "Old Feed", "https://old",
                null, null, false, 60, Instant.now(), Instant.now(), 0,
                false, null, null, 0,
                Instant.now(), Instant.now()
        );

        when(sourceService.updateSource(eq(3L), any()))
                .thenReturn(updated);

        String payload = """
                { "enabled": false, "fetchIntervalMinutes": 60 }
                """;

        mockMvc.perform(patch("/admin/sources/{id}", 3L)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.fetchIntervalMinutes").value(60));
    }
}