package com.example.trippaminebe.domain.diary.repository;

import com.example.trippaminebe.domain.diary.entity.DiaryKeyword;
import com.example.trippaminebe.domain.diary.entity.KeywordType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiaryKeywordRepository extends JpaRepository<DiaryKeyword, Long> {

    // 특정 다이어리에 매핑된 키워드 전체 조회 (IDX_DIARY_KEYWORDS_ID 인덱스 활용)
    List<DiaryKeyword> findByTravelDiary_DiaryId(Long diaryId);

    // 검색/추천/통계용 - 유형별 키워드 랭킹 등에 활용 가능
    List<DiaryKeyword> findByKeywordTypeAndKeywordName(KeywordType keywordType, String keywordName);
}
