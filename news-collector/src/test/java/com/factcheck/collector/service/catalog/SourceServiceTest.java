package com.factcheck.collector.service.catalog;

import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.domain.enums.SourceKind;
import com.factcheck.collector.dto.SourceCreateRequest;
import com.factcheck.collector.repository.PublisherRepository;
import com.factcheck.collector.repository.SourceEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

    @Mock
    private SourceEndpointRepository sourceEndpointRepository;

    @Mock
    private PublisherRepository publisherRepository;

    private SourceService sourceService;

    @BeforeEach
    void setUp() {
        sourceService = new SourceService(sourceEndpointRepository, publisherRepository);
    }

    @Test
    void createSourceRequiresPublisherNameWhenNoPublisherId() {
        SourceCreateRequest request = new SourceCreateRequest(
                null,
                null,
                null,
                null,
                SourceKind.RSS,
                "Example Feed",
                "https://example.com/rss",
                null,
                null,
                true,
                15
        );

        assertThatThrownBy(() -> sourceService.createSource(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publisherName is required");
    }

    @Test
    void createSourceRequiresRssUrlForRssKind() {
        Publisher publisher = Publisher.builder().id(1L).name("Example").build();

        when(publisherRepository.findByNameIgnoreCase("Example"))
                .thenReturn(Optional.of(publisher));
        when(publisherRepository.save(any(Publisher.class)))
                .thenReturn(publisher);

        SourceCreateRequest request = new SourceCreateRequest(
                null,
                "Example",
                null,
                null,
                SourceKind.RSS,
                "Example Feed",
                null,
                null,
                null,
                true,
                15
        );

        assertThatThrownBy(() -> sourceService.createSource(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rssUrl is required");

        verify(sourceEndpointRepository, never()).save(any());
    }

    @Test
    void createSourceRequiresApiProviderForApiKind() {
        Publisher publisher = Publisher.builder().id(1L).name("Example").build();

        when(publisherRepository.findByNameIgnoreCase("Example"))
                .thenReturn(Optional.of(publisher));
        when(publisherRepository.save(any(Publisher.class)))
                .thenReturn(publisher);

        SourceCreateRequest request = new SourceCreateRequest(
                null,
                "Example",
                null,
                null,
                SourceKind.API,
                "Example API",
                null,
                null,
                "q=topic",
                true,
                15
        );

        assertThatThrownBy(() -> sourceService.createSource(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiProvider is required");

        verify(sourceEndpointRepository, never()).save(any());
    }
}