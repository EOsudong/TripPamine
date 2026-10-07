package com.example.trippaminebe.domain.quest.service;

/**
 * RPG 레벨업 곡선.
 *
 * 레벨 N에서 N+1로 올라가기 위해 필요한 EXP = 100 * N (선형 증가).
 * 예) Lv.1 -> 2 : 100 EXP, Lv.2 -> 3 : 200 EXP, Lv.9 -> 10 : 900 EXP
 *
 * 밸런스를 바꾸고 싶으면 이 클래스의 공식만 수정하면 된다
 * (호출부는 모두 requiredExpForLevel()/apply()만 사용).
 */
public final class LevelCurve {

    private static final int EXP_PER_LEVEL_STEP = 100;

    private LevelCurve() {
    }

    // 현재 레벨에서 다음 레벨로 가기 위해 필요한 총 EXP
    public static int requiredExpForLevel(int currentLevel) {
        return EXP_PER_LEVEL_STEP * currentLevel;
    }

    /**
     * 경험치를 적용해 레벨업까지 한 번에 계산한다.
     * 한 번에 여러 레벨이 오르는 경우도 처리한다(큰 보상 EXP 등).
     */
    public static Result apply(int currentLevel, int currentExp, int gainedExp) {
        int level = currentLevel;
        int exp = currentExp + gainedExp;
        int levelsGained = 0;

        while (exp >= requiredExpForLevel(level)) {
            exp -= requiredExpForLevel(level);
            level++;
            levelsGained++;
        }

        return new Result(level, exp, levelsGained);
    }

    public record Result(int newLevel, int remainingExp, int levelsGained) {
        public boolean leveledUp() {
            return levelsGained > 0;
        }
    }
}
