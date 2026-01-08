package com.factcheck.collector.service.catalog;

import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.SourceKind;
import com.factcheck.collector.dto.SourceCreateRequest;
import com.factcheck.collector.dto.SourceUpdateRequest;
import com.factcheck.collector.repository.PublisherRepository;
import com.factcheck.collector.repository.SourceEndpointRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SourceServiceTest {

    private final SourceEndpointRepository sourceEndpointRepository = mock(SourceEndpointRepository.class);
    private final PublisherRepository publisherRepository = mock(PublisherRepository.class);

    private final SourceService service = new SourceService(sourceEndpointRepository, publisherRepository);

    @Test
    void listSources_sortsByIdAsc_andMapsToResponse() {
        Publisher p = Publisher.builder().id(10L).name("Pub").build();
        SourceEndpoint s1 = SourceEndpoint.builder().id(2L).publisher(p).kind(SourceKind.RSS).displayName("B").rssUrl("https://b/rss").enabled(true).fetchIntervalMinutes(30).build();
        SourceEndpoint s2 = SourceEndpoint.builder().id(5L).publisher(p).kind(SourceKind.RSS).displayName("A").rssUrl("https://a/rss").enabled(true).fetchIntervalMinutes(30).build();

        when(sourceEndpointRepository.findAll(any(Sort.class))).thenReturn(List.of(s1, s2));

        var res = service.listSources();

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(sourceEndpointRepository).findAll(sortCaptor.capture());

        Sort sort = sortCaptor.getValue();
        assertThat(sort.getOrderFor("id")).isNotNull();
        assertThat(sort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);

        assertThat(res).hasSize(2);
        assertThat(res.getFirst().publisherId()).isEqualTo(10L);
        assertThat(res.getFirst().publisherName()).isEqualTo("Pub");
    }

    @Test
    void createSource_whenPublisherIdProvided_updatesPublisherFields_andAppliesEndpointDefaults() {
        Publisher existing = Publisher.builder().id(1L).name("Old").countryCode("US").websiteUrl("old").build();
        when(publisherRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(publisherRepository.save(any(Publisher.class))).thenAnswer(inv -> inv.getArgument(0, Publisher.class));

        when(sourceEndpointRepository.save(any(SourceEndpoint.class))).thenAnswer(inv -> {
            SourceEndpoint se = inv.getArgument(0, SourceEndpoint.class);
            se.setId(100L);
            return se;
        });

        SourceCreateRequest req = new SourceCreateRequest(
                1L,
                "New Name",
                "LT",
                "https://pub.example",
                SourceKind.RSS,
                "My Feed",
                "https://example.com/rss.xml",
                null,
                null,
                null,
                null
        );

        var resp = service.createSource(req);

        ArgumentCaptor<Publisher> pubCaptor = ArgumentCaptor.forClass(Publisher.class);
        verify(publisherRepository).save(pubCaptor.capture());
        assertThat(pubCaptor.getValue().getId()).isEqualTo(1L);
        assertThat(pubCaptor.getValue().getName()).isEqualTo("New Name");
        assertThat(pubCaptor.getValue().getCountryCode()).isEqualTo("LT");
        assertThat(pubCaptor.getValue().getWebsiteUrl()).isEqualTo("https://pub.example");

        ArgumentCaptor<SourceEndpoint> seCaptor = ArgumentCaptor.forClass(SourceEndpoint.class);
        verify(sourceEndpointRepository).save(seCaptor.capture());
        SourceEndpoint saved = seCaptor.getValue();

        assertThat(saved.getPublisher().getId()).isEqualTo(1L);
        assertThat(saved.getKind()).isEqualTo(SourceKind.RSS);
        assertThat(saved.getDisplayName()).isEqualTo("My Feed");
        assertThat(saved.getRssUrl()).isEqualTo("https://example.com/rss.xml");
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getFetchIntervalMinutes()).isEqualTo(30);

        assertThat(resp.id()).isEqualTo(100L);
        assertThat(resp.publisherId()).isEqualTo(1L);
        assertThat(resp.kind()).isEqualTo(SourceKind.RSS);
    }

    @Test
    void createSource_whenPublisherIdNotProvided_createsOrReusesByNameIgnoreCase() {
        when(publisherRepository.findByNameIgnoreCase("Pub")).thenReturn(Optional.empty());
        when(publisherRepository.save(any(Publisher.class))).thenAnswer(inv -> {
            Publisher p = inv.getArgument(0, Publisher.class);
            p.setId(77L);
            return p;
        });

        when(sourceEndpointRepository.save(any(SourceEndpoint.class))).thenAnswer(inv -> {
            SourceEndpoint se = inv.getArgument(0, SourceEndpoint.class);
            se.setId(200L);
            return se;
        });

        SourceCreateRequest req = new SourceCreateRequest(
                null,
                "Pub",
                null,
                null,
                SourceKind.API,
                "API Source",
                null,
                "newsapi",
                "q=ai",
                true,
                15
        );

        var resp = service.createSource(req);

        assertThat(resp.publisherId()).isEqualTo(77L);
        assertThat(resp.publisherName()).isEqualTo("Pub");
        assertThat(resp.kind()).isEqualTo(SourceKind.API);
        assertThat(resp.apiProvider()).isEqualTo("newsapi");
        assertThat(resp.apiQuery()).isEqualTo("q=ai");
        assertThat(resp.enabled()).isTrue();
        assertThat(resp.fetchIntervalMinutes()).isEqualTo(15);
    }

    @Test
    void createSource_throwsWhenPublisherNameMissingAndNoPublisherId() {
        SourceCreateRequest req = new SourceCreateRequest(
                null,
                "   ",
                null,
                null,
                SourceKind.RSS,
                "x",
                "https://example.com/rss.xml",
                null,
                null,
                true,
                10
        );

        assertThatThrownBy(() -> service.createSource(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publisherName is required");

        verifyNoInteractions(sourceEndpointRepository);
    }

    @Test
    void createSource_validatesRssKind_requiresRssUrl() {
        when(publisherRepository.findByNameIgnoreCase("Pub")).thenReturn(Optional.of(Publisher.builder().id(1L).name("Pub").build()));
        when(publisherRepository.save(any(Publisher.class))).thenAnswer(inv -> inv.getArgument(0, Publisher.class));

        SourceCreateRequest req = new SourceCreateRequest(
                null,
                "Pub",
                null,
                null,
                SourceKind.RSS,
                "RSS",
                "   ",
                null,
                null,
                true,
                10
        );

        assertThatThrownBy(() -> service.createSource(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rssUrl is required");

        verify(sourceEndpointRepository, never()).save(any());
    }

    @Test
    void createSource_validatesApiKind_requiresProviderAndQuery() {
        when(publisherRepository.findByNameIgnoreCase("Pub")).thenReturn(Optional.of(Publisher.builder().id(1L).name("Pub").build()));
        when(publisherRepository.save(any(Publisher.class))).thenAnswer(inv -> inv.getArgument(0, Publisher.class));

        SourceCreateRequest missingProvider = new SourceCreateRequest(
                null, "Pub", null, null,
                SourceKind.API,
                "API",
                null,
                "   ",
                "q=1",
                true,
                10
        );

        assertThatThrownBy(() -> service.createSource(missingProvider))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiProvider is required");

        SourceCreateRequest missingQuery = new SourceCreateRequest(
                null, "Pub", null, null,
                SourceKind.API,
                "API",
                null,
                "provider",
                "   ",
                true,
                10
        );

        assertThatThrownBy(() -> service.createSource(missingQuery))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiQuery is required");

        verify(sourceEndpointRepository, never()).save(any());
    }

    @Test
    void createSource_wrapsDuplicateAsIllegalArgumentException() {
        when(publisherRepository.findByNameIgnoreCase("Pub")).thenReturn(Optional.of(Publisher.builder().id(1L).name("Pub").build()));
        when(publisherRepository.save(any(Publisher.class))).thenAnswer(inv -> inv.getArgument(0, Publisher.class));

        when(sourceEndpointRepository.save(any(SourceEndpoint.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        SourceCreateRequest req = new SourceCreateRequest(
                null, "Pub", null, null,
                SourceKind.RSS,
                "RSS",
                "https://example.com/rss.xml",
                null,
                null,
                true,
                10
        );

        assertThatThrownBy(() -> service.createSource(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source endpoint already exists for RSS")
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updateSource_throwsWhenNotFound() {
        when(sourceEndpointRepository.findById(123L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSource(123L, new SourceUpdateRequest(
                null, null, null, null,
                null, null, null, null, null,
                null, null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source endpoint not found");

        verify(sourceEndpointRepository, never()).save(any());
    }


    @Test
    void updateSource_updatesPublisherWhenPublisherIdProvided() {
        Publisher oldPub = Publisher.builder().id(1L).name("Old").build();
        Publisher newPub = Publisher.builder().id(2L).name("New").build();

        SourceEndpoint endpoint = SourceEndpoint.builder()
                .id(10L)
                .publisher(oldPub)
                .kind(SourceKind.RSS)
                .displayName("Old disp")
                .rssUrl("https://old/rss")
                .enabled(true)
                .fetchIntervalMinutes(30)
                .build();

        when(sourceEndpointRepository.findById(10L)).thenReturn(Optional.of(endpoint));

        when(publisherRepository.findById(2L)).thenReturn(Optional.of(newPub));
        when(publisherRepository.save(any(Publisher.class))).thenAnswer(inv -> inv.getArgument(0, Publisher.class));

        when(sourceEndpointRepository.save(any(SourceEndpoint.class))).thenAnswer(inv -> inv.getArgument(0, SourceEndpoint.class));

        var resp = service.updateSource(10L, new SourceUpdateRequest(
                2L,
                "New",
                "LT",
                "https://new",
                null,
                "New disp",
                "https://new/rss",
                null,
                null,
                false,
                5
        ));

        assertThat(resp.publisherId()).isEqualTo(2L);
        assertThat(resp.publisherName()).isEqualTo("New");
        assertThat(resp.displayName()).isEqualTo("New disp");
        assertThat(resp.rssUrl()).isEqualTo("https://new/rss");
        assertThat(resp.enabled()).isFalse();
        assertThat(resp.fetchIntervalMinutes()).isEqualTo(5);
    }

    @Test
    void updateSource_validatesAfterKindChangeToApi() {
        Publisher pub = Publisher.builder().id(1L).name("Pub").build();
        SourceEndpoint endpoint = SourceEndpoint.builder()
                .id(10L)
                .publisher(pub)
                .kind(SourceKind.RSS)
                .displayName("X")
                .rssUrl("https://x/rss")
                .enabled(true)
                .fetchIntervalMinutes(30)
                .build();

        when(sourceEndpointRepository.findById(10L)).thenReturn(Optional.of(endpoint));

        assertThatThrownBy(() -> service.updateSource(10L, new SourceUpdateRequest(
                null, null, null, null,
                SourceKind.API,
                null,
                null,
                "   ",
                "q=1",
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiProvider is required");

        verify(sourceEndpointRepository, never()).save(any());
    }

    @Test
    void updateSource_wrapsDuplicateAsIllegalArgumentException() {
        Publisher pub = Publisher.builder().id(1L).name("Pub").build();
        SourceEndpoint endpoint = SourceEndpoint.builder()
                .id(10L)
                .publisher(pub)
                .kind(SourceKind.RSS)
                .displayName("X")
                .rssUrl("https://x/rss")
                .enabled(true)
                .fetchIntervalMinutes(30)
                .build();

        when(sourceEndpointRepository.findById(10L)).thenReturn(Optional.of(endpoint));
        when(sourceEndpointRepository.save(any(SourceEndpoint.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.updateSource(10L, new SourceUpdateRequest(
                null, null, null, null,
                null,
                "X",
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source endpoint already exists for X")
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }
}
