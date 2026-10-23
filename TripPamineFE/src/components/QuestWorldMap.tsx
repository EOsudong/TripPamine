import { useEffect, useMemo, useRef, useState } from "react";
import type { QuestResponse, UserQuestLogResponse } from "../api/quest";
import { usePreciseGeolocation } from "../custom/usePreciseGeolocation";

type QuestMapStatus = "NOT_STARTED" | UserQuestLogResponse["status"];
type QuestRadiusFilter = 1000 | 5000 | 10000 | "ALL";

interface QuestWorldMapProps {
  quests: QuestResponse[];
  logs: UserQuestLogResponse[];
  // null: 내 주변 GPS 모드 / 값 있음: 선택한 지역 또는 챕터 지도 모드.
  focusKey: string | null;
  focusLabel: string | null;
  onQuestSelect: (questId: number) => void;
}

const STATUS_COLOR: Record<QuestMapStatus, string> = {
  NOT_STARTED: "#06b6d4",
  PROGRESS: "#f59e0b",
  FAILED: "#f43f5e",
  SUCCESS: "#10b981",
};

// 내 레벨보다 필요 레벨이 높은(잠긴) 퀘스트는 상태와 무관하게 회색으로 표시한다.
const LOCKED_COLOR = "#64748b";

const SEOUL_CENTER = { latitude: 37.5665, longitude: 126.978 };
const RADIUS_FILTER_OPTIONS: Array<{
  label: string;
  value: QuestRadiusFilter;
}> = [
  { label: "1km", value: 1000 },
  { label: "5km", value: 5000 },
  { label: "10km", value: 10000 },
  { label: "전체", value: "ALL" },
];

function distanceMeters(
    latitude1: number,
    longitude1: number,
    latitude2: number,
    longitude2: number,
) {
  const earthRadius = 6371000;
  const latitudeDelta = ((latitude2 - latitude1) * Math.PI) / 180;
  const longitudeDelta = ((longitude2 - longitude1) * Math.PI) / 180;
  const value =
      Math.sin(latitudeDelta / 2) ** 2 +
      Math.cos((latitude1 * Math.PI) / 180) *
      Math.cos((latitude2 * Math.PI) / 180) *
      Math.sin(longitudeDelta / 2) ** 2;

  return earthRadius * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
}

function formatDistance(distance: number) {
  return distance >= 1000
      ? `${(distance / 1000).toFixed(1)}km`
      : `${Math.round(distance)}m`;
}

