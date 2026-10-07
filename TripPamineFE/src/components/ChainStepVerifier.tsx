import { useState } from "react";

import {
  extractQuestErrorMessage,
  questApi,
  type QuestResponse,
  type QuestStatus,
  type QuestStepResponse,
  type UserQuestLogResponse,
} from "../api/quest";

// RealtimeQuestVerifier와 동일한 GPS 신호 등급. 연쇄 퀘스트는 단계마다 목표/반경이
// 다르므로 clearQuest 대신 clearStep을 호출하는 별도 컴포넌트로 분리했다.
type GpsSignalStrength = "EXCELLENT" | "GOOD" | "POOR" | "SEARCHING";

interface ChainStepVerifierProps {
  quest: QuestResponse;

  // QuestDetailPage가 소유하는 단계 목록(상태 끌어올림) - Map과 동일한 좌표를 보도록
  // 현재 단계 정보를 QuestDetailPage도 함께 알아야 하기 때문에 이 컴포넌트가 직접
  // fetch하지 않고 props로 받는다.
  steps: QuestStepResponse[];
  onStepsChange: (steps: QuestStepResponse[]) => void;

  initialStatus: QuestStatus;
  onLogUpdate: (log: UserQuestLogResponse) => void;

  // QuestDetailPage가 수집한 동일 GPS Context (RealtimeQuestVerifier와 동일한 패턴)
  latitude: number | null;
  longitude: number | null;
  accuracy: number | null;
  gpsSignalStrength: GpsSignalStrength;
  gpsError: string | null;
}

