package com.factcheck.collector.service.ingestion.query;

import com.factcheck.collector.domain.entity.IngestionLog;
import com.factcheck.collector.domain.entity.IngestionRun;
import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.IngestionRunStatus;
import com.factcheck.collector.domain.enums.IngestionStatus;
import com.factcheck.collector.dto.IngestionLogPageResponse;
import com.factcheck.collector.repository.IngestionLogRepository;
import com.factcheck.collector.repository.IngestionRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionQueryServiceTest {

    @Mock private IngestionLogRepository ingestionLogRepository;
    @Mock private IngestionRunRepository ingestionRunRepository;

    private IngestionQueryService service;

    @BeforeEach
    void setUp() {
        service = new IngestionQueryService(ingestionLogRepository, ingestionRunRepository);
    }

    @Test
    void listLogsMapsEndpointAndPublisherFieldsWhenPresent() {
        Publisher publisher = Publisher.builder().id(7L).name("Pub").build();
        SourceEndpoint endpoint = SourceEndpoint.builder().id(5L).displayName("EP").publisher(publisher).build();

        UUID corr = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-01-01T00:00:00Z");

        IngestionLog log = IngestionLog.builder()
                .id(100L)
                .sourceEndpoint(endpoint)
                .status(IngestionStatus.SUCCESS)
                .correlationId(corr)
                .startedAt(startedAt)
                .completedAt(null)
                .articlesFetched(3)
                .articlesProcessed(2)
                .articlesFailed(1)
                .errorDetails(null)
                .build();

        Page<IngestionLog> page = new PageImpl<>(List.of(log), PageRequest.of(0, 10), 1);
        when(ingestionLogRepository.findAll(PageRequest.of(0, 10))).thenReturn(page);

        IngestionLogPageResponse resp = service.listLogs(0, 10);

        assertThat(resp.page()).isEqualTo(0);
        assertThat(resp.size()).isEqualTo(10);
        assertThat(resp.totalElements()).isEqualTo(1);
        assertThat(resp.totalPages()).isEqualTo(1);

        assertThat(resp.items()).hasSize(1);
        var item = resp.items().getFirst();

        assertThat(item.sourceEndpointId()).isEqualTo(5L);
        assertThat(item.sourceEndpointName()).isEqualTo("EP");
        assertThat(item.publisherId()).isEqualTo(7L);
        assertThat(item.publisherName()).isEqualTo("Pub");
        assertThat(item.status()).isEqualTo(IngestionStatus.SUCCESS.name());
        assertThat(item.correlationId()).isEqualTo(corr.toString());
        assertThat(item.articlesFetched()).isEqualTo(3);
        assertThat(item.articlesProcessed()).isEqualTo(2);
        assertThat(item.articlesFailed()).isEqualTo(1);

        verify(ingestionLogRepository).findAll(PageRequest.of(0, 10));
    }

    @Test
    void listLogsMapsNullsWhenEndpointMissingAndStatusCorrelationMissing() {
        IngestionLog log = IngestionLog.builder()
                .id(101L)
                .sourceEndpoint(null)
                .status(null)
                .correlationId(null)
                .startedAt(null)
                .completedAt(null)
                .articlesFetched(0)
                .articlesProcessed(0)
                .articlesFailed(0)
                .errorDetails("err")
                .build();

        Page<IngestionLog> page = new PageImpl<>(List.of(log), PageRequest.of(1, 5), 1);
        when(ingestionLogRepository.findAll(PageRequest.of(1, 5))).thenReturn(page);

        IngestionLogPageResponse resp = service.listLogs(1, 5);

        assertThat(resp.items()).hasSize(1);
        var item = resp.items().getFirst();

        assertThat(item.sourceEndpointId()).isNull();
        assertThat(item.sourceEndpointName()).isNull();
        assertThat(item.publisherId()).isNull();
        assertThat(item.publisherName()).isNull();
        assertThat(item.status()).isNull();
        assertThat(item.correlationId()).isNull();
        assertThat(item.errorDetails()).isEqualTo("err");
    }

    @Test
    void getRunThrowsWhenNotFound() {
        when(ingestionRunRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRun(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ingestion run not found: 99");

        verify(ingestionRunRepository).findById(99L);
    }

    @Test
    void getRunMapsStatusAndCorrelationIdWhenPresent() {
        UUID corr = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant completedAt = Instant.parse("2026-01-01T01:00:00Z");

        IngestionRun run = IngestionRun.builder()
                .id(10L)
                .status(IngestionRunStatus.COMPLETED)
                .correlationId(corr)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .build();

        when(ingestionRunRepository.findById(10L)).thenReturn(Optional.of(run));

        var resp = service.getRun(10L);

        assertThat(resp.id()).isEqualTo(10L);
        assertThat(resp.startedAt()).isEqualTo(startedAt);
        assertThat(resp.completedAt()).isEqualTo(completedAt);
        assertThat(resp.status()).isEqualTo(IngestionRunStatus.COMPLETED.name());
        assertThat(resp.correlationId()).isEqualTo(corr.toString());
    }

    @Test
    void getRunMapsNullStatusAndNullCorrelationIdWhenMissing() {
        IngestionRun run = IngestionRun.builder()
                .id(11L)
                .status(null)
                .correlationId(null)
                .startedAt(null)
                .completedAt(null)
                .build();

        when(ingestionRunRepository.findById(11L)).thenReturn(Optional.of(run));

        var resp = service.getRun(11L);

        assertThat(resp.id()).isEqualTo(11L);
        assertThat(resp.status()).isNull();
        assertThat(resp.correlationId()).isNull();
    }
}