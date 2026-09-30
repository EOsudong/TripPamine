import api from "./axios";

/**
 * 백엔드 GET /ai/emotion/latest 응답 타입.
 *
 * AI Manager 홈에서는 감정 모델의 모든 내부 정보를
 * 보여주지는 않지만, 향후 Insight 생성 등에 사용할 수 있도록
 * 백엔드 응답 구조는 그대로 보존한다.
 */
export interface LatestEmotionResponse {
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
    policyVersion: string;
    contextMode: "FIRST2" | "FULL_FALLBACK";

    fallbackTriggered: boolean;

    observedAt: string;
}

/**
 * 로그인 사용자의 가장 최근 감정 상태를 조회한다.
 *
 * 백엔드는 아직 감정 기록이 없으면
 * 404가 아니라 204 No Content를 반환한다.
 *
 * 따라서:
 *
 * 200 → 감정 데이터
 * 204 → null
 */
export async function getLatestEmotionApi(): Promise<
    LatestEmotionResponse | null
> {
    const response =
        await api.get<LatestEmotionResponse>(
            "/ai/emotion/latest",
        );

    if (response.status === 204) {
        return null;
    }

    return response.data ?? null;
}