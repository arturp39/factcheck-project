package com.factcheck.collector.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SearchFilters(
        List<String> sourceName,
        LocalDateTime publishedAfter,
        LocalDateTime publishedBefore
) {
}