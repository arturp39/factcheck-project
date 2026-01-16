package com.factcheck.backend.repository;

import com.factcheck.backend.entity.ClaimLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimLogRepository extends JpaRepository<ClaimLog, Long> {

    Page<ClaimLog> findByOwnerUsername(String ownerUsername, Pageable pageable);
}
