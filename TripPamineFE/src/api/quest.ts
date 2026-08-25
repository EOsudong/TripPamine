import api from "./axios";

// 백엔드 QuestService/QuestController와 1:1로 맞춘 타입 및 API 함수.
//
// 흐름: 목록 조회(GET /quests) → 유저가 퀘스트를 고르면 시작(POST /quests/{id}/start)
//      → 실시간 GPS로 현재 위치를 반복 전송해 클리어 시도(POST /quests/{id}/clear)
// userId는 더 이상 프론트에서 넘기지 않는다. JWT(Authorization 헤더)로 로그인한 사용자를
// 백엔드가 @AuthenticationPrincipal로 직접 식별하므로, 클라이언트가 임의의 userId를 보내
// 남의 퀘스트를 조작할 수 있는 취약점 자체를 없앤 구조.

export type QuestStatus = "PROGRESS" | "SUCCESS" | "FAILED";

// GET /quests, GET /quests/{id} 응답 - 퀘스트 마스터 정보
export interface QuestResponse {
  questId: number;
  questName: string;
  targetLat: number;
  targetLng: number;
  rewardPoint: number;
  clearRadius: number; // 퀘스트별 클리어 인정 반경(m). 더 이상 프론트에서 50m로 하드코딩하지 않는다.
}

// POST /quests/{id}/start, POST /quests/{id}/clear, GET /quests/my-logs 응답
export interface UserQuestLogResponse {
  logId: number;
  questId: number;
  questName: string;
  rewardPoint: number;
  status: QuestStatus;
  clearDate: string | null;
}

// POST /quests/{id}/clear 요청 바디
export interface QuestClearRequest {
  currentLat: number;
  currentLng: number;
  // GPS Horizontal Accuracy(오차 반경, m). 없으면 서버는 정확도 검증을 건너뛰고 거리만 검증한다.
  accuracyMeters?: number;
}

export const questApi = {
  /** 도전 가능한 퀘스트 전체 목록 */
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
