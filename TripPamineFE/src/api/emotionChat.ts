import api from "./axios";

export interface EmotionChatTurn {
  role: "user" | "assistant";
  content: string;
}

export interface EmotionChatRequest {
  message: string;
  conversationId: number | null;
  saveHistory: boolean;
  analyzeEmotion: boolean;
  humanTurnCount: number;
  history: EmotionChatTurn[];
}

export interface EmotionChatResponse {
  conversationId: number | null;
  answer: string;
  emotionName: string | null;
  createdAt: string;
}

export interface EmotionChatMessage {
  messageId: number;
  role: "USER" | "ASSISTANT";
  content: string;
  createdAt: string;
}

export async function sendEmotionChatApi(
  request: EmotionChatRequest,
): Promise<EmotionChatResponse> {
  const response = await api.post<EmotionChatResponse>(
    "/api/ai-manager/emotion-chat/messages",
    request,
  );
  return response.data;
}

export async function getLatestEmotionChatSessionApi(): Promise<number | null> {
  const response = await api.get<{ conversationId: number | null }>(
    "/api/ai-manager/emotion-chat/sessions/latest",
  );
  return response.data.conversationId;
}

export async function getEmotionChatMessagesApi(
  conversationId: number,
): Promise<EmotionChatMessage[]> {
  const response = await api.get<EmotionChatMessage[]>(
    `/api/ai-manager/emotion-chat/sessions/${conversationId}/messages`,
  );
  return response.data;
}
