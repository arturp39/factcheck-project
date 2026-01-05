package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.MbfcSource;
import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.integration.mbfc.MbfcApiClient;
import com.factcheck.collector.integration.mbfc.MbfcApiEntry;
import com.factcheck.collector.repository.MbfcSourceRepository;
import com.factcheck.collector.repository.PublisherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MbfcSyncServiceTest {

    @Mock
    private MbfcApiClient mbfcApiClient;

    @Mock
    private MbfcSourceRepository mbfcSourceRepository;

    @Mock
    private PublisherRepository publisherRepository;

    @InjectMocks
    private MbfcSyncService mbfcSyncService;

    @Test
    void syncAndMap_savesSourcesAndMapsPublishers() {
        MbfcApiEntry entryOne = new MbfcApiEntry();
        entryOne.setSourceId(104914L);
        entryOne.setSourceName("LiveLeak (ItemFix)");
        entryOne.setMbfcUrl("https://mediabiasfactcheck.com/liveleak/");
        entryOne.setSourceUrl("itemfix.com");

        MbfcApiEntry entryTwo = new MbfcApiEntry();
        entryTwo.setSourceId(107142L);
        entryTwo.setSourceName("Montreal Gazette");
        entryTwo.setMbfcUrl("https://mediabiasfactcheck.com/montreal-gazette/");
        entryTwo.setSourceUrl("montrealgazette.com");

        when(mbfcApiClient.fetchAll()).thenReturn(List.of(entryOne, entryTwo));

        MbfcSource sourceOne = MbfcSource.builder()
                .mbfcSourceId(104914L)
                .sourceName("LiveLeak (ItemFix)")
                .mbfcUrl("https://mediabiasfactcheck.com/liveleak/")
                .sourceUrl("itemfix.com")
                .sourceUrlDomain("itemfix.com")
                .build();
        MbfcSource sourceTwo = MbfcSource.builder()
                .mbfcSourceId(107142L)
                .sourceName("Montreal Gazette")
                .mbfcUrl("https://mediabiasfactcheck.com/montreal-gazette/")
                .sourceUrl("montrealgazette.com")
                .sourceUrlDomain("montrealgazette.com")
                .build();
        when(mbfcSourceRepository.findAll()).thenReturn(List.of(sourceOne, sourceTwo));

        Publisher byUrl = Publisher.builder()
                .id(1L)
                .name("LiveLeak")
                .mbfcUrl("https://mediabiasfactcheck.com/liveleak/")
                .websiteUrl("https://itemfix.com")
                .build();
        Publisher byDomain = Publisher.builder()
                .id(2L)
                .name("Montreal Gazette")
                .websiteUrl("https://montrealgazette.com")
                .build();
        Publisher existing = Publisher.builder()
                .id(3L)
                .name("Existing")
                .mbfcSource(sourceOne)
                .build();

        when(publisherRepository.findAll()).thenReturn(List.of(byUrl, byDomain, existing));

        MbfcSyncService.MbfcSyncResult result = mbfcSyncService.syncAndMap();

        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.saved()).isEqualTo(2);
        assertThat(result.mapped()).isEqualTo(2);

        verify(mbfcSourceRepository, times(2)).save(any(MbfcSource.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Publisher>> captor = (ArgumentCaptor<List<Publisher>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        verify(publisherRepository).saveAll(captor.capture());
        List<Publisher> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).allSatisfy(pub -> assertThat(pub.getMbfcSource()).isNotNull());
        assertThat(saved).anyMatch(pub -> pub.getId().equals(1L) && pub.getMbfcSource().getMbfcSourceId().equals(104914L));
        assertThat(saved).anyMatch(pub -> pub.getId().equals(2L) && pub.getMbfcSource().getMbfcSourceId().equals(107142L));
    }
}
