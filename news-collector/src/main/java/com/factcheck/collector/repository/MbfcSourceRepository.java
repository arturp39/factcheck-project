package com.factcheck.collector.repository;

import com.factcheck.collector.domain.entity.MbfcSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MbfcSourceRepository extends JpaRepository<MbfcSource, Long> {
}