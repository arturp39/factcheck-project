package com.factcheck.collector.integration.ingestion.fetcher;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class ArticleFetchResult {
    Instant fetchedAt;
    Integer httpStatus;
    String httpEtag;
    String httpLastModified;
    String finalUrl;
    String extractedText;
    Boolean blockedSuspected;
    String fetchError;
    String extractionError;
}