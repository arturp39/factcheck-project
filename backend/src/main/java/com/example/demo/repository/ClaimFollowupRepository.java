package com.example.demo.repository;

import com.example.demo.entity.ClaimFollowup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimFollowupRepository extends JpaRepository<ClaimFollowup, Long> {

    List<ClaimFollowup> findByClaimIdOrderByCreatedAtAsc(Long claimId);
}
