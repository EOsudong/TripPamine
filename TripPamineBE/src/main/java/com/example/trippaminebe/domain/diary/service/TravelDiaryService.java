package com.example.trippaminebe.domain.diary.service;

import com.example.trippaminebe.domain.accountbook.entity.TravelLedger;
import com.example.trippaminebe.domain.accountbook.repository.TravelLedgerRepository;
import com.example.trippaminebe.domain.diary.dto.response.TravelDiaryResponse;
import com.example.trippaminebe.domain.diary.entity.DiaryKeyword;
import com.example.trippaminebe.domain.diary.entity.KeywordType;
import com.example.trippaminebe.domain.diary.entity.TravelDiary;
import com.example.trippaminebe.domain.diary.exception.TravelDiaryNotFoundException;
import com.example.trippaminebe.domain.diary.repository.TravelDiaryRepository;
import com.example.trippaminebe.domain.quest.entity.QuestStatus;
import com.example.trippaminebe.domain.quest.repository.UserQuestLogRepository;
import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.travel.repository.TravelPlanRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TravelDiaryService {

	private final TravelDiaryRepository travelDiaryRepository;
	private final TravelPlanRepository travelPlanRepository;
	private final TravelLedgerRepository travelLedgerRepository;
	private final UserQuestLogRepository userQuestLogRepository;
	private final OpenAiDiaryGenerationService openAiDiaryGenerationService;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Transactional(readOnly = true)
	public List<TravelDiaryResponse> findAllByUserId(Long userId) {
		return travelDiaryRepository.findByUser_IdOrderByCreateDateDesc(userId).stream()
				.map(TravelDiaryResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public TravelDiaryResponse findById(Long diaryId, Long userId) {
		TravelDiary diary = travelDiaryRepository.findByDiaryIdAndUser_Id(diaryId, userId)
				.orElseThrow(() -> new TravelDiaryNotFoundException("다이어리를 찾을 수 없거나 접근 권한이 없습니다."));
		return TravelDiaryResponse.from(diary);
	}

	public TravelDiaryResponse toggleShare(Long diaryId, Long userId) {
		TravelDiary diary = travelDiaryRepository.findByDiaryIdAndUser_Id(diaryId, userId)
				.orElseThrow(() -> new TravelDiaryNotFoundException("다이어리를 찾을 수 없거나 접근 권한이 없습니다."));
		diary.toggleShare();
		return TravelDiaryResponse.from(diary);
	}

	public void delete(Long diaryId, Long userId) {
		TravelDiary diary = travelDiaryRepository.findByDiaryIdAndUser_Id(diaryId, userId)
				.orElseThrow(() -> new TravelDiaryNotFoundException("다이어리를 찾을 수 없거나 접근 권한이 없습니다."));
		travelDiaryRepository.delete(diary);
	}

	/**
	 * 여행 종료 후 가계부 지출 패턴과 퀘스트 성공률을 분석하여 AI 다이어리를 생성합니다.
	 */
	@Transactional
	public TravelDiaryResponse generateDiaryReport(Long planId, Long userId) {
		// 1. 여행 계획 조회 및 권한/상태 검증
		TravelPlan plan = travelPlanRepository.findByPlanIdAndUser_IdAndDelYn(planId, userId, "N")
				.orElseThrow(() -> new IllegalArgumentException("여행 계획을 찾을 수 없거나 접근 권한이 없습니다."));

		if (plan.getEndDate() != null && plan.getEndDate().isAfter(LocalDateTime.now())) {
			throw new IllegalArgumentException("아직 종료되지 않은 여행입니다. 여행이 끝난 후 다이어리를 생성할 수 있습니다.");
		}

		travelDiaryRepository.findByTravelPlan_PlanId(planId).ifPresent(existing -> {
			throw new IllegalArgumentException("이미 해당 여행에 대한 다이어리가 생성되어 있습니다. diaryId=" + existing.getDiaryId());
		});

		// 2. 가계부 지출 패턴 분석
		List<TravelLedger> ledgers = travelLedgerRepository.findByTravelPlan_PlanId(planId);
		BigDecimal totalSpent = ledgers.stream()
				.map(TravelLedger::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		String topCategory = ledgers.stream()
				.collect(Collectors.groupingBy(
						this::displayCategory,
						Collectors.reducing(BigDecimal.ZERO, TravelLedger::getAmount, BigDecimal::add)
				))
				.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse("기타");

		// 3. 퀘스트 성공률 분석
		long successCount = userQuestLogRepository.countByUser_IdAndStatus(userId, QuestStatus.SUCCESS);
		long failedCount = userQuestLogRepository.countByUser_IdAndStatus(userId, QuestStatus.FAILED);
		long totalCount = successCount + failedCount;

		// 4. AI 생성 및 Fallback 처리
		String title;
		String content;
		long dopamineScore;
		Map<KeywordType, List<String>> keywordsByType;

		try {
			String json = openAiDiaryGenerationService.generateDiaryJson(
					plan, totalSpent, topCategory, successCount, totalCount
			);
			JsonNode root = objectMapper.readTree(extractJson(json));

			title = root.path("title").asText(plan.getPlanName() + " 여행 다이어리");
			content = root.path("content").asText("여행을 다녀왔습니다.");
			dopamineScore = clampScore(root.path("dopamineScore").asLong(calculateFallbackScore(totalCount, successCount)));
			keywordsByType = parseKeywords(root.path("keywords"));
		} catch (Exception e) {
			log.warn("AI 다이어리 생성 실패, 기본값으로 대체합니다. planId={}, error={}", planId, e.getMessage());
			title = plan.getPlanName() + " 정복 일기";
			content = String.format("이번 여행 '%s'은(는) 총 %s원을 소비하고, 퀘스트 %d개 중 %d개를 성공했습니다!",
					plan.getPlanName(), totalSpent, totalCount, successCount);
			dopamineScore = calculateFallbackScore(totalCount, successCount);
			keywordsByType = Map.of(
					KeywordType.EMOTION, List.of(dopamineScore > 75 ? "짜릿함" : "잔잔한힐링"),
					KeywordType.EXPEND, List.of(totalSpent.compareTo(new BigDecimal(500000)) > 0 ? "플렉스소비" : "가성비소비")
			);
		}

		// 5. 엔티티 생성 및 키워드 추가
		TravelDiary diary = TravelDiary.builder()
				.travelPlan(plan)
				.user(plan.getUser())
				.aiTitle(title)
				.aiContent(content)
				.dopamineScore(dopamineScore)
				.aiImageUrl("https://images.unsplash.com/photo-1507525428034-b723cf961d3e") // 기본 커버 이미지
				.shareYn("N")
				.build();

		keywordsByType.forEach((type, names) ->
				names.forEach(name -> diary.addKeyword(
						DiaryKeyword.builder()
								.keywordType(type)
								.keywordName(name)
								.build()
				))
		);

		// 6. DB 저장 및 DTO 반환
		TravelDiary savedDiary = travelDiaryRepository.save(diary);
		return TravelDiaryResponse.from(savedDiary);
	}

	// --- Helper Methods ---

	/**
	 * CATEGORY_NM(표시명)이 비어있는 과거 데이터를 대비해 CATEGORY_ID(코드)로 폴백합니다.
	 */
	private String displayCategory(TravelLedger ledger) {
		if (ledger.getCategoryNm() != null && !ledger.getCategoryNm().isBlank()) {
			return ledger.getCategoryNm();
		}
		if (ledger.getCategoryId() != null && !ledger.getCategoryId().isBlank()) {
			return ledger.getCategoryId();
		}
		return "기타";
	}

	private long calculateFallbackScore(long totalCount, long successCount) {
		if (totalCount == 0) {
			return 50L;
		}
		return Math.round((successCount * 100.0) / totalCount);
	}

	private long clampScore(long score) {
		return Math.max(0, Math.min(100, score));
	}

	private Map<KeywordType, List<String>> parseKeywords(JsonNode keywordsNode) {
		Map<KeywordType, List<String>> result = new EnumMap<>(KeywordType.class);
		putKeywordList(result, KeywordType.EMOTION, keywordsNode.path("emotion"));
		putKeywordList(result, KeywordType.EXPEND, keywordsNode.path("expend"));
		putKeywordList(result, KeywordType.PLACE, keywordsNode.path("place"));
		return result;
	}

	private void putKeywordList(Map<KeywordType, List<String>> map, KeywordType type, JsonNode arrayNode) {
		List<String> names = new ArrayList<>();
		if (arrayNode.isArray()) {
			arrayNode.forEach(node -> names.add(node.asText()));
		}
		if (!names.isEmpty()) {
			map.put(type, names);
		}
	}

	/**
	 * OpenAI가 코드블록(```json ... ```)을 덧붙이는 경우를 대비한 방어적 파싱
	 */
	private String extractJson(String raw) {
		if (raw == null) return "{}";
		String trimmed = raw.trim();
		int start = trimmed.indexOf('{');
		int end = trimmed.lastIndexOf('}');
		if (start >= 0 && end > start) {
			return trimmed.substring(start, end + 1);
		}
		return trimmed;
	}
}