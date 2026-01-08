package com.factcheck.collector.controller.ingestion;

import com.factcheck.collector.dto.IngestionRunStartResponse;
import com.factcheck.collector.service.ingestion.IngestionJobRunner;
import com.factcheck.collector.exception.IngestionRunAlreadyRunningException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestionTriggerController.class)
class IngestionTriggerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionJobRunner ingestionJobRunner;

    @Test
    void runEnqueuesAndReturnsAccepted() throws Exception {
        String correlationId = UUID.randomUUID().toString();
        when(ingestionJobRunner.startRun(correlationId))
                .thenReturn(new IngestionRunStartResponse(42L, correlationId, 3, "RUNNING"));

        mockMvc.perform(post("/ingestion/run")
                        .param("correlationId", correlationId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").value(42L))
                .andExpect(jsonPath("$.tasksEnqueued").value(3));

        verify(ingestionJobRunner).startRun(correlationId);
    }

    @Test
    void runRejectsInvalidCorrelationId() throws Exception {
        mockMvc.perform(post("/ingestion/run")
                        .param("correlationId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void runReturnsConflictWhenRunAlreadyActive() throws Exception {
        String correlationId = UUID.randomUUID().toString();
        when(ingestionJobRunner.startRun(correlationId))
                .thenThrow(new IngestionRunAlreadyRunningException("Another ingestion run is already RUNNING", null));

        mockMvc.perform(post("/ingestion/run")
                        .param("correlationId", correlationId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }
}