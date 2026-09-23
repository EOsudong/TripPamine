package com.example.trippaminebe.domain.mascot.repository;

import com.example.trippaminebe.domain.mascot.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {
}
