// 마스코트 관리 화면.
// - 탭 3개: 스폰 지점 / 마스코트 / 지역
// - 등록 순서: 지역 → 마스코트 → 스폰 지점 (앞 단계 데이터가 없으면 안내 문구 표시)
// - 스폰 지점 좌표·반경은 퀘스트 관리와 같은 QuestLocationPicker(카카오 지도)로 지정
// 사용하는 백엔드 API: /admin/mascots/** (AdminMascotController)
import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import AdminLayout from "../components/AdminLayout";
import QuestLocationPicker from "../components/QuestLocationPicker";
import { ApiError } from "../api/client";
import { getQuestList } from "../api/quest";
import type { AdminQuest } from "../types/api";
import {
  MASCOT_RARITY_LABEL,
  createMascot,
  createRegion,
  createSpawnPoint,
  deleteMascot,
  deleteRegion,
  deleteSpawnPoint,
  getMascotList,
  getRegionList,
  getSpawnPointList,
  updateMascot,
  updateRegion,
  updateSpawnPoint,
  type AdminMascot,
  type AdminRegion,
  type AdminSpawnPoint,
  type MascotRarity,
  type MascotUpsertRequest,
  type RegionUpsertRequest,
  type SpawnPointUpsertRequest,
} from "../api/mascot";

type TabKey = "spawns" | "mascots" | "regions";

const TABS: Array<{ key: TabKey; label: string }> = [
  { key: "spawns", label: "스폰 지점" },
  { key: "mascots", label: "마스코트" },
  { key: "regions", label: "지역" },
];

const RARITY_BADGE: Record<MascotRarity, string> = {
  COMMON: "bg-slate-100 text-slate-600",
  RARE: "bg-sky-100 text-sky-700",
  EPIC: "bg-fuchsia-100 text-fuchsia-700",
};

const INPUT_CLASS =
  "w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700";
const LABEL_CLASS = "text-xs font-semibold text-slate-600 block mb-1.5";

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

// 카카오맵 웹에서 좌표를 바로 열어보는 링크 (지도 SDK 없이 위치 확인용)
function kakaoMapLink(name: string, lat: number, lng: number) {
  return `https://map.kakao.com/link/map/${encodeURIComponent(name)},${lat},${lng}`;
}

