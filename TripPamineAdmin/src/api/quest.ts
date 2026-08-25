import { apiRequest } from "./client"

// ===================== 퀘스트 관리 =====================
export interface AdminQuest {
    questId: number
    questName: string
    targetLat: number
    targetLng: number
    rewardPoint: number
    clearRadius: number
}

export interface QuestUpsertRequest {
    questName: string
    targetLat: number
    targetLng: number
    rewardPoint?: number
    clearRadius?: number
}

// GET /admin/quests - 퀘스트 목록 조회 (관리자용). 페이징 없이 전체 반환.
export function getQuestList(): Promise<AdminQuest[]> {
    return apiRequest<AdminQuest[]>("/admin/quests")
}

// POST /admin/quests - 퀘스트 등록
export function createQuest(request: QuestUpsertRequest): Promise<AdminQuest> {
    return apiRequest<AdminQuest>("/admin/quests", {
        method: "POST",
        body: request,
    })
}

// PUT /admin/quests/{questId} - 퀘스트 수정
export function updateQuest(questId: number, request: QuestUpsertRequest): Promise<AdminQuest> {
    return apiRequest<AdminQuest>(`/admin/quests/${questId}`, {
        method: "PUT",
        body: request,
    })
}

// DELETE /admin/quests/{questId} - 퀘스트 삭제.
// 이미 진행/완료한 유저 기록(USER_QUEST_LOGS)이 남아있으면 백엔드가 409를 반환하고
// "이 퀘스트를 완료했거나 진행 중인 사용자가 있어 삭제할 수 없습니다." 메시지를 내려준다.
// (하드 삭제로 유저의 클리어/포인트 지급 이력이 사라지는 걸 막기 위한 서버 정책)
export function deleteQuest(questId: number): Promise<void> {
    return apiRequest<void>(`/admin/quests/${questId}`, { method: "DELETE" })
}
