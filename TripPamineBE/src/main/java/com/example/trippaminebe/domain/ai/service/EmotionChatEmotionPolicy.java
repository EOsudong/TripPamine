package com.example.trippaminebe.domain.ai.service;

import java.util.List;
import java.util.Locale;

/**
 * 챗봇용 감정 관측 대상 선별 정책.
 *
 * <p>감정 분류 모델은 입력 문장을 반드시 감정 범주 중 하나로 분류한다.
 * 따라서 퀘스트·도감·자산 조회와 같은 중립 질문을 그대로 분석하면
 * 실제로 표현하지 않은 감정이 표시될 수 있다.</p>
 *
 * <p>이 정책은 감정 모델의 학습/추론 정확도를 변경하지 않는다.
 * 명확한 감정 단서가 없는 발화에 대해 결과 표시를 보류하는 보수적 게이트다.</p>
 */
public final class EmotionChatEmotionPolicy {

    private static final List<String> EMOTION_SIGNALS = List.of(
            "신나", "설레", "행복", "기뻐", "슬퍼", "우울", "불안", "걱정",
            "피곤", "지쳤", "힘들", "무기력", "답답", "외로", "화가",
            "짜증", "스트레스", "두렵", "무서", "안심", "편안", "초조",
            "안달", "서운", "속상", "기분이", "마음이", "감정이"
    );

    private EmotionChatEmotionPolicy() {
    }

    /**
     * 일반 정보 조회 요청에는 감정 예측을 수행하지 않는다.
     * 명시적인 감정 표현이 있을 때만 기존 모델 호출 주기를 허용한다.
     * 모델 정답을 판정하는 함수가 아니다.
     */
    public static boolean hasExplicitEmotionSignal(String message) {
        if (message == null || message.isBlank()) return false;
        String normalized = message.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return EMOTION_SIGNALS.stream().anyMatch(normalized::contains);
    }
}
