package com.example.trippaminebe.domain.mascot.exception;

// 스폰 지점에 연동 퀘스트(LINKED_QUEST_ID)가 걸려 있는데 사용자가 아직 그 퀘스트를 클리어하지 않았을 때
public class MascotLockedException extends RuntimeException {
    public MascotLockedException(String message) {
        super(message);
    }
}
