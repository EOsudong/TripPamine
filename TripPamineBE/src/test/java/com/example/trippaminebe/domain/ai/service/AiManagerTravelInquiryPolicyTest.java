package com.example.trippaminebe.domain.ai.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 여행 목록의 다양한 한국어 표현과 정정 후속 질문을 DB 조회로 연결하는 단위 테스트. */
class AiManagerTravelInquiryPolicyTest {

    @Test
    void 내가등록한여행지는현재여행목록조회로판별한다() {
        assertThat(AiManagerTravelInquiryPolicy.isPersonalTravelLookup(
                "내가 등록한 여행지 알려줘")).isTrue();
        assertThat(AiManagerTravelInquiryPolicy.isPersonalTravelLookup(
                "저장된 여행 목록 보여줘")).isTrue();
        assertThat(AiManagerTravelInquiryPolicy.isPersonalTravelLookup(
                "내 여행이 몇 개야?")).isTrue();
    }

    @Test
    void 일반관광추천과여행등록요청은사실조회가아니다() {
        assertThat(AiManagerTravelInquiryPolicy.isPersonalTravelLookup(
                "가을에 가볼만한 여행지를 추천해 줘")).isFalse();
        assertThat(AiManagerTravelInquiryPolicy.isPersonalTravelLookup(
                "제주도 여행을 새로 등록하고 싶어")).isFalse();
    }

    @Test
    void 잘못된여행목록을정정하면최신목록을조회한다() {
        List<String> history = List.of(
                "내가 등록한 여행지 알려줘",
                "아닌데?"
        );

        assertThat(AiManagerTravelInquiryPolicy.isCorrectionFollowup(
                "다시 조회해봐 아니야 삭제했어", history)).isTrue();
        assertThat(AiManagerTravelInquiryPolicy.isCorrectionFollowup(
                "아닌데?", List.of("내가 등록한 여행지 알려줘"))).isTrue();
    }

    @Test
    void 여행과무관한대화에서재조회를강제하지않는다() {
        assertThat(AiManagerTravelInquiryPolicy.isCorrectionFollowup(
                "아닌데?", List.of("마스코트 도감 어디서 봐?"))).isFalse();
    }

    @Test
    void 다른도메인을새로조회했다면과거여행주제로돌아가지않는다() {
        assertThat(AiManagerTravelInquiryPolicy.isCorrectionFollowup(
                "틀렸어", List.of("내 여행 목록 알려줘", "내 포인트 몇 점이야?")))
                .isFalse();
    }

    @Test
    void 중간에다른주제가등장하면오래된여행질문을복원하지않는다() {
        assertThat(AiManagerTravelInquiryPolicy.isCorrectionFollowup(
                "아닌데?", List.of("내 여행 목록 알려줘", "어제 영화 봤어")))
                .isFalse();
    }

    @Test
    void 삭제실행요청을재조회로오인하지않는다() {
        assertThat(AiManagerTravelInquiryPolicy.isCorrectionFollowup(
                "그 여행 삭제해줘", List.of("내 여행 목록 알려줘"))).isFalse();
    }
}
