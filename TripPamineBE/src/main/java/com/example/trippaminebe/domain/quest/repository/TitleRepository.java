package com.example.trippaminebe.domain.quest.repository;

import com.example.trippaminebe.domain.quest.entity.Title;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TitleRepository extends JpaRepository<Title, Long> {

    // 관리자 목록 조회용 - ID 순으로 고정된 정렬을 보장
    List<Title> findAllByOrderByTitleIdAsc();
}
