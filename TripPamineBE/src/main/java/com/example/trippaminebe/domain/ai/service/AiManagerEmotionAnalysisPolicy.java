package com.example.trippaminebe.domain.ai.service;

import java.util.List;
import java.util.Locale;

/**
 * 감정 모델에 보낼 발화를 선별하는 보수적인 게이트.
 *
 * 여행 목록 조회와 단순 오류 정정은 감정 표현의 충분한 근거가 아니므로 생략한다.
 * 명시적인 감정 표현이 있으면 정정 발화와 함께 있더라도 모델 분석을 허용한다.
 * 모델의 confidence 임계값은 별도의 검증 데이터 평가 이후에 결정한다.
 */
final class AiManagerEmotionAnalysisPolicy {

    private AiManagerEmotionAnalysisPolicy() {
    }

    static boolean shouldAnalyze(
            String message,
            List<String> previousUserMessages
    ) {
        String question = normalize(message);

        // 사용자가 '나는 불안한 게 아니라'라고 정정한 표현은 감정 증거에서 제외한다.
        // 다른 감정을 실제로 밝힌 경우(예: '불안한 게 아니라 속상해')는 분석할 수 있다.
        String emotionEvidence = question
                .replace("불안한게아니라", "")
                .replace("불안한건아니", "")
                .replace("불안한것은아니", "")
                .replace("불안하지않", "")
                .replace("불안하진않", "")
                .replace("걱정하는게아니라", "")
                .replace("걱정하는건아니", "")
                .replace("걱정되지않", "");

        if (contains(
                emotionEvidence,
                "속상", "짜증", "화나", "화가나", "불안", "걱정",
                "우울", "슬프", "외롭", "무섭", "두렵", "힘들",
                "답답", "괴롭", "서운", "억울", "불쾌",
                "행복", "기쁘", "신나", "즐거", "감동", "기대", "설레",
                "지쳤", "스트레스", "울고싶"
        )) {
            return true;
        }

        // 부정된 감정 외에 새로 밝힌 감정이 없다면, 감정명 대신 사용자의 정정을 존중한다.
        if (!question.equals(emotionEvidence)) {
            return false;
        }

        if (AiManagerTravelInquiryPolicy.isPersonalTravelLookup(message)
                || AiManagerTravelInquiryPolicy.isCorrectionFollowup(
                        message, previousUserMessages)) {
            return false;
        }

        // 여행 준비물/순서를 묻는 중립적인 정보 질문은 감정 분석을 강제하지 않는다.
        // 단, 앞부분에서 실제 감정을 명시한 질문은 이미 true로 처리했다.
        if (question.length() <= 200
                && contains(question, "여행", "숙소", "예약")
                && contains(question, "준비", "준비물", "체크리스트", "뭐부터", "무엇부터",
                        "어떻게", "알려", "추천")) {
            return false;
        }

        // 다른 도메인의 짧은 사실 정정도 감정으로 단정하지 않는다.
        if (question.length() <= 50 && contains(
                question,
                "아닌데", "아니야", "틀렸어", "잘못됐",
                "다시조회", "재조회", "확인해봐"
        )) {
            return false;
        }

        // 외부 데이터 조회 요청에는 감정 라벨을 붙이지 않는다.
        if (question.length() <= 120
                && contains(question, "조회해", "다시확인", "다시조회")
                && contains(question, "여행", "포인트", "퀘스트", "삭제", "등록", "목록")) {
            return false;
        }

        return true;
    }

    private static boolean contains(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        return text == null
                ? ""
                : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}