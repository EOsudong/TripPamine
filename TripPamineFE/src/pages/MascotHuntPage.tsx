import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import AppLayout from "../layout/AppLayout";
import MascotHuntMap from "../components/MascotHuntMap";
import MascotCatchModal from "../components/MascotCatchModal";
import { usePreciseGeolocation } from "../custom/usePreciseGeolocation";
import {
  MASCOT_RARITY_COLOR,
  MASCOT_RARITY_LABEL,
  extractMascotErrorMessage,
  mascotApi,
  resolveMascotImageUrl,
  type MascotDexResponse,
  type MascotEncounterResponse,
  type MascotSpawnPointResponse,
} from "../api/mascot";

// 마스코트 탐험 화면 (/mascots).
// 지도에서 마스코트 스폰 지점을 고르고 → 포획 반경 안으로 이동 → "조우하기" → 포획 미니게임.
// 서버(MascotService)도 같은 기준(반경 + GPS 정확도 200m)으로 다시 검증하므로,
// 여기의 거리 판단은 버튼 활성화를 위한 UX 선제 검증이다(RealtimeQuestVerifier와 같은 원칙).

const MAX_ACCEPTABLE_ACCURACY_METERS = 200;

function distanceMeters(lat1: number, lng1: number, lat2: number, lng2: number) {
  const earthRadius = 6371000;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const value =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLng / 2) ** 2;
  return earthRadius * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
}

function formatDistance(distance: number) {
  return distance >= 1000 ? `${(distance / 1000).toFixed(1)}km` : `${Math.round(distance)}m`;
}

