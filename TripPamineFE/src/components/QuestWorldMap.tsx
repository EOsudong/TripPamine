import { useEffect, useMemo, useRef, useState } from "react";
import type { QuestResponse, UserQuestLogResponse } from "../api/quest";
import { usePreciseGeolocation } from "../custom/usePreciseGeolocation";

type QuestMapStatus = "NOT_STARTED" | UserQuestLogResponse["status"];
type QuestRadiusFilter = 1000 | 5000 | 10000 | "ALL";

interface QuestWorldMapProps {
  quests: QuestResponse[];
  logs: UserQuestLogResponse[];
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
                                        onQuestSelect,
                                      }: QuestWorldMapProps) {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const questOverlaysRef = useRef<any[]>([]);
  const prerequisiteLinesRef = useRef<any[]>([]);
  const userOverlayRef = useRef<any>(null);
  const accuracyCircleRef = useRef<any>(null);
  const centeredOnUserRef = useRef(false);
  const [mapError, setMapError] = useState<string | null>(null);
  const [mapReady, setMapReady] = useState(false);
  const [radiusFilter, setRadiusFilter] = useState<QuestRadiusFilter>(10000);

  const { latitude, longitude, accuracy, gpsSignalStrength, error } =
      usePreciseGeolocation(true);

  const logByQuestId = useMemo(() => {
    const map = new Map<number, UserQuestLogResponse>();
    logs.forEach((log) => map.set(log.questId, log));
    return map;
  }, [logs]);

  const visibleQuests = useMemo(() => {
    if (radiusFilter === "ALL" || latitude == null || longitude == null) {
      return quests;
    }

    return quests.filter(
        (quest) =>
            distanceMeters(latitude, longitude, quest.targetLat, quest.targetLng) <=
            radiusFilter,
    );
  }, [latitude, longitude, quests, radiusFilter]);

  const nearestQuest = useMemo(() => {
    if (latitude == null || longitude == null || visibleQuests.length === 0) {
      return null;
    }

    const candidates = visibleQuests.filter((quest) => !quest.locked);
    return (candidates.length > 0 ? candidates : visibleQuests)
        .map((quest) => ({
          quest,
          distance: distanceMeters(
              latitude,
              longitude,
              quest.targetLat,
              quest.targetLng,
          ),
        }))
        .sort((left, right) => left.distance - right.distance)[0];
  }, [latitude, longitude, visibleQuests]);

  const hasCurrentLocation = latitude != null && longitude != null;
  const canInitializeMap = hasCurrentLocation || error != null;

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
        const center = new kakao.maps.LatLng(
            hasCurrentLocation
                ? latitude
                : (initialQuest?.targetLat ?? SEOUL_CENTER.latitude),
            hasCurrentLocation
                ? longitude
                : (initialQuest?.targetLng ?? SEOUL_CENTER.longitude),
        );
        const map = new kakao.maps.Map(mapContainerRef.current, {
          center,
          level: hasCurrentLocation ? 5 : 7,
        });

        mapRef.current = map;
        centeredOnUserRef.current = hasCurrentLocation;
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
  }, [canInitializeMap, hasCurrentLocation, quests]);

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
      markerButton.className = `tp-quest-world-pin ${quest.locked ? "tp-quest-world-pin--locked" : status === "SUCCESS" ? "tp-quest-world-pin--complete" : status === "PROGRESS" ? "tp-quest-world-pin--active" : ""}`;
      markerButton.style.setProperty("--quest-pin-color", markerColor);

      const markerIcon = document.createElement("span");
      markerIcon.textContent = quest.locked
          ? "🔒"
          : status === "SUCCESS"
              ? "✓"
              : "◆";
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
  }, [mapReady, visibleQuests, logByQuestId, onQuestSelect]);

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

    if (!centeredOnUserRef.current) {
      map.panTo(position);
      centeredOnUserRef.current = true;
    }
  }, [mapReady, latitude, longitude, accuracy]);

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
            <h2 className="mt-1 text-xl font-black">주변 퀘스트 지도</h2>
          </div>
          <div
              className="flex rounded-xl border border-slate-700 bg-slate-950 p-1"
              role="group"
              aria-label="퀘스트 검색 반경"
          >
            {RADIUS_FILTER_OPTIONS.map((option) => {
              const selected = radiusFilter === option.value;
              const requiresLocation = option.value !== "ALL";

              return (
                  <button
                      key={option.label}
                      type="button"
                      onClick={() => setRadiusFilter(option.value)}
                      disabled={requiresLocation && !hasCurrentLocation}
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
                    현재 위치를 확인하고 있습니다.
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    위치 권한을 허용하면 내 주변부터 지도가 열립니다.
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
                  선택한 반경 안에 등록된 퀘스트가 없습니다.
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
                NEAREST QUEST
              </span>
                  <strong className="mt-1 block text-sm text-white">
                    {nearestQuest.quest.locked ? "🔒 " : ""}
                    {nearestQuest.quest.questName}
                  </strong>
                  <span className="mt-2 block text-xs text-slate-400">
                현재 위치에서 약 {formatDistance(nearestQuest.distance)}
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
