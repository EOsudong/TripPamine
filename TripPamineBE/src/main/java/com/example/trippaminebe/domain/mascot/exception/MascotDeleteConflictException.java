package com.example.trippaminebe.domain.mascot.exception;

// 관리자 삭제 요청인데 연결된 데이터(포획 기록, 스폰 지점, 소속 마스코트)가 남아 있어 삭제할 수 없을 때.
// quest.exception.QuestDeleteConflictException과 같은 개념(409).
public class MascotDeleteConflictException extends RuntimeException {
    public MascotDeleteConflictException(String message) {
        super(message);
    }
}
