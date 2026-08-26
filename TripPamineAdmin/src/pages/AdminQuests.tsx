import { useEffect, useMemo, useState } from "react";
import AdminLayout from "../components/AdminLayout";

import { ApiError } from "../api/client";
import type { AdminQuest, QuestUpsertRequest } from "../types/api";
import {
  createQuest,
  deleteQuest,
  getQuestList,
  updateQuest,
} from "../api/quest";
import QuestLocationPicker from "../components/QuestLocationPicker";

type QuestFormTarget =
  | { mode: "create"; template?: AdminQuest }
  | { mode: "edit"; quest: AdminQuest }
  | null;

export default function AdminQuests() {
  const [quests, setQuests] = useState<AdminQuest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [formTarget, setFormTarget] = useState<QuestFormTarget>(null);
  const [listQuery, setListQuery] = useState("");

  async function loadQuests() {
    setLoading(true);
    setError(null);
    try {
      const data = await getQuestList();
      setQuests(data);
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : "퀘스트 목록을 불러오지 못했습니다.",
      );
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadQuests();
  }, []);

  const visibleQuests = useMemo(() => {
    const normalizedQuery = listQuery.trim().toLowerCase();
    if (!normalizedQuery) return quests;

    return quests.filter(
      (quest) =>
        quest.questName.toLowerCase().includes(normalizedQuery) ||
        String(quest.questId) === normalizedQuery,
    );
  }, [listQuery, quests]);

  async function handleDelete(quest: AdminQuest) {
    if (!window.confirm(`"${quest.questName}" 퀘스트를 삭제할까요?`)) return;

    setDeletingId(quest.questId);
    setError(null);
    try {
      await deleteQuest(quest.questId);
      await loadQuests();
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : "퀘스트 삭제에 실패했습니다.",
      );
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <AdminLayout>
      <div className="flex flex-col gap-4 mb-6 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-800">퀘스트 관리</h1>
          <p className="text-sm text-slate-500 mt-1">
            장소 검색과 지도 선택으로 GPS 퀘스트를 등록하고 관리합니다.
          </p>
        </div>
        <button
          onClick={() => setFormTarget({ mode: "create" })}
          className="px-4 py-2.5 rounded-lg bg-indigo-500 hover:bg-indigo-400 text-white text-sm font-semibold transition-colors"
        >
          + 새 퀘스트 등록
        </button>
      </div>

      <div className="mb-4 flex items-center gap-3">
        <input
          type="search"
          value={listQuery}
          onChange={(event) => setListQuery(event.target.value)}
          placeholder="퀘스트명 또는 ID 검색"
          className="w-full max-w-sm px-3.5 py-2.5 rounded-lg border border-slate-200 bg-white text-sm outline-none focus:border-indigo-400"
        />
        <span className="text-xs text-slate-400">
          전체 {quests.length}개 · 표시 {visibleQuests.length}개
        </span>
      </div>

      {error && (
        <p className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-4 py-2.5 mb-4">
          {error}
        </p>
      )}

      <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-slate-50 border-b border-slate-200 text-left text-xs font-semibold text-slate-500 uppercase tracking-wide">
              <th className="px-5 py-3">ID</th>
              <th className="px-5 py-3">퀘스트명</th>
              <th className="px-5 py-3">목표 좌표</th>
              <th className="px-5 py-3">클리어 반경</th>
              <th className="px-5 py-3">보상 포인트</th>
              <th className="px-5 py-3 text-right">관리</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td
                  colSpan={6}
                  className="px-5 py-10 text-center text-slate-400"
                >
                  불러오는 중...
                </td>
              </tr>
            ) : visibleQuests.length === 0 ? (
              <tr>
                <td
                  colSpan={6}
                  className="px-5 py-10 text-center text-slate-400"
                >
                  {quests.length === 0
                    ? "등록된 퀘스트가 없습니다."
                    : "검색 조건에 맞는 퀘스트가 없습니다."}
                </td>
              </tr>
            ) : (
              visibleQuests.map((quest) => (
                <tr
                  key={quest.questId}
                  className="border-b border-slate-100 last:border-0 hover:bg-slate-50/70"
                >
                  <td className="px-5 py-3.5 text-slate-400 font-mono text-xs">
                    #{quest.questId}
                  </td>
                  <td className="px-5 py-3.5 text-slate-700 font-medium">
                    {quest.questName}
                  </td>
                  <td className="px-5 py-3.5 text-slate-500 font-mono text-xs">
                    {quest.targetLat.toFixed(5)}, {quest.targetLng.toFixed(5)}
                  </td>
                  <td className="px-5 py-3.5 text-slate-500">
                    {quest.clearRadius}m
                  </td>
                  <td className="px-5 py-3.5 text-slate-500">
                    {quest.rewardPoint.toLocaleString()} P
                  </td>
                  <td className="px-5 py-3.5 text-right whitespace-nowrap">
                    <button
                      onClick={() =>
                        setFormTarget({ mode: "create", template: quest })
                      }
                      className="px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-600 hover:bg-slate-100 transition-colors"
                    >
                      복제
                    </button>
                    <button
                      onClick={() => setFormTarget({ mode: "edit", quest })}
                      className="px-3 py-1.5 rounded-lg text-xs font-semibold text-indigo-600 hover:bg-indigo-50 transition-colors"
                    >
                      수정
                    </button>
                    <button
                      onClick={() => handleDelete(quest)}
                      disabled={deletingId === quest.questId}
                      className="px-3 py-1.5 rounded-lg text-xs font-semibold text-red-600 hover:bg-red-50 disabled:text-slate-300 disabled:hover:bg-transparent transition-colors"
                    >
                      {deletingId === quest.questId ? "삭제 중..." : "삭제"}
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {formTarget && (
        <QuestFormModal
          target={formTarget}
          quests={quests}
          onClose={() => setFormTarget(null)}
          onSuccess={() => {
            setFormTarget(null);
            loadQuests();
          }}
        />
      )}
    </AdminLayout>
  );
}

interface QuestFormModalProps {
  target: Exclude<QuestFormTarget, null>;
  quests: AdminQuest[];
  onClose: () => void;
  onSuccess: () => void;
}

function QuestFormModal({
  target,
  quests,
  onClose,
  onSuccess,
}: QuestFormModalProps) {
  const isEdit = target.mode === "edit";
  const sourceQuest = isEdit ? target.quest : target.template;

  const [questName, setQuestName] = useState(
    isEdit
      ? target.quest.questName
      : sourceQuest
        ? `${sourceQuest.questName} 복사본`
        : "",
  );
  const [targetLat, setTargetLat] = useState(
    sourceQuest ? String(sourceQuest.targetLat) : "",
  );
  const [targetLng, setTargetLng] = useState(
    sourceQuest ? String(sourceQuest.targetLng) : "",
  );
  const [rewardPoint, setRewardPoint] = useState(
    sourceQuest ? String(sourceQuest.rewardPoint) : "0",
  );
  const [clearRadius, setClearRadius] = useState(
    sourceQuest ? String(sourceQuest.clearRadius) : "100",
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function validate(): string | null {
    if (!questName.trim()) return "퀘스트명을 입력해주세요.";

    const latitude = Number(targetLat);
    const longitude = Number(targetLng);
    if (targetLat.trim() === "" || Number.isNaN(latitude)) {
      return "지도에서 목표 위치를 선택해주세요.";
    }
    if (targetLng.trim() === "" || Number.isNaN(longitude)) {
      return "지도에서 목표 위치를 선택해주세요.";
    }
    if (latitude < -90 || latitude > 90) {
      return "위도는 -90 ~ 90 사이여야 합니다.";
    }
    if (longitude < -180 || longitude > 180) {
      return "경도는 -180 ~ 180 사이여야 합니다.";
    }

    if (rewardPoint.trim() !== "") {
      const point = Number(rewardPoint);
      if (!Number.isInteger(point) || point < 0) {
        return "보상 포인트는 0 이상의 정수로 입력해주세요.";
      }
    }

    if (clearRadius.trim() !== "") {
      const radius = Number(clearRadius);
      if (!Number.isInteger(radius) || radius < 1) {
        return "클리어 반경은 1m 이상의 정수로 입력해주세요.";
      }
    }

    return null;
  }

  async function handleSubmit() {
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    const request: QuestUpsertRequest = {
      questName: questName.trim(),
      targetLat: Number(targetLat),
      targetLng: Number(targetLng),
      rewardPoint: rewardPoint.trim() === "" ? undefined : Number(rewardPoint),
      clearRadius: clearRadius.trim() === "" ? undefined : Number(clearRadius),
    };

    setSubmitting(true);
    setError(null);
    try {
      if (target.mode === "edit") {
        await updateQuest(target.quest.questId, request);
      } else {
        await createQuest(request);
      }
      onSuccess();
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : "저장에 실패했습니다.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  function handleCoordinateChange(latitude: number, longitude: number) {
    setTargetLat(latitude.toFixed(7));
    setTargetLng(longitude.toFixed(7));
  }

  const radiusForPreview = Math.max(1, Number(clearRadius) || 100);
  const rewardForPreview = Math.max(0, Number(rewardPoint) || 0);

  return (
    <div className="fixed inset-0 bg-slate-950/60 flex items-center justify-center p-3 z-50">
      <div className="bg-white rounded-2xl w-full max-w-6xl max-h-[94vh] flex flex-col shadow-2xl overflow-hidden">
        <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-6 py-5">
          <div>
            <h2 className="font-bold text-lg text-slate-800">
              {isEdit
                ? "퀘스트 수정"
                : sourceQuest
                  ? "퀘스트 복제 등록"
                  : "새 퀘스트 등록"}
            </h2>
            <p className="text-sm text-slate-500 mt-1">
              장소 검색 후 마커와 클리어 반경을 확인하고 저장하세요.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="h-9 w-9 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700"
          >
            ✕
          </button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto p-6">
          <div className="grid gap-7 lg:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)]">
            <div className="space-y-4">
              <div>
                <label className="text-xs font-semibold text-slate-600 block mb-1.5">
                  퀘스트명
                </label>
                <input
                  type="text"
                  value={questName}
                  onChange={(event) => setQuestName(event.target.value)}
                  placeholder="예) 경복궁 광화문 현지 인증"
                  className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1.5">
                    목표 위도
                  </label>
                  <input
                    type="text"
                    inputMode="decimal"
                    value={targetLat}
                    onChange={(event) => setTargetLat(event.target.value)}
                    placeholder="37.5759360"
                    className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm font-mono text-slate-700"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1.5">
                    목표 경도
                  </label>
                  <input
                    type="text"
                    inputMode="decimal"
                    value={targetLng}
                    onChange={(event) => setTargetLng(event.target.value)}
                    placeholder="126.9768150"
                    className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm font-mono text-slate-700"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1.5">
                    클리어 반경 (m)
                  </label>
                  <input
                    type="number"
                    inputMode="numeric"
                    min="1"
                    step="1"
                    value={clearRadius}
                    onChange={(event) => setClearRadius(event.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700"
                  />
                  <p className="text-[11px] text-slate-400 mt-1">
                    매장 50m · 관광지 100m · 축제장 200m 권장
                  </p>
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1.5">
                    보상 포인트
                  </label>
                  <input
                    type="number"
                    inputMode="numeric"
                    min="0"
                    step="1"
                    value={rewardPoint}
                    onChange={(event) => setRewardPoint(event.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700"
                  />
                </div>
              </div>

              <div className="rounded-2xl bg-slate-950 p-5 text-white shadow-lg">
                <p className="text-[10px] font-bold tracking-[0.2em] text-cyan-400">
                  USER QUEST PREVIEW
                </p>
                <div className="mt-3 flex items-start justify-between gap-3">
                  <div>
                    <h3 className="font-black text-lg">
                      {questName.trim() || "퀘스트명을 입력해주세요"}
                    </h3>
                    <p className="text-xs text-slate-400 mt-1">
                      목표 지점 반경 {Math.round(radiusForPreview)}m 이내에서
                      GPS 위치 인증
                    </p>
                  </div>
                  <span className="shrink-0 rounded-full bg-yellow-400/15 border border-yellow-400/30 px-3 py-1 text-xs font-black text-yellow-300">
                    +{rewardForPreview.toLocaleString()} P
                  </span>
                </div>
                <div className="mt-4 h-2 rounded-full bg-slate-800 overflow-hidden">
                  <div className="h-full w-2/3 rounded-full bg-gradient-to-r from-cyan-400 to-indigo-500" />
                </div>
              </div>

              {error && (
                <p className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-3.5 py-3">
                  {error}
                </p>
              )}
            </div>

            <QuestLocationPicker
              latitude={targetLat}
              longitude={targetLng}
              radius={clearRadius}
              quests={quests}
              excludeQuestId={isEdit ? target.quest.questId : undefined}
              onCoordinateChange={handleCoordinateChange}
              onRadiusChange={setClearRadius}
              onPlaceNameSuggestion={(placeName) =>
                setQuestName((currentName) =>
                  currentName.trim() ? currentName : `${placeName} 방문 인증`,
                )
              }
            />
          </div>
        </div>

        <div className="flex justify-end gap-2 border-t border-slate-200 bg-slate-50 px-6 py-4">
          <button
            type="button"
            onClick={onClose}
            className="px-5 py-2.5 rounded-lg border border-slate-200 bg-white text-slate-600 text-sm font-semibold hover:bg-slate-50"
          >
            취소
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={submitting}
            className="px-5 py-2.5 rounded-lg bg-indigo-500 hover:bg-indigo-400 disabled:bg-slate-300 text-white text-sm font-semibold"
          >
            {submitting ? "저장 중..." : isEdit ? "수정 완료" : "퀘스트 등록"}
          </button>
        </div>
      </div>
    </div>
  );
}
