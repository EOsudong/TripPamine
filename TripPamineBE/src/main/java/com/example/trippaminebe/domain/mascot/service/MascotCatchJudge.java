package com.example.trippaminebe.domain.mascot.service;

import com.example.trippaminebe.domain.mascot.entity.MascotRarity;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

// [포획 판정 서버 이전]
// 예전에는 클라이언트가 보낸 success=true/false를 그대로 믿었다. 그러면 /catch에 success=true만
// 보내도 무조건 잡힌다. 이제 클라이언트(웹/안드로이드 미니게임)는 "점수(0~100)"만 보내고,
// 실제로 잡혔는지는 서버가 희귀도 + 점수로 확률을 계산해서 결정한다.
//
// 확률 공식: rate = base + (1 - base) * (score / 100) * SCORE_WEIGHT
//   예) COMMON(0.90) 100점 → 0.95 / RARE(0.60) 100점 → 0.80 / EPIC(0.35) 100점 → 0.675
//       EPIC 0점 → 0.35
// 점수를 조작해서 100점을 보내도 확률이 1이 되지 않으므로 "무조건 포획"은 불가능하다.
@Component
public class MascotCatchJudge {

    // 미니게임 점수가 확률에 미치는 비중 (0이면 점수 무시, 1이면 100점 = 100% 포획)
    private static final double SCORE_WEIGHT = 0.5;

    public double catchRate(MascotRarity rarity, int score) {
        double base = rarity.getBaseCatchRate();
        int clamped = Math.max(0, Math.min(100, score));
        return base + (1 - base) * (clamped / 100.0) * SCORE_WEIGHT;
    }

    public boolean roll(double catchRate) {
        return ThreadLocalRandom.current().nextDouble() < catchRate;
    }
}
