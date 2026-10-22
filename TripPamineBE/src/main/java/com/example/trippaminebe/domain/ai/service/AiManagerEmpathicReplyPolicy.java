package com.example.trippaminebe.domain.ai.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 매니저가 사용자의 감정을 확정하는 말투로 대화를 시작하지 않도록 하는 최소한의 보호 장치.
 *
 * 중요한 설계 원칙:
 * 1. 모델의 감정분류 코드/신뢰도는 실제 사용자 감정의 확정 사실이 아니다.
 * 2. 사용자에게 도움이 되는 뒤쪽 답변은 지우지 않고 유지한다.
 * 3. 사용자 기록이나 DB/API에서 확인한 직접 답변에는 적용하지 않는다.
 * 4. 모든 표현을 정규식으로 검사할 수는 없으므로 프롬프트 규칙과 함께 사용한다.
 */
final class AiManagerEmpathicReplyPolicy {

    // 대답 첫머리에서 감정 이름을 단정하는 표현만 좁게 탐지한다.
    // "걱정되실 수도 있겠어요" 같은 조심스러운 문장이나 본문의 정보는 그대로 둔다.
    private static final Pattern LEADING_EMOTION_JUDGMENT = Pattern.compile(
            "^\\s*(?:아[,，]?\\s*)?(?:(?:정말|많이|조금)\\s+)?"
                    + "(?:초조하시군요|초조하시네요|불안하시군요|불안하시네요|"
                    + "우울하시군요|우울하시네요|당황하시군요|당황하시네요|"
                    + "외로우시군요|외로우시네요|슬프시군요|슬프시네요|"
                    + "짜증나시군요|짜증나시네요|걱정되시군요|걱정되시네요|"
                    + "긴장하시군요|긴장하시네요|행복하시군요|행복하시네요)"
                    + "\\s*[.!?…~]*\\s*"
    );

    private static final String NEUTRAL_FOLLOWUP =
            "말씀해주신 상황을 함께 살펴볼게요. 어떤 부분부터 이야기해 볼까요?";

    private AiManagerEmpathicReplyPolicy() {
    }

    static String softenLeadingEmotionJudgment(String answer) {
        if (answer == null || answer.isBlank()) {
            return answer;
        }

        Matcher matcher = LEADING_EMOTION_JUDGMENT.matcher(answer);
        if (!matcher.find()) {
            return answer;
        }

        // 확정적 감정 판정 문구만 제거하고, 실제 문제 해결에 관한 설명은 살린다.
        String remainingAnswer = answer.substring(matcher.end()).stripLeading();
        if (!remainingAnswer.isBlank()) {
            return remainingAnswer;
        }

        // 감정 판정 한 문장만 생성된 경우 사용자 상황을 질문하는 중립적인 답변으로 교체한다.
        return NEUTRAL_FOLLOWUP;
    }
}
