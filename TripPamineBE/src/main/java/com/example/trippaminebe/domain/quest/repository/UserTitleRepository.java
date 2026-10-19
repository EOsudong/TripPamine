package com.example.trippaminebe.domain.quest.repository;

import com.example.trippaminebe.domain.quest.entity.UserTitle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface UserTitleRepository extends JpaRepository<UserTitle, Long> {

    List<UserTitle> findByUser_IdOrderByEarnedAtDesc(Long userId);

    // 이미 보유 중인 칭호 ID 집합 - 신규 획득 여부 판정에 사용
    @Query("select ut.title.titleId from UserTitle ut where ut.user.id = :userId")
    Set<Long> findTitleIdsByUserId(Long userId);

    // [칭호 변경] 이 칭호를 실제로 획득했는지 (장착 전 소유 확인)
    boolean existsByUser_IdAndTitle_TitleId(Long userId, Long titleId);
}