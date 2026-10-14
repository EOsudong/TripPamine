import api from "./axios";


/* =====================================================
 * Emotion
 * ===================================================== */

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

    contextMode:
        | "FIRST2"
        | "FULL_FALLBACK";

    fallbackTriggered: boolean;

    observedAt: string;
}


/**
 * 기존 감정 단독 조회 API.
 *
 * 다른 화면에서도 사용할 수 있으므로 삭제하지 않는다.
 */
export async function getLatestEmotionApi():
    Promise<LatestEmotionResponse | null> {

    const response =
        await api.get<LatestEmotionResponse>(
            "/ai/emotion/latest",
        );

    if (response.status === 204) {
        return null;
    }

    return response.data ?? null;
}


/**
 * 로그인 사용자의 대화를 AI 모델로 분석하고 Oracle에 기록한다.
 * JWT 인증과 사용자 ID 검증은 기존 Backend가 담당한다.
 */
export interface EmotionAnalysisRequest {
    planId: number | null;
    humanTurns: string[];
}

export interface EmotionAnalysisResponse {
    requestId: string;
    modelRunId: number;
    eventId: number;
    observationId: number;
    fineLabel: string;
    fineLabelName: string;
    coarseLabel: string;
    confidence: number;
    first2Confidence: number;
    first2Margin: number;
    contextMode: "FIRST2" | "FULL_FALLBACK";
    fallbackTriggered: boolean;
    humanTurnCount: number;
    turnsUsed: number;
    policyVersion: string;
    modelVersion: string;
    latencyMs: number;
}

export async function analyzeEmotionApi(
    request: EmotionAnalysisRequest,
): Promise<EmotionAnalysisResponse> {
    const response = await api.post<EmotionAnalysisResponse>(
        "/ai/emotion/predict",
        request,
    );

    return response.data;
}


/* =====================================================
 * AI Manager Home
 * ===================================================== */

export interface AiManagerUserContext {
    userId: number;

    userName: string;

    totalPoints: number;

    grade: string | null;

    profileImageUrl: string | null;
}


export interface AiManagerFocusTravel {
    planId: number;

    planName: string;

    totalBudget: number;

    companionType: string | null;

    locationCd: string | null;

    blindYn: string;

    startDate: string | null;

    endDate: string | null;

    state:
        | "ACTIVE"
        | "UPCOMING";

    daysUntilStart:
        number | null;
}


export interface AiManagerTravelContext {
    totalPlanCount: number;

    focusPlan:
        AiManagerFocusTravel | null;
}


export interface AiManagerRecentCompletedQuest {
    logId: number;

    questId: number;

    questName: string;

    rewardPoint: number;

    clearDate: string;
}


export interface AiManagerQuestContext {
    activeCount: number;

    completedCount: number;

    failedCount: number;

    recentCompleted:
        AiManagerRecentCompletedQuest[];
}


export interface AiManagerHomeResponse {
    generatedAt: string;

    user:
        AiManagerUserContext;

    emotion:
        LatestEmotionResponse | null;

    travel:
        AiManagerTravelContext;

    quests:
        AiManagerQuestContext;
}


/**
 * AI Manager Home 통합 Context API.
 *
 * 기존:
 *
 * /users/auth/me
 * /ai/emotion/latest
 * /travel-plans
 * /quests/my-logs
 *
 * 네 요청을 각각 호출하지 않고
 * Backend AI Manager가 조립한 결과를 한 번에 받는다.
 */
export async function getAiManagerHomeApi():
    Promise<AiManagerHomeResponse> {

    const response =
        await api.get<AiManagerHomeResponse>(
            "/api/ai-manager/home",
        );

    return response.data;
}