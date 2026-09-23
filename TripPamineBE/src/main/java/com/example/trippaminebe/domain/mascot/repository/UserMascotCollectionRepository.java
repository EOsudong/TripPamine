package com.example.trippaminebe.domain.mascot.repository;

import com.example.trippaminebe.domain.mascot.entity.UserMascotCollection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserMascotCollectionRepository extends JpaRepository<UserMascotCollection, Long> {

    List<UserMascotCollection> findByUser_IdOrderByCaughtAtDesc(Long userId);

    // 같은 유저가 같은 마스코트를 이미 잡았는지 (도감 중복 표시, 재포획 정책 판단용)
    Optional<UserMascotCollection> findFirstByUser_IdAndMascot_MascotId(Long userId, Long mascotId);

    boolean existsByUser_IdAndMascot_MascotId(Long userId, Long mascotId);

    // AI 다이어리(diary 도메인)에서 "이번 여행에서 지역 마스코트를 몇 마리 모았는지" 같은
    // 통계에 재사용할 수 있도록 quest.repository의 countBy... 패턴을 그대로 따름
    long countByUser_Id(Long userId);
}
