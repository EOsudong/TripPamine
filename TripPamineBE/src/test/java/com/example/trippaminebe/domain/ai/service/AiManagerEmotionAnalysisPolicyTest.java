package com.example.trippaminebe.domain.ai.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 정보 조회·오류 정정에는 근거 없는 감정 라벨을 붙이지 않도록 검사한다. */
class AiManagerEmotionAnalysisPolicyTest {

    @Test
    void 여행목록조회는감정분석을생략한다() {
        assertThat(AiManagerEmotionAnalysisPolicy.shouldAnalyze(
                "내가 등록한 여행지 알려줘", List.of())).isFalse();
    }

    @Test
    void 삭제한여행정정은감정분석을생략한다() {
        assertThat(AiManagerEmotionAnalysisPolicy.shouldAnalyze(
                "다시 조회해봐 아니야 삭제했어",
                List.of("내가 등록한 여행지 알려줘", "아닌데?"))).isFalse();
    }

    @Test
    void 짧은정정도감정을단정하지않는다() {
        assertThat(AiManagerEmotionAnalysisPolicy.shouldAnalyze(
                "아닌데?", List.of("오늘 날씨 알려줘"))).isFalse();
    }

    @Test
    void 감정표현을동반한정정은감정분석이가능하다() {
        assertThat(AiManagerEmotionAnalysisPolicy.shouldAnalyze(
                "계속 틀리니까 너무 짜증나",
                List.of("내가 등록한 여행지 알려줘"))).isTrue();
    }

    @Test
    void 실제감정표현은기존분석기능을유지한다() {
        assertThat(AiManagerEmotionAnalysisPolicy.shouldAnalyze(
                "요즘 여행 가기 전부터 불안해", List.of())).isTrue();
        assertThat(AiManagerEmotionAnalysisPolicy.shouldAnalyze(
                "오늘 정말 행복했어", List.of())).isTrue();
    }

    @Test
    void 무관한일반대화는현재정책을유지한다() {
        assertThat(AiManagerEmotionAnalysisPolicy.shouldAnalyze(
                "이번 주말에 뭐 할까?", List.of())).isTrue();
    }
}
