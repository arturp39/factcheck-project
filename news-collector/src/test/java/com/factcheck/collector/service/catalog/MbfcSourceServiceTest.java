package com.factcheck.collector.service.catalog;

import com.factcheck.collector.domain.entity.MbfcSource;
import com.factcheck.collector.dto.MbfcSourceCreateRequest;
import com.factcheck.collector.dto.MbfcSourceUpdateRequest;
import com.factcheck.collector.repository.MbfcSourceRepository;
import com.factcheck.collector.repository.PublisherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MbfcSourceServiceTest {

    @Mock
    private MbfcSourceRepository mbfcSourceRepository;

    @Mock
    private PublisherRepository publisherRepository;

    private MbfcSourceService service;

    @BeforeEach
    void setUp() {
        service = new MbfcSourceService(mbfcSourceRepository, publisherRepository);
    }

    @Test
    void listSourcesMapsEntities() {
        MbfcSource source = MbfcSource.builder()
                .mbfcSourceId(10L)
                .sourceName("Example")
                .mbfcUrl("https://mbfc.org/example")
                .bias("left")
                .country("US")
                .factualReporting("high")
                .mediaType("news")
                .sourceUrl("https://example.com")
                .sourceUrlDomain("example.com")
                .credibility("high")
                .syncedAt(Instant.parse("2024-01-01T00:00:00Z"))
                .build();

        // FIX: disambiguate overloaded findAll(...) by typing the matcher as Sort
        when(mbfcSourceRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(source));

        var result = service.listSources();

        assertThat(result).hasSize(1);
        var item = result.getFirst();
        assertThat(item.mbfcSourceId()).isEqualTo(10L);
        assertThat(item.sourceUrlDomain()).isEqualTo("example.com");
        assertThat(item.credibility()).isEqualTo("high");
    }

    @Test
    void createSourceRejectsDuplicateId() {
        MbfcSourceCreateRequest request = new MbfcSourceCreateRequest(
                7L,
                "Example",
                "https://mbfc.org/example",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        when(mbfcSourceRepository.existsById(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.createSource(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createSourcePersistsDomainAndFields() {
        MbfcSourceCreateRequest request = new MbfcSourceCreateRequest(
                12L,
                "Example",
                "https://mbfc.org/example",
                "left",
                "US",
                "high",
                "news",
                "https://www.example.com/page",
                null,
                "medium"
        );

        when(mbfcSourceRepository.existsById(12L)).thenReturn(false);
        when(mbfcSourceRepository.save(any(MbfcSource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createSource(request);

        assertThat(response.mbfcSourceId()).isEqualTo(12L);
        assertThat(response.sourceUrlDomain()).isEqualTo("example.com");
        assertThat(response.bias()).isEqualTo("left");
        assertThat(response.credibility()).isEqualTo("medium");
    }

    @Test
    void createSourceWrapsDataIntegrityViolations() {
        MbfcSourceCreateRequest request = new MbfcSourceCreateRequest(
                13L,
                "Example",
                "https://mbfc.org/example",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        when(mbfcSourceRepository.existsById(13L)).thenReturn(false);
        when(mbfcSourceRepository.save(any(MbfcSource.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.createSource(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mbfcUrl");
    }

    @Test
    void updateSourceUpdatesDomainAndFields() {
        MbfcSource source = MbfcSource.builder()
                .mbfcSourceId(20L)
                .sourceName("Old")
                .mbfcUrl("https://mbfc.org/old")
                .sourceUrl("https://old.com")
                .sourceUrlDomain("old.com")
                .build();

        MbfcSourceUpdateRequest request = new MbfcSourceUpdateRequest(
                "New Name",
                "https://mbfc.org/new",
                "center",
                null,
                "mixed",
                null,
                "https://newsite.com/path",
                null,
                "medium"
        );

        when(mbfcSourceRepository.findById(20L)).thenReturn(Optional.of(source));
        when(mbfcSourceRepository.save(any(MbfcSource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateSource(20L, request);

        assertThat(response.sourceName()).isEqualTo("New Name");
        assertThat(response.mbfcUrl()).isEqualTo("https://mbfc.org/new");
        assertThat(response.sourceUrlDomain()).isEqualTo("newsite.com");
        assertThat(response.bias()).isEqualTo("center");
    }

    @Test
    void deleteSourceRejectsReferencedSources() {
        MbfcSource source = MbfcSource.builder().mbfcSourceId(30L).build();
        when(mbfcSourceRepository.findById(30L)).thenReturn(Optional.of(source));
        when(publisherRepository.existsByMbfcSource(source)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteSource(30L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("still referenced");

        verify(mbfcSourceRepository, never()).delete(any());
    }

    @Test
    void deleteSourceRemovesUnreferencedSource() {
        MbfcSource source = MbfcSource.builder().mbfcSourceId(31L).build();
        when(mbfcSourceRepository.findById(31L)).thenReturn(Optional.of(source));
        when(publisherRepository.existsByMbfcSource(source)).thenReturn(false);

        service.deleteSource(31L);

        ArgumentCaptor<MbfcSource> captor = ArgumentCaptor.forClass(MbfcSource.class);
        verify(mbfcSourceRepository).delete(captor.capture());
        assertThat(captor.getValue().getMbfcSourceId()).isEqualTo(31L);
    }

    @Test
    void getSource_whenNotFound_throws() {
        when(mbfcSourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSource(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void createSource_rejectsBlankRequiredFields() {
        MbfcSourceCreateRequest request = new MbfcSourceCreateRequest(
                1L,
                "   ",
                "   ",
                null, null, null, null, null, null, null
        );

        when(mbfcSourceRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.createSource(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceName must not be blank");
    }

    @Test
    void updateSource_whenSourceUrlDomainProvided_usesItOverSourceUrl() {
        MbfcSource source = MbfcSource.builder()
                .mbfcSourceId(40L)
                .sourceName("Old")
                .mbfcUrl("https://mbfc.org/old")
                .sourceUrl("https://old.com/path")
                .sourceUrlDomain("old.com")
                .build();

        MbfcSourceUpdateRequest request = new MbfcSourceUpdateRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                "https://ignored.com/page",
                "  WWW.Example.COM  ",
                null
        );

        when(mbfcSourceRepository.findById(40L)).thenReturn(Optional.of(source));
        when(mbfcSourceRepository.save(any(MbfcSource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateSource(40L, request);

        assertThat(response.sourceUrl()).isEqualTo("https://ignored.com/page");
        assertThat(response.sourceUrlDomain()).isEqualTo("example.com");
    }

    @Test
    void updateSource_whenSourceNameProvidedButBlank_throws() {
        MbfcSource source = MbfcSource.builder()
                .mbfcSourceId(41L)
                .sourceName("Old")
                .mbfcUrl("https://mbfc.org/old")
                .build();

        when(mbfcSourceRepository.findById(41L)).thenReturn(Optional.of(source));

        MbfcSourceUpdateRequest request = new MbfcSourceUpdateRequest(
                "   ",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.updateSource(41L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceName must not be blank");
    }

    @Test
    void updateSource_whenSourceUrlCleared_setsDomainNull() {
        MbfcSource source = MbfcSource.builder()
                .mbfcSourceId(42L)
                .sourceName("Old")
                .mbfcUrl("https://mbfc.org/old")
                .sourceUrl("https://old.com/path")
                .sourceUrlDomain("old.com")
                .build();

        when(mbfcSourceRepository.findById(42L)).thenReturn(Optional.of(source));
        when(mbfcSourceRepository.save(any(MbfcSource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MbfcSourceUpdateRequest request = new MbfcSourceUpdateRequest(
                null, null, null, null, null, null,
                "   ",
                null,
                null
        );

        var response = service.updateSource(42L, request);

        assertThat(response.sourceUrl()).isNull();
        assertThat(response.sourceUrlDomain()).isNull();
    }

    @Test
    void updateSource_wrapsDataIntegrityViolation() {
        MbfcSource source = MbfcSource.builder()
                .mbfcSourceId(43L)
                .sourceName("Old")
                .mbfcUrl("https://mbfc.org/old")
                .build();

        when(mbfcSourceRepository.findById(43L)).thenReturn(Optional.of(source));
        when(mbfcSourceRepository.save(any(MbfcSource.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        MbfcSourceUpdateRequest request = new MbfcSourceUpdateRequest(
                "New",
                null,
                null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> service.updateSource(43L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mbfcUrl");
    }
}