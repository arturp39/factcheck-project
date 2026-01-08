package com.factcheck.collector.repository;

import com.factcheck.collector.domain.entity.IngestionRun;
import com.factcheck.collector.domain.enums.IngestionRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface IngestionRunRepository extends JpaRepository<IngestionRun, Long> {

    List<IngestionRun> findByStatusAndStartedAtBefore(IngestionRunStatus status, Instant cutoff);

    @Modifying
    @Query(value = """
            UPDATE content.ingestion_runs r
            SET status = 'FAILED',
                completed_at = NOW(),
                version = r.version + 1
            WHERE r.id = :runId
              AND r.status = 'RUNNING'
              AND NOT EXISTS (
                SELECT 1
                FROM content.ingestion_logs l
                WHERE l.run_id = r.id
                  AND l.completed_at IS NULL
              )
              AND EXISTS (
                SELECT 1
                FROM content.ingestion_logs l
                WHERE l.run_id = r.id
                  AND l.status IN ('FAILED', 'PARTIAL')
              )
            """, nativeQuery = true)
    int finalizeRunFailedIfComplete(@Param("runId") Long runId);

    @Modifying
    @Query(value = """
            UPDATE content.ingestion_runs r
            SET status = 'COMPLETED',
                completed_at = NOW(),
                version = r.version + 1
            WHERE r.id = :runId
              AND r.status = 'RUNNING'
              AND NOT EXISTS (
                SELECT 1
                FROM content.ingestion_logs l
                WHERE l.run_id = r.id
                  AND l.completed_at IS NULL
              )
              AND NOT EXISTS (
                SELECT 1
                FROM content.ingestion_logs l
                WHERE l.run_id = r.id
                  AND l.status IN ('FAILED', 'PARTIAL')
              )
            """, nativeQuery = true)
    int finalizeRunCompletedIfComplete(@Param("runId") Long runId);
}