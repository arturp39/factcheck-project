package com.factcheck.collector.controller.admin;

import com.factcheck.collector.dto.IngestionLogPageResponse;
import com.factcheck.collector.dto.IngestionRunDetailResponse;
import com.factcheck.collector.dto.IngestionRunResponse;
import com.factcheck.collector.service.ingestion.admin.IngestionAdminService;
import com.factcheck.collector.service.ingestion.query.IngestionQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestionAdminController.class)
class IngestionAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionQueryService ingestionQueryService;

    @MockitoBean
    private IngestionAdminService ingestionAdminService;

    @Test
    void listLogs_returnsPage() throws Exception {
        IngestionRunResponse run = new IngestionRunResponse(
                5L, 1L, "BBC Feed", 10L, "BBC",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T01:00:00Z"),
                1, 1, 0,
                "SUCCESS",
                null,
                "cid-x"
        );

        when(ingestionQueryService.listLogs(0, 20))
                .thenReturn(new IngestionLogPageResponse(0, 20, 1, 1, List.of(run)));

        mockMvc.perform(get("/admin/ingestion/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(5L))
                .andExpect(jsonPath("$.items[0].sourceEndpointId").value(1L))
                .andExpect(jsonPath("$.items[0].sourceEndpointName").value("BBC Feed"))
                .andExpect(jsonPath("$.items[0].publisherName").value("BBC"));
    }

    @Test
    void getRun_returnsRun() throws Exception {
        IngestionRunDetailResponse run = new IngestionRunDetailResponse(
                7L,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T01:00:00Z"),
                "FAILED",
                "cid-y"
        );

        when(ingestionQueryService.getRun(7L)).thenReturn(run);

        mockMvc.perform(get("/admin/ingestion/runs/{id}", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7L))
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void abortRun_abortsAndReturnsRun() throws Exception {
        IngestionRunDetailResponse run = new IngestionRunDetailResponse(
                9L,
                Instant.parse("2025-01-02T00:00:00Z"),
                Instant.parse("2025-01-02T01:00:00Z"),
                "FAILED",
                "cid-z"
        );

        when(ingestionAdminService.abortActiveRun(null))
                .thenReturn(Optional.of(com.factcheck.collector.domain.entity.IngestionRun.builder()
                        .id(9L)
                        .status(com.factcheck.collector.domain.enums.IngestionRunStatus.RUNNING)
                        .correlationId(java.util.UUID.randomUUID())
                        .build()));
        when(ingestionQueryService.getRun(9L)).thenReturn(run);

        mockMvc.perform(post("/admin/ingestion/runs/abort-active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9L))
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void abortRun_returnsNotFoundWhenNoActiveRun() throws Exception {
        when(ingestionAdminService.abortActiveRun(null)).thenReturn(Optional.empty());

        mockMvc.perform(post("/admin/ingestion/runs/abort-active"))
                .andExpect(status().isNotFound());
    }
}
