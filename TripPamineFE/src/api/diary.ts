import { api } from "./axios"

export interface DiaryKeyword {
    keywordId: number
    keywordType: "EMOTION" | "EXPEND" | "PLACE"
    keywordName: string
}

export interface TravelDiary {
    diaryId: number
    planId: number
    aiTitle: string
    aiContent: string
    dopamineScore: number
    aiImageUrl: string | null
    shareYn: string
    createDate: string | null
    keywords: DiaryKeyword[]
}

export const getDiariesApi = async (): Promise<TravelDiary[]> => {
    const response = await api.get<TravelDiary[]>("/diaries")
    return response.data
}

export const generateDiaryApi = async (planId: number): Promise<TravelDiary> => {
    const response = await api.post<TravelDiary>("/diaries/generate", null, { params: { planId } })
    return response.data
}

export const toggleDiaryShareApi = async (diaryId: number): Promise<TravelDiary> => {
    const response = await api.patch<TravelDiary>(`/diaries/${diaryId}/share`)
    return response.data
}

export const deleteDiaryApi = async (diaryId: number) => {
    await api.delete(`/diaries/${diaryId}`)
}

export type DiaryKeywordType = DiaryKeyword["keywordType"]

export interface DiaryKeywordRequest {
    keywordType: DiaryKeywordType
    keywordName: string
}

export interface DiaryUpdateRequest {
    aiTitle: string
    aiContent: string
    keywords?: DiaryKeywordRequest[]
}

export const updateDiaryApi = async (diaryId: number, data: DiaryUpdateRequest): Promise<TravelDiary> => {
    const response = await api.put<TravelDiary>(`/diaries/${diaryId}`, data)
    return response.data
}