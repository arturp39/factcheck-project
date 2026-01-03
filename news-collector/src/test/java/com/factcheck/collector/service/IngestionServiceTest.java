package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.repository.SourceEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private SourceEndpointRepository sourceEndpointRepository;

    @Mock
    private SourceIngestionService sourceIngestionService;

    @InjectMocks
    private IngestionService ingestionService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(ingestionService, "maxParallelSources", 2);
    }

    @Test
    void ingestAllSourcesReturnsWhenNoneEnabled() {
        when(sourceEndpointRepository.findByEnabledTrue()).thenReturn(List.of());

        ingestionService.ingestAllSources("corr-none");

        verify(sourceIngestionService, never()).ingestSingleSource(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ingestAllSourcesProcessesEachEnabledSource() {
        SourceEndpoint sourceOne = SourceEndpoint.builder().id(1L).build();
        SourceEndpoint sourceTwo = SourceEndpoint.builder().id(2L).build();
        when(sourceEndpointRepository.findByEnabledTrue()).thenReturn(List.of(sourceOne, sourceTwo));

        ingestionService.ingestAllSources("corr-all");

        verify(sourceIngestionService).ingestSingleSource(sourceOne, "corr-all");
        verify(sourceIngestionService).ingestSingleSource(sourceTwo, "corr-all");
    }
}