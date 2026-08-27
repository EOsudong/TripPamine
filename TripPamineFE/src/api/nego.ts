import { Client } from "@stomp/stompjs";
import api, { API_BASE_URL } from "./axios";
import SockJS from "sockjs-client";
import { getUserAccessToken } from "../auth/session";

export interface AiNegoLogResponse {
  negoId: number;
  itemName: string;
  offeredPrice: number;
  expiredAt: string;
  conversionYn: "Y" | "N";
  remainingSeconds: number;
}

export const negoApi = {
  /** 현재 유효한 내 핫딜 제안 목록 */
  getActiveOffers: async (): Promise<AiNegoLogResponse[]> => {
    const response = await api.get<AiNegoLogResponse[]>("/nego/active");
    return response.data;
  },

  /** 카운트다운 만료 전 핫딜 수락 (결제에 사용할 계좌를 지정한다) */
  accept: async (
    negoId: number,
    accountId: number,
  ): Promise<AiNegoLogResponse> => {
    // 백엔드 NegoAcceptRequest는 accountId(@NotNull)를 요구한다.
    const response = await api.post<AiNegoLogResponse>(
      `/nego/${negoId}/accept`,
      { accountId },
    );
    return response.data;
  },
};

export function extractNegoErrorMessage(err: unknown, fallback: string): string {
  const anyErr = err as { response?: { data?: { message?: string } } };
  return anyErr?.response?.data?.message ?? fallback;
}

export function connectNegoSocket(
  onOffer: (offer: AiNegoLogResponse) => void,
): Client {
  const client = new Client({
    webSocketFactory: () => new SockJS(`${API_BASE_URL}/ws`),
    connectHeaders: { Authorization: `Bearer ${getUserAccessToken()}` },
    onConnect: () => {
      client.subscribe("/user/queue/nego", (message) => {
        onOffer(JSON.parse(message.body) as AiNegoLogResponse);
      });
    },
    reconnectDelay: 5000, // 끊기면 5초 후 재연결 — TTL 60초짜리 오퍼를 놓치지 않으려면 재연결이 중요
  });
  client.activate();
  return client;
}


