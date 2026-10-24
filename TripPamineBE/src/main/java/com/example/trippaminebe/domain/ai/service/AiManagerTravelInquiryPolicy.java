package com.example.trippaminebe.domain.ai.service;

import java.util.List;
import java.util.Locale;

/**
 * AI 매니저의 사용자별 여행 조회와 정정 발화를 판별한다.
 *
 * 실제 여행 정보는 이 클래스에서 만들지 않고 검증된 DB 조회 서비스에 위임한다.
 * 짧은 정정 발화는 최근 사용자의 여행 조회 질문에만 연결한다.
 */
final class AiManagerTravelInquiryPolicy {

    private AiManagerTravelInquiryPolicy() {
    }

    /**
     * 표현이 달라도 현재 로그인 사용자의 여행 목록을 묻는 질문인지 판별한다.
     */
    static boolean isPersonalTravelLookup(String message) {
        String question = normalize(message);

        // 표현이 달라도 등록된 여행 목록을 조회할 수 있도록 동의어를 추가한다.
        boolean personalTravel = contains(
            question,
            "내여행", "나의여행", "저장된여행", "저장한여행",
            "등록된여행", "등록한여행", "계획된여행",
            "여행계획목록", "현재여행계획",
            "내일정", "나의일정"
        );

        boolean lookup = contains(
                question,
                "알려", "보여", "조회", "확인", "목록", "리스트",
                "몇개", "몇건", "총몇", "언제", "일정", "예산",
                "계획", "어디", "어떤", "뭐", "말해"
        );

        return personalTravel && lookup;
    }

    /**
     * "아닌데?" 같은 정정은 최근 3개 사용자 발화에 여행 조회가 있을 때만 재조회한다.
     * 삭제·수정 실행 요청을 조회 요청으로 오해하지 않는다.
     */
    static boolean isCorrectionFollowup(
            String message,
            List<String> previousUserMessages
    ) {
        String question = normalize(message);

        if (question.isBlank() || question.length() > 120
                || previousUserMessages == null || previousUserMessages.isEmpty()
                || contains(question, "삭제해줘", "지워줘", "수정해줘", "변경해줘", "등록해줘")
                || !isCorrectionSignal(question)) {
            return false;
        }

        // 최근 3개의 사용자 질문에서 마지막으로 조회한 도메인을 복원한다.
        for (int index = previousUserMessages.size() - 1, checked = 0;
             index >= 0 && checked < 3; index--, checked++) {
            String previous = previousUserMessages.get(index);

            if (isPersonalTravelLookup(previous)) {
                return true;
            }

            // 중간 발화가 정정이 아니면 오래된 여행 질문까지 거슬러 올라가지 않는다.
            // 예: 여행 조회 → 일상 대화 → "아닌데?" 는 여행 재조회가 아니다.
            if (!isCorrectionSignal(normalize(previous))) {
                return false;
            }
        }

        return false;
    }

    private static boolean isCorrectionSignal(String question) {
        return contains(
                question,
                "아닌데", "아니야", "아니라고", "틀렸", "잘못",
                "다시조회", "재조회", "다시확인", "확인해봐",
                "삭제했", "삭제됐", "삭제된", "삭제한여행",
                "없는데", "계속나와", "다시봐"
        );
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
