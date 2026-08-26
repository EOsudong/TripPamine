// 미스터리 투어 API.
//
// [리팩터링] 예전에는 이 파일이 axios를 직접 import해서
//   1) baseURL을 "http://localhost:8080"으로 하드코딩하고,
//   2) localStorage.getItem("accessToken")으로 매 요청마다 Authorization 헤더를 손으로 붙였다.
// 그래서 VITE_API_BASE_URL을 바꿔도(예: 폰 테스트용 터널 주소) 미스터리 투어 요청만 계속
// localhost로 나가서 조용히 실패했고, api/axios.ts의 인터셉터 - 토큰 만료 선제 차단과
// 401 응답 시 세션 정리 - 를 전혀 타지 않아 만료된 토큰으로도 계속 요청을 날렸다.
// 이제 공용 api 인스턴스만 사용하므로 baseURL·인증 처리가 앱 전체와 동일하게 적용된다.
import api from "./axios";

// 백엔드가 "진행 중인 투어 없음"을 204 No Content로 내려주는 엔드포인트들이 있어서,
// 204를 에러가 아닌 정상 응답으로 취급하도록 허용한다.
const allowNoContent = (status: number) =>
    (status >= 200 && status < 300) || status === 204;

export interface MysteryTourRequest {
  travelDate: string;
  travelDays: number;
  peopleCount: number;
  budget: number;
  radiusKm: number;
  departure: string;
  travelStyle: string;
}

export interface MysteryTourResponse {
  mysteryTourId: number;
  travelDate: string;
  travelDays: number;
  peopleCount: number;
  budget: number;
  questCount: number;
  status: string;
  destinationLocked: boolean;
}

export interface MysteryQuestResponse {
  mysteryQuestId: number;
  questOrder: number;
  questName: string;
  questDesc: string;
  verifyType: string;
  targetLat: number | null;
  targetLng: number | null;
  rewardPoint: number;
  status: string;
}

// [TODO] 백엔드 MysteryTourController가 아직 userId를 쿼리 파라미터로 받고 있어서
// (@RequestParam Long userId) 프론트가 localStorage의 userId를 그대로 실어 보낸다.
// 퀘스트 API(quest.ts)는 이미 JWT의 @AuthenticationPrincipal로 사용자를 식별하도록
// 정리했으므로, 미스터리 투어도 같은 방식으로 옮기면 이 파라미터는 사라져야 한다.
// (클라이언트가 임의의 userId를 보내 남의 투어를 조회/생성할 수 있는 구조이기 때문)
function getStoredUserId(): string | null {
  return localStorage.getItem("userId");
}

/** 미스터리 투어 생성 */
export const createMysteryTour = async (
    data: MysteryTourRequest,
): Promise<MysteryTourResponse> => {
  const userId = getStoredUserId();

  if (!userId) {
    throw new Error("로그인 사용자 정보를 찾을 수 없습니다.");
  }

  const response = await api.post<MysteryTourResponse>(
      `/mystery-tours?userId=${userId}`,
      data,
  );

  return response.data;
};

/** 현재 진행 중인 미스터리 투어. 없으면(204) null */
export const getActiveMysteryTour =
    async (): Promise<MysteryTourResponse | null> => {
      const userId = getStoredUserId();

      if (!userId) {
        return null;
      }

      const response = await api.get<MysteryTourResponse>(
          `/mystery-tours/active?userId=${userId}`,
          { validateStatus: allowNoContent },
      );

      return response.status === 204 ? null : response.data;
    };

/** 투어 시작 (생성 후 실제 출발 처리) */
export const startMysteryTour = async (
    mysteryTourId: number,
): Promise<void> => {
  await api.post(`/mystery-tours/${mysteryTourId}/start`, {});
};

/** 현재 수행해야 할 퀘스트. 없으면(204) null */
export const getCurrentMysteryQuest = async (
    mysteryTourId: number,
): Promise<MysteryQuestResponse | null> => {
  const response = await api.get<MysteryQuestResponse>(
      `/mystery-tours/${mysteryTourId}/quests/current`,
      { validateStatus: allowNoContent },
  );

  return response.status === 204 ? null : response.data;
};

/** 퀘스트 완료 처리. 다음 퀘스트가 없으면(204) null */
export const completeMysteryQuest = async (
    mysteryTourId: number,
    mysteryQuestId: number,
): Promise<MysteryQuestResponse | null> => {
  const response = await api.post<MysteryQuestResponse>(
      `/mystery-tours/${mysteryTourId}/quests/${mysteryQuestId}/complete`,
      {},
      { validateStatus: allowNoContent },
  );

  return response.status === 204 ? null : response.data;
};

/** 투어 취소 */
export const cancelMysteryTour = async (
    mysteryTourId: number,
): Promise<void> => {
  await api.delete(`/mystery-tours/${mysteryTourId}`);
};
