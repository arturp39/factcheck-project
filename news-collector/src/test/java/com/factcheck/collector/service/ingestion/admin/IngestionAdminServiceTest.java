package com.factcheck.collector.service.ingestion.admin;

import com.factcheck.collector.domain.entity.IngestionRun;
import com.factcheck.collector.domain.enums.IngestionRunStatus;
import com.factcheck.collector.repository.IngestionLogRepository;
import com.factcheck.collector.repository.IngestionRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionAdminServiceTest {

    @Mock private IngestionRunRepository ingestionRunRepository;
    @Mock private IngestionLogRepository ingestionLogRepository;

    private IngestionAdminService service;

    @BeforeEach
    void setUp() {
        service = new IngestionAdminService(ingestionRunRepository, ingestionLogRepository);
    }

    @Test
    void abortRunThrowsWhenRunIdNull() {
        assertThatThrownBy(() -> service.abortRun(null, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId is required");

        verifyNoInteractions(ingestionRunRepository, ingestionLogRepository);
    }

    @Test
    void abortRunThrowsWhenRunNotFound() {
        when(ingestionRunRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.abortRun(10L, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ingestion run not found: 10");

        verify(ingestionRunRepository).findById(10L);
        verifyNoMoreInteractions(ingestionRunRepository);
        verifyNoInteractions(ingestionLogRepository);
    }

    @Test
    void abortRunDoesNothingWhenRunNotRunning() {
        IngestionRun run = IngestionRun.builder()
                .id(10L)
                .status(IngestionRunStatus.COMPLETED)
                .build();

        when(ingestionRunRepository.findById(10L)).thenReturn(Optional.of(run));

        service.abortRun(10L, "x");

        verify(ingestionRunRepository).findById(10L);
        verify(ingestionRunRepository, never()).save(any());
        verify(ingestionLogRepository, never()).failPendingLogsForRun(anyLong(), anyString());
    }

    @Test
    void abortRunMarksFailedAndUsesDefaultMessageWhenReasonBlank() {
        IngestionRun run = IngestionRun.builder()
                .id(10L)
                .status(IngestionRunStatus.RUNNING)
                .build();

        when(ingestionRunRepository.findById(10L)).thenReturn(Optional.of(run));
        when(ingestionLogRepository.failPendingLogsForRun(10L, "Aborted by admin request")).thenReturn(3);

        service.abortRun(10L, "   ");

        ArgumentCaptor<IngestionRun> runCaptor = ArgumentCaptor.forClass(IngestionRun.class);
        verify(ingestionRunRepository).save(runCaptor.capture());

        IngestionRun saved = runCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(IngestionRunStatus.FAILED);
        assertThat(saved.getCompletedAt()).isNotNull();
        assertThat(saved.getCompletedAt()).isAfter(Instant.EPOCH);

        verify(ingestionLogRepository).failPendingLogsForRun(10L, "Aborted by admin request");
    }

    @Test
    void abortRunTrimsReason() {
        IngestionRun run = IngestionRun.builder()
                .id(10L)
                .status(IngestionRunStatus.RUNNING)
                .build();

        when(ingestionRunRepository.findById(10L)).thenReturn(Optional.of(run));
        when(ingestionLogRepository.failPendingLogsForRun(10L, "stop now")).thenReturn(1);

        service.abortRun(10L, "  stop now  ");

        verify(ingestionLogRepository).failPendingLogsForRun(10L, "stop now");
        verify(ingestionRunRepository).save(any(IngestionRun.class));
    }

    @Test
    void abortActiveRun_returnsEmptyWhenNoRunningRun() {
        when(ingestionRunRepository.findTopByStatusOrderByStartedAtDesc(IngestionRunStatus.RUNNING))
                .thenReturn(Optional.empty());

        Optional<IngestionRun> result = service.abortActiveRun(null);

        assertThat(result).isEmpty();
        verifyNoInteractions(ingestionLogRepository);
    }

    @Test
    void abortActiveRun_abortsLatestRunningRun() {
        IngestionRun run = IngestionRun.builder()
                .id(42L)
                .status(IngestionRunStatus.RUNNING)
                .build();

        when(ingestionRunRepository.findTopByStatusOrderByStartedAtDesc(IngestionRunStatus.RUNNING))
                .thenReturn(Optional.of(run));
        when(ingestionLogRepository.failPendingLogsForRun(42L, "Aborted by admin request")).thenReturn(2);

        Optional<IngestionRun> result = service.abortActiveRun(" ");

        assertThat(result).contains(run);
        verify(ingestionRunRepository).save(run);
        verify(ingestionLogRepository).failPendingLogsForRun(42L, "Aborted by admin request");
    }
}
