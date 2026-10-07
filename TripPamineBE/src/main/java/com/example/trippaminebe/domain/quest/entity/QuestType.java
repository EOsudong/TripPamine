package com.example.trippaminebe.domain.quest.entity;

// 퀘스트 유형 분류
public enum QuestType {
    LOCATION,   // 탐험 퀘스트: 목표 위치 반경 진입
    DISCOVERY,  // 발견 퀘스트: 지정된 여러 장소 방문
    PHOTO,      // 사진 퀘스트: GPS + 현장 사진 업로드
    ACTION,     // 행동 퀘스트: GPS + QR/사진 등 복합 인증
    CHAIN       // 연속 퀘스트: 여러 퀘스트를 순서대로 클리어하는 챕터형 시리즈
}
