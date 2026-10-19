package com.example.trippaminebe.domain.ai.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * AI Manager 기능 지식 카탈로그 (STEP 1).
 *
 * <p>실제 구현이 확인된 기능 설명만 Qwen3에 전달한다.
 * 개인 여행·금융·포인트 기록은 여기서 조회하지 않으며,
 * 본인 데이터 조회는 후속 도메인 도구 연동에서 수행한다.</p>
 *
 * <p>새 기능을 넣을 때에는 Controller, Service, FE Router를 확인한 뒤
 * 기능명·경로·제한 사항을 함께 등록할 것.</p>
 */
public final class AiManagerFeatureCatalog {

    private static final int MAX_FEATURES = 3;

    private static final List<Feature> FEATURES = List.of(
            feature("TRAVEL_PLAN", "여행 플래너", "/travel", "여행계획 생성·조회·수정, 일정·예산·동행 유형 관리", "저장된 본인 여행 기록은 별도 조회해야 알 수 있다.", "여행", "여행계획", "일정", "목적지", "출발"),
            feature("TRAVEL_SOS", "여행 SOS 재계획", "/travel", "재계획 미리보기·확정 적용·변경 이력 확인", "응급 신고 기능이 아니다. 사용자 승인 없이 일정을 변경할 수 없으며 이동 시간·영업시간은 미검증일 수 있다.", "sos", "재계획", "일정변경", "일정 변경", "일정 바꾸", "계획 바꾸"),
            feature("WEATHER", "날씨 조회", "/travel", "좌표 기반 현재 날씨·초단기 예보 및 여행계획별 날씨", "위치·일정과 실제 API 응답 없이 현재 날씨를 말하지 않는다.", "날씨", "예보", "강수", "비가", "비오", "비 와", "눈이", "태풍", "기온"),
            feature("TRAVEL_RECOMMENDATION", "AI 여행 추천", "/ai-recommend", "여행계획 기반 AI 일정 추천·재생성·사용자 조정 일정 저장", "추천 생성이나 일정 저장이 완료되었다고 임의로 주장하지 않는다.", "여행 추천", "코스 추천", "추천코스", "ai추천", "코스 짜"),
            feature("ASSET_DASHBOARD", "자산관리", "/assets", "자산 현황·수입·지출·자산 추이와 미래 자산 시뮬레이션", "금융 활용 동의가 필요하고 실제 조회 없는 잔액·금액은 답할 수 없다. 추정 수익은 보장되지 않는다.", "자산", "잔액", "저축", "재정", "재무", "지출", "수입", "예산", "돈 관리"),
            feature("ASSET_GOAL", "자산·여행자금 목표", "/assets", "본인 자산 목표·목표 금액·진행 현황 관리", "실제 저축 및 목표 달성 가능성은 사용자 데이터를 확인해야 한다.", "목표 금액", "목표금액", "저축 목표", "저축목표", "여행자금", "모으"),
            feature("ACCOUNT_BOOK", "가계부·계좌", "/mypage", "마이페이지 가계부·계좌 탭에서 거래와 계좌 정보 관리", "거래 내역·연동 상태·잔액은 조회하지 않았다.", "가계부", "계좌", "거래 내역", "거래내역", "소비 내역"),
            feature("FINANCIAL_PRODUCTS", "금융상품", "/finance", "금융상품 정보 조회·비교 및 관심 상품 저장", "금리·가입 조건·수익은 최신 외부 정보 조회 없이 확정할 수 없다.", "금융상품", "금융 상품", "예금", "적금", "금리"),
            feature("QUEST", "여행 퀘스트", "/quests", "퀘스트 목록·진행 이력·GPS 인증·연쇄 단계 조회", "클리어·보상 지급은 실제 서버 결과로만 확인할 수 있다.", "퀘스트", "미션", "임무", "도전과제"),
            feature("RPG", "RPG·포인트·칭호", "/quests", "RPG 레벨·경험치·완료 퀘스트·칭호 조회와 장착", "포인트와 EXP는 다른 값이다. 보유 수치와 보상은 조회해야 한다.", "포인트", "레벨", "경험치", "exp", "칭호", "rpg", "보상"),
            feature("MASCOT", "마스코트 탐험", "/mascots", "주변 마스코트·GPS 조우·포획 시도", "포획은 GPS·서버 검증·확률 판정이 필요하다.", "마스코트", "포획", "캐릭터 잡"),
            feature("MASCOT_DEX", "마스코트 도감", "/mascots/dex", "지역별 마스코트와 본인 수집 현황을 확인하는 도감", "수집률과 포획 횟수는 실제 조회하지 않은 상태다.", "마스코트 도감", "도감", "수집"),
            feature("MYSTERY_TOUR", "미스터리 투어", "/travel", "GPS·사진 미션 진행, 퀘스트 스킵·포기", "진행 화면에는 실제 투어 ID가 필요하며 스킵 보상을 임의로 지급할 수 없다.", "미스터리", "블라인드", "랜덤여행", "랜덤 여행"),
            feature("TOUR_INFORMATION", "관광·축제 정보", "/tour/destinations", "관광 여행지·축제·관광 산업 정보와 북마크", "일정·입장료·운영 여부는 실제 조회로 확인해야 한다.", "축제", "관광지", "관광 정보", "볼거리", "가볼만한"),
            feature("AI_PHOTO", "AI 사진 편집", "/ai-photo-editor", "AI 사진 편집 화면", "대화를 통해 실제 이미지 편집이나 저장을 수행했다고 말하지 않는다.", "사진 편집", "사진편집", "ai 사진", "이미지 편집"),
            feature("COMMUNITY", "커뮤니티", "/boards", "게시글 목록·상세·작성·수정 화면", "챗봇은 게시글을 자동으로 작성·삭제하지 않는다.", "커뮤니티", "게시판", "게시글", "후기 작성"),
            feature("EMOTION", "마음 메이트 감정 대화", "/ai/emotion", "감정 분석 동의 시 감정 모델 예측 결과 안내", "감정 결과는 추정이며 의학적 진단이 아니다.", "감정", "기분", "마음", "우울", "스트레스")
    );

