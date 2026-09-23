package com.example.trippaminebe.domain.mascot.repository;

import com.example.trippaminebe.domain.mascot.entity.Mascot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MascotRepository extends JpaRepository<Mascot, Long> {

    // 도감 화면에서 "이 지역 마스코트 다 모았는지" 진행률 계산 등에 쓸 수 있음
    List<Mascot> findByRegion_RegionId(Long regionId);
}
