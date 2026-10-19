package com.example.trippaminebe.domain.quest.service;

import com.example.trippaminebe.domain.quest.exception.TitleNotFoundException;

import com.example.trippaminebe.domain.quest.dto.response.RpgStatsResponse;
import com.example.trippaminebe.domain.quest.dto.response.TitleResponse;
import com.example.trippaminebe.domain.quest.entity.Title;
import com.example.trippaminebe.domain.quest.entity.UserRpgStats;
import com.example.trippaminebe.domain.quest.entity.UserTitle;
import com.example.trippaminebe.domain.quest.repository.TitleRepository;
import com.example.trippaminebe.domain.quest.repository.UserRpgStatsRepository;
import com.example.trippaminebe.domain.quest.repository.UserTitleRepository;
import com.example.trippaminebe.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 퀘스트 RPG 레벨/경험치/칭호를 담당하는 Service.
 *
 * QuestService는 "퀘스트가 시작/완료되었다"는 사실만 이 Service에 위임하고,
 * 레벨업 공식(LevelCurve)이나 칭호 자동 획득 로직의 세부사항은 알 필요가 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RpgStatsService {

    private final UserRpgStatsRepository userRpgStatsRepository;
    private final TitleRepository titleRepository;
    private final UserTitleRepository userTitleRepository;

    /* =====================================================
     * 조회
     * ===================================================== */

    @Transactional(readOnly = true)
    public RpgStatsResponse findMyStats(Long userId) {
        // 아직 퀘스트를 한 번도 시작하지 않은 사용자는 USER_RPG_STATS row가 없을 수 있다.
        // 이 경우 row를 미리 만들지 않고(읽기 전용 트랜잭션이기도 하고), "레벨 1 초기 상태"를
        // 그대로 응답한다. 실제 row는 onQuestStarted()에서 처음 생성된다.
        return userRpgStatsRepository.findByUser_Id(userId)
            .map(RpgStatsResponse::from)
            .orElseGet(RpgStatsResponse::defaultStats);
    }

    @Transactional(readOnly = true)
    public Integer findMyLevel(Long userId) {
        return userRpgStatsRepository.findByUser_Id(userId)
            .map(UserRpgStats::getLevel)
            .orElse(1);
    }

    @Transactional(readOnly = true)
    public List<TitleResponse> findMyTitles(Long userId) {
        List<Title> allTitles = titleRepository.findAll();
        List<UserTitle> earnedTitles = userTitleRepository.findByUser_IdOrderByEarnedAtDesc(userId);

        Map<Long, UserTitle> earnedByTitleId = earnedTitles.stream()
            .collect(Collectors.toMap(ut -> ut.getTitle().getTitleId(), ut -> ut));

        Long currentTitleId = userRpgStatsRepository.findByUser_Id(userId)
            .map(stats -> stats.getCurrentTitle() != null ? stats.getCurrentTitle().getTitleId() : null)
            .orElse(null);

        return allTitles.stream()
            .map(title -> {
                UserTitle earned = earnedByTitleId.get(title.getTitleId());
                boolean isEarned = earned != null;
                boolean isEquipped = isEarned && title.getTitleId().equals(currentTitleId);
                return TitleResponse.of(title, isEarned, isEarned ? earned.getEarnedAt() : null, isEquipped);
            })
            .toList();
    }

    /* =====================================================
     * Quest 시작/완료 반영 - QuestService에서 호출
     * ===================================================== */

    public void onQuestStarted(User user) {
        UserRpgStats stats = getOrInitStats(user);
        stats.increaseTotalQuests();
    }

    /**
     * @return 레벨업 결과(화면에서 "레벨업!" 연출에 사용할 수 있도록 반환)
     */
    public LevelCurve.Result onQuestCompleted(User user, Long earnedExp) {
        UserRpgStats stats = getOrInitStats(user);
        stats.increaseCompletedQuests();

        int gainedExp = earnedExp != null ? earnedExp.intValue() : 0;
        LevelCurve.Result result = LevelCurve.apply(stats.getLevel(), stats.getExp(), gainedExp);
        stats.applyLevelUp(result.newLevel(), result.remainingExp(), gainedExp);

        evaluateAndGrantTitles(user, stats);

        return result;
    }

    /* =====================================================
     * 칭호 자동 평가/지급
     * ===================================================== */

    private void evaluateAndGrantTitles(User user, UserRpgStats stats) {
        Set<Long> alreadyOwned = userTitleRepository.findTitleIdsByUserId(user.getId());

        List<Title> newlyUnlocked = titleRepository.findAll().stream()
            .filter(title -> !alreadyOwned.contains(title.getTitleId()))
            .filter(title -> title.isSatisfiedBy(stats.getLevel(), stats.getCompletedQuests()))
            .toList();

        if (newlyUnlocked.isEmpty()) {
            return;
        }

        Title lastUnlocked = null;
        for (Title title : newlyUnlocked) {
            userTitleRepository.save(
                UserTitle.builder()
                    .user(user)
                    .title(title)
                    .build()
            );
            lastUnlocked = title;
        }

        // 새로 여러 개를 동시에 얻었다면 가장 마지막(= 가장 상위 조건) 칭호를 자동 장착한다.
        stats.equipTitle(lastUnlocked);
    }

    /* =====================================================
     * [칭호 변경] 사용자가 보유한 칭호 중 하나를 직접 장착/해제
     * ===================================================== */

    public RpgStatsResponse equipTitle(Long userId, Long titleId) {
        Title title = titleRepository.findById(titleId)
            .orElseThrow(() -> new TitleNotFoundException("존재하지 않는 칭호입니다. id=" + titleId));

        // 획득하지 않은 칭호는 장착할 수 없다 (요청 바디/URL 조작 방지)
        if (!userTitleRepository.existsByUser_IdAndTitle_TitleId(userId, titleId)) {
            throw new IllegalArgumentException("아직 획득하지 않은 칭호예요.");
        }

        // 칭호를 하나라도 가졌다면 퀘스트를 완료한 적이 있으므로 USER_RPG_STATS 행이 반드시 있다.
        UserRpgStats stats = userRpgStatsRepository.findByUser_Id(userId)
            .orElseThrow(() -> new IllegalArgumentException("RPG 정보가 없습니다. 퀘스트를 먼저 완료해주세요."));

        stats.equipTitle(title);
        return RpgStatsResponse.from(stats);
    }

    public RpgStatsResponse unequipTitle(Long userId) {
        return userRpgStatsRepository.findByUser_Id(userId)
            .map(stats -> {
                stats.equipTitle(null);
                return RpgStatsResponse.from(stats);
            })
            .orElseGet(RpgStatsResponse::defaultStats);
    }

    /* =====================================================
     * Internal
     * ===================================================== */

    private UserRpgStats getOrInitStats(User user) {
        return userRpgStatsRepository.findByUser_Id(user.getId())
            .orElseGet(() -> userRpgStatsRepository.save(UserRpgStats.createDefault(user)));
    }
}