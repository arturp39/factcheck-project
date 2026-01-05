package com.factcheck.collector.repository;

import com.factcheck.collector.domain.entity.IngestionRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionRunRepository extends JpaRepository<IngestionRun, Long> {
}
