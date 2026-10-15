package com.example.trippaminebe.domain.mascot.repository;

import com.example.trippaminebe.domain.mascot.entity.UserMascotCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserMascotCollectionRepository extends JpaRepository<UserMascotCollection, Long> {

    List<UserMascotCollection> findByUser_IdOrderByCaughtAtDesc(Long userId);

    // 같은 유저가 같은 마스코트를 이미 잡았는지 (도감 중복 표시, 재포획 정책 판단용)
    Optional<UserMascotCollection> findFirstByUser_IdAndMascot_MascotId(Long userId, Long mascotId);

    boolean existsByUser_IdAndMascot_MascotId(Long userId, Long mascotId);

    // 이 마스코트를 지금까지 몇 번 잡았는지 (재포획 횟수 표시용)
    long countByUser_IdAndMascot_MascotId(Long userId, Long mascotId);

    // AI 다이어리(diary 도메인)에서 "이번 여행에서 지역 마스코트를 몇 마리 모았는지" 같은
    // 통계에 재사용할 수 있도록 quest.repository의 countBy... 패턴을 그대로 따름
    long countByUser_Id(Long userId);

    // [N+1 수정] 주변 스폰 조회 시 스폰 지점마다 exists 쿼리를 날리던 것을
    // "내가 잡은 마스코트 ID 목록" 한 번 조회로 바꾼다.
    @Query("select distinct c.mascot.mascotId from UserMascotCollection c where c.user.id = :userId")
    Set<Long> findCollectedMascotIds(@Param("userId") Long userId);

    // 도감용 마스코트별 집계 (포획 횟수, 첫/마지막 포획 시각)
    @Query("""
            select c.mascot.mascotId as mascotId,
                   count(c) as catchCount,
                   min(c.caughtAt) as firstCaughtAt,
                   max(c.caughtAt) as lastCaughtAt
            from UserMascotCollection c
            where c.user.id = :userId
            group by c.mascot.mascotId
            """)
    List<MascotCatchStat> findCatchStats(@Param("userId") Long userId);

    // 관리자 마스코트 삭제 시 포획 기록이 있는지 확인 (있으면 삭제 대신 스폰 비활성화 유도)
    boolean existsByMascot_MascotId(Long mascotId);
}
