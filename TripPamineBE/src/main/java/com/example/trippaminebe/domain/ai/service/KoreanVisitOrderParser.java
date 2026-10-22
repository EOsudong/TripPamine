package com.example.trippaminebe.domain.ai.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 한국어 여행 일차/방문 순서의 표기를 하나로 해석한다.
 * "1일차", "일일차", "첫날"과 "1번째", "첫번째"를 동일하게 처리한다.
 * 장소명에는 영향을 주지 않도록 일차/순서 접미사가 있을 때만 숫자를 인식한다.
 */
final class KoreanVisitOrderParser {
    private static final Pattern DAY = Pattern.compile("([1-9]\\d?)일차");
    private static final Pattern ORDER = Pattern.compile("([1-9]\\d?)번째");
    private static final Pattern KOREAN_DAY = Pattern.compile("([일이삼사오육칠팔구십]+)일차");
    private static final Pattern DAY_PREFIX = Pattern.compile(
            "^(?:(?:[1-9]\\d?|[일이삼사오육칠팔구십]+)일차|첫날|둘째날|셋째날)");
    private static final Pattern ORDER_PREFIX = Pattern.compile(
            "^(?:[1-9]\\d?번째|첫번째|두번째|세번째|네번째|다섯번째|여섯번째|일곱번째|여덟번째|아홉번째|열번째)");
    private static final Map<String, Integer> NUMERALS = Map.of(
            "일", 1, "이", 2, "삼", 3, "사", 4, "오", 5,
            "육", 6, "칠", 7, "팔", 8, "구", 9, "십", 10);
    private static final String[] ORDINALS = {
            "첫번째", "두번째", "세번째", "네번째", "다섯번째",
            "여섯번째", "일곱번째", "여덟번째", "아홉번째", "열번째"
    };

    private KoreanVisitOrderParser() { }

    static Integer day(String normalizedQuestion) {
        Matcher arabic = DAY.matcher(normalizedQuestion);
        if (arabic.find()) return Integer.valueOf(arabic.group(1));
        Matcher korean = KOREAN_DAY.matcher(normalizedQuestion);
        while (korean.find()) {
            Integer value = sinoNumber(korean.group(1));
            if (value != null && value >= 1 && value <= 31) return value;
        }
        if (normalizedQuestion.contains("첫날") || normalizedQuestion.contains("첫째날")) return 1;
        if (normalizedQuestion.contains("둘째날")) return 2;
        if (normalizedQuestion.contains("셋째날")) return 3;
        return null;
    }

    static Integer sequence(String normalizedQuestion) {
        Matcher arabic = ORDER.matcher(normalizedQuestion);
        if (arabic.find()) return Integer.valueOf(arabic.group(1));
        for (int index = 0; index < ORDINALS.length; index++) {
            if (normalizedQuestion.contains(ORDINALS[index])) return index + 1;
        }
        return null;
    }

    static boolean bareDay(String q) {
        return day(q) != null && (q.matches("(?:[1-9]\\d?|[일이삼사오육칠팔구십]+)일차[?？]?")
                || q.matches("(?:첫날|첫째날|둘째날|셋째날)[?？]?"));
    }

    /** 장소명 추출 전 질문 맨 앞의 일차 및 방문 순서를 제거한다. */
    static String removeLeadingDayAndOrder(String prefix) {
        String value = DAY_PREFIX.matcher(prefix).replaceFirst("");
        value = value.replaceFirst("^(?:첫째날|둘째날|셋째날)", "");
        return ORDER_PREFIX.matcher(value).replaceFirst("");
    }

    private static Integer sinoNumber(String value) {
        Integer direct = NUMERALS.get(value);
        if (direct != null) return direct;
        int split = value.indexOf("십");
        if (split < 0 || split != value.lastIndexOf("십")) return null;
        String ten = value.substring(0, split);
        String one = value.substring(split + 1);
        Integer tens = ten.isEmpty() ? 1 : NUMERALS.get(ten);
        Integer units = one.isEmpty() ? 0 : NUMERALS.get(one);
        if (tens == null || units == null || tens < 1 || tens > 3 || units > 9) return null;
        int result = tens * 10 + units;
        return result <= 31 ? result : null;
    }
}