export default function QuestWorldMap({
                                        quests,
                                        logs,
                                        focusKey,
                                        focusLabel,
                                        onQuestSelect,
                                      }: QuestWorldMapProps) {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const questOverlaysRef = useRef<any[]>([]);
  const prerequisiteLinesRef = useRef<any[]>([]);
  const userOverlayRef = useRef<any>(null);
  const accuracyCircleRef = useRef<any>(null);
  const centeredOnUserRef = useRef(false);
  const previousFocusKeyRef = useRef<string | null>(null);
  const [mapError, setMapError] = useState<string | null>(null);
  const [mapReady, setMapReady] = useState(false);
  const [radiusFilter, setRadiusFilter] = useState<QuestRadiusFilter>(10000);

  useEffect(() => {
    // 원거리 지역 선택 시 현재 GPS 기준 10km 필터 때문에 마커가 사라지지 않도록 한다.
    setRadiusFilter(focusKey == null ? 10000 : "ALL");
  }, [focusKey]);

  const { latitude, longitude, accuracy, gpsSignalStrength, error } =
      usePreciseGeolocation(true);

  const logByQuestId = useMemo(() => {
    const map = new Map<number, UserQuestLogResponse>();
    logs.forEach((log) => map.set(log.questId, log));
    return map;
  }, [logs]);

  // 지역 탐색 시 반경 기준은 GPS가 아닌 선택 지역의 퀘스트 중심이다.
  // GPS를 거부해도 발표용 지역 지도는 탐색할 수 있다.
  const filterCenter = useMemo(() => {
    if (focusKey != null && quests.length > 0) {
      return {
        lat: quests.reduce((sum, quest) => sum + quest.targetLat, 0) / quests.length,
        lng: quests.reduce((sum, quest) => sum + quest.targetLng, 0) / quests.length,
      };
    }
    return latitude == null || longitude == null ? null : { lat: latitude, lng: longitude };
  }, [focusKey, latitude, longitude, quests]);
  const visibleQuests = useMemo(() => {
    if (radiusFilter === "ALL" || filterCenter == null) return quests;
    return quests.filter((quest) =>
        distanceMeters(filterCenter.lat, filterCenter.lng, quest.targetLat, quest.targetLng) <= radiusFilter,
    );
  }, [quests, radiusFilter, filterCenter]);

  const nearestQuest = useMemo(() => {
    if (visibleQuests.length === 0) return null;
    const candidates = visibleQuests.filter((quest) => !quest.locked);
    const pool = candidates.length > 0 ? candidates : visibleQuests;
    const distanceTo = (quest: QuestResponse) => latitude == null || longitude == null
        ? null
        : distanceMeters(latitude, longitude, quest.targetLat, quest.targetLng);
    if (focusKey != null) {
      // 지역 선택 중에는 서울·부산까지의 직선거리보다 현재 도전 가능한 미션을 우선한다.
      const next = pool.find((quest) => logs.some((log) => log.questId === quest.questId && log.status === "PROGRESS"))
          ?? pool.find((quest) => !logs.some((log) => log.questId === quest.questId && log.status === "SUCCESS"))
          ?? pool[0];
      return { quest: next, distance: distanceTo(next) };
    }
    return pool.map((quest) => ({ quest, distance: distanceTo(quest) }))
        .sort((left, right) => (left.distance ?? Infinity) - (right.distance ?? Infinity))[0];
  }, [latitude, longitude, visibleQuests, focusKey, logs]);

  // 잠금이 풀렸고 아직 완료하지 않은 첫 미션을 지도에서 강조한다.
  const highlightedQuestId = useMemo(() => {
    const available = quests.filter((quest) =>
        !quest.locked && logByQuestId.get(quest.questId)?.status !== "SUCCESS",
    );
    return (available.find((quest) => logByQuestId.get(quest.questId)?.status === "PROGRESS")
        ?? available[0])?.questId ?? null;
  }, [quests, logByQuestId]);

  const hasCurrentLocation = latitude != null && longitude != null;
  const canInitializeMap = hasCurrentLocation || error != null || quests.length > 0;

  useEffect(() => {
    if (!mapContainerRef.current || !canInitializeMap) return;

    if (typeof window.kakao?.maps?.load !== "function") {
      setMapError(
          "Kakao Map SDK를 불러오지 못했습니다. JavaScript 키와 등록 도메인을 확인해주세요.",
      );
      return;
    }

    const kakao = window.kakao;
    let disposed = false;

    setMapReady(false);
    setMapError(null);

    const clearMapObjects = () => {
      questOverlaysRef.current.forEach((overlay) => overlay.setMap(null));
      questOverlaysRef.current = [];
      prerequisiteLinesRef.current.forEach((line) => line.setMap(null));
      prerequisiteLinesRef.current = [];
      userOverlayRef.current?.setMap(null);
      accuracyCircleRef.current?.setMap(null);
      userOverlayRef.current = null;
      accuracyCircleRef.current = null;
      mapRef.current = null;
      centeredOnUserRef.current = false;
    };

    kakao.maps.load(() => {
      if (disposed || !mapContainerRef.current) return;

      try {
        const initialQuest = quests[0];
        const startAtQuest = focusKey != null || !hasCurrentLocation;
        const center = new kakao.maps.LatLng(
            startAtQuest ? (initialQuest?.targetLat ?? SEOUL_CENTER.latitude) : latitude,
            startAtQuest ? (initialQuest?.targetLng ?? SEOUL_CENTER.longitude) : longitude,
        );
        const map = new kakao.maps.Map(mapContainerRef.current, {
          center,
          level: startAtQuest ? 6 : 5,
        });

        mapRef.current = map;
        centeredOnUserRef.current = hasCurrentLocation || focusKey != null;
        setMapReady(true);
        setMapError(null);
      } catch (initializationError) {
        console.error("Kakao Map 초기화 실패:", initializationError);
        clearMapObjects();
        setMapReady(false);
        setMapError("지도를 초기화하지 못했습니다. 잠시 후 다시 시도해주세요.");
      }
    });

    return () => {
      disposed = true;
      clearMapObjects();
    };
  // 초기 SDK 로딩은 한 번만 수행한다. 지역/챕터 변경은 아래 setBounds로만 이동한다.
  }, [canInitializeMap]);

  useEffect(() => {
    if (!mapReady || !mapRef.current || focusKey == null || quests.length === 0) return;
    const kakao = window.kakao;
    const valid = quests.filter((quest) => Number.isFinite(quest.targetLat) && Number.isFinite(quest.targetLng));
    if (valid.length === 0) return;
    const map = mapRef.current;
    if (valid.length === 1) {
      map.setCenter(new kakao.maps.LatLng(valid[0].targetLat, valid[0].targetLng));
      map.setLevel(4);
    } else {
      const bounds = new kakao.maps.LatLngBounds();
      valid.forEach((quest) => bounds.extend(new kakao.maps.LatLng(quest.targetLat, quest.targetLng)));
      map.setBounds(bounds);
    }
    // GPS 신호가 뒤늦게 도착하더라도 선택 지역 포커스가 바뀌지 않게 한다.
    centeredOnUserRef.current = true;
  }, [mapReady, focusKey, quests]);

  useEffect(() => {
    if (!mapReady || !mapRef.current) return;

    const kakao = window.kakao;
    const map = mapRef.current;

    questOverlaysRef.current.forEach((overlay) => overlay.setMap(null));
    prerequisiteLinesRef.current.forEach((line) => line.setMap(null));
    prerequisiteLinesRef.current = [];

    // 서버에서 받은 선행 관계만 지도 위에 잇는다. 직선 연결은 도로 경로가 아니다.
    const pointsById = new Map(visibleQuests.map((item) => [item.questId, item]));
    visibleQuests.forEach((quest) => {
      (quest.prerequisiteQuestIds ?? []).forEach((previousId) => {
        const previous = pointsById.get(previousId);
        if (!previous) return;
        const previousCompleted = logByQuestId.get(previousId)?.status === "SUCCESS";
        const line = new kakao.maps.Polyline({
          path: [
            new kakao.maps.LatLng(previous.targetLat, previous.targetLng),
            new kakao.maps.LatLng(quest.targetLat, quest.targetLng),
          ],
          strokeWeight: 4,
          strokeColor: previousCompleted ? "#10b981" : "#64748b",
          strokeOpacity: previousCompleted ? 0.85 : 0.6,
          strokeStyle: "dashed",
        });
        line.setMap(map);
        prerequisiteLinesRef.current.push(line);
      });
    });

    questOverlaysRef.current = visibleQuests.map((quest) => {
      const status: QuestMapStatus =
          logByQuestId.get(quest.questId)?.status ?? "NOT_STARTED";
      const markerColor = quest.locked ? LOCKED_COLOR : STATUS_COLOR[status];
      const position = new kakao.maps.LatLng(quest.targetLat, quest.targetLng);
      const markerButton = document.createElement("button");
      markerButton.type = "button";
      markerButton.title = quest.locked
          ? `${quest.questName} (${(quest.missingPrerequisiteQuestIds?.length ?? 0) > 0 ? "선행 미완료" : `Lv.${quest.requiredLevel} 필요`})`
          : quest.questName;
      const highlighted = quest.questId === highlightedQuestId;
      markerButton.className = `tp-quest-world-pin ${quest.locked ? "tp-quest-world-pin--locked" : status === "SUCCESS" ? "tp-quest-world-pin--complete" : status === "PROGRESS" || highlighted ? "tp-quest-world-pin--active" : ""}`;
      if (highlighted) markerButton.title += " · 다음 도전 추천";
      markerButton.style.setProperty("--quest-pin-color", markerColor);

      const markerIcon = document.createElement("span");
      markerIcon.textContent = quest.locked
          ? "🔒"
          : status === "SUCCESS"
              ? "✓"
              : highlighted ? "★" : "◆";
      markerIcon.className = "tp-quest-world-pin-icon";
      markerButton.appendChild(markerIcon);
      markerButton.addEventListener("click", () =>
          onQuestSelect(quest.questId),
      );

      const overlay = new kakao.maps.CustomOverlay({
        position,
        content: markerButton,
        yAnchor: 1,
      });
      overlay.setMap(map);
      return overlay;
    });

    return () => {
      questOverlaysRef.current.forEach((overlay) => overlay.setMap(null));
      questOverlaysRef.current = [];
      prerequisiteLinesRef.current.forEach((line) => line.setMap(null));
      prerequisiteLinesRef.current = [];
    };
  }, [mapReady, visibleQuests, logByQuestId, highlightedQuestId, onQuestSelect]);

  useEffect(() => {
    if (!mapReady || latitude == null || longitude == null) return;

    const kakao = window.kakao;
    const map = mapRef.current;
    const position = new kakao.maps.LatLng(latitude, longitude);

    if (!userOverlayRef.current) {
      const userMarker = document.createElement("div");
      userMarker.className = "tp-quest-user-pulse";
      userMarker.setAttribute("aria-label", "내 현재 위치");
      const userCore = document.createElement("span");
      userCore.className = "tp-quest-user-core";
      userMarker.appendChild(userCore);
      userOverlayRef.current = new kakao.maps.CustomOverlay({
        position,
        content: userMarker,
        yAnchor: 0.5,
      });
      userOverlayRef.current.setMap(map);
    } else {
      userOverlayRef.current.setPosition(position);
    }

    if (!accuracyCircleRef.current) {
      accuracyCircleRef.current = new kakao.maps.Circle({
        center: position,
        radius: accuracy ?? 0,
        strokeWeight: 1,
        strokeColor: "#2563eb",
        strokeOpacity: 0.8,
        fillColor: "#3b82f6",
        fillOpacity: 0.12,
      });
      accuracyCircleRef.current.setMap(map);
    } else {
      accuracyCircleRef.current.setPosition(position);
      accuracyCircleRef.current.setRadius(accuracy ?? 0);
    }

    if (!centeredOnUserRef.current && focusKey == null) {
      map.panTo(position);
      centeredOnUserRef.current = true;
    }
  }, [mapReady, latitude, longitude, accuracy, focusKey]);

  useEffect(() => {
    // 지역 모드에서 내 주변으로 돌아올 때만 한 번 GPS 중심으로 이동한다.
    const previous = previousFocusKeyRef.current;
    previousFocusKeyRef.current = focusKey;
    if (previous != null && focusKey == null) {
      // 지역 탐색을 끝낼 때 GPS가 아직 없다면, 나중에 수신되는 좌표로 이동한다.
      centeredOnUserRef.current = false;
      if (mapReady && latitude != null && longitude != null) {
        mapRef.current?.panTo(new window.kakao.maps.LatLng(latitude, longitude));
        centeredOnUserRef.current = true;
      }
    }
  }, [focusKey, mapReady, latitude, longitude]);

  const moveToMyLocation = () => {
    if (latitude == null || longitude == null || !mapRef.current) return;
    const position = new window.kakao.maps.LatLng(latitude, longitude);
    mapRef.current.panTo(position);
  };

  return (
      <section className="mt-8 overflow-hidden rounded-[2rem] border border-cyan-400/15 bg-slate-900 shadow-[0_35px_95px_-35px_rgba(34,211,238,0.26)]">
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 px-5 py-4">
          <div>
            <p className="text-xs font-black tracking-[0.2em] text-cyan-400">
              ADVENTURE MAP
            </p>
            <h2 className="mt-1 text-xl font-black">{focusLabel ? `${focusLabel} · 모험 지도` : "주변 퀘스트 지도"}</h2>
            {focusKey && <p className="mt-1 text-[11px] text-slate-400">선택한 지역의 목적지를 기준으로 표시합니다. GPS 인증은 현장에서만 가능합니다.</p>}
          </div>
          <div
              className="flex rounded-xl border border-slate-700 bg-slate-950 p-1"
              role="group"
              aria-label={focusKey ? "선택 지역 중심 검색 반경" : "내 주변 검색 반경"}
          >
            {RADIUS_FILTER_OPTIONS.map((option) => {
              const selected = radiusFilter === option.value;
              const requiresLocation = option.value !== "ALL" && filterCenter == null;

              return (
                  <button
                      key={option.label}
                      type="button"
                      onClick={() => setRadiusFilter(option.value)}
                      disabled={requiresLocation}
                      aria-pressed={selected}
                      className={`rounded-lg px-3 py-2 text-xs font-black transition disabled:cursor-not-allowed disabled:opacity-40 ${
                          selected
                              ? "bg-cyan-500 text-slate-950"
                              : "text-slate-400 hover:bg-slate-800 hover:text-white"
                      }`}
                  >
                    {option.label}
                  </button>
              );
            })}
          </div>
          <button
              type="button"
              onClick={moveToMyLocation}
              disabled={latitude == null || longitude == null}
              className="rounded-xl border border-cyan-500/30 bg-cyan-500/10 px-4 py-2 text-xs font-black text-cyan-300 transition hover:bg-cyan-500/20 disabled:cursor-not-allowed disabled:opacity-40"
          >
            내 위치로 이동
          </button>
        </div>

        <div className="grid lg:grid-cols-[1fr_280px]">
          <div className="relative h-[440px] bg-slate-800 shadow-[inset_0_8px_22px_rgba(2,6,23,0.32)]">
            <div ref={mapContainerRef} className="h-full w-full" />
            {mapReady && (
                <div className="pointer-events-none absolute left-4 top-4 z-10 rounded-xl border border-cyan-500/30 bg-slate-950/90 px-3 py-2 text-xs font-black text-cyan-300 shadow-2xl backdrop-blur-xl">
                  표시 중 {visibleQuests.length}개
                </div>
            )}
            {!mapReady && !mapError && (
                <div className="absolute inset-0 z-10 flex flex-col items-center justify-center bg-slate-900 text-center">
                  <span className="h-7 w-7 animate-spin rounded-full border-2 border-cyan-400 border-t-transparent" />
                  <p className="mt-3 text-sm font-bold text-slate-300">
                    모험 지도를 준비하고 있습니다.
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    지역을 선택하면 GPS 권한 없이도 모험 지도를 확인할 수 있습니다.
                  </p>
                </div>
            )}
            {mapError && (
                <div className="absolute inset-0 z-10 flex items-center justify-center bg-slate-950/90 p-6 text-center text-sm text-rose-300">
                  {mapError}
                </div>
            )}
            {mapReady && visibleQuests.length === 0 && (
                <div className="absolute bottom-4 left-1/2 z-10 -translate-x-1/2 rounded-xl border border-slate-700 bg-slate-950/90 px-4 py-3 text-center text-xs text-slate-300 shadow-xl">
                  {focusKey ? "선택한 지역 반경에 퀘스트가 없습니다. 전체 버튼을 선택하세요." : "선택한 반경 안에 등록된 퀘스트가 없습니다."}
                </div>
            )}
          </div>

          <aside className="space-y-4 p-5">
            <GpsStatus
                signal={gpsSignalStrength}
                latitude={latitude}
                longitude={longitude}
                accuracy={accuracy}
                error={error}
            />

            {nearestQuest && (
                <button
                    type="button"
                    onClick={() => onQuestSelect(nearestQuest.quest.questId)}
                    className="w-full rounded-2xl border border-cyan-500/20 bg-cyan-500/10 p-4 text-left transition hover:bg-cyan-500/20"
                >
              <span className="text-[10px] font-black text-cyan-400">
                {focusKey ? "NEXT REGIONAL MISSION" : "NEAREST QUEST"}
              </span>
                  <strong className="mt-1 block text-sm text-white">
                    {nearestQuest.quest.locked ? "🔒 " : ""}
                    {nearestQuest.quest.questName}
                  </strong>
                  <span className="mt-2 block text-xs text-slate-400">
                {nearestQuest.distance == null ? "GPS 위치 정보 없이 지역 탐색 중" : `내 현재 위치에서 직선거리 약 ${formatDistance(nearestQuest.distance)}`}
                    {nearestQuest.quest.locked
                        ? ` · Lv.${nearestQuest.quest.requiredLevel} 필요`
                        : ""}
              </span>
                </button>
            )}

            <p className="text-[11px] leading-5 text-slate-500">점선은 선행 퀘스트 연결을 뜻하며 실제 도로 이동 경로가 아닙니다.</p>
            <div className="grid grid-cols-2 gap-2 text-[11px]">
              <Legend color={STATUS_COLOR.NOT_STARTED} label="도전 가능" />
              <Legend color={STATUS_COLOR.PROGRESS} label="진행 중" />
              <Legend color={STATUS_COLOR.FAILED} label="재도전" />
              <Legend color={STATUS_COLOR.SUCCESS} label="완료" />
              <Legend color={LOCKED_COLOR} label="레벨·선행 잠김" />
            </div>
          </aside>
        </div>
      </section>
  );
}

