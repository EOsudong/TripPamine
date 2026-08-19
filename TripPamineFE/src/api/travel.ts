import { api } from "./axios"
import type { TravelPlan } from "../types"


export interface TravelPlanRequest {
    planName: string
    totalBudget: number
    companionType: string | null
    blindYn: string
    startDate: string | null
    endDate: string | null
}

/** 여행 계획 목록 조회 */
export const getTravelPlansApi = async (): Promise<TravelPlan[]> => {
    const response = await api.get<TravelPlan[]>("/travel-plans")
    return response.data
}

/** 여행 계획 등록 */
export const createTravelPlanApi = async (data: TravelPlanRequest) => {
    const response = await api.post("/travel-plans", data)
    return response.data
}

/** 여행 계획 수정 */
export const updateTravelPlanApi = async (planId: number, data: TravelPlanRequest) => {
    const response = await api.put(`/travel-plans/${planId}`, data)
    return response.data
}

/** 여행 계획 삭제 */
export const deleteTravelPlanApi = async (planId: number) => {
    const response = await api.delete(`/travel-plans/${planId}`)
    return response.data
}