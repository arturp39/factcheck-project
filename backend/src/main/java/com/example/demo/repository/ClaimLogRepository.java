package com.example.demo.repository;

import com.example.demo.entity.ClaimLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimLogRepository extends JpaRepository<ClaimLog, Long> {
}