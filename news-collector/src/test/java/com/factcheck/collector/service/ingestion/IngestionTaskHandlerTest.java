package com.factcheck.collector.service.ingestion;

import com.factcheck.collector.domain.entity.IngestionLog;
import com.factcheck.collector.domain.entity.IngestionRun;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.IngestionRunStatus;
import com.factcheck.collector.domain.enums.IngestionStatus;
import com.factcheck.collector.dto.IngestionTaskRequest;
import com.factcheck.collector.repository.IngestionLogRepository;
import com.factcheck.collector.repository.IngestionRunRepository;
import com.factcheck.collector.repository.SourceEndpointRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionTaskHandlerTest {

    @Mock
    private IngestionRunRepository ingestionRunRepository;

    @Mock
    private IngestionLogRepository ingestionLogRepository;

    @Mock
    private SourceEndpointRepository sourceEndpointRepository;

    @Mock
    private EndpointIngestionJob endpointIngestionJob;

    @Mock
    private EntityManager entityManager;

    @Mock
    private PlatformTransactionManager transactionManager;

    private IngestionTaskHandler handler;

    @BeforeEach
    void setUp() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        handler = new IngestionTaskHandler(
                ingestionRunRepository,
                ingestionLogRepository,
                sourceEndpointRepository,
                endpointIngestionJob,
                txTemplate
        );
        ReflectionTestUtils.setField(handler, "entityManager", entityManager);
        ReflectionTestUtils.setField(handler, "taskLeaseSeconds", 120L);
    }

    @Test
    void handleRejectsMissingRequest() {
        assertThatThrownBy(() -> handler.handle(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId and sourceEndpointId are required");
    }

    @Test
    void handleSkipsWhenRunMissing() {
        when(ingestionRunRepository.findById(1L)).thenReturn(Optional.empty());

        handler.handle(new IngestionTaskRequest(1L, 2L, "demo"));

        verifyNoInteractions(sourceEndpointRepository, ingestionLogRepository, endpointIngestionJob);
    }

    @Test
    void handleMarksLogSkippedWhenRunNotRunning() {
        IngestionRun run = IngestionRun.builder()
                .id(1L)
                .status(IngestionRunStatus.COMPLETED)
                .correlationId(UUID.randomUUID())
                .build();
        SourceEndpoint endpoint = SourceEndpoint.builder().id(2L).build();
        IngestionLog logEntry = IngestionLog.builder()
                .id(3L)
                .run(run)
                .sourceEndpoint(endpoint)
                .status(IngestionStatus.STARTED)
                .build();

        when(ingestionRunRepository.findById(1L)).thenReturn(Optional.of(run));
        when(sourceEndpointRepository.findById(2L)).thenReturn(Optional.of(endpoint));
        when(ingestionLogRepository.findByRunIdAndSourceEndpointId(1L, 2L))
                .thenReturn(Optional.of(logEntry));
        when(ingestionLogRepository.save(any(IngestionLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        handler.handle(new IngestionTaskRequest(1L, 2L, UUID.randomUUID().toString()));

        ArgumentCaptor<IngestionLog> captor = ArgumentCaptor.forClass(IngestionLog.class);
        verify(ingestionLogRepository).save(captor.capture());
        IngestionLog saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(IngestionStatus.SKIPPED);
        assertThat(saved.getCompletedAt()).isNotNull();
        assertThat(saved.getErrorDetails()).contains("Run is not RUNNING");
        verify(endpointIngestionJob, never()).ingestSingleSource(any(), any(), any(), any());
    }

    @Test
    void handleClaimsAndDispatchesJob() {
        UUID correlationId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        IngestionRun run = IngestionRun.builder()
                .id(1L)
                .status(IngestionRunStatus.RUNNING)
                .correlationId(correlationId)
                .build();
        SourceEndpoint endpoint = SourceEndpoint.builder().id(2L).build();
        IngestionLog logEntry = IngestionLog.builder()
                .id(3L)
                .run(run)
                .sourceEndpoint(endpoint)
                .startedAt(Instant.now())
                .build();

        when(ingestionRunRepository.findById(1L)).thenReturn(Optional.of(run));
        when(sourceEndpointRepository.findById(2L)).thenReturn(Optional.of(endpoint));
        when(ingestionLogRepository.findByRunIdAndSourceEndpointId(1L, 2L))
                .thenReturn(Optional.of(logEntry));
        when(ingestionLogRepository.save(any(IngestionLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(ingestionLogRepository.claimLog(eq(1L), eq(2L), anyLong())).thenReturn(1);
        when(ingestionLogRepository.existsByRunIdAndCompletedAtIsNull(1L)).thenReturn(true);
        doNothing().when(entityManager).refresh(logEntry);

        handler.handle(new IngestionTaskRequest(1L, 2L, correlationId.toString()));

        verify(endpointIngestionJob).ingestSingleSource(endpoint, correlationId, run, logEntry);
    }

    @Test
    void handleSkipsWhenClaimLogReturnsZero() {
        UUID correlationId = UUID.randomUUID();
        IngestionRun run = IngestionRun.builder().id(1L).status(IngestionRunStatus.RUNNING).correlationId(correlationId).build();
        SourceEndpoint endpoint = SourceEndpoint.builder().id(2L).build();
        IngestionLog logEntry = IngestionLog.builder()
                .id(3L)
                .run(run)
                .sourceEndpoint(endpoint)
                .status(IngestionStatus.STARTED)
                .startedAt(Instant.now())
                .build();

        when(ingestionRunRepository.findById(1L)).thenReturn(Optional.of(run));
        when(sourceEndpointRepository.findById(2L)).thenReturn(Optional.of(endpoint));
        when(ingestionLogRepository.findByRunIdAndSourceEndpointId(1L, 2L)).thenReturn(Optional.of(logEntry));
        when(ingestionLogRepository.claimLog(1L, 2L, 120L)).thenReturn(0);

        handler.handle(new IngestionTaskRequest(1L, 2L, correlationId.toString()));

        verify(endpointIngestionJob, never()).ingestSingleSource(any(), any(), any(), any());
        verify(entityManager, never()).refresh(any());
    }

    @Test
    void handleUsesExistingCorrelationIdWhenMismatch() {
        UUID existing = UUID.fromString("00000000-0000-0000-0000-000000000111");
        UUID incoming = UUID.fromString("00000000-0000-0000-0000-000000000222");

        IngestionRun run = IngestionRun.builder().id(1L).status(IngestionRunStatus.RUNNING).correlationId(existing).build();
        SourceEndpoint endpoint = SourceEndpoint.builder().id(2L).build();
        IngestionLog logEntry = IngestionLog.builder()
                .id(3L)
                .run(run)
                .sourceEndpoint(endpoint)
                .status(IngestionStatus.STARTED)
                .correlationId(existing)
                .startedAt(Instant.now())
                .build();

        when(ingestionRunRepository.findById(1L)).thenReturn(Optional.of(run));
        when(sourceEndpointRepository.findById(2L)).thenReturn(Optional.of(endpoint));
        when(ingestionLogRepository.findByRunIdAndSourceEndpointId(1L, 2L)).thenReturn(Optional.of(logEntry));
        when(ingestionLogRepository.claimLog(1L, 2L, 120L)).thenReturn(1);
        doNothing().when(entityManager).refresh(logEntry);

        when(ingestionLogRepository.existsByRunIdAndCompletedAtIsNull(1L)).thenReturn(true);

        handler.handle(new IngestionTaskRequest(1L, 2L, incoming.toString()));

        ArgumentCaptor<UUID> correlationCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(endpointIngestionJob).ingestSingleSource(eq(endpoint), correlationCaptor.capture(), eq(run), eq(logEntry));
        assertThat(correlationCaptor.getValue()).isEqualTo(existing);
    }

    @Test
    void handleCompletesRunWhenOnlyIgnoredFailuresPlusSuccess() {
        UUID correlationId = UUID.randomUUID();

        IngestionRun run = IngestionRun.builder().id(1L).status(IngestionRunStatus.RUNNING).correlationId(correlationId).build();
        SourceEndpoint endpoint = SourceEndpoint.builder().id(2L).build();

        IngestionLog current = IngestionLog.builder()
                .id(3L)
                .run(run)
                .sourceEndpoint(endpoint)
                .status(IngestionStatus.STARTED)
                .correlationId(correlationId)
                .startedAt(Instant.now())
                .build();

        IngestionLog ignoredFailure = IngestionLog.builder()
                .id(4L)
                .run(run)
                .sourceEndpoint(SourceEndpoint.builder().id(99L).build())
                .status(IngestionStatus.FAILED)
                .errorDetails("Robots.txt disallows scraping for this source")
                .completedAt(Instant.now())
                .correlationId(correlationId)
                .build();

        when(ingestionRunRepository.findById(1L)).thenReturn(Optional.of(run));
        when(sourceEndpointRepository.findById(2L)).thenReturn(Optional.of(endpoint));
        when(ingestionLogRepository.findByRunIdAndSourceEndpointId(1L, 2L)).thenReturn(Optional.of(current));
        when(ingestionLogRepository.claimLog(1L, 2L, 120L)).thenReturn(1);
        doNothing().when(entityManager).refresh(current);

        doAnswer(inv -> {
            current.setStatus(IngestionStatus.SUCCESS);
            current.setCompletedAt(Instant.now());
            return null;
        }).when(endpointIngestionJob).ingestSingleSource(eq(endpoint), eq(correlationId), eq(run), eq(current));

        when(ingestionLogRepository.existsByRunIdAndCompletedAtIsNull(1L)).thenReturn(false);
        when(ingestionLogRepository.findByRunId(1L)).thenReturn(List.of(current, ignoredFailure));

        when(ingestionRunRepository.save(any(IngestionRun.class))).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(new IngestionTaskRequest(1L, 2L, correlationId.toString()));

        ArgumentCaptor<IngestionRun> runCaptor = ArgumentCaptor.forClass(IngestionRun.class);
        verify(ingestionRunRepository, atLeastOnce()).save(runCaptor.capture());
        IngestionRun finalRun = runCaptor.getAllValues().getLast();

        assertThat(finalRun.getStatus()).isEqualTo(IngestionRunStatus.COMPLETED);
        assertThat(finalRun.getCompletedAt()).isNotNull();
    }
}