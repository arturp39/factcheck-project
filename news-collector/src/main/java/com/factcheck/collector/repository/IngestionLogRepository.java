package com.factcheck.collector.repository;

import com.factcheck.collector.domain.entity.IngestionLog;
import com.factcheck.collector.domain.enums.IngestionStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface IngestionLogRepository extends JpaRepository<IngestionLog, Long> {

    Optional<IngestionLog> findByRunIdAndSourceEndpointId(Long runId, Long sourceEndpointId);

    List<IngestionLog> findByRunId(Long runId);

    /**
     * Optional lock for manual operations.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("""
            select l
            from IngestionLog l
            where l.run.id = :runId
              and l.sourceEndpoint.id = :sourceEndpointId
            """)
    Optional<IngestionLog> findByRunIdAndSourceEndpointIdForUpdate(
            @Param("runId") Long runId,
            @Param("sourceEndpointId") Long sourceEndpointId
    );

    boolean existsByRunIdAndCompletedAtIsNull(Long runId);

    boolean existsByRunIdAndStatusIn(Long runId, Set<IngestionStatus> statuses);

    /**
     * Claim a log row with a lease and allow reclaim if stuck.
     */
    @Modifying
    @Query(value = """
            UPDATE content.ingestion_logs
            SET status = 'PROCESSING',
                started_at = NOW(),
                version = version + 1
            WHERE run_id = :runId
              AND source_endpoint_id = :endpointId
              AND completed_at IS NULL
              AND (
                    status IS NULL
                 OR status <> 'PROCESSING'
                 OR started_at < NOW() - (:leaseSeconds * INTERVAL '1 second')
              )
            """, nativeQuery = true)
    int claimLog(
            @Param("runId") Long runId,
            @Param("endpointId") Long endpointId,
            @Param("leaseSeconds") long leaseSeconds
    );

    @Modifying
    @Query(value = """
            UPDATE content.ingestion_logs
            SET status = 'FAILED',
                error_details = :msg,
                completed_at = NOW(),
                version = version + 1
            WHERE run_id = :runId
              AND completed_at IS NULL
            """, nativeQuery = true)
    int failPendingLogsForRun(@Param("runId") Long runId, @Param("msg") String msg);
}