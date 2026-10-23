import api from "./axios";

/**
 * =========================================================
 * AI Manager Home API Types
 * =========================================================
 *
 * AI Manager 메인 화면은 이제 여러 API를 직접 조합하지 않는다.
 *
 * Frontend
 *      ↓
 * GET /api/ai-manager/home
 *      ↓
 * Backend AI Manager
 *      ↓
 * User / Emotion / Travel / Quest / Activity
 *
 * 구조로 사용한다.
 */

/* =========================================================
 * User Context
 * ========================================================= */

/**
 * AI Manager Home에서 필요한 최소 사용자 정보.
 */
export interface AiManagerUserContext {
  userId: number;

  userName: string;

  totalPoints: number;

  grade: string | null;

  profileImageUrl: string | null;
}

/* =========================================================
 * Emotion Context
 * ========================================================= */

/**
 * 가장 최근에 성공적으로 저장된 감정 관측 결과.
 *
 * 감정 관측 이력이 없다면 null이 될 수 있다.
 */
export interface AiManagerEmotionContext {
  observationId: number;

  eventId: number | null;

  modelRunId: number;

  requestId: string;

  planId: number | null;

  fineLabel: string;

  fineLabelName: string;

  coarseLabel: string;

  confidence: number;

  modelVersion: string;

  policyVersion: string | null;

  contextMode: string | null;

  fallbackTriggered: boolean;

  observedAt: string;
}

/* =========================================================
 * Travel Context
 * ========================================================= */

/**
 * AI Manager가 현재 대표로 보고 있는 여행.
 *
 * ACTIVE:
 * 현재 여행 기간 중
 *
 * UPCOMING:
 * 앞으로 시작할 여행 중 가장 가까운 여행
 */
export interface AiManagerFocusTravel {
  planId: number;

  planName: string;

  totalBudget: number | null;

  companionType: string | null;

  locationCd: string | null;

  blindYn: string | null;

  startDate: string | null;

  endDate: string | null;

  state: "ACTIVE" | "UPCOMING";

  /**
   * Backend가 Asia/Seoul 날짜 기준으로 계산한 D-Day.
   *
   * ACTIVE 여행에서는 null일 수 있다.
   */
  daysUntilStart: number | null;
}

export interface AiManagerTravelContext {
  totalPlanCount: number;

  focusPlan: AiManagerFocusTravel | null;
}

/* =========================================================
 * Quest Context
 * ========================================================= */

/**
 * 최근 완료 Quest.
 *
 * 현재는 Context 정보로 남겨둔다.
 *
 * 주의:
 * Activity Feed에서는 더 이상 이 값을 사용하지 않는다.
 *
 * Activity Feed의 공식 Source는 이제
 * AI_MANAGER_EVENTS이다.
 */
export interface AiManagerRecentCompletedQuest {
  logId: number;

  questId: number;

  questName: string;

  rewardPoint: number;

  clearDate: string;
}

export interface AiManagerQuestContext {
  activeCount: number;

  completedCount: number;

  failedCount: number;

  recentCompleted: AiManagerRecentCompletedQuest[];
}

/* =========================================================
 * Activity Context
 * ========================================================= */

/**
 * AI_MANAGER_EVENTS에서 만들어진
 * 공식 AI Manager Activity 한 건.
 *
 *
 * 중요한 변화:
 *
 * 이전에는 Frontend가:
 *
 * emotion
 * +
 * recentCompletedQuest
 *
 * 를 직접 합쳐 Activity를 만들었다.
 *
 *
 * 이제는 Backend가:
 *
 * AI_MANAGER_EVENTS
 *      ↓
 * AiManagerActivityService
 *      ↓
 * title / description / domain / occurredAt
 *
 * 형태로 완성해서 내려준다.
 *
 * Frontend는 이 데이터를 그대로 표시하면 된다.
 */
export interface AiManagerActivityEvent {
  eventId: number;

  /**
   * 현재 Backend가 제공하는 Domain.
   *
   * 앞으로 Domain이 추가될 수 있으므로
   * 마지막에 string을 허용하지 않고
   * 여기에서 지원 범위를 명시적으로 관리한다.
   */
  domain: "travel" | "quest" | "emotion" | "finance" | "diary" | "system";

  /**
   * 실제 저장된 EVENT_TYPE.
   *
   * 예:
   *
   * EMOTION_ANALYZED
   * QUEST_COMPLETED
   */
  eventType: string;

  /**
   * Event를 발생시킨 원본 Domain/System.
   *
   * 예:
   *
   * EMOTION_API
   * QUEST
   */
  sourceType: string | null;

  /**
   * 원본 Domain Entity 또는 Request 식별자.
   */
  sourceId: string | null;

  /**
   * Backend가 사용자 화면용으로 변환한 제목.
   */
  title: string;

  /**
   * Backend가 사용자 화면용으로 변환한 설명.
   */
  description: string;

  /**
   * 실제 사건 발생 시간.
   */
  occurredAt: string | null;
}

/* =========================================================
 * Home Response
 * ========================================================= */
/**
 * 사용자의 이번 달 금융 현황.
 */
export interface AiFinanceMonthlySummary {
  // 메인 홈은 저장 Snapshot 없이 원본 거래로 실시간 계산하므로 null일 수 있다.
  summaryId: number | null;
  periodStart: string;
  periodEnd: string;
  totalIncome: number;
  totalExpense: number;
  netAmount: number;
  topCategory: string | null;
  expensesByCategory: Record<string, number>;
  sourceRecordCount: number;
}

/**
 * AI Manager 메인 화면 전체 응답.
 */
export interface AiManagerHomeResponse {
  generatedAt: string;

  user: AiManagerUserContext;

  emotion: AiManagerEmotionContext | null;

  travel: AiManagerTravelContext;

  quests: AiManagerQuestContext;

  /**
   * AI_MANAGER_EVENTS 기반 공식 Timeline.
   */
  activities: AiManagerActivityEvent[];

  /**
   * 금융 정보 활용을 허용한 사용자에게만 제공된다.
   * 허용하지 않았거나 기록이 없으면 null이다.
   */
  finance: AiFinanceMonthlySummary | null;
}

/* =========================================================
 * API
 * ========================================================= */

/**
 * 로그인 사용자의 AI Manager Home Context를 조회한다.
 *
 * JWT Authorization Header는
 * 공통 axios interceptor가 담당한다.
 */
export async function getAiManagerHomeApi(): Promise<AiManagerHomeResponse> {
  const response = await api.get<AiManagerHomeResponse>("/api/ai-manager/home");

  return response.data;
}
