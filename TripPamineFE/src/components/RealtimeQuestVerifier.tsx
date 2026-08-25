import React, { useState } from "react";
import { usePreciseGeolocation } from "../custom/usePreciseGeolocation";
import { questApi } from "../api/quest";

interface RealtimeQuestVerifierProps {
  questId: number;
  questName: string;
  rewardPoint: number;
  userId: number;
}

export const RealtimeQuestVerifier: React.FC<RealtimeQuestVerifierProps> = ({
  questId,
  questName,
  rewardPoint,
  userId,
}) => {
  const [isVerifying, setIsVerifying] = useState(false);
  const [questCleared, setQuestCleared] = useState(false);
  const [customError, setCustomError] = useState<string | null>(null);

  // 위치 실시간 훅 수동 제어 바인딩
  const {
    latitude,
    longitude,
    accuracy,
    gpsSignalStrength,
    error: gpsError,
  } = usePreciseGeolocation(!questCleared);

  const handleVerifyLocation = async () => {
    if (!latitude || !longitude) {
      setCustomError(
        "GPS 정보 수신을 기다리는 중입니다. 잠시 후 다시 시도해 주세요.",
      );
      return;
    }

    // 위치 오차 감도가 POOR(25m 초과)인 상태일 경우 억울한 오인증이나 부정 수급 방지를 위한 경고 발령
    if (gpsSignalStrength === "POOR") {
      setCustomError(
        "GPS 수신 신호가 너무 약해 검증이 불가합니다. 실외로 이동해 주세요.",
      );
      return;
    }

    try {
      setIsVerifying(true);
      setCustomError(null);

      // 백엔드로 정밀 필터링된 위/경도 전송 (백엔드에서는 Vincenty 적용)
      const isVerified = await questApi.verifyQuest(
        questId,
        userId,
        latitude,
        longitude,
      );

      if (isVerified) {
        setQuestCleared(true);
      } else {
        setCustomError(
          "지정한 목적지 반경 50m 이내에 도달하지 않았습니다. 더 가까이 이동해 주세요.",
        );
      }
    } catch (err: any) {
      console.error(err);
      // 백엔드 Anti-Spoofing 걸렸을 경우 예외 피드백 반영
      const message =
        err.response?.data?.message || "위치 정보 검증 중 오류가 발생했습니다.";
      setCustomError(message);
    } finally {
      setIsVerifying(false);
    }
  };

  // 실시간 수신률 레이블 렌더링 도우미
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
          🎯 {questName}
        </h4>
        <span className="text-base font-black text-yellow-400">
          {rewardPoint.toLocaleString()} P
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
            disabled={isVerifying || !latitude}
            className={`w-full py-4 rounded-2xl text-center text-sm font-black transition-all ${
              isVerifying
                ? "bg-slate-800 text-slate-500 cursor-not-allowed"
                : !latitude
                  ? "bg-slate-800 text-slate-400 cursor-wait"
                  : "bg-gradient-to-r from-cyan-500 to-blue-600 text-white hover:opacity-90 shadow-lg shadow-cyan-500/20 active:scale-95"
            }`}
          >
            {isVerifying ? (
              <span className="flex items-center justify-center gap-2">
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                지구 타원체 기준 위성 매핑 중...
              </span>
            ) : !latitude ? (
              "📡 GPS 신호 탐색 대기 중..."
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
