import { apiRequest } from "./client"

// ===================== 마스코트 관리 =====================
// 백엔드 mascot/controller/AdminMascotController.java와 1:1로 맞춘 타입 및 API 함수.
// 지역(Region) → 마스코트(Mascot) → 스폰 지점(MascotSpawnPoint) 순서로 등록한다.

export type MascotRarity = "COMMON" | "RARE" | "EPIC"

export const MASCOT_RARITY_LABEL: Record<MascotRarity, string> = {
    COMMON: "일반",
    RARE: "희귀",
    EPIC: "에픽",
}

// ----- 지역 -----
export interface AdminRegion {
    regionId: number
    regionName: string
    sidoCode: string | null
}

export interface RegionUpsertRequest {
    regionName: string
    sidoCode?: string
}

// ----- 마스코트 -----
export interface AdminMascot {
    mascotId: number
    regionId: number
    regionName: string
    mascotName: string
    rarity: MascotRarity
    imageUrl: string | null
    modelUrl: string | null
    rewardPoint: number
}

export interface MascotUpsertRequest {
    regionId: number
    mascotName: string
    rarity?: MascotRarity
    imageUrl?: string
    modelUrl?: string
    rewardPoint?: number
}

// ----- 스폰 지점 -----
// 관리자 목록 조회 응답은 사용자 지도용 DTO(MascotSpawnPointResponse)를 그대로 쓰기 때문에
// alreadyCaught는 관리자 조회에서 항상 false다.
// linkedQuestId / activeYn은 이번 백엔드 보완에서 응답에 추가된 필드다.
export interface AdminSpawnPoint {
    linkedQuestId: number | null
    activeYn: "Y" | "N"
    spawnId: number
    spotName: string | null
    targetLat: number
    targetLng: number
    catchRadius: number
    mascotId: number
    mascotName: string
    mascotRarity: MascotRarity
    mascotImageUrl: string | null
    alreadyCaught: boolean
}

export interface SpawnPointUpsertRequest {
    mascotId: number
    spotName?: string
    targetLat: number
    targetLng: number
    catchRadius?: number
    linkedQuestId?: number
    activeYn?: "Y" | "N"
}

// GET /admin/mascots/regions
export function getRegionList(): Promise<AdminRegion[]> {
    return apiRequest<AdminRegion[]>("/admin/mascots/regions")
}

// POST /admin/mascots/regions
export function createRegion(request: RegionUpsertRequest): Promise<AdminRegion> {
    return apiRequest<AdminRegion>("/admin/mascots/regions", { method: "POST", body: request })
}

// PUT /admin/mascots/regions/{regionId}
export function updateRegion(regionId: number, request: RegionUpsertRequest): Promise<AdminRegion> {
    return apiRequest<AdminRegion>(`/admin/mascots/regions/${regionId}`, { method: "PUT", body: request })
}

// DELETE /admin/mascots/regions/{regionId} - 소속 마스코트가 있으면 409
export function deleteRegion(regionId: number): Promise<void> {
    return apiRequest<void>(`/admin/mascots/regions/${regionId}`, { method: "DELETE" })
}

// GET /admin/mascots
export function getMascotList(): Promise<AdminMascot[]> {
    return apiRequest<AdminMascot[]>("/admin/mascots")
}

// POST /admin/mascots
export function createMascot(request: MascotUpsertRequest): Promise<AdminMascot> {
    return apiRequest<AdminMascot>("/admin/mascots", { method: "POST", body: request })
}

// PUT /admin/mascots/{mascotId}
export function updateMascot(mascotId: number, request: MascotUpsertRequest): Promise<AdminMascot> {
    return apiRequest<AdminMascot>(`/admin/mascots/${mascotId}`, { method: "PUT", body: request })
}

// DELETE /admin/mascots/{mascotId} - 포획 기록 또는 스폰 지점이 남아 있으면 409
export function deleteMascot(mascotId: number): Promise<void> {
    return apiRequest<void>(`/admin/mascots/${mascotId}`, { method: "DELETE" })
}

// GET /admin/mascots/spawns
export function getSpawnPointList(): Promise<AdminSpawnPoint[]> {
    return apiRequest<AdminSpawnPoint[]>("/admin/mascots/spawns")
}

// POST /admin/mascots/spawns
export function createSpawnPoint(request: SpawnPointUpsertRequest): Promise<AdminSpawnPoint> {
    return apiRequest<AdminSpawnPoint>("/admin/mascots/spawns", { method: "POST", body: request })
}

// PUT /admin/mascots/spawns/{spawnId}
export function updateSpawnPoint(spawnId: number, request: SpawnPointUpsertRequest): Promise<AdminSpawnPoint> {
    return apiRequest<AdminSpawnPoint>(`/admin/mascots/spawns/${spawnId}`, { method: "PUT", body: request })
}

// DELETE /admin/mascots/spawns/{spawnId}
export function deleteSpawnPoint(spawnId: number): Promise<void> {
    return apiRequest<void>(`/admin/mascots/spawns/${spawnId}`, { method: "DELETE" })
}
