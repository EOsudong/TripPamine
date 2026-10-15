package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.AiFinanceMonthlySummaryResponse;
import com.example.trippaminebe.domain.ai.dto.AiManagerHomeResponse;
import com.example.trippaminebe.domain.ai.dto.LatestEmotionResponse;
import com.example.trippaminebe.domain.quest.dto.response.UserQuestLogResponse;
import com.example.trippaminebe.domain.quest.entity.QuestStatus;
import com.example.trippaminebe.domain.quest.service.QuestService;
import com.example.trippaminebe.domain.travel.dto.TravelPlanResponse;
import com.example.trippaminebe.domain.travel.service.TravelPlanService;
import com.example.trippaminebe.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * AI Manager Home Context 집계 서비스.
 *
 * <p>
 * 기존 각 도메인의 Service를 직접 재사용합니다.
 * </p>
 *
 * <pre>
 * User
 * Emotion
 * Travel
 * Quest
 *     ↓
 * AiManagerHomeService
 *     ↓
 * 하나의 Home Response
 * </pre>
 *
 * 현재 단계에서는 AI 추론이나 추천 우선순위를 결정하지 않습니다.
 * "현재 사용자에게 어떤 Context가 존재하는지"만 조립합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiManagerHomeService {

	/**
	 * TripPamine 서비스 기준 시간대.
	 *
	 * 서버 OS/JVM timezone에 의존하지 않고
	 * 여행 D-Day 계산 기준을 명확하게 고정합니다.
	 */
	private static final ZoneId SERVICE_ZONE =
			ZoneId.of("Asia/Seoul");

	private final EmotionQueryService emotionQueryService;
	private final TravelPlanService travelPlanService;
	private final QuestService questService;
	private final AiManagerActivityService aiManagerActivityService;

	// 저장된 월별 금융 요약을 조회한다.
	private final AiFinanceMonthlySummaryService
			aiFinanceMonthlySummaryService;

	// 사용자의 금융 정보 활용 동의 여부를 확인한다.
	private final AiManagerSettingsService
			aiManagerSettingsService;

	/**
	 * 현재 로그인 사용자의 AI Manager Home Context를 생성합니다.
	 */
	public AiManagerHomeResponse getHome(User user) {

		if (user == null || user.getId() == null) {
			throw new IllegalArgumentException(
					"AI Manager Home을 조회하려면 인증된 사용자가 필요합니다."
			);
		}

		Long userId = user.getId();

		LocalDateTime now =
				LocalDateTime.now(SERVICE_ZONE);


		/* =================================================
		 * 1. Emotion
		 * =================================================
		 *
		 * Q18-I에서 만든 최신 성공 감정 조회 로직을 그대로 사용합니다.
		 *
		 * planId = null
		 * → 특정 여행이 아니라 사용자의 전체 최신 감정
		 */
		LatestEmotionResponse latestEmotion =
				emotionQueryService
						.findLatest(userId, null)
						.orElse(null);


		/* =================================================
		 * 2. Travel
		 * ================================================= */

		List<TravelPlanResponse> travelPlans =
				travelPlanService.findAllByUserId(userId);

		AiManagerHomeResponse.FocusTravel focusTravel =
				selectFocusTravel(
						travelPlans,
						now
				);

		AiManagerHomeResponse.TravelContext travelContext =
				new AiManagerHomeResponse.TravelContext(
						travelPlans.size(),
						focusTravel
				);


		/* =================================================
		 * 3. Quest
		 * ================================================= */

		List<UserQuestLogResponse> questLogs =
				questService.findMyLogs(
						userId
				);


		/*
		 * 현재 진행 중인 Quest 개수.
		 */
		long activeCount =
				questLogs.stream()
						.filter(log ->
								log.getStatus()
										== QuestStatus.PROGRESS
						)
						.count();


		/*
		 * 완료된 Quest 개수.
		 */
		long completedCount =
				questLogs.stream()
						.filter(log ->
								log.getStatus()
										== QuestStatus.SUCCESS
						)
						.count();


		/*
		 * 실패한 Quest 개수.
		 */
		long failedCount =
				questLogs.stream()
						.filter(log ->
								log.getStatus()
										== QuestStatus.FAILED
						)
						.count();


		/*
		 * =================================================
		 * Active Quest Context
		 * =================================================
		 *
		 * 이전에는 activeCount만 전달했기 때문에
		 * AI Manager가:
		 *
		 * "진행 중 Quest가 2개 있다"
		 *
		 * 는 사실만 알 수 있었다.
		 *
		 * 이제 실제 진행 중 Quest의:
		 *
		 * - logId
		 * - questId
		 * - 이름
		 * - 보상
		 * - 시작 시각
		 * - Chain 진행도
		 *
		 * 를 Context에 포함한다.
		 *
		 *
		 * startedAt이 존재하는 경우
		 * 최근 시작한 Quest부터 배치한다.
		 *
		 * startedAt이 null인 비정상/레거시 데이터는
		 * 목록에서 삭제하지 않고 마지막으로 보낸다.
		 */
		List<AiManagerHomeResponse.ActiveQuest> activeQuests =
				questLogs.stream()
						.filter(log ->
								log.getStatus()
										== QuestStatus.PROGRESS
						)
						.sorted(
								Comparator.comparing(
										UserQuestLogResponse::getStartedAt,
										Comparator.nullsLast(
												Comparator.reverseOrder()
										)
								)
						)
						.map(log ->
								new AiManagerHomeResponse.ActiveQuest(

										log.getLogId(),

										log.getQuestId(),

										log.getQuestName(),

										log.getRewardPoint(),

										log.getRewardExp(),

										log.getStartedAt(),

										log.getClearedStepCount(),

										log.getTotalStepCount()
								)
						)
						.toList();


		/*
		 * =================================================
		 * Recent Completed Quest
		 * =================================================
		 *
		 * 기존 동작을 그대로 유지한다.
		 */
		List<AiManagerHomeResponse.RecentCompletedQuest> recentCompleted =
				questLogs.stream()
						.filter(log ->
								log.getStatus()
										== QuestStatus.SUCCESS
										&&
										log.getClearDate() != null
						)
						.sorted(
								Comparator.comparing(
										UserQuestLogResponse::getClearDate
								).reversed()
						)
						.limit(
								3
						)
						.map(log ->
								new AiManagerHomeResponse.RecentCompletedQuest(

										log.getLogId(),

										log.getQuestId(),

										log.getQuestName(),

										log.getRewardPoint(),

										log.getClearDate()
								)
						)
						.toList();


		/*
		 * 최종 Quest Context.
		 *
		 * activeCount와 activeQuests를 둘 다 유지하는 이유:
		 *
		 * activeCount
		 * → 빠른 Summary / UI 표시
		 *
		 * activeQuests
		 * → Priority Engine / Manager SLM 판단
		 */
		AiManagerHomeResponse.QuestContext questContext =
				new AiManagerHomeResponse.QuestContext(
						activeCount,
						completedCount,
						failedCount,
						activeQuests,
						recentCompleted
				);

		List<AiManagerHomeResponse.ActivityEvent> activities =
				aiManagerActivityService.findRecent(
						userId
				);


		/* =================================================
		 * 4. User
		 * ================================================= */

		AiManagerHomeResponse.UserContext userContext =
				new AiManagerHomeResponse.UserContext(
						user.getId(),
						user.getUserName(),
						user.getTotalPoints() != null
								? user.getTotalPoints()
								: 0L,
						user.getGrade() != null
								? user.getGrade().name()
								: null,
						user.getProfileImageUrl()
				);


		/* =================================================
		 * Final
		 * ================================================= */


		/*
		 * =================================================
		 * 5. Finance
		 * =================================================
		 *
		 * 금융 정보를 사용하도록 설정한 사용자에게만
		 * 저장된 이번 달 수입과 지출을 제공한다.
		 *
		 * 이곳에서는 금융 기록을 다시 계산하거나
		 * 새로운 기록을 저장하지 않는다.
		 */
		AiFinanceMonthlySummaryResponse financeContext = null;

// 기존 사용자 설정을 확인한다.
		boolean financeEnabled =
				aiManagerSettingsService
						.getSettings(userId)
						.financeInsightEnabled();

		if (financeEnabled) {

			// 한국 시간 기준 현재 월을 사용한다.
			YearMonth currentMonth =
					YearMonth.from(now);

			/*
			 * 이미 저장된 금융 요약만 가져온다.
			 *
			 * 아직 생성된 요약이 없다면
			 * null을 유지해 실제 금액을 추측하지 않는다.
			 */
			financeContext =
					aiFinanceMonthlySummaryService
							.findMonthly(
									userId,
									currentMonth
							)
							.orElse(null);
		}

		/*
		 * =================================================
		 * Final
		 * =================================================
		 *
		 * 기존 감정·여행·퀘스트·활동 정보에
		 * 금융 요약을 추가해 한 번에 반환한다.
		 */
		return new AiManagerHomeResponse(
				now,
				userContext,
				latestEmotion,
				travelContext,
				questContext,
				activities,
				financeContext
		);

	}


	/**
	 * AI Manager Home에서 대표로 보여줄 여행 하나를 선택합니다.
	 *
	 * 우선순위:
	 *
	 * 1. 현재 여행 중인 계획
	 * 2. 앞으로 가장 빨리 시작하는 여행
	 * 3. 없으면 null
	 *
	 * 이것은 AI Priority Engine이 아니라
	 * 기본적인 현재 Travel Context 선택 규칙입니다.
	 */
	private AiManagerHomeResponse.FocusTravel selectFocusTravel(
			List<TravelPlanResponse> plans,
			LocalDateTime now
	) {

		/*
		 * 현재 진행 중인 여행.
		 *
		 * 종료일이 없는 계획은 시작일이 지났다면
		 * ACTIVE로 취급합니다.
		 */
		TravelPlanResponse activePlan =
				plans.stream()
						.filter(plan ->
								plan.getStartDate() != null
										&& !plan.getStartDate().isAfter(now)
										&& (
										plan.getEndDate() == null
												|| !plan.getEndDate().isBefore(now)
								)
						)
						.min(
								Comparator.comparing(
										TravelPlanResponse::getStartDate
								)
						)
						.orElse(null);


		if (activePlan != null) {
			return toFocusTravel(
					activePlan,
					"ACTIVE",
					null
			);
		}


		/*
		 * 아직 시작하지 않은 여행 중
		 * 가장 가까운 여행.
		 */
		TravelPlanResponse upcomingPlan =
				plans.stream()
						.filter(plan ->
								plan.getStartDate() != null
										&& plan.getStartDate().isAfter(now)
						)
						.min(
								Comparator.comparing(
										TravelPlanResponse::getStartDate
								)
						)
						.orElse(null);


		if (upcomingPlan == null) {
			return null;
		}


		/*
		 * 시간 단위가 아니라 날짜 기준 D-Day.
		 *
		 * 예:
		 * 오늘 밤 23:00 → 내일 08:00
		 *
		 * 실제 남은 시간은 9시간이지만
		 * 사용자 UI에서는 D-1이 자연스럽습니다.
		 */
		long daysUntilStart =
				ChronoUnit.DAYS.between(
						now.toLocalDate(),
						upcomingPlan
								.getStartDate()
								.toLocalDate()
				);


		return toFocusTravel(
				upcomingPlan,
				"UPCOMING",
				Math.max(
						0L,
						daysUntilStart
				)
		);
	}


	/**
	 * 기존 TravelPlanResponse를
	 * AI Manager Home 전용 최소 Travel DTO로 변환합니다.
	 */
	private AiManagerHomeResponse.FocusTravel toFocusTravel(
			TravelPlanResponse plan,
			String state,
			Long daysUntilStart
	) {

		return new AiManagerHomeResponse.FocusTravel(
				plan.getPlanId(),
				plan.getPlanName(),
				plan.getTotalBudget(),
				plan.getCompanionType(),
				plan.getLocationCd(),
				plan.getBlindYn(),
				plan.getStartDate(),
				plan.getEndDate(),
				state,
				daysUntilStart
		);
	}
}