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

export const saveCustomRecommendationApi = async (
    planId: number,
    modifiedRecommendJson: string
): Promise<AiRecommendationResponse> => {
    try {
        const response = await api.post(
            `/recommendations/travel-plans/${planId}/custom`,
            { modifiedRecommendJson }
        );
        
        return response.data;
    } catch (error) {
        console.error("커스텀 여행 경로 저장 에러:", error);
        throw new Error("커스텀 여행 경로 저장에 실패했습니다.");
    }
};