export function ChainStepVerifier({
  quest,
  steps,
  onStepsChange,
  initialStatus,
  onLogUpdate,
  latitude,
  longitude,
  accuracy,
  gpsSignalStrength,
  gpsError,
}: ChainStepVerifierProps) {
  const [isVerifying, setIsVerifying] = useState(false);
  const [customError, setCustomError] = useState<string | null>(null);

  const sortedSteps = [...steps].sort((a, b) => a.stepOrder - b.stepOrder);
  const totalCount = sortedSteps.length;
  const clearedCount = sortedSteps.filter((step) => step.cleared).length;
  const currentStep = sortedSteps.find((step) => !step.cleared) ?? null;
  const allStepsCleared = totalCount > 0 && clearedCount === totalCount;
  const questCleared = initialStatus === "SUCCESS" || allStepsCleared;

  if (totalCount === 0) {
    return (
      <section className="rounded-2xl border border-amber-400/20 bg-amber-400/10 p-6 text-center">
        <p className="text-sm font-bold text-amber-300">
          등록된 단계가 없는 연쇄 퀘스트입니다.
        </p>
        <p className="mt-1 text-xs text-amber-200/70">
          관리자에게 문의해주세요.
        </p>
      </section>
    );
  }

  const handleVerifyLocation = async () => {
    if (!currentStep) return;

    if (latitude === null || longitude === null) {
      setCustomError(
        "현재 위치를 확인하고 있습니다. GPS 좌표가 수신된 뒤 다시 시도해주세요.",
      );
      return;
    }

    if (gpsSignalStrength === "POOR") {
      setCustomError(
        "GPS 정확도가 낮아 위치 인증이 어렵습니다. 가능하면 실외로 이동한 뒤 다시 시도해주세요.",
      );
      return;
    }

    try {
      setIsVerifying(true);
      setCustomError(null);

      const result = await questApi.clearStep(quest.questId, currentStep.stepId, {
        currentLat: latitude,
        currentLng: longitude,
        accuracyMeters: accuracy ?? undefined,
      });

      if (result.stepCleared) {
        const updatedSteps = sortedSteps.map((step) =>
          step.stepId === currentStep.stepId ? { ...step, cleared: true } : step,
        );
        onStepsChange(updatedSteps);
        onLogUpdate(result.questLog);
      } else {
        setCustomError(
          `아직 인증 반경 ${currentStep.clearRadius}m 밖에 있습니다. 목표 위치에 조금 더 가까이 이동해주세요.`,
        );
      }
    } catch (error) {
      console.error("단계 위치 인증 실패:", error);
      setCustomError(
        extractQuestErrorMessage(error, "현재 위치를 인증하는 중 문제가 발생했습니다."),
      );
    } finally {
      setIsVerifying(false);
    }
  };

  const signal = getSignalPresentation(gpsSignalStrength, accuracy);

  return (
    <section className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-900">
      <div className="border-b border-white/5 p-5">
        <p className="text-[10px] font-black tracking-[0.18em] text-cyan-400">
          CHAIN QUEST PROGRESS
        </p>
        <h3 className="mt-2 text-lg font-black text-white">단계별 위치 인증</h3>
        <p className="mt-1 text-xs leading-5 text-slate-400">
          순서대로 각 단계의 목표 지점에 도달해 인증하세요. {clearedCount}/{totalCount}{" "}
          단계 완료
        </p>

        <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-800">
          <div
            className="h-full rounded-full bg-gradient-to-r from-cyan-400 to-indigo-500 transition-all"
            style={{ width: `${(clearedCount / totalCount) * 100}%` }}
          />
        </div>
      </div>

      <div className="space-y-2 p-5">
        {sortedSteps.map((step) => {
          const isCurrent = currentStep?.stepId === step.stepId;
          return (
            <div
              key={step.stepId}
              className={`flex items-center gap-3 rounded-xl border px-4 py-3 ${
                step.cleared
                  ? "border-emerald-400/20 bg-emerald-400/5"
                  : isCurrent
                    ? "border-cyan-400/30 bg-cyan-400/5"
                    : "border-white/5 bg-slate-950/40 opacity-60"
              }`}
            >
              <span
                className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-[11px] font-black ${
                  step.cleared
                    ? "bg-emerald-400 text-slate-950"
                    : isCurrent
                      ? "bg-cyan-400 text-slate-950"
                      : "bg-slate-800 text-slate-500"
                }`}
              >
                {step.cleared ? "✓" : step.stepOrder}
              </span>
              <div className="min-w-0 flex-1">
                <p
                  className={`truncate text-sm font-bold ${
                    step.cleared ? "text-emerald-300" : "text-white"
                  }`}
                >
                  {step.stepName}
                </p>
                {isCurrent && (
                  <p className="mt-0.5 text-[11px] text-slate-400">
                    목표 반경 {step.clearRadius}m 이내로 이동하세요.
                  </p>
                )}
              </div>
            </div>
          );
        })}
      </div>

      <div className="border-t border-white/5 p-5">
        <div className="rounded-xl border border-white/5 bg-slate-950/60 p-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-[10px] font-bold text-slate-500">GPS SIGNAL</p>
              <p className="mt-1 text-sm font-bold text-white">{signal.label}</p>
            </div>
            <span className={`h-3 w-3 rounded-full ${signal.dotClass}`} />
          </div>

          <div className="mt-4 grid grid-cols-2 gap-2">
            <SmallMetric
              label="측위 오차"
              value={accuracy === null ? "--" : `±${Math.round(accuracy)}m`}
            />
            <SmallMetric
              label="인증 범위"
              value={currentStep ? `${currentStep.clearRadius}m` : "-"}
            />
          </div>
        </div>

        {(gpsError || customError) && (
          <div className="mt-4 rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-xs leading-5 text-rose-300">
            {gpsError ?? customError}
          </div>
        )}

        <div className="mt-5">
          {questCleared ? (
            <div className="rounded-xl border border-emerald-400/20 bg-emerald-400/10 p-5 text-center">
              <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-full bg-emerald-400 text-slate-950">
                ✓
              </div>
              <p className="mt-3 text-sm font-black text-emerald-300">연쇄 퀘스트 완료</p>
              <p className="mt-1 text-xs text-slate-400">
                모든 단계를 클리어해 보상 포인트가 반영되었습니다.
              </p>
            </div>
          ) : (
            <button
              type="button"
              onClick={handleVerifyLocation}
              disabled={isVerifying || latitude === null || longitude === null || !currentStep}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-cyan-500 px-4 py-3.5 text-sm font-black text-slate-950 transition hover:bg-cyan-400 disabled:cursor-not-allowed disabled:bg-slate-800 disabled:text-slate-500"
            >
              {isVerifying ? (
                <>
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
                  위치 확인 중...
                </>
              ) : latitude === null ? (
                "GPS 위치 수신 대기 중"
              ) : (
                `${currentStep?.stepOrder ?? ""}단계 위치 인증하기`
              )}
            </button>
          )}
        </div>

        <p className="mt-4 text-center text-[10px] leading-4 text-slate-500">
          실제 성공 여부는 서버에서 현재 좌표, GPS 정확도와 단계별 인증 범위를 기준으로
          최종 판정합니다.
        </p>
      </div>
    </section>
  );
}

function getSignalPresentation(signal: GpsSignalStrength, accuracy: number | null) {
  switch (signal) {
    case "EXCELLENT":
      return {
        label: accuracy === null ? "매우 좋음" : `매우 좋음 · ±${Math.round(accuracy)}m`,
        dotClass: "bg-emerald-400",
      };
    case "GOOD":
      return {
        label: accuracy === null ? "양호" : `양호 · ±${Math.round(accuracy)}m`,
        dotClass: "bg-cyan-400",
      };
    case "POOR":
      return {
        label:
          accuracy === null ? "신호 불안정" : `정확도 낮음 · ±${Math.round(accuracy)}m`,
        dotClass: "bg-rose-400",
      };
    case "SEARCHING":
    default:
      return {
        label: "위치 탐색 중",
        dotClass: "bg-slate-500 animate-pulse",
      };
  }
}

function SmallMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-white/[0.03] px-3 py-2.5">
      <p className="text-[10px] text-slate-500">{label}</p>
      <p className="mt-1 text-xs font-bold text-slate-200">{value}</p>
    </div>
  );
}
