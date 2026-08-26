import { api } from "./axios"
import type { FinancialBankType, FinancialProduct, FinancialRecommendation } from "../types"

// 백엔드 /finance/* 엔드포인트 호출.
// 로그인 여부와 무관하게(비로그인 사용자도) 조회 가능한 공개 API입니다 (SecurityConfig의 "/finance/**" permitAll 참고).

/** 정기예금 상품 목록 조회. bankType: "bank"(시중은행, 기본값) | "savings"(저축은행) */
export const getDepositProductsApi = async (
    bankType: FinancialBankType = "bank",
): Promise<FinancialProduct[]> => {
    const response = await api.get<FinancialProduct[]>("/finance/deposits", {
        params: { bankType },
    })
    return response.data
}

/** 적금 상품 목록 조회. bankType: "bank"(시중은행, 기본값) | "savings"(저축은행) */
export const getSavingProductsApi = async (
    bankType: FinancialBankType = "bank",
): Promise<FinancialProduct[]> => {
    const response = await api.get<FinancialProduct[]>("/finance/savings", {
        params: { bankType },
    })
    return response.data
}

export interface FinancialRecommendationParams {
    productType: "deposit" | "saving"
    bankType?: FinancialBankType
    months?: number
    amount?: number
    limit?: number
}

/**
 * 예치금액/기간 조건에 맞는 금융상품 추천 조회 (규칙 기반, 외부 AI 미사용).
 * FinancialRecommendationPanel.tsx의 "맞춤 추천받기" 폼에서 호출합니다.
 */
export const getFinancialRecommendationsApi = async (
    params: FinancialRecommendationParams,
): Promise<FinancialRecommendation[]> => {
    const response = await api.get<FinancialRecommendation[]>("/finance/recommendations", {
        params: {
            productType: params.productType,
            bankType: params.bankType ?? "bank",
            months: params.months,
            amount: params.amount,
            limit: params.limit,
        },
    })
    return response.data
}
