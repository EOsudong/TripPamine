import api from "./axios";

/**
 * AI 매니저에서 표시할 여행 한 건의 정보.
 *
 * 서버에서 이미 계산한 상태와 남은 날짜를 사용한다.
 * 화면에서는 여행 상태를 다시 판단하지 않는다.
 */
export interface AiManagerTravelItem {
  planId: number;
  planName: string;
  locationCd: string | null;
  companionType: string | null;
  totalBudget: number | null;
  startDate: string | null;
  endDate: string | null;
  phase: "PLANNED" | "ACTIVE" | "COMPLETED";
  statusLabel: string;
  daysUntilStart: number | null;
}

/**
 * 사용자 여행 전체 현황.
 *
 * 전체 여행 수와 상태별 여행 수,
 * 그리고 여행별 상세 정보를 포함한다.
 */
export interface AiManagerTravelOverview {
  generatedAt: string;
  totalPlanCount: number;
  plannedCount: number;
  activeCount: number;
  completedCount: number;
  plans: AiManagerTravelItem[];
}

/**
 * 로그인한 사용자의 여행 현황을 가져온다.
 *
 * 인증 정보는 프로젝트 공통 axios가 자동으로 전달한다.
 * 이 함수는 여행 정보를 읽기만 한다.
 */
export async function getAiManagerTravelOverviewApi(): Promise<AiManagerTravelOverview> {
  const response = await api.get<AiManagerTravelOverview>(
    "/api/ai-manager/travel/overview",
  );

  return response.data;
}
