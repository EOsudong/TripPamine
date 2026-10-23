import { apiRequest } from "./client"

// ===================== 퀘스트 관리 =====================
// 레벨/EXP/챕터/난이도 관련 필드 추가.
// ../types/api.ts의 AdminQuest/QuestUpsertRequest와 구조가 동일해야 한다(기존 코드베이스
// 컨벤션 - 컴포넌트는 ../types/api.ts의 타입을 쓰고, 이 파일의 함수 시그니처는 구조적으로
// 호환되는 자체 타입을 쓴다).
export type QuestType = "LOCATION" | "DISCOVERY" | "PHOTO" | "ACTION" | "CHAIN"

export interface AdminQuest {
    questId: number
    questName: string
    description: string | null
    targetLat: number
    targetLng: number
    rewardPoint: number
    rewardExp: number
    clearRadius: number
    questType: QuestType
    difficulty: number
    imageUrl: string | null
    thumbnailUrl: string | null
    chapterId: number | null
    chapterName: string | null
    requiredLevel: number
    isActive: boolean
    locked: boolean
}

export interface QuestUpsertRequest {
    questName: string
    description?: string
    targetLat: number
    targetLng: number
    rewardPoint?: number
    rewardExp?: number
    clearRadius?: number
    questType?: QuestType
    difficulty?: number
    imageUrl?: string
    thumbnailUrl?: string
    chapterId?: number
    requiredLevel?: number
    isActive?: boolean
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

// ===================== 독립 퀘스트 선행 관계 관리 =====================
export interface QuestPrerequisiteInfo {
    prerequisiteQuestId: number
    prerequisiteQuestName: string
    isActive: boolean
}

export function getQuestPrerequisites(questId: number): Promise<QuestPrerequisiteInfo[]> {
    return apiRequest<QuestPrerequisiteInfo[]>(`/admin/quests/${questId}/prerequisites`)
}

export function addQuestPrerequisite(questId: number, prerequisiteQuestId: number): Promise<QuestPrerequisiteInfo[]> {
    return apiRequest<QuestPrerequisiteInfo[]>(`/admin/quests/${questId}/prerequisites`, {
        method: "POST",
        body: { prerequisiteQuestId },
    })
}

export function deleteQuestPrerequisite(questId: number, prerequisiteQuestId: number): Promise<void> {
    return apiRequest<void>(`/admin/quests/${questId}/prerequisites/${prerequisiteQuestId}`, { method: "DELETE" })
}

// ===================== 퀘스트 챕터 관리 =====================

export interface AdminQuestChapter {
    chapterId: number
    chapterName: string
    description: string | null
    region: string | null
    orderNo: number
    requiredLevel: number
    imageUrl: string | null
    locked: boolean
}

export interface QuestChapterUpsertRequest {
    chapterName: string
    description?: string
    region?: string
    orderNo?: number
    requiredLevel?: number
    imageUrl?: string
}

// GET /admin/quests/chapters - 챕터 목록 조회 (관리자용)
export function getChapterList(): Promise<AdminQuestChapter[]> {
    return apiRequest<AdminQuestChapter[]>("/admin/quests/chapters")
}

// POST /admin/quests/chapters - 챕터 등록
export function createChapter(request: QuestChapterUpsertRequest): Promise<AdminQuestChapter> {
    return apiRequest<AdminQuestChapter>("/admin/quests/chapters", {
        method: "POST",
        body: request,
    })
}

// PUT /admin/quests/chapters/{chapterId} - 챕터 수정
export function updateChapter(
    chapterId: number,
    request: QuestChapterUpsertRequest,
): Promise<AdminQuestChapter> {
    return apiRequest<AdminQuestChapter>(`/admin/quests/chapters/${chapterId}`, {
        method: "PUT",
        body: request,
    })
}

// DELETE /admin/quests/chapters/{chapterId} - 챕터 삭제.
// 이 챕터에 배정된 퀘스트가 남아있으면 백엔드가 400을 반환한다.
export function deleteChapter(chapterId: number): Promise<void> {
    return apiRequest<void>(`/admin/quests/chapters/${chapterId}`, { method: "DELETE" })
}
