package com.factcheck.collector.repository;

import com.factcheck.collector.domain.entity.SourceEndpoint;
import com.factcheck.collector.domain.enums.SourceKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceEndpointRepository extends JpaRepository<SourceEndpoint, Long> {

    List<SourceEndpoint> findByEnabledTrue();

    List<SourceEndpoint> findByEnabledTrueAndKind(SourceKind kind);
}