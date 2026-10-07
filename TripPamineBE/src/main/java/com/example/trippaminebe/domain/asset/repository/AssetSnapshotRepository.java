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
    findTopByUserIdOrderBySnapshotDateDesc(
            Long userId
    );


    // =========================================
    // 사용자의 전체 자산 변화 그래프
    // =========================================

    List<AssetSnapshot>
    findByUserIdOrderBySnapshotDateAsc(
            Long userId
    );


    // =========================================
    // 특정 사용자의 특정 날짜 Snapshot
    //
    // 하루에 Snapshot 하나만 사용
    // =========================================

    Optional<AssetSnapshot>
    findByUserIdAndSnapshotDate(
            Long userId,
            LocalDate snapshotDate
    );


    // =========================================
    // 기간별 자산 Snapshot 조회
    //
    // 예:
    // 1주일
    // 1개월
    // 1분기
    // 1년
    //
    // startDate 이상
    // endDate 이하
    //
    // 날짜 오름차순으로 반환
    // =========================================

    List<AssetSnapshot>
    findByUserIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );
}