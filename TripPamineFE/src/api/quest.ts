import api from "./axios";

// 백엔드 QuestService/QuestController와 1:1로 맞춘 타입 및 API 함수.
//
// 목록 조회(GET /quests) → 유저가 퀘스트를 고르면 시작(POST /quests/{id}/start)
//      → 실시간 GPS로 현재 위치를 반복 전송해 클리어 시도(POST /quests/{id}/clear)
// userId는 더 이상 프론트에서 넘기지 않는다. JWT(Authorization 헤더)로 로그인한 사용자를
// 백엔드가 @AuthenticationPrincipal로 직접 식별하므로, 클라이언트가 임의의 userId를 보내
// 남의 퀘스트를 조작할 수 있는 취약점 자체를 없앤 구조.
//
// 레벨/EXP/챕터/칭호 관련 타입과 API를 추가했다.
// 기존 필드는 그대로 두고 옵셔널 필드만 덧붙였기 때문에, 이 파일을 쓰던 기존 화면은
// 전부 그대로 동작한다.

export type QuestStatus = "PROGRESS" | "SUCCESS" | "FAILED";

export type QuestType = "LOCATION" | "DISCOVERY" | "PHOTO" | "ACTION" | "CHAIN";

// GET /quests, GET /quests/{id} 응답 - 퀘스트 마스터 정보
export interface QuestResponse {
  questId: number;
  questName: string;
  description: string | null;
  targetLat: number;
  targetLng: number;
  rewardPoint: number;
  rewardExp: number;
  clearRadius: number; // 퀘스트별 클리어 인정 반경(m). 더 이상 프론트에서 50m로 하드코딩하지 않는다.
  questType: QuestType;
  difficulty: number; // 1~3 권장 (★☆☆ ~ ★★★)
  imageUrl: string | null;
  thumbnailUrl: string | null;
  chapterId: number | null;
  chapterName: string | null;
  requiredLevel: number;
  isActive: boolean;
  // 내 현재 레벨 기준으로 서버가 계산해주는 잠금 여부. 로그인 사용자 조회에서만 의미가 있다.
  locked: boolean;
  // 독립 퀘스트 선행 관계 정보(기존 CHAIN 내부 단계와 구별)
  prerequisiteQuestIds?: number[];
  missingPrerequisiteQuestIds?: number[];
}

// POST /quests/{id}/start, POST /quests/{id}/clear, GET /quests/my-logs 응답
export interface UserQuestLogResponse {
  logId: number;
  questId: number;
  questName: string;
  rewardPoint: number;
  rewardExp: number;
  status: QuestStatus;
  startedAt: string | null;
  clearDate: string | null;
  // 클리어 시점 보상 스냅샷 (아직 미클리어면 null)
  earnedPoint: number | null;
  earnedExp: number | null;
  // 연쇄(CHAIN) 퀘스트 진행률(N/M). 일반 퀘스트거나 계산되지 않은 조회에서는 둘 다 null.
  clearedStepCount?: number | null;
  totalStepCount?: number | null;
}

// POST /quests/{id}/clear 요청 바디
export interface QuestClearRequest {
  currentLat: number;
  currentLng: number;
  // GPS Horizontal Accuracy(오차 반경, m). 없으면 서버는 정확도 검증을 건너뛰고 거리만 검증한다.
  accuracyMeters?: number;
}

// GET /quests/chapters 응답
export interface QuestChapterResponse {
  chapterId: number;
  chapterName: string;
  description: string | null;
  region: string | null;
  orderNo: number;
  requiredLevel: number;
  imageUrl: string | null;
  locked: boolean;
}

// GET /quests/me/stats 응답
export interface RpgStatsResponse {
  level: number;
  exp: number;
  expToNextLevel: number;
  totalExp: number;
  totalQuests: number;
  completedQuests: number;
  currentTitleId: number | null;
  currentTitleName: string | null;
}

// 칭호 자동 획득 조건 (백엔드 TitleConditionType)
export type TitleConditionType = "FIRST_QUEST_CLEAR" | "QUEST_COUNT" | "LEVEL_REACHED";

// GET /quests/me/titles 응답
export interface TitleResponse {
  titleId: number;
  titleName: string;
  description: string | null;
  iconUrl: string | null;
  // 백엔드는 이미 내려주고 있었는데 타입에 빠져 있던 필드. 잠긴 칭호의 획득 조건 안내에 쓴다.
  requiredConditionType?: TitleConditionType | null;
  requiredConditionValue?: number | null;
  earned: boolean;
  earnedAt: string | null;
  equipped: boolean;
}

// GET /quests/{questId}/steps 응답 - 연쇄(CHAIN) 퀘스트의 단계 + 내 클리어 여부
export interface QuestStepResponse {
  stepId: number;
  questId: number;
  stepOrder: number;
  stepName: string;
  description: string | null;
  targetLat: number;
  targetLng: number;
  clearRadius: number;
  cleared: boolean;
}

// POST /quests/{questId}/steps/{stepId}/clear 응답
export interface QuestStepClearResponse {
  // 이번 요청으로 이 단계를 실제로 클리어했는지(반경 밖이면 false - 다시 시도)
  stepCleared: boolean;
  // 이번 클리어로 연쇄 퀘스트 전체가 완료되었는지(마지막 단계였는지)
  questCompleted: boolean;
  clearedStepCount: number;
  totalStepCount: number;
  // 다음에 클리어해야 할 단계 ID. 전체 완료되었으면 null.
  nextStepId: number | null;
  questLog: UserQuestLogResponse;
}