export default function MascotHuntPage() {
  const [spawns, setSpawns] = useState<MascotSpawnPointResponse[]>([]);
  const [dex, setDex] = useState<MascotDexResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedSpawnId, setSelectedSpawnId] = useState<number | null>(null);
  const [encountering, setEncountering] = useState(false);
  const [encounterError, setEncounterError] = useState<string | null>(null);
  const [encounter, setEncounter] = useState<MascotEncounterResponse | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  const { latitude, longitude, accuracy, gpsSignalStrength, error: gpsError } = usePreciseGeolocation(true);

  // 스폰 목록(전체 활성 스폰)과 도감 요약을 함께 불러온다. 거리 계산은 화면에서 한다.
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    Promise.all([mascotApi.getNearby(), mascotApi.getDex().catch(() => null)])
      .then(([spawnList, dexResponse]) => {
        if (cancelled) return;
        setSpawns(spawnList);
        setDex(dexResponse);
      })
      .catch((err) => {
        if (!cancelled) setError(extractMascotErrorMessage(err, "마스코트 정보를 불러오지 못했어요."));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [reloadToken]);

  const hasLocation = latitude != null && longitude != null;

  const spawnsWithDistance = useMemo(
    () =>
      spawns
        .map((spawn) => ({
          spawn,
          distance: hasLocation
            ? distanceMeters(latitude as number, longitude as number, spawn.targetLat, spawn.targetLng)
            : null,
        }))
        .sort((left, right) => (left.distance ?? Infinity) - (right.distance ?? Infinity)),
    [spawns, hasLocation, latitude, longitude],
  );

  // 처음 위치를 잡으면 가장 가까운 스폰을 자동 선택
  useEffect(() => {
    if (selectedSpawnId == null && spawnsWithDistance.length > 0 && spawnsWithDistance[0].distance != null) {
      setSelectedSpawnId(spawnsWithDistance[0].spawn.spawnId);
    }
  }, [selectedSpawnId, spawnsWithDistance]);

  const selected = spawnsWithDistance.find((item) => item.spawn.spawnId === selectedSpawnId) ?? null;
  const inRange = selected?.distance != null && selected.distance <= selected.spawn.catchRadius;
  const gpsTooWeak = accuracy != null && accuracy > MAX_ACCEPTABLE_ACCURACY_METERS;

  const handleSelect = useCallback((spawnId: number) => {
    setSelectedSpawnId(spawnId);
    setEncounterError(null);
  }, []);

  async function handleEncounter() {
    if (!selected || latitude == null || longitude == null) return;
    setEncountering(true);
    setEncounterError(null);
    try {
      const response = await mascotApi.encounter(selected.spawn.spawnId, {
        currentLat: latitude,
        currentLng: longitude,
        accuracyMeters: accuracy ?? undefined,
      });
      setEncounter(response);
    } catch (err) {
      setEncounterError(extractMascotErrorMessage(err, "조우에 실패했어요. 잠시 후 다시 시도해주세요."));
    } finally {
      setEncountering(false);
    }
  }

  function handleCatchClose(changed: boolean) {
    setEncounter(null);
    if (changed) setReloadToken((token) => token + 1);
  }

  return (
    <AppLayout pageClassName="bg-night-950 text-white">
      <div className="mx-auto max-w-6xl px-4 py-10">
        {/* 헤더 */}
        <section className="relative isolate overflow-hidden rounded-[2rem] border border-white/10 p-6 md:p-8">
          <div className="absolute inset-0 -z-10 tp-aurora" />
          <div className="absolute inset-0 -z-10 tp-dot-grid opacity-40" />
          <p className="text-xs font-black tracking-[0.25em] text-cyan-400">TRIPPAMINE MASCOT HUNT</p>
          <div className="mt-3 flex flex-wrap items-end justify-between gap-6">
            <div>
              <h1 className="font-display text-3xl font-bold tracking-tight md:text-5xl">마스코트 탐험</h1>
              <p className="mt-2 max-w-xl text-sm text-white/60">
                여행지 곳곳에 숨어 있는 지역 마스코트를 찾아 포획하세요. 처음 잡은 마스코트는 도감에 등록되고 포인트를 받아요.
              </p>
            </div>
            <Link
              to="/mascots/dex"
              className="group rounded-2xl border border-white/15 bg-white/5 px-5 py-4 transition hover:bg-white/10"
            >
              <p className="text-xs font-bold text-white/50">내 도감</p>
              <p className="mt-1 text-2xl font-black">
                {dex ? `${dex.collectedCount} / ${dex.totalCount}` : "-"}
                <span className="ml-2 text-sm font-bold text-cyan-300">{dex ? `${dex.completionRate}%` : ""}</span>
              </p>
              <p className="mt-1 text-xs font-semibold text-cyan-300 group-hover:underline">도감 보기 →</p>
            </Link>
          </div>
        </section>

        {/* GPS 상태 */}
        <div className="mt-6 flex flex-wrap items-center gap-2 text-xs font-semibold">
          <span
            className={`rounded-full px-3 py-1 ${
              gpsSignalStrength === "EXCELLENT"
                ? "bg-emerald-400/15 text-emerald-300"
                : gpsSignalStrength === "GOOD"
                  ? "bg-cyan-400/15 text-cyan-300"
                  : gpsSignalStrength === "SEARCHING"
                    ? "bg-white/10 text-white/60"
                    : "bg-rose-400/15 text-rose-300"
            }`}
          >
            GPS{" "}
            {gpsSignalStrength === "SEARCHING"
              ? "위치 찾는 중..."
              : accuracy != null
                ? `오차 ±${Math.round(accuracy)}m`
                : "신호 약함"}
          </span>
          {gpsError && <span className="text-rose-300">{gpsError}</span>}
        </div>

        {error && (
          <div className="mt-4 rounded-2xl border border-rose-400/30 bg-rose-500/10 px-5 py-4 text-sm text-rose-200">
            {error}
            <button onClick={() => setReloadToken((token) => token + 1)} className="ml-3 font-bold underline">
              다시 시도
            </button>
          </div>
        )}

        {loading ? (
          <div className="mt-6 h-[420px] animate-pulse rounded-3xl bg-white/5 md:h-[520px]" />
        ) : spawns.length === 0 ? (
          <div className="mt-6 rounded-3xl border border-dashed border-white/15 px-6 py-16 text-center text-sm text-white/50">
            아직 나타난 마스코트가 없어요. 퀘스트를 클리어하면 새로운 마스코트가 나타날 수도 있어요!
          </div>
        ) : (
          <div className="mt-6 grid gap-6 lg:grid-cols-[1.6fr_1fr]">
            <MascotHuntMap
              spawns={spawns}
              selectedSpawnId={selectedSpawnId}
              latitude={latitude}
              longitude={longitude}
              accuracy={accuracy}
              onSelect={handleSelect}
            />

            <div className="flex flex-col gap-4">
              {/* 선택한 스폰 */}
              {selected ? (
                <SelectedSpawnCard
                  spawn={selected.spawn}
                  distance={selected.distance}
                  inRange={inRange}
                  gpsTooWeak={gpsTooWeak}
                  hasLocation={hasLocation}
                  encountering={encountering}
                  error={encounterError}
                  onEncounter={handleEncounter}
                />
              ) : (
                <div className="rounded-3xl border border-white/10 bg-white/5 p-6 text-sm text-white/50">
                  지도에서 마스코트를 선택하세요.
                </div>
              )}

              {/* 가까운 순 목록 */}
              <div className="rounded-3xl border border-white/10 bg-white/5 p-4">
                <p className="px-1 text-xs font-black tracking-[0.2em] text-white/40">NEARBY</p>
                <ul className="mt-2 max-h-72 space-y-1 overflow-y-auto">
                  {spawnsWithDistance.map(({ spawn, distance }) => (
                    <li key={spawn.spawnId}>
                      <button
                        type="button"
                        onClick={() => handleSelect(spawn.spawnId)}
                        className={`flex w-full items-center gap-3 rounded-2xl px-3 py-2 text-left transition ${
                          spawn.spawnId === selectedSpawnId ? "bg-white/15" : "hover:bg-white/10"
                        }`}
                      >
                        <MascotAvatar url={spawn.mascotImageUrl} rarity={spawn.mascotRarity} size="h-10 w-10" />
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-bold">
                            {spawn.mascotName}
                            {spawn.alreadyCaught && <span className="ml-1.5 text-[10px] text-emerald-300">✓ 수집</span>}
                          </p>
                          <p className="truncate text-xs text-white/50">{spawn.spotName ?? "이름 없는 장소"}</p>
                        </div>
                        <span className="shrink-0 text-xs font-bold text-white/60">
                          {distance != null ? formatDistance(distance) : "-"}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          </div>
        )}
      </div>

      {encounter && <MascotCatchModal encounter={encounter} onClose={handleCatchClose} />}
    </AppLayout>
  );
}

function MascotAvatar({ url, rarity, size }: { url: string | null; rarity: MascotSpawnPointResponse["mascotRarity"]; size: string }) {
  const [broken, setBroken] = useState(false);
  const resolved = resolveMascotImageUrl(url);
  const color = MASCOT_RARITY_COLOR[rarity];
  return (
    <div className={`${size} shrink-0 overflow-hidden rounded-full border-2 bg-night-900`} style={{ borderColor: color }}>
      {resolved && !broken ? (
        <img src={resolved} alt="" onError={() => setBroken(true)} className="h-full w-full object-contain" />
      ) : (
        <div className="flex h-full w-full items-center justify-center" style={{ color }}>
          ✦
        </div>
      )}
    </div>
  );
}

function SelectedSpawnCard({
  spawn,
  distance,
  inRange,
  gpsTooWeak,
  hasLocation,
  encountering,
  error,
  onEncounter,
}: {
  spawn: MascotSpawnPointResponse;
  distance: number | null;
  inRange: boolean;
  gpsTooWeak: boolean;
  hasLocation: boolean;
  encountering: boolean;
  error: string | null;
  onEncounter: () => void;
}) {
  const color = MASCOT_RARITY_COLOR[spawn.mascotRarity];
  const remaining = distance != null ? Math.max(0, distance - spawn.catchRadius) : null;
  const disabled = !hasLocation || !inRange || gpsTooWeak || encountering;

  let hint = "";
  if (!hasLocation) hint = "현재 위치를 확인하는 중이에요.";
  else if (gpsTooWeak) hint = "GPS 신호가 약해요. 실외의 트인 곳으로 이동해주세요.";
  else if (!inRange && remaining != null) hint = `포획 반경까지 약 ${formatDistance(remaining)} 남았어요.`;
  else if (inRange) hint = "마스코트가 근처에 있어요!";

  return (
    <div className="rounded-3xl border border-white/10 bg-white/5 p-5">
      <div className="flex items-center gap-4">
        <MascotAvatar url={spawn.mascotImageUrl} rarity={spawn.mascotRarity} size="h-20 w-20" />
        <div className="min-w-0">
          <span className="rounded-full px-2 py-0.5 text-[10px] font-black" style={{ background: `${color}33`, color }}>
            {MASCOT_RARITY_LABEL[spawn.mascotRarity]}
          </span>
          <h2 className="mt-1 truncate text-xl font-black">{spawn.mascotName}</h2>
          <p className="truncate text-xs text-white/50">
            {spawn.spotName ?? "이름 없는 장소"} · 반경 {spawn.catchRadius}m
          </p>
        </div>
      </div>

      <div className="mt-4 flex items-center justify-between rounded-2xl bg-night-900/70 px-4 py-3">
        <span className="text-xs font-semibold text-white/50">현재 거리</span>
        <span className={`text-lg font-black ${inRange ? "text-emerald-300" : "text-white"}`}>
          {distance != null ? formatDistance(distance) : "-"}
        </span>
      </div>

      {spawn.alreadyCaught && (
        <p className="mt-3 text-xs text-white/50">이미 잡은 마스코트예요. 다시 잡을 수 있지만 포인트는 지급되지 않아요.</p>
      )}

      <button
        type="button"
        onClick={onEncounter}
        disabled={disabled}
        className="mt-4 w-full rounded-2xl bg-cyan-400 py-3.5 text-sm font-black text-night-950 transition hover:bg-cyan-300 disabled:bg-white/10 disabled:text-white/40"
      >
        {encountering ? "다가가는 중..." : "조우하기"}
      </button>
      {hint && <p className={`mt-2 text-center text-xs ${inRange && !gpsTooWeak ? "text-emerald-300" : "text-white/50"}`}>{hint}</p>}
      {error && <p className="mt-2 rounded-xl bg-rose-500/10 px-3 py-2 text-center text-xs font-semibold text-rose-200">{error}</p>}
    </div>
  );
}
