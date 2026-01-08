package com.factcheck.collector.service.catalog;

import com.factcheck.collector.domain.entity.MbfcSource;
import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.dto.PublisherCreateRequest;
import com.factcheck.collector.dto.PublisherUpdateRequest;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.repository.MbfcSourceRepository;
import com.factcheck.collector.repository.PublisherRepository;
import com.factcheck.collector.repository.SourceEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublisherServiceTest {

    @Mock
    private PublisherRepository publisherRepository;

    @Mock
    private MbfcSourceRepository mbfcSourceRepository;

    @Mock
    private SourceEndpointRepository sourceEndpointRepository;

    @Mock
    private ArticleRepository articleRepository;

    private PublisherService service;

    @BeforeEach
    void setUp() {
        service = new PublisherService(
                publisherRepository,
                mbfcSourceRepository,
                sourceEndpointRepository,
                articleRepository
        );
    }

    @Test
    void listPublishersMapsResponses() {
        Publisher publisher = Publisher.builder()
                .id(1L)
                .name("Example")
                .countryCode("US")
                .websiteUrl("https://example.com")
                .build();

        // FIX: disambiguate overloaded findAll(...) by typing the matcher as Sort
        when(publisherRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(publisher));

        var result = service.listPublishers();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Example");
        assertThat(result.getFirst().countryCode()).isEqualTo("US");
    }

    @Test
    void createPublisherRejectsBlankName() {
        PublisherCreateRequest request = new PublisherCreateRequest(
                " ",
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.createPublisher(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
    }

    @Test
    void createPublisherRejectsDuplicateName() {
        when(publisherRepository.findByNameIgnoreCase("Example"))
                .thenReturn(Optional.of(Publisher.builder().id(1L).name("Example").build()));

        PublisherCreateRequest request = new PublisherCreateRequest(
                "Example",
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.createPublisher(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createPublisherPersistsMbfcSource() {
        MbfcSource mbfcSource = MbfcSource.builder().mbfcSourceId(9L).build();
        when(mbfcSourceRepository.findById(9L)).thenReturn(Optional.of(mbfcSource));
        when(publisherRepository.findByNameIgnoreCase("Example")).thenReturn(Optional.empty());
        when(publisherRepository.save(any(Publisher.class)))
                .thenAnswer(invocation -> {
                    Publisher saved = invocation.getArgument(0);
                    saved.setId(10L);
                    return saved;
                });

        PublisherCreateRequest request = new PublisherCreateRequest(
                "Example",
                "US",
                "https://example.com",
                9L
        );

        var response = service.createPublisher(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.mbfcSourceId()).isEqualTo(9L);
        assertThat(response.websiteUrl()).isEqualTo("https://example.com");
    }

    @Test
    void updatePublisherRejectsDuplicateName() {
        Publisher existing = Publisher.builder().id(1L).name("Old").build();
        when(publisherRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(publisherRepository.findByNameIgnoreCase("New"))
                .thenReturn(Optional.of(Publisher.builder().id(2L).name("New").build()));

        PublisherUpdateRequest request = new PublisherUpdateRequest(
                "New",
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.updatePublisher(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updatePublisherUpdatesFields() {
        Publisher existing = Publisher.builder().id(1L).name("Old").build();
        MbfcSource mbfcSource = MbfcSource.builder().mbfcSourceId(5L).build();
        when(publisherRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(mbfcSourceRepository.findById(5L)).thenReturn(Optional.of(mbfcSource));
        when(publisherRepository.findByNameIgnoreCase("New")).thenReturn(Optional.empty());
        when(publisherRepository.save(any(Publisher.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PublisherUpdateRequest request = new PublisherUpdateRequest(
                "New",
                "CA",
                "https://new.com",
                5L
        );

        var response = service.updatePublisher(1L, request);

        assertThat(response.name()).isEqualTo("New");
        assertThat(response.countryCode()).isEqualTo("CA");
        assertThat(response.mbfcSourceId()).isEqualTo(5L);
    }

    @Test
    void deletePublisherRejectsWhenReferenced() {
        Publisher publisher = Publisher.builder().id(4L).name("Example").build();
        when(publisherRepository.findById(4L)).thenReturn(Optional.of(publisher));
        when(sourceEndpointRepository.existsByPublisher(publisher)).thenReturn(true);

        assertThatThrownBy(() -> service.deletePublisher(4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source endpoints");

        verify(publisherRepository, never()).delete(any());
    }

    @Test
    void deletePublisherRejectsWhenArticlesExist() {
        Publisher publisher = Publisher.builder().id(5L).name("Example").build();
        when(publisherRepository.findById(5L)).thenReturn(Optional.of(publisher));
        when(sourceEndpointRepository.existsByPublisher(publisher)).thenReturn(false);
        when(articleRepository.existsByPublisher(publisher)).thenReturn(true);

        assertThatThrownBy(() -> service.deletePublisher(5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("articles");

        verify(publisherRepository, never()).delete(any());
    }

    @Test
    void deletePublisherRemovesWhenUnused() {
        Publisher publisher = Publisher.builder().id(6L).name("Example").build();
        when(publisherRepository.findById(6L)).thenReturn(Optional.of(publisher));
        when(sourceEndpointRepository.existsByPublisher(publisher)).thenReturn(false);
        when(articleRepository.existsByPublisher(publisher)).thenReturn(false);

        service.deletePublisher(6L);

        ArgumentCaptor<Publisher> captor = ArgumentCaptor.forClass(Publisher.class);
        verify(publisherRepository).delete(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(6L);
    }

    @Test
    void savePublisherWrapsDataIntegrityViolation() {
        when(publisherRepository.findByNameIgnoreCase("Example")).thenReturn(Optional.empty());
        when(publisherRepository.save(any(Publisher.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        PublisherCreateRequest request = new PublisherCreateRequest(
                "Example",
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.createPublisher(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }
}