function GpsStatus({
                     signal,
                     latitude,
                     longitude,
                     accuracy,
                     error,
                   }: {
  signal: "EXCELLENT" | "GOOD" | "POOR" | "SEARCHING";
  latitude: number | null;
  longitude: number | null;
  accuracy: number | null;
  error: string | null;
}) {
  const signalLabel = {
    EXCELLENT: "매우 좋음",
    GOOD: "양호",
    POOR: "부정확",
    SEARCHING: "탐색 중",
  }[signal];

  return (
      <div className="rounded-2xl border border-slate-800 bg-slate-950 p-4 text-xs">
        <div className="flex items-center justify-between">
          <span className="font-bold text-slate-400">GPS 수신 상태</span>
          <strong className="text-cyan-300">{signalLabel}</strong>
        </div>
        {error ? (
            <p className="mt-3 leading-relaxed text-rose-400">{error}</p>
        ) : latitude == null || longitude == null ? (
            <p className="mt-3 text-slate-500">현재 위치를 찾고 있습니다.</p>
        ) : (
            <div className="mt-3 space-y-1 text-slate-400">
              <p>위도: {latitude.toFixed(6)}</p>
              <p>경도: {longitude.toFixed(6)}</p>
              <p>오차 반경: ±{Math.round(accuracy ?? 0)}m</p>
            </div>
        )}
      </div>
  );
}

function Legend({ color, label }: { color: string; label: string }) {
  return (
      <div className="flex items-center gap-2 rounded-xl bg-slate-950 px-3 py-2 text-slate-400">
      <span
          className="h-2.5 w-2.5 rounded-full"
          style={{ backgroundColor: color }}
      />
        {label}
      </div>
  );
}
