import api from "./axios";

export const questApi = {
  /**
   * 사용자의 현재 GPS 좌표를 백엔드로 전송하여 빈센티(Vincenty) 반경 검증 및 퀘스트 클리어를 요청합니다. [2, 3]
   * @param questId 검증할 퀘스트 일련번호
   * @param userId 요청을 수행하는 유저 일련번호
   * @param lat 현재 GPS 위도 (정밀도 NUMBER(10,7)) [2]
   * @param lng 현재 GPS 경도 (정밀도 NUMBER(11,7)) [2]
   * @returns 검증 성공 여부 (true: 성공 및 포인트 지급완료, false: 반경 초과)
   */
  verifyQuest: async (
    questId: number,
    userId: number,
    lat: number,
    lng: number,
  ): Promise<boolean> => {
    const response = await api.post<boolean>(
      `/api/quests/${questId}/verify`,
      null, // Request Body는 비우고 Query Parameter로 전송
      {
        params: {
          userId,
          currentLat: lat,
          currentLng: lng,
        },
      },
    );
    return response.data;
  },
};
