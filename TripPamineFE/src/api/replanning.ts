import { api } from "./axios"

export interface ReplanningRequest {
  remainingPlaces: string[]
  currentLocation: string
  weather: string
  delayMinutes: number
}
export interface ReplannedPlace {
  name: string
  description: string
  estimatedCost: number
}
export interface ReplanningResponse {
  planId: number
  totalBudget: number
  spentAmount: number
  remainingBudget: number
  previewId: string
  originalPlaces: string[]

  proposal: {
    reason: string
    places: ReplannedPlace[]
    estimatedTotal: number
    cautions?: string[]
  }
}
export async function previewReplanning(planId: number, payload: ReplanningRequest): Promise<ReplanningResponse> {
  const { data } = await api.post<ReplanningResponse>(`/api/travel-replanning/${planId}/preview`, payload)
  return data
}
