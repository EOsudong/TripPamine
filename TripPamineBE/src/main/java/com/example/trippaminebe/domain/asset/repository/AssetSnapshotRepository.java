package com.example.trippaminebe.domain.asset.repository;

import com.example.trippaminebe.domain.asset.entity.AssetSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


public interface AssetSnapshotRepository
        extends JpaRepository<AssetSnapshot, Long> {

    // =========================================
    // 가장 최근 자산 정보
    // =========================================

    Optional<AssetSnapshot>
    findTopByUserIdOrderBySnapshotDateDesc(Long userId);


    // =========================================
    // 자산 변화 그래프용
    // =========================================

    List<AssetSnapshot>
    findByUserIdOrderBySnapshotDateAsc(Long userId);


    // =========================================
    // 특정 사용자의 특정 날짜 Snapshot 조회
    //
    // 하루에 Snapshot 하나만 사용하기 위한 메서드
    // =========================================

    Optional<AssetSnapshot>
    findByUserIdAndSnapshotDate(
            Long userId,
            LocalDate snapshotDate
    );
}