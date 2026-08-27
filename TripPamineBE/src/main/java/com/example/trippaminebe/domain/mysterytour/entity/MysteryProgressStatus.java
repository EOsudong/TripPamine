package com.example.trippaminebe.domain.mysterytour.entity;

// 미스터리 투어와 퀘스트가 함께 사용하는 단일 진행 상태값.
// 각 엔티티에서 허용되는 상태 전이는 MysteryTour/MysteryQuest의 도메인 메서드가 제한한다.
public enum MysteryProgressStatus {
    READY,
    LOCKED,
    PROGRESS,
    SUCCESS,
    FAILED,
    SKIPPED,
    CANCELLED,
    ABANDONED
}
