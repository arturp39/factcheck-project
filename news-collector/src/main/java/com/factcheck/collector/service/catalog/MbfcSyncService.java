package com.factcheck.collector.service.catalog;

import com.factcheck.collector.domain.entity.MbfcSource;
import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.integration.catalog.mbfc.MbfcApiClient;
import com.factcheck.collector.integration.catalog.mbfc.MbfcApiEntry;
import com.factcheck.collector.repository.MbfcSourceRepository;
import com.factcheck.collector.repository.PublisherRepository;
import com.factcheck.collector.util.DomainUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MbfcSyncService {

    private static final int BATCH_SIZE = 500;

    private final MbfcApiClient mbfcApiClient;
    private final MbfcSourceRepository mbfcSourceRepository;
    private final PublisherRepository publisherRepository;

    @Transactional
    public MbfcSyncResult syncAndMap() {
        List<MbfcApiEntry> entries = mbfcApiClient.fetchAll();
        int saved = upsertSources(entries);
        int mapped = mapUnmappedPublishers();

        log.info("MBFC sync finished: fetched={}, saved={}, mapped={}", entries.size(), saved, mapped);
        return new MbfcSyncResult(entries.size(), saved, mapped);
    }

    @Transactional
    public MbfcSyncResult mapExistingSources() {
        int mapped = mapUnmappedPublishers();
        log.info("MBFC mapping finished: mapped={}", mapped);
        return new MbfcSyncResult(0, 0, mapped);
    }

    private int upsertSources(List<MbfcApiEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }

        Map<Long, MbfcApiEntry> byId = new LinkedHashMap<>();
        for (MbfcApiEntry e : entries) {
            if (e == null || e.getSourceId() == null) {
                continue;
            }
            byId.putIfAbsent(e.getSourceId(), e);
        }

        List<MbfcSource> toSave = new ArrayList<>(byId.size());
        Instant now = Instant.now();

        for (MbfcApiEntry entry : byId.values()) {
            SourceUrlInfo sourceUrlInfo = resolveSourceUrl(entry.getSourceUrl());

            MbfcSource source = MbfcSource.builder()
                    .mbfcSourceId(entry.getSourceId())
                    .sourceName(trimToNull(entry.getSourceName()))
                    .mbfcUrl(trimToNull(entry.getMbfcUrl()))
                    .bias(trimToNull(entry.getBias()))
                    .country(trimToNull(entry.getCountry()))
                    .factualReporting(trimToNull(entry.getFactualReporting()))
                    .mediaType(trimToNull(entry.getMediaType()))
                    .sourceUrl(sourceUrlInfo.sourceUrl())
                    .sourceUrlDomain(sourceUrlInfo.sourceUrlDomain())
                    .credibility(trimToNull(entry.getCredibility()))
                    .syncedAt(now)
                    .build();

            toSave.add(source);
        }

        int total = 0;
        for (int i = 0; i < toSave.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, toSave.size());
            mbfcSourceRepository.saveAll(toSave.subList(i, end));
            total += (end - i);
        }

        return total;
    }

    private int mapUnmappedPublishers() {
        List<MbfcSource> sources = mbfcSourceRepository.findAll();
        if (sources.isEmpty()) {
            return 0;
        }

        Map<String, MbfcSource> byDomain = new HashMap<>();
        for (MbfcSource source : sources) {
            String d = source.getSourceUrlDomain();
            if (d != null) {
                byDomain.putIfAbsent(d, source);
            }
        }

        List<Publisher> publishers = publisherRepository.findAllByMbfcSourceIsNull();
        if (publishers.isEmpty()) {
            return 0;
        }

        int mapped = 0;
        List<Publisher> toUpdate = new ArrayList<>();

        for (Publisher publisher : publishers) {
            String publisherDomain = DomainUtils.normalizeDomain(publisher.getWebsiteUrl());
            if (publisherDomain == null) {
                continue;
            }

            MbfcSource match = byDomain.get(publisherDomain);
            if (match == null) {
                continue;
            }

            publisher.setMbfcSource(match);
            toUpdate.add(publisher);
            mapped++;
        }

        if (!toUpdate.isEmpty()) {
            publisherRepository.saveAll(toUpdate);
        }

        return mapped;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record MbfcSyncResult(int fetched, int saved, int mapped) {}

    private SourceUrlInfo resolveSourceUrl(String sourceUrl) {
        String trimmed = trimToNull(sourceUrl);
        if (trimmed == null) {
            return new SourceUrlInfo(null, null);
        }
        String domain = DomainUtils.normalizeDomain(trimmed);
        if (domain == null || !domain.contains(".")) {
            return new SourceUrlInfo(null, null);
        }
        return new SourceUrlInfo(trimmed, domain);
    }

    private record SourceUrlInfo(String sourceUrl, String sourceUrlDomain) {}
}