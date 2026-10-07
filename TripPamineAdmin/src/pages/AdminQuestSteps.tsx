import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import AdminLayout from "../components/AdminLayout";

import { ApiError } from "../api/client";
import type { AdminQuest, AdminQuestStep, QuestStepUpsertRequest } from "../types/api";
import { getQuestList } from "../api/quest";
import {
  createStep,
  deleteStep,
  getStepList,
  updateStep,
} from "../api/questStep";
import QuestLocationPicker from "../components/QuestLocationPicker";

type StepFormTarget =
  | { mode: "create" }
  | { mode: "edit"; step: AdminQuestStep }
  | null;

export default function AdminQuestSteps() {
  const { questId: questIdParam } = useParams<{ questId: string }>();
  const questId = Number(questIdParam);

  const [quest, setQuest] = useState<AdminQuest | null>(null);
  const [steps, setSteps] = useState<AdminQuestStep[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [formTarget, setFormTarget] = useState<StepFormTarget>(null);

  async function loadAll() {
    setLoading(true);
    setError(null);
    try {
      const [questListData, stepListData] = await Promise.all([
        getQuestList(),
        getStepList(questId),
      ]);
      setQuest(questListData.find((q) => q.questId === questId) ?? null);
      setSteps(stepListData);
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : "단계 목록을 불러오지 못했습니다.",
      );
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (!Number.isInteger(questId) || questId <= 0) {
      setError("올바르지 않은 퀘스트 번호입니다.");
      setLoading(false);
      return;
    }
    loadAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [questId]);

  const sortedSteps = useMemo(
    () => [...steps].sort((a, b) => a.stepOrder - b.stepOrder || a.stepId - b.stepId),
    [steps],
  );

  async function handleDelete(step: AdminQuestStep) {
    if (!window.confirm(`"${step.stepOrder}단계. ${step.stepName}" 단계를 삭제할까요?`)) return;

    setDeletingId(step.stepId);
    setError(null);
    try {
      await deleteStep(questId, step.stepId);
      await loadAll();
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : "단계 삭제에 실패했습니다. 이미 유저가 클리어한 단계인지 확인해주세요.",
      );
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <AdminLayout>
      <Link
        to="/quests"
        className="inline-flex items-center gap-1.5 text-sm font-semibold text-slate-500 hover:text-indigo-600 transition-colors mb-4"
      >
        <span>←</span> 퀘스트 목록
      </Link>

      <div className="flex flex-col gap-4 mb-6 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-800">
            단계 관리{quest ? ` — ${quest.questName}` : ""}
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            유저는 순서대로 각 단계의 목표 지점에 도달해야 다음 단계로 진행할 수 있습니다.
            마지막 단계를 클리어하면 퀘스트 전체 보상이 지급됩니다.
          </p>
        </div>
        <button
          onClick={() => setFormTarget({ mode: "create" })}
          className="px-4 py-2.5 rounded-lg bg-indigo-500 hover:bg-indigo-400 text-white text-sm font-semibold transition-colors shrink-0"
        >
          + 새 단계 등록
        </button>
      </div>

      {quest && quest.questType !== "CHAIN" && (
        <p className="text-sm text-amber-600 bg-amber-50 border border-amber-100 rounded-lg px-4 py-2.5 mb-4">
          이 퀘스트의 퀘스트 타입이 "연쇄 퀘스트(CHAIN)"가 아닙니다. 단계를 등록해도 유저
          화면에는 반영되지 않습니다 — 먼저 퀘스트 관리 화면에서 타입을 CHAIN으로
          변경해주세요.
        </p>
      )}

      {error && (
        <p className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-4 py-2.5 mb-4">
          {error}
        </p>
      )}

      <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-slate-50 border-b border-slate-200 text-left text-xs font-semibold text-slate-500 uppercase tracking-wide">
              <th className="px-5 py-3">순서</th>
              <th className="px-5 py-3">단계명</th>
              <th className="px-5 py-3">목표 좌표</th>
              <th className="px-5 py-3">클리어 반경</th>
              <th className="px-5 py-3">설명</th>
              <th className="px-5 py-3 text-right">관리</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6} className="px-5 py-10 text-center text-slate-400">
                  불러오는 중...
                </td>
              </tr>
            ) : sortedSteps.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-5 py-10 text-center text-slate-400">
                  등록된 단계가 없습니다.
                </td>
              </tr>
            ) : (
              sortedSteps.map((step) => (
                <tr
                  key={step.stepId}
                  className="border-b border-slate-100 last:border-0 hover:bg-slate-50/70"
                >
                  <td className="px-5 py-3.5 text-slate-400 font-mono text-xs">
                    {step.stepOrder}
                  </td>
                  <td className="px-5 py-3.5 text-slate-700 font-medium">
                    {step.stepName}
                  </td>
                  <td className="px-5 py-3.5 text-slate-500 font-mono text-xs">
                    {step.targetLat.toFixed(5)}, {step.targetLng.toFixed(5)}
                  </td>
                  <td className="px-5 py-3.5 text-slate-500">{step.clearRadius}m</td>
                  <td className="px-5 py-3.5 text-slate-500 text-xs max-w-xs truncate">
                    {step.description ?? "-"}
                  </td>
                  <td className="px-5 py-3.5 text-right whitespace-nowrap">
                    <button
                      onClick={() => setFormTarget({ mode: "edit", step })}
                      className="px-3 py-1.5 rounded-lg text-xs font-semibold text-indigo-600 hover:bg-indigo-50 transition-colors"
                    >
                      수정
                    </button>
                    <button
                      onClick={() => handleDelete(step)}
                      disabled={deletingId === step.stepId}
                      className="px-3 py-1.5 rounded-lg text-xs font-semibold text-red-600 hover:bg-red-50 disabled:text-slate-300 disabled:hover:bg-transparent transition-colors"
                    >
                      {deletingId === step.stepId ? "삭제 중..." : "삭제"}
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {formTarget && (
        <StepFormModal
          questId={questId}
          nextOrder={steps.length + 1}
          target={formTarget}
          onClose={() => setFormTarget(null)}
          onSuccess={() => {
            setFormTarget(null);
            loadAll();
          }}
        />
      )}
    </AdminLayout>
  );
}

interface StepFormModalProps {
  questId: number;
  nextOrder: number;
  target: Exclude<StepFormTarget, null>;
  onClose: () => void;
  onSuccess: () => void;
}

function StepFormModal({ questId, nextOrder, target, onClose, onSuccess }: StepFormModalProps) {
  const isEdit = target.mode === "edit";
  const sourceStep = isEdit ? target.step : undefined;

  const [stepOrder, setStepOrder] = useState(
    sourceStep ? String(sourceStep.stepOrder) : String(nextOrder),
  );
  const [stepName, setStepName] = useState(sourceStep?.stepName ?? "");
  const [description, setDescription] = useState(sourceStep?.description ?? "");
  const [targetLat, setTargetLat] = useState(sourceStep ? String(sourceStep.targetLat) : "");
  const [targetLng, setTargetLng] = useState(sourceStep ? String(sourceStep.targetLng) : "");
  const [clearRadius, setClearRadius] = useState(
    sourceStep ? String(sourceStep.clearRadius) : "100",
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function validate(): string | null {
    if (!stepName.trim()) return "단계명을 입력해주세요.";

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

    if (stepOrder.trim() !== "") {
      const order = Number(stepOrder);
      if (!Number.isInteger(order) || order < 1) {
        return "순서는 1 이상의 정수로 입력해주세요.";
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

    const request: QuestStepUpsertRequest = {
      stepOrder: stepOrder.trim() === "" ? undefined : Number(stepOrder),
      stepName: stepName.trim(),
      description: description.trim() === "" ? undefined : description.trim(),
      targetLat: Number(targetLat),
      targetLng: Number(targetLng),
      clearRadius: clearRadius.trim() === "" ? undefined : Number(clearRadius),
    };

    setSubmitting(true);
    setError(null);
    try {
      if (target.mode === "edit") {
        await updateStep(questId, target.step.stepId, request);
      } else {
        await createStep(questId, request);
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

  return (
    <div className="fixed inset-0 bg-slate-950/60 flex items-center justify-center p-3 z-50">
      <div className="bg-white rounded-2xl w-full max-w-6xl max-h-[94vh] flex flex-col shadow-2xl overflow-hidden">
        <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-6 py-5">
          <div>
            <h2 className="font-bold text-lg text-slate-800">
              {isEdit ? "단계 수정" : "새 단계 등록"}
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
              <div className="grid grid-cols-[1fr_2fr] gap-3">
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1.5">
                    순서
                  </label>
                  <input
                    type="number"
                    inputMode="numeric"
                    min="1"
                    step="1"
                    value={stepOrder}
                    onChange={(event) => setStepOrder(event.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1.5">
                    단계명
                  </label>
                  <input
                    type="text"
                    value={stepName}
                    onChange={(event) => setStepName(event.target.value)}
                    placeholder="예) 광화문 앞에서 사진 찍기"
                    className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700"
                  />
                </div>
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-600 block mb-1.5">
                  설명 (선택)
                </label>
                <textarea
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  placeholder="유저에게 보여줄 단계 설명을 입력하세요."
                  rows={2}
                  className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700 resize-none"
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
              quests={[]}
              onCoordinateChange={handleCoordinateChange}
              onRadiusChange={setClearRadius}
              onPlaceNameSuggestion={(placeName) =>
                setStepName((currentName) =>
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
            {submitting ? "저장 중..." : isEdit ? "수정 완료" : "단계 등록"}
          </button>
        </div>
      </div>
    </div>
  );
}