export const questApi = {
  /** 도전 가능한 퀘스트 전체 목록 (활성 퀘스트만, 내 레벨 기준 잠금 여부 포함) */
  getQuests: async (): Promise<QuestResponse[]> => {
    const response = await api.get<QuestResponse[]>("/quests");
    return response.data;
  },

  /** 퀘스트 단건 조회 */
  getQuest: async (questId: number): Promise<QuestResponse> => {
    const response = await api.get<QuestResponse>(`/quests/${questId}`);
    return response.data;
  },

  /** 내 퀘스트 진행/완료 이력 전체 (최근 순) */
  getMyLogs: async (): Promise<UserQuestLogResponse[]> => {
    const response = await api.get<UserQuestLogResponse[]>("/quests/my-logs");
    return response.data;
  },

  /**
   * 퀘스트 시작. 이미 시작/완료된 퀘스트면 백엔드가 기존 로그를 그대로 반환하므로
   * (중복 시작 방지, idempotent) 화면 진입 시 걱정 없이 호출해도 된다.
   * 레벨이 부족한(잠긴) 퀘스트를 시작하려 하면 400 에러가 던져진다.
   */
  startQuest: async (questId: number): Promise<UserQuestLogResponse> => {
    const response = await api.post<UserQuestLogResponse>(
        `/quests/${questId}/start`,
    );
    return response.data;
  },

  /**
   * 현재 GPS 좌표로 클리어 인증 시도.
   * 응답 status가 SUCCESS/FAILED 둘 다로 정상 반환될 수 있음 (반경 밖이면 FAILED, 예외 아님).
   * GPS 신호가 너무 약하거나(오차 200m 초과) 이미 SUCCESS로 끝난 퀘스트를 다시 시도하면
   * 400으로 예외가 던져지므로 호출부에서 반드시 catch 처리할 것.
   */
  clearQuest: async (
      questId: number,
      request: QuestClearRequest,
  ): Promise<UserQuestLogResponse> => {
    const response = await api.post<UserQuestLogResponse>(
        `/quests/${questId}/clear`,
        request,
    );
    return response.data;
  },

  /** 퀘스트 챕터 목록 (내 레벨 기준 잠금 여부 포함) */
  getChapters: async (): Promise<QuestChapterResponse[]> => {
    const response = await api.get<QuestChapterResponse[]>("/quests/chapters");
    return response.data;
  },

  /** 내 RPG 스탯(레벨/EXP/완료 퀘스트 수 등) */
  getMyStats: async (): Promise<RpgStatsResponse> => {
    const response = await api.get<RpgStatsResponse>("/quests/me/stats");
    return response.data;
  },

  /** 전체 칭호 + 내 보유/장착 여부 */
  getMyTitles: async (): Promise<TitleResponse[]> => {
    const response = await api.get<TitleResponse[]>("/quests/me/titles");
    return response.data;
  },

  /** [칭호 변경] 보유한 칭호 장착. 미보유 칭호면 400. 바뀐 RPG 스탯을 돌려준다. */
  equipTitle: async (titleId: number): Promise<RpgStatsResponse> => {
    const response = await api.put<RpgStatsResponse>(`/quests/me/titles/${titleId}/equip`);
    return response.data;
  },

  /** [칭호 변경] 장착한 칭호 해제 */
  unequipTitle: async (): Promise<RpgStatsResponse> => {
    const response = await api.delete<RpgStatsResponse>("/quests/me/titles/equip");
    return response.data;
  },

  /** 연쇄(CHAIN) 퀘스트의 단계 목록 + 내 클리어 여부 */
  getSteps: async (questId: number): Promise<QuestStepResponse[]> => {
    const response = await api.get<QuestStepResponse[]>(
        `/quests/${questId}/steps`,
    );
    return response.data;
  },

  /**
   * 연쇄 퀘스트의 "다음 차례" 단계를 GPS로 인증한다. clearQuest와 마찬가지로
   * 반경 밖이면 stepCleared=false로 정상 응답하며(예외 아님), 순서를 건너뛰거나
   * 이미 시작하지 않은 퀘스트면 400 예외가 던져진다.
   */
  clearStep: async (
      questId: number,
      stepId: number,
      request: QuestClearRequest,
  ): Promise<QuestStepClearResponse> => {
    const response = await api.post<QuestStepClearResponse>(
        `/quests/${questId}/steps/${stepId}/clear`,
        request,
    );
    return response.data;
  },
};

// axios 에러에서 백엔드가 내려준 한국어 메시지를 안전하게 꺼내는 헬퍼.
// GlobalExceptionHandler/QuestExceptionHandler가 { status, message } 형태로 내려주는 걸 그대로 사용.
export function extractQuestErrorMessage(
    err: unknown,
    fallback: string,
): string {
  const anyErr = err as { response?: { data?: { message?: string } } };
  return anyErr?.response?.data?.message ?? fallback;
}

// 잠긴 칭호의 획득 조건 문구
export function describeTitleCondition(title: TitleResponse): string {
  const value = title.requiredConditionValue ?? 0;
  switch (title.requiredConditionType) {
    case "FIRST_QUEST_CLEAR":
      return "첫 퀘스트 완료 시 획득";
    case "QUEST_COUNT":
      return `퀘스트 ${value}개 완료 시 획득`;
    case "LEVEL_REACHED":
      return `Lv.${value} 달성 시 획득`;
    default:
      return title.description ?? "획득 조건 비공개";
  }
}