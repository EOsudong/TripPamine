import React, { useState } from "react";
import { usePreciseGeolocation } from "../custom/usePreciseGeolocation";
import {
  extractQuestErrorMessage,
  questApi,
  type QuestResponse,
  type QuestStatus,
  type UserQuestLogResponse,
} from "../api/quest";

interface RealtimeQuestVerifierProps {
  quest: QuestResponse;
  initialStatus: QuestStatus;
  // 클리어 시도 결과(성공/실패 포함)가 바뀔 때마다 부모(MyPage)에게 최신 로그를 올려준다.
  // 포인트 누적, 목록 상태 갱신을 부모가 서버가 내려준 값 그대로 반영하도록 하기 위함
  // (프론트에서 임의로 rewardPoint를 더하지 않음 - 서버가 실제로 지급한 값만 신뢰).
  onLogUpdate: (log: UserQuestLogResponse) => void;
}

export const RealtimeQuestVerifier: React.FC<RealtimeQuestVerifierProps> = ({
  quest,
  initialStatus,
  onLogUpdate,
}) => {
  const [isVerifying, setIsVerifying] = useState(false);
  const [status, setStatus] = useState<QuestStatus>(initialStatus);
  const [customError, setCustomError] = useState<string | null>(null);

  const questCleared = status === "SUCCESS";

  // 위치 실시간 훅. 이미 클리어된 퀘스트면 배터리 소모를 막기 위해 추적을 멈춘다.
  const {
    latitude,
    longitude,
    accuracy,
    gpsSignalStrength,
    error: gpsError,
  } = usePreciseGeolocation(!questCleared);

  const handleVerifyLocation = async () => {
    if (latitude == null || longitude == null) {
      setCustomError(
        "GPS 정보 수신을 기다리는 중입니다. 잠시 후 다시 시도해 주세요.",
      );
      return;
    }

    // 오차가 너무 큰 상태에서는 굳이 서버까지 요청을 보내지 않고 먼저 안내한다.
    // (서버도 동일 기준으로 한 번 더 검증하므로 이건 UX 개선용 선제 차단이지, 유일한 방어선이 아님)
    if (gpsSignalStrength === "POOR") {
      setCustomError(
        "GPS 수신 신호가 너무 약해 검증이 불가합니다. 실외로 이동해 주세요.",
      );
      return;
    }

    try {
      setIsVerifying(true);
      setCustomError(null);

      const log = await questApi.clearQuest(quest.questId, {
        currentLat: latitude,
        currentLng: longitude,
        accuracyMeters: accuracy ?? undefined,
      });

      setStatus(log.status);
      onLogUpdate(log);

      if (log.status === "FAILED") {
        setCustomError(
          `아직 인증 반경(${quest.clearRadius}m) 밖에 있습니다. 목표 지점에 더 가까이 이동한 뒤 다시 시도해 주세요.`,
        );
      }
    } catch (err) {
      console.error(err);
      // 백엔드가 200m 초과 GPS 오차 리젝, 이미 SUCCESS 처리된 퀘스트 재시도 등을 이 메시지로 내려줌
      setCustomError(
        extractQuestErrorMessage(err, "위치 정보 검증 중 오류가 발생했습니다."),
      );
    } finally {
      setIsVerifying(false);
    }
  };

  const renderSignalBadge = () => {
    switch (gpsSignalStrength) {
      case "EXCELLENT":
        return (
          <span className="px-3 py-1 rounded-full text-xs font-black bg-emerald-500/20 text-emerald-400 border border-emerald-500/50">
            🛰️ 초정밀 수신 중 (오차 {accuracy?.toFixed(1)}m 이내)
          </span>
        );
      case "GOOD":
        return (
          <span className="px-3 py-1 rounded-full text-xs font-semibold bg-cyan-500/20 text-cyan-400 border border-cyan-500/50">
            📡 보통 감도 (오차 {accuracy?.toFixed(1)}m)
          </span>
        );
      case "POOR":
        return (
          <span className="px-3 py-1 rounded-full text-xs font-bold bg-rose-500/20 text-rose-400 border border-rose-500/50">
            ⚠️ 오차 높음 (실내/지하 등 검증 불가)
          </span>
        );
      default:
        return (
          <span className="px-3 py-1 rounded-full text-xs font-medium bg-slate-800 text-slate-400">
            🔍 위성 탐색 중...
          </span>
        );
    }
  };

  return (
    <div className="rounded-3xl bg-slate-900 p-6 text-white border border-slate-800 shadow-xl max-w-md mx-auto">
      <div className="flex items-center justify-between">
        <h4 className="text-xl font-extrabold text-white flex items-center gap-2">
          🎯 {quest.questName}
        </h4>
        <span className="text-base font-black text-yellow-400">
          {quest.rewardPoint.toLocaleString()} P
        </span>
      </div>

      <div className="mt-4 flex flex-col gap-2">
        <div className="flex items-center justify-between text-xs text-gray-400">
          <span>실시간 GPS 수신 감도</span>
          {renderSignalBadge()}
        </div>
      </div>

      {(gpsError || customError) && (
        <div className="mt-4 rounded-xl bg-rose-500/10 border border-rose-500/30 p-3 text-xs text-rose-400 leading-relaxed">
          {gpsError || customError}
        </div>
      )}

      <div className="mt-6">
        {questCleared ? (
          <div className="rounded-2xl bg-emerald-500 text-slate-950 p-4 text-center font-black animate-pulse shadow-lg shadow-emerald-500/20">
            🎉 축하합니다! 퀘스트 정복 완료!
          </div>
        ) : (
          <button
            onClick={handleVerifyLocation}
            disabled={isVerifying || latitude == null}
            className={`w-full py-4 rounded-2xl text-center text-sm font-black transition-all ${
              isVerifying
                ? "bg-slate-800 text-slate-500 cursor-not-allowed"
                : latitude == null
                  ? "bg-slate-800 text-slate-400 cursor-wait"
                  : "bg-gradient-to-r from-cyan-500 to-blue-600 text-white hover:opacity-90 shadow-lg shadow-cyan-500/20 active:scale-95"
            }`}
          >
            {isVerifying ? (
              <span className="flex items-center justify-center gap-2">
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                지구 타원체 기준 위성 매핑 중...
              </span>
            ) : latitude == null ? (
              "📡 GPS 신호 탐색 대기 중..."
            ) : status === "FAILED" ? (
              "다시 위치 인증하기"
            ) : (
              "실시간 위치 매칭 완료하기"
            )}
          </button>
        )}
      </div>

      <p className="text-[11px] text-gray-500 text-center mt-3 leading-normal">
        * 본 퀘스트는 초정밀 위치 검증 모델을 적용하고 있습니다.
        <br />
        GPS를 조작하는 행위 적발 시 제재 대상이 될 수 있습니다.
      </p>
    </div>
  );
};
