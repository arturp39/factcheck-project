package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.MbfcSource;
import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.integration.mbfc.MbfcApiClient;
import com.factcheck.collector.integration.mbfc.MbfcApiEntry;
import com.factcheck.collector.repository.MbfcSourceRepository;
import com.factcheck.collector.repository.PublisherRepository;
import com.factcheck.collector.util.DomainUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MbfcSyncService {

    private final MbfcApiClient mbfcApiClient;
    private final MbfcSourceRepository mbfcSourceRepository;
    private final PublisherRepository publisherRepository;

    public MbfcSyncResult syncAndMap() {
        List<MbfcApiEntry> entries = mbfcApiClient.fetchAll();
        int saved = upsertSources(entries);
        int mapped = mapPublishers();
        return new MbfcSyncResult(entries.size(), saved, mapped);
    }

    private int upsertSources(List<MbfcApiEntry> entries) {
        int saved = 0;
        for (MbfcApiEntry entry : entries) {
            if (entry.getSourceId() == null) {
                continue;
            }

            MbfcSource source = MbfcSource.builder()
                    .mbfcSourceId(entry.getSourceId())
                    .sourceName(trimToNull(entry.getSourceName()))
                    .mbfcUrl(trimToNull(entry.getMbfcUrl()))
                    .bias(trimToNull(entry.getBias()))
                    .country(trimToNull(entry.getCountry()))
                    .factualReporting(trimToNull(entry.getFactualReporting()))
                    .mediaType(trimToNull(entry.getMediaType()))
                    .sourceUrl(trimToNull(entry.getSourceUrl()))
                    .sourceUrlDomain(DomainUtils.normalizeDomain(entry.getSourceUrl()))
                    .credibility(trimToNull(entry.getCredibility()))
                    .syncedAt(Instant.now())
                    .build();

            mbfcSourceRepository.save(source);
            saved++;
        }
        return saved;
    }

    private int mapPublishers() {
        Map<String, MbfcSource> byDomain = new HashMap<>();
        Map<String, MbfcSource> byUrl = new HashMap<>();

        List<MbfcSource> sources = mbfcSourceRepository.findAll();
        for (MbfcSource source : sources) {
            if (source.getSourceUrlDomain() != null) {
                byDomain.putIfAbsent(source.getSourceUrlDomain(), source);
            }
            if (source.getMbfcUrl() != null) {
                byUrl.putIfAbsent(source.getMbfcUrl().toLowerCase(), source);
            }
        }

        List<Publisher> publishers = publisherRepository.findAll();
        List<Publisher> toUpdate = new ArrayList<>();
        int mapped = 0;

        for (Publisher publisher : publishers) {
            if (publisher.getMbfcSource() != null) {
                continue;
            }

            MbfcSource match = null;
            String publisherMbfcUrl = trimToNull(publisher.getMbfcUrl());
            if (publisherMbfcUrl != null) {
                match = byUrl.get(publisherMbfcUrl.toLowerCase());
            }

            if (match == null) {
                String publisherDomain = DomainUtils.normalizeDomain(publisher.getWebsiteUrl());
                if (publisherDomain != null) {
                    match = byDomain.get(publisherDomain);
                }
            }

            if (match != null) {
                publisher.setMbfcSource(match);
                toUpdate.add(publisher);
                mapped++;
            }
        }

        if (!toUpdate.isEmpty()) {
            publisherRepository.saveAll(toUpdate);
        }

        return mapped;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record MbfcSyncResult(int fetched, int saved, int mapped) {}
}
