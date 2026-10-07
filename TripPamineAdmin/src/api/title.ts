import { apiRequest } from "./client"

// ===================== 칭호 관리 =====================
// [RPG 확장] 칭호(TITLES) 마스터 데이터 관리자 CRUD.
// ../types/api.ts의 AdminTitle/TitleUpsertRequest와 구조가 동일해야 한다(기존 코드베이스
// 컨벤션 - 컴포넌트는 ../types/api.ts의 타입을 쓰고, 이 파일의 함수 시그니처는 구조적으로
// 호환되는 자체 타입을 쓴다).
export type TitleConditionType = "FIRST_QUEST_CLEAR" | "QUEST_COUNT" | "LEVEL_REACHED"

export interface AdminTitle {
    titleId: number
    titleName: string
    description: string | null
    iconUrl: string | null
    requiredConditionType: TitleConditionType
    requiredConditionValue: number | null
    earned: boolean
    earnedAt: string | null
    equipped: boolean
}

export interface TitleUpsertRequest {
    titleName: string
    description?: string
    iconUrl?: string
    requiredConditionType: TitleConditionType
    requiredConditionValue?: number
}

// GET /admin/titles - 칭호 목록 조회 (관리자용)
export function getTitleList(): Promise<AdminTitle[]> {
    return apiRequest<AdminTitle[]>("/admin/titles")
}

// POST /admin/titles - 칭호 등록
export function createTitle(request: TitleUpsertRequest): Promise<AdminTitle> {
    return apiRequest<AdminTitle>("/admin/titles", {
        method: "POST",
        body: request,
    })
}

// PUT /admin/titles/{titleId} - 칭호 수정
export function updateTitle(titleId: number, request: TitleUpsertRequest): Promise<AdminTitle> {
    return apiRequest<AdminTitle>(`/admin/titles/${titleId}`, {
        method: "PUT",
        body: request,
    })
}

// DELETE /admin/titles/{titleId} - 칭호 삭제.
// 이미 이 칭호를 획득했거나(USER_TITLES) 현재 장착 중인(USER_RPG_STATS.CURRENT_TITLE_ID) 유저가
// 있으면 백엔드가 400을 반환한다.
export function deleteTitle(titleId: number): Promise<void> {
    return apiRequest<void>(`/admin/titles/${titleId}`, { method: "DELETE" })
}
