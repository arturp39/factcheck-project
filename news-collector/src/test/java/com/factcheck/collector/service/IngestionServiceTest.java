package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.IngestionStatus;
import com.factcheck.collector.repository.SourceEndpointRepository;
import com.factcheck.collector.repository.IngestionRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private SourceEndpointRepository sourceEndpointRepository;

    @Mock
    private SourceIngestionService sourceIngestionService;

    @Mock
    private IngestionRunRepository ingestionRunRepository;

    @InjectMocks
    private IngestionService ingestionService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(ingestionService, "maxParallelSources", 1);
    }

    @Test
    void ingestAllSourcesReturnsWhenNoneEnabled() {
        when(sourceEndpointRepository.findByEnabledTrue()).thenReturn(List.of());

        ingestionService.ingestAllSources("corr-none");

        verify(sourceIngestionService, never()).ingestSingleSource(
                any(),
                any(),
                any()
        );
        verify(ingestionRunRepository, never()).save(any());
    }

    @Test
    void ingestAllSourcesProcessesEachEnabledSource() {
        SourceEndpoint sourceOne = SourceEndpoint.builder().id(1L).build();
        SourceEndpoint sourceTwo = SourceEndpoint.builder().id(2L).build();
        when(sourceEndpointRepository.findByEnabledTrue()).thenReturn(List.of(sourceOne, sourceTwo));
        when(sourceIngestionService.ingestSingleSource(eq(sourceOne),
                any(), any()))
                .thenReturn(IngestionStatus.SUCCESS);
        when(sourceIngestionService.ingestSingleSource(eq(sourceTwo),
                any(), any()))
                .thenReturn(IngestionStatus.FAILED);

        String correlationId = UUID.randomUUID().toString();
        ingestionService.ingestAllSources(correlationId);

        ArgumentCaptor<UUID> cidCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(sourceIngestionService).ingestSingleSource(
                eq(sourceOne), cidCaptor.capture(), any());
        verify(sourceIngestionService).ingestSingleSource(
                eq(sourceTwo), cidCaptor.capture(), any());
        assertThat(cidCaptor.getAllValues()).allMatch(cid -> cid.toString().equals(correlationId));

        ArgumentCaptor<com.factcheck.collector.domain.entity.IngestionRun> runCaptor =
                ArgumentCaptor.forClass(com.factcheck.collector.domain.entity.IngestionRun.class);
        verify(ingestionRunRepository, atLeast(2)).save(runCaptor.capture());
        var finalRun = runCaptor.getAllValues().getLast();
        assertThat(finalRun.getStatus()).isEqualTo(IngestionStatus.PARTIAL);
        assertThat(finalRun.getCompletedAt()).isNotNull();
    }

    @Test
    void ingestAllSourcesMarksRunSkippedWhenAllSkipped() {
        SourceEndpoint sourceOne = SourceEndpoint.builder().id(1L).build();
        SourceEndpoint sourceTwo = SourceEndpoint.builder().id(2L).build();
        when(sourceEndpointRepository.findByEnabledTrue()).thenReturn(List.of(sourceOne, sourceTwo));
        when(sourceIngestionService.ingestSingleSource(eq(sourceOne),
                any(), any()))
                .thenReturn(IngestionStatus.SKIPPED);
        when(sourceIngestionService.ingestSingleSource(eq(sourceTwo),
                any(), any()))
                .thenReturn(IngestionStatus.SKIPPED);

        String correlationId = UUID.randomUUID().toString();
        ingestionService.ingestAllSources(correlationId);

        ArgumentCaptor<com.factcheck.collector.domain.entity.IngestionRun> runCaptor =
                ArgumentCaptor.forClass(com.factcheck.collector.domain.entity.IngestionRun.class);
        verify(ingestionRunRepository, atLeast(2)).save(runCaptor.capture());
        var finalRun = runCaptor.getAllValues().getLast();
        assertThat(finalRun.getStatus()).isEqualTo(IngestionStatus.SKIPPED);
        assertThat(finalRun.getCompletedAt()).isNotNull();
    }
}