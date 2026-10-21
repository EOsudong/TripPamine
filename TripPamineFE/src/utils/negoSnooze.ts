// AI 타임 네고 팝업의 일시 숨김 설정은 사용자별·브라우저별로만 저장한다.
// 거래 완료 여부는 반드시 백엔드 AI_NEGO_LOGS에서 판정하며 이 설정으로 결제를 막지 않는다.
const STORAGE_PREFIX = "trippamine:nego:snooze-until:v1:";
export const NEGO_SNOOZE_ONE_HOUR_MS = 60 * 60 * 1000;

export function negoSnoozeStorageKey(userId: number): string {
  return `${STORAGE_PREFIX}${userId}`;
}

export function readNegoSnoozeUntil(userId: number | null): number {
  if (userId == null) return 0;

  try {
    const raw = window.localStorage.getItem(negoSnoozeStorageKey(userId));
    const until = Number(raw);
    return Number.isFinite(until) && until > Date.now() ? until : 0;
  } catch {
    // 저장소 접근이 제한된 브라우저에서는 팝업 기능 자체를 중단하지 않는다.
    return 0;
  }
}

export function saveNegoSnoozeOneHour(userId: number): number {
  const until = Date.now() + NEGO_SNOOZE_ONE_HOUR_MS;
  try {
    window.localStorage.setItem(negoSnoozeStorageKey(userId), String(until));
  } catch {
    // 메모리(ref) 상태는 유지되므로 현재 열린 페이지에서는 숨김이 동작한다.
  }
  return until;
}