    private AiManagerFeatureCatalog() {
    }

    /**
     * 질문과 관련된 기능 최대 3개를 선택한다.
     * 짧은 후속 질문은 직전 사용자 발화에서 도메인 맥락을 보완한다.
     */
    public static List<String> relevantFeatureIds(
            String latestMessage, List<String> previousUserMessages) {
        if (latestMessage == null || latestMessage.isBlank()) return List.of();

        List<FeatureScore> current = rank(latestMessage);
        List<Feature> selected = new ArrayList<>();
        for (FeatureScore score : current) {
            if (selected.size() >= MAX_FEATURES) break;
            selected.add(score.feature());
        }

        if (isFollowUp(latestMessage) && selected.size() < MAX_FEATURES
                && previousUserMessages != null) {
            int checked = 0;
            for (int i = previousUserMessages.size() - 1; i >= 0 && checked < 4; i--, checked++) {
                for (FeatureScore prior : rank(previousUserMessages.get(i))) {
                    if (!selected.contains(prior.feature())) selected.add(prior.feature());
                    if (selected.size() >= MAX_FEATURES) break;
                }
                if (selected.size() >= MAX_FEATURES || (current.isEmpty() && !selected.isEmpty())) break;
            }
        }

        return selected.stream().map(Feature::id).toList();
    }

    /**
     * 답변용 '기능 설명'만 만든다. 사용자의 현재 DB 값처럼 보이는 가상 데이터를
     * 생성하지 않으며, 조회 여부를 Qwen3가 오해하지 않도록 경계를 명시한다.
     */
    public static String buildPromptContext(
            String latestMessage, List<String> previousUserMessages) {
        List<String> ids = relevantFeatureIds(latestMessage, previousUserMessages);
        StringBuilder prompt = new StringBuilder("""
                [TripPamine 실제 구현 기능 안내]
                다음 내용은 앱의 기능 설명일 뿐 사용자별 현재 상태가 아니다.
                사용자의 여행 일정·자산·잔액·퀘스트·포인트·마스코트·날씨는 아직 조회하지 않았다.
                개인 금액·횟수·날씨를 추측하지 말고 데이터 확인이 필요함을 알려라.
                아래에 없는 화면·기능·API 실행 결과는 만들어내지 않는다.
                내부 라우트 주소, API 주소, 개발 용어를 답변에 출력하지 않는다.
                사용자가 관련 질문을 하면 해당 기능의 사용자용 이름과 가능한 다음 행동을 안내한다.
                화면 이동 버튼은 별도 UI가 제공한다.
                """);

        if (ids.isEmpty()) {
            prompt.append("""
                    [서비스 전체 요약]
                    여행 플래너, AI 여행 추천, 여행 SOS(여행 일정 재계획),
                    날씨 조회, 자산관리, 가계부, 금융상품,
                    퀘스트·RPG·포인트·칭호, 마스코트 탐험과 도감,
                    미스터리 투어, 관광지, 축제,
                    AI 사진 편집, 게시판, 감정 대화.
                    """);
            return prompt.toString();
        }

        for (Feature feature : FEATURES) {
            if (!ids.contains(feature.id())) continue;
            prompt.append("\n- 기능: ").append(feature.name())
                    .append("\n  확인된 기능: ").append(feature.description())
                    .append("\n  한계: ").append(feature.limitations());
        }
        return prompt.toString();
    }


