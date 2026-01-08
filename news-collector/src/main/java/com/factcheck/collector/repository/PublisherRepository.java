package com.factcheck.collector.repository;

import com.factcheck.collector.domain.entity.MbfcSource;
import com.factcheck.collector.domain.entity.Publisher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PublisherRepository extends JpaRepository<Publisher, Long> {

    Optional<Publisher> findByNameIgnoreCase(String name);

    boolean existsByMbfcSource(MbfcSource mbfcSource);

    List<Publisher> findAllByMbfcSourceIsNull();

    @Query("select p from Publisher p left join fetch p.mbfcSource where p.id = :id")
    Optional<Publisher> findByIdWithMbfcSource(Long id);
}