import { apiRequest } from "./client"

// ===================== 연쇄 퀘스트 단계 관리 =====================
// [RPG 확장] 연쇄(CHAIN) 퀘스트의 단계(QUEST_STEPS) 마스터 데이터 관리자 CRUD.
// 퀘스트에 종속된 리소스라 모든 경로가 questId를 포함한다.
// ../types/api.ts의 AdminQuestStep/QuestStepUpsertRequest와 구조가 동일해야 한다(기존
// 코드베이스 컨벤션 - 컴포넌트는 ../types/api.ts의 타입을 쓰고, 이 파일의 함수 시그니처는
// 구조적으로 호환되는 자체 타입을 쓴다).
export interface AdminQuestStep {
    stepId: number
    questId: number
    stepOrder: number
    stepName: string
    description: string | null
    targetLat: number
    targetLng: number
    clearRadius: number
    cleared: boolean
}

export interface QuestStepUpsertRequest {
    stepOrder?: number
    stepName: string
    description?: string
    targetLat: number
    targetLng: number
    clearRadius?: number
}

// GET /admin/quests/{questId}/steps - 단계 목록 조회 (관리자용, 순서대로)
export function getStepList(questId: number): Promise<AdminQuestStep[]> {
    return apiRequest<AdminQuestStep[]>(`/admin/quests/${questId}/steps`)
}

// POST /admin/quests/{questId}/steps - 단계 등록
export function createStep(
    questId: number,
    request: QuestStepUpsertRequest,
): Promise<AdminQuestStep> {
    return apiRequest<AdminQuestStep>(`/admin/quests/${questId}/steps`, {
        method: "POST",
        body: request,
    })
}

// PUT /admin/quests/{questId}/steps/{stepId} - 단계 수정
export function updateStep(
    questId: number,
    stepId: number,
    request: QuestStepUpsertRequest,
): Promise<AdminQuestStep> {
    return apiRequest<AdminQuestStep>(`/admin/quests/${questId}/steps/${stepId}`, {
        method: "PUT",
        body: request,
    })
}

// DELETE /admin/quests/{questId}/steps/{stepId} - 단계 삭제.
// 이미 유저가 이 단계를 클리어한 기록(USER_QUEST_STEP_LOGS)이 남아있으면 백엔드가 400을 반환한다.
export function deleteStep(questId: number, stepId: number): Promise<void> {
    return apiRequest<void>(`/admin/quests/${questId}/steps/${stepId}`, {
        method: "DELETE",
    })
}
