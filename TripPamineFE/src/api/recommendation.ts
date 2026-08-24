import api from "./axios";

export interface AiRecommendationResponse {
    recommendId: number;
    planId: number;
    recommendJson: string;
    createdAt: string;
    updatedAt: string;
}

// 최초 조회
// DB에 있으면 DB 결과 반환
// 없으면 OpenAI 호출 → DB 저장 → 반환
export const getAiRecommendationApi = async (
    planId: number
): Promise<AiRecommendationResponse> => {
    const response = await api.get<AiRecommendationResponse>(
        `/recommendations/travel-plans/${planId}`
    );

    return response.data;
};

// 추천 다시 받기
export const regenerateAiRecommendationApi = async (
    planId: number
): Promise<AiRecommendationResponse> => {
    const response = await api.post<AiRecommendationResponse>(
        `/recommendations/travel-plans/${planId}/regenerate`
    );

    return response.data;
};