export default function AdminMascots() {
  const [tab, setTab] = useState<TabKey>("spawns");
  const [regions, setRegions] = useState<AdminRegion[]>([]);
  const [mascots, setMascots] = useState<AdminMascot[]>([]);
  const [spawns, setSpawns] = useState<AdminSpawnPoint[]>([]);
  const [quests, setQuests] = useState<AdminQuest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function loadAll() {
    setLoading(true);
    setError(null);
    try {
      const [regionList, mascotList, spawnList, questList] = await Promise.all([
        getRegionList(),
        getMascotList(),
        getSpawnPointList(),
        // 퀘스트 목록은 "연동 퀘스트" 선택과 지도 주변 퀘스트 표시용. 실패해도 화면은 동작해야 한다.
        getQuestList().catch(() => [] as AdminQuest[]),
      ]);
      setRegions(regionList);
      setMascots(mascotList);
      setSpawns(spawnList);
      setQuests(questList);
    } catch (requestError) {
      setError(errorMessage(requestError, "마스코트 데이터를 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadAll();
  }, []);

  const spawnCountByMascot = useMemo(() => {
    const map = new Map<number, number>();
    spawns.forEach((spawn) => map.set(spawn.mascotId, (map.get(spawn.mascotId) ?? 0) + 1));
    return map;
  }, [spawns]);

  const mascotCountByRegion = useMemo(() => {
    const map = new Map<number, number>();
    mascots.forEach((mascot) => map.set(mascot.regionId, (map.get(mascot.regionId) ?? 0) + 1));
    return map;
  }, [mascots]);

  return (
    <AdminLayout>
      <div className="mb-6">
        <h1 className="text-xl font-bold text-slate-800">마스코트 관리</h1>
        <p className="text-sm text-slate-500 mt-1">
          지역 → 마스코트 → 스폰 지점 순서로 등록합니다. 사용자는 스폰 지점 반경 안에서 마스코트를 만나 포획할 수 있고,
          포인트는 마스코트별 <b>첫 포획</b>에만 지급됩니다.
        </p>
      </div>

      <div className="grid grid-cols-3 gap-3 mb-6 max-w-xl">
        <SummaryCard label="지역" value={regions.length} />
        <SummaryCard label="마스코트" value={mascots.length} />
        <SummaryCard
          label="활성 스폰"
          value={spawns.filter((spawn) => spawn.activeYn !== "N").length}
          sub={`전체 ${spawns.length}`}
        />
      </div>

      <div className="flex gap-1 mb-4 border-b border-slate-200">
        {TABS.map((item) => (
          <button
            key={item.key}
            onClick={() => setTab(item.key)}
            className={`px-4 py-2.5 text-sm font-semibold border-b-2 -mb-px transition-colors ${
              tab === item.key
                ? "border-indigo-500 text-indigo-600"
                : "border-transparent text-slate-400 hover:text-slate-600"
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      {error && (
        <p className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-4 py-2.5 mb-4">
          {error}
        </p>
      )}

      {loading ? (
        <div className="bg-white rounded-2xl border border-slate-200 px-5 py-10 text-center text-slate-400 text-sm">
          불러오는 중...
        </div>
      ) : tab === "spawns" ? (
        <SpawnTab
          spawns={spawns}
          mascots={mascots}
          quests={quests}
          onChanged={loadAll}
          onError={setError}
          onGoMascots={() => setTab("mascots")}
        />
      ) : tab === "mascots" ? (
        <MascotTab
          mascots={mascots}
          regions={regions}
          spawnCountByMascot={spawnCountByMascot}
          onChanged={loadAll}
          onError={setError}
          onGoRegions={() => setTab("regions")}
        />
      ) : (
        <RegionTab
          regions={regions}
          mascotCountByRegion={mascotCountByRegion}
          onChanged={loadAll}
          onError={setError}
        />
      )}
    </AdminLayout>
  );
}

function SummaryCard({ label, value, sub }: { label: string; value: number; sub?: string }) {
  return (
    <div className="bg-white rounded-xl border border-slate-200 px-4 py-3">
      <p className="text-xs font-semibold text-slate-400">{label}</p>
      <p className="text-2xl font-bold text-slate-800 mt-0.5">
        {value}
        {sub && <span className="text-xs font-medium text-slate-400 ml-1.5">{sub}</span>}
      </p>
    </div>
  );
}

function MascotThumb({ src, name, size = "h-12 w-12" }: { src: string | null; name: string; size?: string }) {
  const [broken, setBroken] = useState(false);
  if (!src || broken) {
    return (
      <div className={`${size} rounded-xl bg-slate-100 text-slate-300 flex items-center justify-center text-lg`}>
        ✦
      </div>
    );
  }
  return (
    <img
      src={src}
      alt={name}
      onError={() => setBroken(true)}
      className={`${size} rounded-xl bg-slate-50 object-contain`}
    />
  );
}

function EmptyNotice({ message, actionLabel, onAction }: { message: string; actionLabel?: string; onAction?: () => void }) {
  return (
    <div className="bg-white rounded-2xl border border-dashed border-slate-300 px-5 py-10 text-center">
      <p className="text-sm text-slate-500">{message}</p>
      {actionLabel && onAction && (
        <button onClick={onAction} className="mt-3 text-sm font-semibold text-indigo-600 hover:underline">
          {actionLabel}
        </button>
      )}
    </div>
  );
}

function HeaderRow({ title, desc, buttonLabel, onClick, disabled }: {
  title: string;
  desc: string;
  buttonLabel: string;
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <div className="flex flex-col gap-3 mb-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h2 className="text-base font-bold text-slate-700">{title}</h2>
        <p className="text-xs text-slate-500 mt-0.5">{desc}</p>
      </div>
      <button
        onClick={onClick}
        disabled={disabled}
        className="px-4 py-2.5 rounded-lg bg-indigo-500 hover:bg-indigo-400 disabled:bg-slate-300 text-white text-sm font-semibold transition-colors"
      >
        {buttonLabel}
      </button>
    </div>
  );
}

// =====================================================================
// 스폰 지점 탭
// =====================================================================

type SpawnFormTarget = { mode: "create" } | { mode: "edit"; spawn: AdminSpawnPoint } | null;

function SpawnTab({ spawns, mascots, quests, onChanged, onError, onGoMascots }: {
  spawns: AdminSpawnPoint[];
  mascots: AdminMascot[];
  quests: AdminQuest[];
  onChanged: () => void;
  onError: (message: string | null) => void;
  onGoMascots: () => void;
}) {
  const [formTarget, setFormTarget] = useState<SpawnFormTarget>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [mascotFilter, setMascotFilter] = useState<number | "ALL">("ALL");

  const questNameById = useMemo(() => {
    const map = new Map<number, string>();
    quests.forEach((quest) => map.set(quest.questId, quest.questName));
    return map;
  }, [quests]);

  const visibleSpawns = useMemo(
    () => (mascotFilter === "ALL" ? spawns : spawns.filter((spawn) => spawn.mascotId === mascotFilter)),
    [spawns, mascotFilter],
  );

  async function handleDelete(spawn: AdminSpawnPoint) {
    const name = spawn.spotName ?? `스폰 #${spawn.spawnId}`;
    if (!window.confirm(`"${name}" 스폰 지점을 삭제할까요?\n(이미 잡은 사용자의 도감 기록은 유지됩니다)`)) return;
    setDeletingId(spawn.spawnId);
    onError(null);
    try {
      await deleteSpawnPoint(spawn.spawnId);
      onChanged();
    } catch (requestError) {
      onError(errorMessage(requestError, "스폰 지점 삭제에 실패했습니다."));
    } finally {
      setDeletingId(null);
    }
  }

  if (mascots.length === 0) {
    return (
      <EmptyNotice
        message="스폰 지점을 만들려면 먼저 마스코트를 등록해야 합니다."
        actionLabel="마스코트 탭으로 이동 →"
        onAction={onGoMascots}
      />
    );
  }

  return (
    <>
      <HeaderRow
        title="스폰 지점"
        desc="사용자 지도에 마스코트가 나타나는 위치입니다. 비활성(N)으로 두면 지도에서 숨겨집니다."
        buttonLabel="+ 새 스폰 지점"
        onClick={() => setFormTarget({ mode: "create" })}
      />

      <div className="mb-3 flex items-center gap-2">
        <label className="text-xs font-semibold text-slate-500">마스코트</label>
        <select
          value={mascotFilter}
          onChange={(event) => setMascotFilter(event.target.value === "ALL" ? "ALL" : Number(event.target.value))}
          className="px-3 py-1.5 rounded-lg border border-slate-200 text-sm text-slate-600 bg-white"
        >
          <option value="ALL">전체</option>
          {mascots.map((mascot) => (
            <option key={mascot.mascotId} value={mascot.mascotId}>
              {mascot.mascotName}
            </option>
          ))}
        </select>
      </div>

      <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-slate-50 border-b border-slate-200 text-left text-xs font-semibold text-slate-500 uppercase tracking-wide">
              <th className="px-5 py-3">ID</th>
              <th className="px-5 py-3">마스코트</th>
              <th className="px-5 py-3">장소명</th>
              <th className="px-5 py-3">좌표</th>
              <th className="px-5 py-3">반경</th>
              <th className="px-5 py-3">연동 퀘스트</th>
              <th className="px-5 py-3">상태</th>
              <th className="px-5 py-3 text-right">관리</th>
            </tr>
          </thead>
          <tbody>
            {visibleSpawns.length === 0 ? (
              <tr>
                <td colSpan={8} className="px-5 py-10 text-center text-slate-400">
                  등록된 스폰 지점이 없습니다.
                </td>
              </tr>
            ) : (
              visibleSpawns.map((spawn) => (
                <tr key={spawn.spawnId} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/70">
                  <td className="px-5 py-3.5 text-slate-400 font-mono text-xs">#{spawn.spawnId}</td>
                  <td className="px-5 py-3.5">
                    <div className="flex items-center gap-3">
                      <MascotThumb src={spawn.mascotImageUrl} name={spawn.mascotName} size="h-9 w-9" />
                      <div>
                        <p className="text-slate-700 font-medium">{spawn.mascotName}</p>
                        <span className={`inline-block mt-0.5 px-1.5 py-0.5 rounded text-[10px] font-bold ${RARITY_BADGE[spawn.mascotRarity]}`}>
                          {MASCOT_RARITY_LABEL[spawn.mascotRarity]}
                        </span>
                      </div>
                    </div>
                  </td>
                  <td className="px-5 py-3.5 text-slate-600">{spawn.spotName ?? "-"}</td>
                  <td className="px-5 py-3.5 text-xs">
                    <a
                      href={kakaoMapLink(spawn.spotName ?? spawn.mascotName, spawn.targetLat, spawn.targetLng)}
                      target="_blank"
                      rel="noreferrer"
                      className="font-mono text-slate-500 hover:text-indigo-600 hover:underline"
                    >
                      {Number(spawn.targetLat).toFixed(5)}, {Number(spawn.targetLng).toFixed(5)} ↗
                    </a>
                  </td>
                  <td className="px-5 py-3.5 text-slate-500">{spawn.catchRadius}m</td>
                  <td className="px-5 py-3.5 text-slate-500 text-xs">
                    {spawn.linkedQuestId
                      ? `#${spawn.linkedQuestId} ${questNameById.get(spawn.linkedQuestId) ?? ""}`
                      : "-"}
                  </td>
                  <td className="px-5 py-3.5">
                    {spawn.activeYn === "N" ? (
                      <span className="px-2 py-0.5 rounded-full text-[11px] font-semibold bg-slate-100 text-slate-500">비활성</span>
                    ) : (
                      <span className="px-2 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-600">활성</span>
                    )}
                  </td>
                  <td className="px-5 py-3.5 text-right whitespace-nowrap">
                    <button
                      onClick={() => setFormTarget({ mode: "edit", spawn })}
                      className="px-3 py-1.5 rounded-lg text-xs font-semibold text-indigo-600 hover:bg-indigo-50 transition-colors"
                    >
                      수정
                    </button>
                    <button
                      onClick={() => handleDelete(spawn)}
                      disabled={deletingId === spawn.spawnId}
                      className="px-3 py-1.5 rounded-lg text-xs font-semibold text-red-600 hover:bg-red-50 disabled:text-slate-300 transition-colors"
                    >
                      {deletingId === spawn.spawnId ? "삭제 중..." : "삭제"}
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {formTarget && (
        <SpawnFormModal
          target={formTarget}
          mascots={mascots}
          quests={quests}
          onClose={() => setFormTarget(null)}
          onSuccess={() => {
            setFormTarget(null);
            onChanged();
          }}
        />
      )}
    </>
  );
}

function SpawnFormModal({ target, mascots, quests, onClose, onSuccess }: {
  target: Exclude<SpawnFormTarget, null>;
  mascots: AdminMascot[];
  quests: AdminQuest[];
  onClose: () => void;
  onSuccess: () => void;
}) {
  const isEdit = target.mode === "edit";
  const source = isEdit ? target.spawn : undefined;

  const [mascotId, setMascotId] = useState<string>(source ? String(source.mascotId) : String(mascots[0]?.mascotId ?? ""));
  const [spotName, setSpotName] = useState(source?.spotName ?? "");
  const [targetLat, setTargetLat] = useState(source ? String(source.targetLat) : "");
  const [targetLng, setTargetLng] = useState(source ? String(source.targetLng) : "");
  const [catchRadius, setCatchRadius] = useState(source ? String(source.catchRadius) : "100");
  const [linkedQuestId, setLinkedQuestId] = useState(source?.linkedQuestId ? String(source.linkedQuestId) : "");
  const [active, setActive] = useState(source ? source.activeYn !== "N" : true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectedMascot = mascots.find((mascot) => String(mascot.mascotId) === mascotId);

  function validate(): string | null {
    if (!mascotId) return "마스코트를 선택해주세요.";
    const lat = Number(targetLat);
    const lng = Number(targetLng);
    if (targetLat.trim() === "" || !Number.isFinite(lat) || lat < -90 || lat > 90) return "위도를 지도에서 선택하거나 올바르게 입력해주세요.";
    if (targetLng.trim() === "" || !Number.isFinite(lng) || lng < -180 || lng > 180) return "경도를 지도에서 선택하거나 올바르게 입력해주세요.";
    const radius = Number(catchRadius);
    if (!Number.isInteger(radius) || radius < 1) return "포획 반경은 1 이상의 정수(m)로 입력해주세요.";
    return null;
  }

  async function handleSubmit() {
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    const request: SpawnPointUpsertRequest = {
      mascotId: Number(mascotId),
      spotName: spotName.trim() === "" ? undefined : spotName.trim(),
      targetLat: Number(targetLat),
      targetLng: Number(targetLng),
      catchRadius: Number(catchRadius),
      linkedQuestId: linkedQuestId === "" ? undefined : Number(linkedQuestId),
      activeYn: active ? "Y" : "N",
    };

    setSubmitting(true);
    setError(null);
    try {
      if (target.mode === "edit") {
        await updateSpawnPoint(target.spawn.spawnId, request);
      } else {
        await createSpawnPoint(request);
      }
      onSuccess();
    } catch (requestError) {
      setError(errorMessage(requestError, "저장에 실패했습니다."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <ModalShell
      title={isEdit ? "스폰 지점 수정" : "새 스폰 지점 등록"}
      desc="지도에서 위치를 찍거나 장소를 검색해 좌표를 지정하세요."
      wide
      onClose={onClose}
      footer={
        <ModalFooter
          onClose={onClose}
          onSubmit={handleSubmit}
          submitting={submitting}
          submitLabel={isEdit ? "수정 완료" : "스폰 등록"}
        />
      }
    >
      <div className="grid gap-5 lg:grid-cols-[1fr_1.2fr]">
        <div className="space-y-4">
          <div>
            <label className={LABEL_CLASS}>마스코트</label>
            <div className="flex items-center gap-3">
              <MascotThumb src={selectedMascot?.imageUrl ?? null} name={selectedMascot?.mascotName ?? ""} />
              {/* 백엔드 updateSpawnPoint는 마스코트 변경을 지원하지 않으므로 수정 시에는 고정 */}
              <select
                value={mascotId}
                disabled={isEdit}
                onChange={(event) => setMascotId(event.target.value)}
                className={`${INPUT_CLASS} disabled:bg-slate-50 disabled:text-slate-400`}
              >
                {mascots.map((mascot) => (
                  <option key={mascot.mascotId} value={mascot.mascotId}>
                    [{MASCOT_RARITY_LABEL[mascot.rarity]}] {mascot.mascotName} · {mascot.regionName}
                  </option>
                ))}
              </select>
            </div>
            {isEdit && <p className="text-[11px] text-slate-400 mt-1">스폰 지점의 마스코트는 바꿀 수 없습니다. 다른 마스코트는 새 스폰으로 등록하세요.</p>}
          </div>

          <div>
            <label className={LABEL_CLASS}>장소명 (선택)</label>
            <input
              type="text"
              value={spotName}
              onChange={(event) => setSpotName(event.target.value)}
              placeholder="예) 해운대 해수욕장"
              className={INPUT_CLASS}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={LABEL_CLASS}>위도</label>
              <input type="text" value={targetLat} onChange={(event) => setTargetLat(event.target.value)} className={`${INPUT_CLASS} font-mono text-xs`} />
            </div>
            <div>
              <label className={LABEL_CLASS}>경도</label>
              <input type="text" value={targetLng} onChange={(event) => setTargetLng(event.target.value)} className={`${INPUT_CLASS} font-mono text-xs`} />
            </div>
          </div>

          <div>
            <label className={LABEL_CLASS}>포획 반경 (m)</label>
            <input type="number" min="1" step="1" value={catchRadius} onChange={(event) => setCatchRadius(event.target.value)} className={INPUT_CLASS} />
            <p className="text-[11px] text-slate-400 mt-1">사용자가 이 반경 안에 들어와야 조우할 수 있습니다. GPS 오차를 고려해 100m 이상을 권장합니다.</p>
          </div>

          <div>
            <label className={LABEL_CLASS}>연동 퀘스트 (선택)</label>
            <select value={linkedQuestId} onChange={(event) => setLinkedQuestId(event.target.value)} className={INPUT_CLASS}>
              <option value="">연동 안 함 (누구나 만날 수 있음)</option>
              {quests.map((quest) => (
                <option key={quest.questId} value={quest.questId}>
                  #{quest.questId} {quest.questName}
                </option>
              ))}
            </select>
            <p className="text-[11px] text-slate-400 mt-1">선택하면 이 퀘스트를 클리어한 사용자에게만 마스코트가 나타납니다.</p>
          </div>

          <label className="flex items-center gap-2.5 cursor-pointer select-none">
            <input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} className="h-4 w-4 accent-indigo-500" />
            <span className="text-sm text-slate-600">지도에 노출 (활성)</span>
          </label>

          {error && <p className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-3.5 py-3">{error}</p>}
        </div>

        <div>
          <QuestLocationPicker
            latitude={targetLat}
            longitude={targetLng}
            radius={catchRadius}
            quests={quests}
            onCoordinateChange={(lat, lng) => {
              setTargetLat(lat.toFixed(7));
              setTargetLng(lng.toFixed(7));
            }}
            onRadiusChange={setCatchRadius}
            onPlaceNameSuggestion={(placeName) =>
              setSpotName((current) => (current.trim() ? current : placeName))
            }
          />
        </div>
      </div>
    </ModalShell>
  );
}

// =====================================================================
// 마스코트 탭
// =====================================================================

type MascotFormTarget = { mode: "create" } | { mode: "edit"; mascot: AdminMascot } | null;

function MascotTab({ mascots, regions, spawnCountByMascot, onChanged, onError, onGoRegions }: {
  mascots: AdminMascot[];
  regions: AdminRegion[];
  spawnCountByMascot: Map<number, number>;
  onChanged: () => void;
  onError: (message: string | null) => void;
  onGoRegions: () => void;
}) {
  const [formTarget, setFormTarget] = useState<MascotFormTarget>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  async function handleDelete(mascot: AdminMascot) {
    if (!window.confirm(`"${mascot.mascotName}" 마스코트를 삭제할까요?`)) return;
    setDeletingId(mascot.mascotId);
    onError(null);
    try {
      await deleteMascot(mascot.mascotId);
      onChanged();
    } catch (requestError) {
      onError(errorMessage(requestError, "마스코트 삭제에 실패했습니다."));
    } finally {
      setDeletingId(null);
    }
  }

  if (regions.length === 0) {
    return (
      <EmptyNotice
        message="마스코트를 만들려면 먼저 지역을 등록해야 합니다."
        actionLabel="지역 탭으로 이동 →"
        onAction={onGoRegions}
      />
    );
  }

  return (
    <>
      <HeaderRow
        title="마스코트"
        desc="지역별 캐릭터입니다. 이미지는 투명 배경 PNG를 권장합니다 (예: /mascots/padori.png)."
        buttonLabel="+ 새 마스코트"
        onClick={() => setFormTarget({ mode: "create" })}
      />

      {mascots.length === 0 ? (
        <EmptyNotice message="등록된 마스코트가 없습니다." />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {mascots.map((mascot) => (
            <div key={mascot.mascotId} className="bg-white rounded-2xl border border-slate-200 p-4 flex gap-4">
              <MascotThumb src={mascot.imageUrl} name={mascot.mascotName} size="h-20 w-20" />
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5">
                  <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${RARITY_BADGE[mascot.rarity]}`}>
                    {MASCOT_RARITY_LABEL[mascot.rarity]}
                  </span>
                  <span className="text-[11px] text-slate-400 font-mono">#{mascot.mascotId}</span>
                </div>
                <p className="mt-1 font-semibold text-slate-800 truncate">{mascot.mascotName}</p>
                <p className="text-xs text-slate-500 truncate">{mascot.regionName}</p>
                <p className="text-xs text-slate-500 mt-1">
                  첫 포획 보상 <b className="text-slate-700">{mascot.rewardPoint.toLocaleString()}P</b>
                  <span className="mx-1.5 text-slate-300">|</span>
                  스폰 {spawnCountByMascot.get(mascot.mascotId) ?? 0}곳
                </p>
                <div className="mt-2 flex gap-1">
                  <button
                    onClick={() => setFormTarget({ mode: "edit", mascot })}
                    className="px-2.5 py-1 rounded-lg text-xs font-semibold text-indigo-600 hover:bg-indigo-50"
                  >
                    수정
                  </button>
                  <button
                    onClick={() => handleDelete(mascot)}
                    disabled={deletingId === mascot.mascotId}
                    className="px-2.5 py-1 rounded-lg text-xs font-semibold text-red-600 hover:bg-red-50 disabled:text-slate-300"
                  >
                    {deletingId === mascot.mascotId ? "삭제 중..." : "삭제"}
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {formTarget && (
        <MascotFormModal
          target={formTarget}
          regions={regions}
          onClose={() => setFormTarget(null)}
          onSuccess={() => {
            setFormTarget(null);
            onChanged();
          }}
        />
      )}
    </>
  );
}

function MascotFormModal({ target, regions, onClose, onSuccess }: {
  target: Exclude<MascotFormTarget, null>;
  regions: AdminRegion[];
  onClose: () => void;
  onSuccess: () => void;
}) {
  const isEdit = target.mode === "edit";
  const source = isEdit ? target.mascot : undefined;

  const [regionId, setRegionId] = useState(source ? String(source.regionId) : String(regions[0]?.regionId ?? ""));
  const [mascotName, setMascotName] = useState(source?.mascotName ?? "");
  const [rarity, setRarity] = useState<MascotRarity>(source?.rarity ?? "COMMON");
  const [imageUrl, setImageUrl] = useState(source?.imageUrl ?? "");
  const [rewardPoint, setRewardPoint] = useState(source ? String(source.rewardPoint) : "100");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit() {
    if (!regionId) return setError("지역을 선택해주세요.");
    if (!mascotName.trim()) return setError("마스코트명을 입력해주세요.");
    const point = Number(rewardPoint);
    if (!Number.isInteger(point) || point < 0) return setError("보상 포인트는 0 이상의 정수로 입력해주세요.");

    const request: MascotUpsertRequest = {
      regionId: Number(regionId),
      mascotName: mascotName.trim(),
      rarity,
      imageUrl: imageUrl.trim() === "" ? undefined : imageUrl.trim(),
      // 3D 모델(ARCore) 필드는 아직 쓰지 않으므로 기존 값을 그대로 유지
      modelUrl: source?.modelUrl ?? undefined,
      rewardPoint: point,
    };

    setSubmitting(true);
    setError(null);
    try {
      if (target.mode === "edit") {
        await updateMascot(target.mascot.mascotId, request);
      } else {
        await createMascot(request);
      }
      onSuccess();
    } catch (requestError) {
      setError(errorMessage(requestError, "저장에 실패했습니다."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <ModalShell
      title={isEdit ? "마스코트 수정" : "새 마스코트 등록"}
      desc="희귀도가 높을수록 포획 확률이 낮아집니다. (일반 90% / 희귀 60% / 에픽 35% 기본)"
      onClose={onClose}
      footer={
        <ModalFooter
          onClose={onClose}
          onSubmit={handleSubmit}
          submitting={submitting}
          submitLabel={isEdit ? "수정 완료" : "마스코트 등록"}
        />
      }
    >
      <div className="space-y-4">
        <div className="flex items-center gap-4">
          <MascotThumb src={imageUrl.trim() || null} name={mascotName} size="h-24 w-24" />
          <div className="flex-1">
            <label className={LABEL_CLASS}>이미지 URL</label>
            <input
              type="text"
              value={imageUrl}
              onChange={(event) => setImageUrl(event.target.value)}
              placeholder="/mascots/padori.png 또는 https://..."
              className={`${INPUT_CLASS} text-xs`}
            />
            <p className="text-[11px] text-slate-400 mt-1">입력하면 왼쪽에 미리보기가 표시됩니다.</p>
          </div>
        </div>

        <div>
          <label className={LABEL_CLASS}>마스코트명</label>
          <input type="text" value={mascotName} onChange={(event) => setMascotName(event.target.value)} placeholder="예) 해운대 갈매기 파도리" className={INPUT_CLASS} />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={LABEL_CLASS}>지역</label>
            <select value={regionId} onChange={(event) => setRegionId(event.target.value)} className={INPUT_CLASS}>
              {regions.map((region) => (
                <option key={region.regionId} value={region.regionId}>
                  {region.regionName}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className={LABEL_CLASS}>첫 포획 보상 (P)</label>
            <input type="number" min="0" step="10" value={rewardPoint} onChange={(event) => setRewardPoint(event.target.value)} className={INPUT_CLASS} />
          </div>
        </div>

        <div>
          <label className={LABEL_CLASS}>희귀도</label>
          <div className="grid grid-cols-3 gap-2">
            {(Object.keys(MASCOT_RARITY_LABEL) as MascotRarity[]).map((key) => (
              <button
                key={key}
                type="button"
                onClick={() => setRarity(key)}
                className={`py-2.5 rounded-lg border-2 text-sm font-semibold transition-colors ${
                  rarity === key ? "border-indigo-400 bg-indigo-50 text-indigo-700" : "border-slate-200 text-slate-500 hover:border-slate-300"
                }`}
              >
                {MASCOT_RARITY_LABEL[key]}
              </button>
            ))}
          </div>
        </div>

        {error && <p className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-3.5 py-3">{error}</p>}
      </div>
    </ModalShell>
  );
}

// =====================================================================
// 지역 탭
// =====================================================================

type RegionFormTarget = { mode: "create" } | { mode: "edit"; region: AdminRegion } | null;

function RegionTab({ regions, mascotCountByRegion, onChanged, onError }: {
  regions: AdminRegion[];
  mascotCountByRegion: Map<number, number>;
  onChanged: () => void;
  onError: (message: string | null) => void;
}) {
  const [formTarget, setFormTarget] = useState<RegionFormTarget>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  async function handleDelete(region: AdminRegion) {
    if (!window.confirm(`"${region.regionName}" 지역을 삭제할까요?`)) return;
    setDeletingId(region.regionId);
    onError(null);
    try {
      await deleteRegion(region.regionId);
      onChanged();
    } catch (requestError) {
      onError(errorMessage(requestError, "지역 삭제에 실패했습니다."));
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <>
      <HeaderRow
        title="지역"
        desc="마스코트가 소속되는 지역입니다. 사용자 도감은 지역별로 묶여서 수집률이 표시됩니다."
        buttonLabel="+ 새 지역"
        onClick={() => setFormTarget({ mode: "create" })}
      />

      <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden overflow-x-auto max-w-3xl">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-slate-50 border-b border-slate-200 text-left text-xs font-semibold text-slate-500 uppercase tracking-wide">
              <th className="px-5 py-3">ID</th>
              <th className="px-5 py-3">지역명</th>
              <th className="px-5 py-3">행정구역 코드</th>
              <th className="px-5 py-3">마스코트 수</th>
              <th className="px-5 py-3 text-right">관리</th>
            </tr>
          </thead>
          <tbody>
            {regions.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-5 py-10 text-center text-slate-400">
                  등록된 지역이 없습니다.
                </td>
              </tr>
            ) : (
              regions.map((region) => (
                <tr key={region.regionId} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/70">
                  <td className="px-5 py-3.5 text-slate-400 font-mono text-xs">#{region.regionId}</td>
                  <td className="px-5 py-3.5 text-slate-700 font-medium">{region.regionName}</td>
                  <td className="px-5 py-3.5 text-slate-500 font-mono text-xs">{region.sidoCode ?? "-"}</td>
                  <td className="px-5 py-3.5 text-slate-500">{mascotCountByRegion.get(region.regionId) ?? 0}</td>
                  <td className="px-5 py-3.5 text-right whitespace-nowrap">
                    <button
                      onClick={() => setFormTarget({ mode: "edit", region })}
                      className="px-3 py-1.5 rounded-lg text-xs font-semibold text-indigo-600 hover:bg-indigo-50 transition-colors"
                    >
                      수정
                    </button>
                    <button
                      onClick={() => handleDelete(region)}
                      disabled={deletingId === region.regionId}
                      className="px-3 py-1.5 rounded-lg text-xs font-semibold text-red-600 hover:bg-red-50 disabled:text-slate-300 transition-colors"
                    >
                      {deletingId === region.regionId ? "삭제 중..." : "삭제"}
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {formTarget && (
        <RegionFormModal
          target={formTarget}
          onClose={() => setFormTarget(null)}
          onSuccess={() => {
            setFormTarget(null);
            onChanged();
          }}
        />
      )}
    </>
  );
}

function RegionFormModal({ target, onClose, onSuccess }: {
  target: Exclude<RegionFormTarget, null>;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const isEdit = target.mode === "edit";
  const [regionName, setRegionName] = useState(isEdit ? target.region.regionName : "");
  const [sidoCode, setSidoCode] = useState(isEdit ? (target.region.sidoCode ?? "") : "");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit() {
    if (!regionName.trim()) return setError("지역명을 입력해주세요.");
    const request: RegionUpsertRequest = {
      regionName: regionName.trim(),
      sidoCode: sidoCode.trim() === "" ? undefined : sidoCode.trim(),
    };
    setSubmitting(true);
    setError(null);
    try {
      if (target.mode === "edit") {
        await updateRegion(target.region.regionId, request);
      } else {
        await createRegion(request);
      }
      onSuccess();
    } catch (requestError) {
      setError(errorMessage(requestError, "저장에 실패했습니다."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <ModalShell
      title={isEdit ? "지역 수정" : "새 지역 등록"}
      desc="시/도 단위로 시작하고, 필요하면 시/군/구까지 세분화할 수 있습니다."
      onClose={onClose}
      footer={
        <ModalFooter
          onClose={onClose}
          onSubmit={handleSubmit}
          submitting={submitting}
          submitLabel={isEdit ? "수정 완료" : "지역 등록"}
        />
      }
    >
      <div className="space-y-4">
        <div>
          <label className={LABEL_CLASS}>지역명</label>
          <input type="text" value={regionName} onChange={(event) => setRegionName(event.target.value)} placeholder="예) 부산광역시 해운대구" className={INPUT_CLASS} />
        </div>
        <div>
          <label className={LABEL_CLASS}>행정구역 코드 (선택)</label>
          <input type="text" value={sidoCode} onChange={(event) => setSidoCode(event.target.value)} placeholder="예) 26350" className={`${INPUT_CLASS} font-mono`} />
        </div>
        {error && <p className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-3.5 py-3">{error}</p>}
      </div>
    </ModalShell>
  );
}

// =====================================================================
// 공통 모달 뼈대 (AdminQuestChapters의 모달 스타일과 동일)
// =====================================================================

function ModalShell({ title, desc, wide, onClose, footer, children }: {
  title: string;
  desc: string;
  wide?: boolean;
  onClose: () => void;
  footer: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="fixed inset-0 bg-slate-950/60 flex items-center justify-center p-3 z-50">
      <div className={`bg-white rounded-2xl w-full ${wide ? "max-w-5xl" : "max-w-lg"} max-h-[94vh] flex flex-col shadow-2xl overflow-hidden`}>
        <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-6 py-5">
          <div>
            <h2 className="font-bold text-lg text-slate-800">{title}</h2>
            <p className="text-sm text-slate-500 mt-1">{desc}</p>
          </div>
          <button type="button" onClick={onClose} aria-label="닫기" className="h-9 w-9 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700">
            ✕
          </button>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto p-6">{children}</div>
        {footer}
      </div>
    </div>
  );
}

function ModalFooter({ onClose, onSubmit, submitting, submitLabel }: {
  onClose: () => void;
  onSubmit: () => void;
  submitting: boolean;
  submitLabel: string;
}) {
  return (
    <div className="flex justify-end gap-2 border-t border-slate-200 bg-slate-50 px-6 py-4">
      <button type="button" onClick={onClose} className="px-5 py-2.5 rounded-lg border border-slate-200 bg-white text-slate-600 text-sm font-semibold hover:bg-slate-50">
        취소
      </button>
      <button
        type="button"
        onClick={onSubmit}
        disabled={submitting}
        className="px-5 py-2.5 rounded-lg bg-indigo-500 hover:bg-indigo-400 disabled:bg-slate-300 text-white text-sm font-semibold"
      >
        {submitting ? "저장 중..." : submitLabel}
      </button>
    </div>
  );
}
