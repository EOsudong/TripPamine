package com.example.trippaminebe.domain.ai.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 감정 단정 표현만 완화하고 여행 상담·정보 전달 내용은 보존하는지 검증한다. */
class AiManagerEmpathicReplyPolicyTest {

    @Test
    void 감정명으로_단정한_첫문장을_삭제하고_실용적_안내는_남긴다() {
        String answer = "초조하시군요. 여행 출발일을 알려주시면 준비 순서를 살펴볼게요.";

        assertThat(AiManagerEmpathicReplyPolicy.softenLeadingEmotionJudgment(answer))
                .isEqualTo("여행 출발일을 알려주시면 준비 순서를 살펴볼게요.");
    }

    @Test
    void 강조표현이_있는_감정단정도_완화한다() {
        assertThat(AiManagerEmpathicReplyPolicy.softenLeadingEmotionJudgment(
                "아, 정말 불안하시네요! 여행 계획을 함께 정리해봐요."))
                .isEqualTo("여행 계획을 함께 정리해봐요.");
    }

    @Test
    void 감정단정만_생성된_경우_중립적인_질문으로_돌려준다() {
        assertThat(AiManagerEmpathicReplyPolicy.softenLeadingEmotionJudgment("초조하시군요."))
                .isEqualTo("말씀해주신 상황을 함께 살펴볼게요. 어떤 부분부터 이야기해 볼까요?");
    }

    @Test
    void 사용자의_표현을_공감하는_조심스러운_문장은_보존한다() {
        String answer = "날씨 때문에 걱정되실 수도 있겠어요. 출발일을 알려주세요.";

        assertThat(AiManagerEmpathicReplyPolicy.softenLeadingEmotionJudgment(answer))
                .isEqualTo(answer);
    }

    @Test
    void 본문_중간의_인용문과_일반_여행안내는_수정하지_않는다() {
        String answer = "제주 일정은 일정을 먼저 확인하면 좋아요. '초조하시군요'라는 표현은 사용하지 않을게요.";

        assertThat(AiManagerEmpathicReplyPolicy.softenLeadingEmotionJudgment(answer))
                .isEqualTo(answer);
    }

    @Test
    void 비어있는_답변과_null은_그대로_보존한다() {
        assertThat(AiManagerEmpathicReplyPolicy.softenLeadingEmotionJudgment(null)).isNull();
        assertThat(AiManagerEmpathicReplyPolicy.softenLeadingEmotionJudgment(" ")).isEqualTo(" ");
    }
}
