package com.factcheck.collector.repository;

import com.factcheck.collector.domain.entity.Publisher;
import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.SourceKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SourceEndpointRepository extends JpaRepository<SourceEndpoint, Long> {

    List<SourceEndpoint> findByEnabledTrue();

    List<SourceEndpoint> findByEnabledTrueAndKind(SourceKind kind);

    boolean existsByPublisher(Publisher publisher);

    @Query(value = """
            SELECT *
            FROM content.source_endpoints
            WHERE enabled = true
              AND (blocked_until IS NULL OR blocked_until < CAST(:now AS TIMESTAMPTZ))
              AND (
                last_fetched_at IS NULL
                OR last_fetched_at < (CAST(:now AS TIMESTAMPTZ) - (fetch_interval_minutes * INTERVAL '1 minute'))
              )
            """, nativeQuery = true)
    List<SourceEndpoint> findEligibleForIngestion(@Param("now") Instant now);

    Optional<SourceEndpoint> findByPublisherAndKindAndApiProviderIgnoreCaseAndApiQueryIgnoreCase(
            Publisher publisher,
            SourceKind kind,
            String apiProvider,
            String apiQuery
    );
}