import api, { API_BASE_URL } from "./axios";

// 백엔드 mascot/controller/MascotController.java와 1:1로 맞춘 타입 및 API 함수.
//
// 흐름: 주변 스폰 조회(GET /mascots/nearby) → 반경 안에서 조우(POST /mascots/spawns/{id}/encounter)
//      → 포획 미니게임(웹 또는 안드로이드 AR) → 점수와 함께 포획 확정(POST /mascots/spawns/{id}/catch)
//
// 재포획 정책: 같은 마스코트를 여러 번 잡을 수 있지만, 포인트는 첫 포획(firstCatch=true)에만 지급된다.
// 포획 성공 여부는 클라이언트가 아니라 서버가 희귀도 + 미니게임 점수로 판정한다.

export type MascotRarity = "COMMON" | "RARE" | "EPIC";

export const MASCOT_RARITY_LABEL: Record<MascotRarity, string> = {
  COMMON: "일반",
  RARE: "희귀",
  EPIC: "에픽",
};

// 지도·카드에서 쓰는 희귀도 색상
export const MASCOT_RARITY_COLOR: Record<MascotRarity, string> = {
  COMMON: "#94a3b8",
  RARE: "#38bdf8",
  EPIC: "#e879f9",
};

// GET /mascots/nearby
export interface MascotSpawnPointResponse {
  spawnId: number;
  spotName: string | null;
  targetLat: number;
  targetLng: number;
  catchRadius: number;
  mascotId: number;
  mascotName: string;
  mascotRarity: MascotRarity;
  mascotImageUrl: string | null;
  alreadyCaught: boolean;
  linkedQuestId: number | null;
  activeYn: "Y" | "N";
}

// POST /mascots/spawns/{spawnId}/encounter
export interface MascotEncounterRequest {
  currentLat: number;
  currentLng: number;
  accuracyMeters?: number;
}

export interface MascotEncounterResponse {
  encounterToken: string;
  expiresInSeconds: number;
  maxAttempts: number;
  spawnId: number;
  mascotId: number;
  mascotName: string;
  mascotRarity: MascotRarity;
  mascotImageUrl: string | null;
  rewardPoint: number;
  alreadyCollected: boolean;
}

// POST /mascots/spawns/{spawnId}/catch
export interface MascotCatchRequest {
  encounterToken: string;
  score: number; // 0~100
}

export interface MascotCatchResponse {
  caught: boolean;
  firstCatch: boolean;
  catchCount: number;
  remainingAttempts: number;
  mascotId: number;
  mascotName: string;
  rewardPoint: number;
  totalPoints: number;
}

// GET /mascots/dex
export interface MascotDexEntry {
  mascotId: number;
  mascotName: string;
  rarity: MascotRarity;
  rarityLabel: string;
  imageUrl: string | null;
  rewardPoint: number;
  collected: boolean;
  catchCount: number;
  firstCaughtAt: string | null;
  lastCaughtAt: string | null;
}

export interface MascotRegionDex {
  regionId: number;
  regionName: string;
  totalCount: number;
  collectedCount: number;
  mascots: MascotDexEntry[];
}

export interface MascotDexResponse {
  totalCount: number;
  collectedCount: number;
  completionRate: number;
  regions: MascotRegionDex[];
}

export const mascotApi = {
  getNearby: async (params?: {
    lat?: number;
    lng?: number;
    radiusKm?: number;
  }): Promise<MascotSpawnPointResponse[]> => {
    const response = await api.get<MascotSpawnPointResponse[]>("/mascots/nearby", {
      params,
    });
    return response.data;
  },

  encounter: async (
    spawnId: number,
    request: MascotEncounterRequest,
  ): Promise<MascotEncounterResponse> => {
    const response = await api.post<MascotEncounterResponse>(
      `/mascots/spawns/${spawnId}/encounter`,
      request,
    );
    return response.data;
  },

  catchMascot: async (
    spawnId: number,
    request: MascotCatchRequest,
  ): Promise<MascotCatchResponse> => {
    const response = await api.post<MascotCatchResponse>(
      `/mascots/spawns/${spawnId}/catch`,
      request,
    );
    return response.data;
  },

  getDex: async (): Promise<MascotDexResponse> => {
    const response = await api.get<MascotDexResponse>("/mascots/dex");
    return response.data;
  },
};

// 마스코트 이미지는 백엔드 정적 리소스(/mascots/xxx.png)로 제공되므로
// 상대 경로면 API 서버 주소를 붙인다. (http로 시작하는 외부 URL은 그대로 사용)
export function resolveMascotImageUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  if (/^(https?:)?\/\//.test(url) || url.startsWith("data:")) return url;
  return `${API_BASE_URL}${url.startsWith("/") ? "" : "/"}${url}`;
}

export function extractMascotErrorMessage(err: unknown, fallback: string): string {
  const anyErr = err as { response?: { data?: { message?: string } } };
  return anyErr?.response?.data?.message ?? fallback;
}

export function extractMascotErrorStatus(err: unknown): number | null {
  const anyErr = err as { response?: { status?: number } };
  return anyErr?.response?.status ?? null;
}

// ===================== 안드로이드 AR 포획 브릿지 =====================
// 안드로이드 WebView 앱(TripPamineAD)에서 실행 중이면 window.androidBridge.startArCatch(...)로
// 네이티브 의사 AR 화면(ArCatchActivity)을 띄우고, 미니게임이 끝나면 네이티브가
// window.onArCatchResult({ score }) 를 호출해 점수를 돌려준다.
// 포획 확정 API(/catch)는 JWT를 가진 웹에서 호출한다.
// 브릿지 메서드가 없으면(PC 브라우저, AR 미구현 앱) 웹 미니게임으로 진행한다.

interface AndroidBridge {
  startArCatch?: (payloadJson: string) => void;
}

declare global {
  interface Window {
    androidBridge?: AndroidBridge;
    onArCatchResult?: (result: { score: number } | string) => void;
  }
}

export function hasAndroidArCatch(): boolean {
  return typeof window.androidBridge?.startArCatch === "function";
}

export function startAndroidArCatch(
  encounter: MascotEncounterResponse,
  onScore: (score: number) => void,
): void {
  window.onArCatchResult = (result) => {
    const parsed = typeof result === "string" ? (JSON.parse(result) as { score: number }) : result;
    onScore(Math.max(0, Math.min(100, Math.round(Number(parsed?.score) || 0))));
  };

  window.androidBridge?.startArCatch?.(
    JSON.stringify({
      spawnId: encounter.spawnId,
      mascotName: encounter.mascotName,
      mascotRarity: encounter.mascotRarity,
      imageUrl: resolveMascotImageUrl(encounter.mascotImageUrl),
    }),
  );
}
