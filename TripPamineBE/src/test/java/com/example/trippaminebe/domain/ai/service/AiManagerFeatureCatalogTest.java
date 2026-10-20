package com.example.trippaminebe.domain.ai.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기능 지식 검색 검증.
 * 실제 Oracle DB나 외부 Qwen3 서버를 호출하지 않는 단위 테스트다.
 */
class AiManagerFeatureCatalogTest {

    @Test
    void 날씨와SOS질문에해당기능을선택한다() {
        List<String> ids = AiManagerFeatureCatalog.relevantFeatureIds(
                "비가 오면 여행 SOS로 일정 바꿀 수 있어?", List.of());

        assertThat(ids).contains("WEATHER", "TRAVEL_SOS").hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void 여행대화이후예산후속질문의맥락을유지한다() {
        List<String> ids = AiManagerFeatureCatalog.relevantFeatureIds(
                "예산은 괜찮을까?", List.of("이번 여행은 휴식 중심으로 가고 싶어"));

        assertThat(ids).contains("ASSET_DASHBOARD", "TRAVEL_PLAN");
    }

    @Test
    void 짧은후속질문도기존기능을선택한다() {
        List<String> ids = AiManagerFeatureCatalog.relevantFeatureIds(
                "그건 어떻게 해?", List.of("마스코트 도감은 어디서 봐?"));

        assertThat(ids).contains("MASCOT");
    }

    @Test
    void 여행SOS는긴급신고라고안내하지않는다() {
        String context = AiManagerFeatureCatalog.buildPromptContext(
                "여행 SOS는 무엇이야?", List.of());

        assertThat(context)
            .contains("응급 신고 기능이 아니다")
            .doesNotContain("/travel");
    }

    @Test
    void 자산상태를조회했다고허위주장하지않는다() {
        String context = AiManagerFeatureCatalog.buildPromptContext(
                "내 자산은 얼마야?", List.of());

        assertThat(context)
            .contains("아직 조회하지 않았다")
            .doesNotContain("/assets");
    }

    @Test
    void 전체기능질문에는카탈로그요약을제공한다() {
        String context = AiManagerFeatureCatalog.buildPromptContext(
                "트립파민에 무슨 기능이 있어?", List.of());

        assertThat(context)
            .contains("퀘스트", "마스코트 도감", "AI 여행 추천")
            .doesNotContain("/quests", "/mascots/dex", "/ai-recommend");
    }


    @Test
    void 마스코트도감_질문에_정확한메뉴안내를반환한다() {

        String answer =
            AiManagerFeatureCatalog.getVerifiedMascotDexGuide(
                "마스코트 도감은 어디서 볼 수 있어?"
            );

        assertThat(answer)
            .contains("마스코트 탐험")
            .contains("내 도감")
            .contains("도감 보기")
            .doesNotContain("/mascots/dex");
    }

    @Test
    void Qwen3답변에서_내부경로를제거한다() {

        String original =
            "마스코트 도감은 `/mascots/dex` 경로에서 확인할 수 있습니다.";

        String answer =
            AiManagerFeatureCatalog.toUserFacingAnswer(original);

        assertThat(answer)
            .contains("마스코트 도감")
            .doesNotContain("/mascots/dex");
    }

    @Test
    void Qwen3프롬프트에_화면경로를넣지않는다() {

        String context =
            AiManagerFeatureCatalog.buildPromptContext(
                "마스코트 도감은 어디서 볼 수 있어?",
                List.of()
            );

        assertThat(context)
            .contains("마스코트")
            .doesNotContain("/mascots/dex");
    }
}