    /**
     * 모델이 생성하지 않아도 정확하게 답할 수 있는 핵심 정책.
     * SOS는 '여행 일정 재계획'이라는 뜻이며 긴급 신고 시스템이 아니다.
     * 이 중요 사항은 작은 언어 모델의 자유 생성에 맡기지 않는다.
     */
    public static String verifiedSosAnswer(String latestMessage) {
        if (latestMessage == null) return null;
        String normalized = normalize(latestMessage);
        if (!normalized.contains("sos") && !normalized.contains("여행에스오에스")) return null;

        if (List.of("다쳤", "사고", "구급", "긴급", "위급", "위험", "살려", "구조", "응급", "119", "112")
                .stream().anyMatch(normalized::contains)) {
            return "위급한 상황이라면 트립파민의 여행 SOS가 아닌 실제 긴급 신고를 이용해 주세요. "
                    + "구급·구조는 119, 경찰 신고는 112입니다. "
                    + "트립파민 여행 SOS는 여행 일정을 다시 짜는 기능입니다.";
        }
        return "트립파민의 여행 SOS는 비가 오거나 일정이 지연됐을 때 여행 일정을 재계획하는 기능입니다. "
                + "변경안을 먼저 확인한 뒤 사용자가 직접 확정할 수 있으며, 긴급 신고 서비스는 아닙니다. "
                + "여행 플래너에서 사용할 수 있어요.";
    }

    /**
     * Qwen3가 예전 대화나 추측으로 URL을 다시 출력하더라도,
     * 개발용 라우트가 사용자 대화 말풍선에 노출되지 않도록 변환한다.
     * 실제 버튼 주소는 응답의 기능 ID를 통해 프론트엔드가 결정한다.
     */
    public static String toUserFacingAnswer(String answer) {
        if (answer == null) return "";
        String safe = answer;
        // 긴 경로를 먼저 변환한다(예: /mascots/dex가 /mascots보다 우선).
        String[][] routes = {
                {"/mascots/dex", "마스코트 도감"},
                {"/tour/destinations", "관광 여행지"},
                {"/tour/festivals", "축제·행사"},
                {"/tour/industry", "관광 산업"},
                {"/ai-photo-editor", "AI 사진 편집"},
                {"/ai-recommend", "AI 여행 추천"},
                {"/ai/emotion", "마음 메이트"},
                {"/mascots", "마스코트 탐험"},
                {"/quests", "여행 퀘스트"},
                {"/assets", "자산관리"},
                {"/finance", "금융상품"},
                {"/mypage", "마이페이지"},
                {"/travel", "여행 플래너"},
                {"/boards", "게시판"}
        };
        for (String[] item : routes) {
            safe = safe.replace("`" + item[0] + "`", item[1])
                    .replace(item[0], item[1]);
        }
        // 아직 등록되지 않은 내부 경로까지 일반 안내로 대체한다.
        return safe.replaceAll("(?<![\\w/:])/(?:[a-z][a-z0-9-]*)(?:/[a-z0-9-]+)*", "해당 서비스");
    }

    private static Feature feature(String id, String name, String route, String description,
                                   String limitations, String... keywords) {
        return new Feature(id, name, route, description, limitations, List.of(keywords));
    }

    private static List<FeatureScore> rank(String message) {
        if (message == null || message.isBlank()) return List.of();
        String text = normalize(message);
        List<FeatureScore> ranked = new ArrayList<>();
        for (Feature feature : FEATURES) {
            int score = 0;
            for (String keyword : feature.keywords()) {
                if (text.contains(normalize(keyword))) score++;
            }
            if (score > 0) ranked.add(new FeatureScore(feature, score));
        }
        ranked.sort(Comparator.comparingInt(FeatureScore::score).reversed());
        return ranked;
    }

    private static boolean isFollowUp(String message) {
        String text = normalize(message);
        if (text.length() > 38) return false;
        return List.of("그", "저거", "이거", "그럼", "이번", "예산", "비용",
                        "얼마", "방법", "어떻게", "계속", "추가", "가능", "어디",
                        "언제", "바꾸", "해줘", "그쪽")
                .stream().anyMatch(text::contains);
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private record Feature(String id, String name, String route, String description,
                           String limitations, List<String> keywords) {
    }

    private record FeatureScore(Feature feature, int score) {
    }
}
