package com.example.trippaminebe.domain.mascot.exception;

// GPS 정확도가 너무 낮거나(quest.exception의 IllegalArgumentException 케이스와 동일한 개념),
// 반경 밖이라 조우에 실패했을 때 사용
public class MascotLocationException extends RuntimeException {
    public MascotLocationException(String message) {
        super(message);
    }
}
