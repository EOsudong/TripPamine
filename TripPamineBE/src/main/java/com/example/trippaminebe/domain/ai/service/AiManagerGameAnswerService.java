
package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.mascot.dto.response.MascotDexResponse;
import com.example.trippaminebe.domain.mascot.service.MascotService;
import com.example.trippaminebe.domain.quest.dto.response.RpgStatsResponse;
import com.example.trippaminebe.domain.quest.dto.response.UserQuestLogResponse;
import com.example.trippaminebe.domain.quest.entity.QuestStatus;
import com.example.trippaminebe.domain.quest.service.QuestService;
import com.example.trippaminebe.domain.quest.service.RpgStatsService;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 인증된 사용자 본인의 게임 활동을 조회해
 * 검증된 수치로 직접 응답하는 AI 매니저 서비스.
 *
 * 조회 전용이며 포인트 지급, 퀘스트 완료,
 * 마스코트 포획 등의 변경 작업은 수행하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiManagerGameAnswerService {

	private final UserRepository userRepository;
	private final QuestService questService;
	private final RpgStatsService rpgStatsService;
	private final MascotService mascotService;

	/**
	 * 개인 게임 현황 질문이면 실제 조회 결과 반환.
	 * 관련 없는 일반 대화라면 null 반환.
	 */
	public String findAnswer(Long userId, String message) {

		String q = normalize(message);

		if (!isPersonalQuestion(q)) {
			return null;
		}

		boolean points = contains(q, "포인트", "point");
		boolean quests = contains(q, "퀘스트", "미션");
		boolean rpg = contains(q, "rpg", "레벨", "경험치", "exp", "칭호");
		boolean mascots = q.contains("마스코트")
				&& contains(q, "수집", "모은", "잡은", "도감", "포획");

		if (!points && !quests && !rpg && !mascots) {
			return null;
		}

		if (userId == null || userId <= 0) {
			throw new IllegalArgumentException("로그인 사용자 정보가 필요합니다.");
		}

		List<String> answers = new ArrayList<>();

		if (points) {
			Long balance = userRepository.findById(userId)
					.map(User::getTotalPoints)
					.orElseThrow(() ->
							new IllegalArgumentException("회원 정보를 확인할 수 없습니다.")
					);

			answers.add("현재 보유 포인트는 "
					+ balance + "P입니다.");
		}

		if (quests) {
			List<UserQuestLogResponse> logs =
					questService.findMyLogs(userId);

			long progressing = logs.stream()
					.filter(log -> log.getStatus() == QuestStatus.PROGRESS)
					.count();

			long completed = logs.stream()
					.filter(log -> log.getStatus() == QuestStatus.SUCCESS)
					.count();

			long failed = logs.stream()
					.filter(log -> log.getStatus() == QuestStatus.FAILED)
					.count();

			StringBuilder questAnswer = new StringBuilder()
					.append("퀘스트 수행 기록은 진행 중 ")
					.append(progressing)
					.append("건, 완료 ")
					.append(completed)
					.append("건, 실패 ")
					.append(failed)
					.append("건입니다.");

			List<UserQuestLogResponse> active = logs.stream()
					.filter(log -> log.getStatus() == QuestStatus.PROGRESS)
					.limit(3)
					.toList();

			if (!active.isEmpty()) {
				questAnswer.append("\n진행 중인 퀘스트:");

				for (UserQuestLogResponse log : active) {
					questAnswer.append("\n- ")
							.append(safeText(log.getQuestName()));

					if (log.getClearedStepCount() != null
							&& log.getTotalStepCount() != null) {
						questAnswer.append(" (")
								.append(log.getClearedStepCount())
								.append("/")
								.append(log.getTotalStepCount())
								.append("단계)");
					}
				}

				if (progressing > active.size()) {
					questAnswer.append("\n그 외 ")
							.append(progressing - active.size())
							.append("건이 진행 중입니다.");
				}
			}

			answers.add(questAnswer.toString());
		}

		if (rpg) {
			RpgStatsResponse stats =
					rpgStatsService.findMyStats(userId);

			StringBuilder rpgAnswer = new StringBuilder()
					.append("현재 RPG 레벨은 ")
					.append(stats.getLevel())
					.append("이며, 현재 레벨 경험치는 ")
					.append(stats.getExp())
					.append("EXP입니다. ")
					.append("다음 레벨의 요구 경험치는 ")
					.append(stats.getExpToNextLevel())
					.append("EXP입니다.");

			if (stats.getCurrentTitleName() != null) {
				rpgAnswer.append(" 장착 칭호는 '")
						.append(safeText(stats.getCurrentTitleName()))
						.append("'입니다.");
			}

			answers.add(rpgAnswer.toString());
		}

		if (mascots) {
			MascotDexResponse dex =
					mascotService.findDex(userId);

			answers.add("마스코트 도감에서 전체 "
					+ dex.totalCount()
					+ "종 중 "
					+ dex.collectedCount()
					+ "종을 수집했습니다. "
					+ "현재 수집률은 "
					+ dex.completionRate()
					+ "%입니다.");
		}

		return String.join("\n", answers);
	}

	private static boolean isPersonalQuestion(String q) {
		return q.startsWith("내")
				|| q.startsWith("나의")
				|| q.startsWith("제")
				|| q.startsWith("저의")
				|| q.contains("내가")
				|| q.contains("보유한")
				|| q.contains("현재내")
				|| q.contains("진행중인")
				|| q.contains("완료한");
	}

	private static boolean contains(String value, String... keywords) {
		for (String keyword : keywords) {
			if (value.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	private static String normalize(String value) {
		return value == null
				? ""
				: value.toLowerCase(Locale.ROOT)
				.replaceAll("\\s+", "");
	}

	private static String safeText(String value) {
		if (value == null || value.isBlank()) {
			return "이름 미등록";
		}

		String result = value
				.replaceAll("[\\p{Cntrl}]", " ")
				.trim();

		return result.length() > 60
				? result.substring(0, 60)
				: result;
	}
}