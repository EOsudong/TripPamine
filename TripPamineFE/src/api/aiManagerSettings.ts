import api from "./axios";

/**
 * 현재 사용자가 선택한 AI 활용 설정.
 *
 * 기존 서버 응답 구조와 동일하게 정의한다.
 */
export interface AiManagerSettings {
  configured: boolean;
  memoryEnabled: boolean;
  emotionEnabled: boolean;
  financeInsightEnabled: boolean;
  autoDiaryEnabled: boolean;
  personalizationEnabled: boolean;
}

/**
 * 저장된 AI 활용 설정 조회.
 *
 * 인증 정보는 공통 axios가 자동으로 전달한다.
 */
export async function getAiManagerSettingsApi(): Promise<AiManagerSettings> {
  const response = await api.get<AiManagerSettings>("/api/ai-manager/settings");

  return response.data;
}

/**
 * 금융 정보 활용 허용 또는 중지.
 *
 * enabled = true  → 허용
 * enabled = false → 중지
 *
 * 다른 AI 설정값은 함께 전송하지 않는다.
 */
export async function changeFinanceConsentApi(
  enabled: boolean,
): Promise<AiManagerSettings> {
  const response = await api.put<AiManagerSettings>(
    "/api/ai-manager/settings/finance",
    { enabled },
  );

  return response.data;
}

/** 감정 분석 활용 여부만 변경하며 다른 동의 항목에는 영향을 주지 않는다. */
export async function changeEmotionConsentApi(
  enabled: boolean,
): Promise<AiManagerSettings> {
  const response = await api.put<AiManagerSettings>(
    "/api/ai-manager/settings/emotion",
    { enabled },
  );
  return response.data;
}
