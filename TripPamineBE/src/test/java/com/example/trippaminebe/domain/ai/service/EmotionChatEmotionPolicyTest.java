package com.example.trippaminebe.domain.ai.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 학습된 감정 분류 모델의 가중치를 건드리지 않고 분석 대상만 선별한다. */
class EmotionChatEmotionPolicyTest {

    @Test
    void 포인트와도감의일반질문은분석하지않는다() {
        assertThat(EmotionChatEmotionPolicy.hasExplicitEmotionSignal(
                "내 포인트와 퀘스트 진행 상황을 알려줘.")).isFalse();
        assertThat(EmotionChatEmotionPolicy.hasExplicitEmotionSignal(
                "마스코트 도감은 어디서 볼 수 있어?")).isFalse();
    }

    @Test
    void 명시적인감정표현은감정분석대상이다() {
        assertThat(EmotionChatEmotionPolicy.hasExplicitEmotionSignal(
                "이번 여행을 떠날거라 너무 신나!")).isTrue();
        assertThat(EmotionChatEmotionPolicy.hasExplicitEmotionSignal(
                "지난 여행은 너무 피곤했어.")).isTrue();
    }

    @Test
    void 객관적인여행기능질문은중립으로취급한다() {
        assertThat(EmotionChatEmotionPolicy.hasExplicitEmotionSignal(
                "여행 SOS 기능이 뭐야?")).isFalse();
        assertThat(EmotionChatEmotionPolicy.hasExplicitEmotionSignal(
                "그럼 예산은 어떻게 확인해?")).isFalse();
    }
}
