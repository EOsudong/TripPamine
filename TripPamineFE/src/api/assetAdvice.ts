import { API_BASE_URL } from "./axios"


// =====================================================
// 여행 목표 등록 Request
// =====================================================

export interface TravelGoalRequest {
    goalName: string
    targetAmount: number
    currentAmount: number
    targetDate: string
}


// =====================================================
// 여행 목표 정보
// =====================================================

export interface TravelGoalInfo {
    goalId: number

    goalName: string

    targetAmount: number

    currentAmount: number

    remainingAmount: number

    progressRate: number

    targetDate: string

    remainingMonths: number

    requiredMonthlySaving: number
}


// =====================================================
// 맞춤 추천 정보
// =====================================================

export interface RecommendationInfo {

    category:
        | "SAVING"
        | "BUDGET"
        | "EXPENSE"
        | "GOAL"
        | "FINANCE"

    title: string

    description: string

    priority:
        | "HIGH"
        | "MEDIUM"
        | "LOW"
}


// =====================================================
// 자산관리 상태
// =====================================================

export type FinancialStatus =
    | "ACHIEVABLE"
    | "TIGHT"
    | "SHORTAGE"
    | "COMPLETED"
    | "NO_GOAL"


// =====================================================
// 자산관리 가이드 Response
// =====================================================

export interface AssetAdviceResponse {

    // 최근 3개월 기준 월평균 수입
    monthlyAverageIncome: number

    // 최근 3개월 기준 월평균 지출
    monthlyAverageExpense: number

    // 월평균 여유자금
    monthlySurplus: number

    // 여행 목표
    travelGoal: TravelGoalInfo | null

    // 자산관리 상태
    financialStatus: FinancialStatus

    // 맞춤 추천
    recommendations: RecommendationInfo[]
}


// =====================================================
// Access Token
// =====================================================

const getAccessToken = () => {

    const accessToken =
        localStorage.getItem(
            "accessToken",
        )

    if (!accessToken) {

        throw new Error(
            "로그인 토큰을 확인할 수 없습니다.",
        )
    }

    return accessToken
}


// =====================================================
// 자산관리 가이드 조회
//
// GET /api/assets/advice
// =====================================================

export const getAssetAdvice =
    async (): Promise<AssetAdviceResponse> => {

        const accessToken =
            getAccessToken()


        const response =
            await fetch(
                `${API_BASE_URL}/api/assets/advice`,
                {
                    method: "GET",

                    headers: {

                        Accept:
                            "application/json",

                        Authorization:
                            `Bearer ${accessToken}`,
                    },
                },
            )


    if (!response.ok) {

      const errorText =
        await response.text()

      console.error(
        "Asset Advice API Error =",
        errorText,
      )

      throw new Error(
        "자산관리 가이드를 불러오지 못했습니다.",
      )
    }


    const data: AssetAdviceResponse =
      await response.json()


    return {

      ...data,

      travelGoal:
        data.travelGoal ?? null,

      recommendations:
        data.recommendations ?? [],
    }
  }


// =====================================================
// 여행 목표 등록
//
// POST /api/assets/advice/travel-goal
// =====================================================

export const createTravelGoal =
  async (
    request: TravelGoalRequest,
  ): Promise<number> => {

    const accessToken =
      getAccessToken()


    const response =
      await fetch(
        `${API_BASE_URL}/api/assets/advice/travel-goal`,
        {
          method: "POST",

          headers: {

            Accept:
              "application/json",

            "Content-Type":
              "application/json",

            Authorization:
              `Bearer ${accessToken}`,
          },

          body:
            JSON.stringify(
              request,
            ),
        },
      )


    if (!response.ok) {

      const errorText =
        await response.text()

      console.error(
        "Travel Goal API Error =",
        errorText,
      )

      throw new Error(
        "여행 목표를 등록하지 못했습니다.",
      )
    }


    const goalId: number =
      await response.json()


    return goalId
  }