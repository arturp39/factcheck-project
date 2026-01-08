package com.factcheck.collector.service.ingestion;

import com.factcheck.collector.domain.entity.IngestionRun;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.IngestionRunStatus;
import com.factcheck.collector.dto.IngestionRunStartResponse;
import com.factcheck.collector.integration.tasks.TaskPublisher;
import com.factcheck.collector.repository.IngestionLogRepository;
import com.factcheck.collector.repository.IngestionRunRepository;
import com.factcheck.collector.repository.SourceEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionJobRunnerTest {

    @Mock
    private IngestionRunRepository ingestionRunRepository;

    @Mock
    private IngestionLogRepository ingestionLogRepository;

    @Mock
    private SourceEndpointRepository sourceEndpointRepository;

    @Mock
    private TaskPublisher taskPublisher;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private IngestionJobRunner ingestionJobRunner;

    @BeforeEach
    void setup() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        ingestionJobRunner = new IngestionJobRunner(
                ingestionRunRepository,
                ingestionLogRepository,
                sourceEndpointRepository,
                taskPublisher,
                transactionManager
        );
        ReflectionTestUtils.setField(ingestionJobRunner, "runTimeout", Duration.ofHours(6));
    }

    @Test
    void startRunCompletesImmediatelyWhenNoEndpointsEligible() {
        when(ingestionRunRepository.findByStatusAndStartedAtBefore(any(), any(Instant.class)))
                .thenReturn(List.of());
        when(sourceEndpointRepository.findEligibleForIngestion(any(Instant.class)))
                .thenReturn(List.of());
        when(ingestionRunRepository.save(any(IngestionRun.class)))
                .thenAnswer(invocation -> {
                    IngestionRun run = invocation.getArgument(0);
                    if (run.getId() == null) {
                        run.setId(1L);
                    }
                    return run;
                });

        IngestionRunStartResponse response = ingestionJobRunner.startRun(UUID.randomUUID().toString());

        assertThat(response.runId()).isEqualTo(1L);
        assertThat(response.tasksEnqueued()).isEqualTo(0);

        ArgumentCaptor<IngestionRun> runCaptor = ArgumentCaptor.forClass(IngestionRun.class);
        verify(ingestionRunRepository, atLeast(2)).save(runCaptor.capture());
        IngestionRun finalRun = runCaptor.getAllValues().getLast();
        assertThat(finalRun.getStatus()).isEqualTo(IngestionRunStatus.COMPLETED);
        assertThat(finalRun.getCompletedAt()).isNotNull();

        verifyNoInteractions(taskPublisher);
    }

    @Test
    void startRunEnqueuesTasksForEligibleEndpoints() {
        when(ingestionRunRepository.findByStatusAndStartedAtBefore(any(), any(Instant.class)))
                .thenReturn(List.of());
        SourceEndpoint first = SourceEndpoint.builder().id(10L).build();
        SourceEndpoint second = SourceEndpoint.builder().id(20L).build();
        when(sourceEndpointRepository.findEligibleForIngestion(any(Instant.class)))
                .thenReturn(List.of(first, second));

        when(ingestionRunRepository.save(any(IngestionRun.class)))
                .thenAnswer(invocation -> {
                    IngestionRun run = invocation.getArgument(0);
                    if (run.getId() == null) {
                        run.setId(5L);
                    }
                    return run;
                });

        IngestionRunStartResponse response = ingestionJobRunner.startRun(UUID.randomUUID().toString());

        assertThat(response.runId()).isEqualTo(5L);
        assertThat(response.tasksEnqueued()).isEqualTo(2);

        verify(taskPublisher, times(2)).enqueueIngestionTask(any());
    }
}