import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  getCurrentMysteryQuest,
  completeMysteryQuest,
  type MysteryQuestCompleteRequest,
  type MysteryQuestResponse,
} from "../api/mysteryTour";
// quest.ts에 있지만 axios 에러에서 백엔드 메시지를 꺼내는 범용 헬퍼라 이 파일에서도 그대로 재사용한다.
import { extractQuestErrorMessage } from "../api/quest";
import { MysteryQuestMapView } from "../components/MysteryQuestMapView";

// verifyType이 GPS인 퀘스트를 완료하기 직전에 한 번만 현재 좌표를 읽어온다.
// QuestLocationServerTest.tsx의 GPS 수집 방식(getCurrentPosition 1회, enableHighAccuracy)과 동일한 패턴.
function getCurrentLocationOrThrow(): Promise<MysteryQuestCompleteRequest> {
  return new Promise((resolve, reject) => {
    if (!window.isSecureContext) {
      reject(new Error("GPS는 HTTPS 주소 또는 localhost에서만 사용할 수 있습니다."));
      return;
    }
    if (!navigator.geolocation) {
      reject(new Error("현재 브라우저는 GPS를 지원하지 않습니다."));
      return;
    }

    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        resolve({
          currentLat: coords.latitude,
          currentLng: coords.longitude,
          accuracyMeters: coords.accuracy,
        });
      },
      (error) => {
        switch (error.code) {
          case error.PERMISSION_DENIED:
            reject(new Error("위치 권한이 거부되었습니다. 설정에서 권한을 허용해 주세요."));
            break;
          case error.POSITION_UNAVAILABLE:
            reject(new Error("현재 위치를 확인할 수 없습니다."));
            break;
          case error.TIMEOUT:
            reject(new Error("위치 확인 시간이 초과되었습니다."));
            break;
          default:
            reject(new Error("GPS 정보를 가져오는 중 오류가 발생했습니다."));
        }
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
    );
  });
}

export default function MysteryTourPlay() {
  const navigate = useNavigate();
  const { mysteryTourId } = useParams();
  const [quest, setQuest] = useState<MysteryQuestResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [completing, setCompleting] = useState(false);
  const [tourCompleted, setTourCompleted] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    const loadQuest = async () => {
      if (!mysteryTourId) return;

      try {
        const result = await getCurrentMysteryQuest(Number(mysteryTourId));
        setQuest(result);
      } catch (error) {
        console.error("현재 퀘스트 조회 실패:", error);
      } finally {
        setLoading(false);
      }
    };

    loadQuest();
  }, [mysteryTourId]);
  const handleCompleteQuest = async () => {
    if (!quest || !mysteryTourId || completing) return;

    try {
      setCompleting(true);
      setErrorMessage(null);

      // GPS 타입일 때만 위치를 읽어서 함께 보낸다. PHOTO/SIMPLE은 지금처럼 location 없이 호출.
      const location =
        quest.verifyType === "GPS" ? await getCurrentLocationOrThrow() : undefined;

      const nextQuest = await completeMysteryQuest(
        Number(mysteryTourId),
        quest.mysteryQuestId,
        location,
      );

      if (nextQuest) {
        setQuest(nextQuest);
      } else {
        setQuest(null);
        setTourCompleted(true);
      }
    } catch (error) {
      console.error("퀘스트 완료 실패:", error);
      // getCurrentLocationOrThrow가 던진 순수 Error는 response가 없어 extractQuestErrorMessage가
      // fallback을 그대로 반환하므로, 여기서는 fallback 자리에 error.message를 넣어 GPS 에러 메시지도
      // 백엔드 검증 실패 메시지("목표 지점에서 너무 멀리 있습니다" 등)와 동일한 방식으로 보여준다.
      const fallbackMessage =
        error instanceof Error ? error.message : "퀘스트 완료 중 오류가 발생했습니다.";
      setErrorMessage(extractQuestErrorMessage(error, fallbackMessage));
    } finally {
      setCompleting(false);
    }
  };
  return (
    <div className="min-h-screen bg-slate-950 flex items-center justify-center px-6 py-10">
      <button
        type="button"
        onClick={() => navigate("/")}
        className="absolute top-6 left-6 px-4 py-2 rounded-xl bg-white/5 border border-white/10 text-sm font-bold text-slate-300 hover:bg-white/10 hover:text-white transition-all"
      >
        ← 홈으로
      </button>
      <div className="w-full max-w-2xl">
        <div className="text-center">
          <p className="text-violet-400 text-xs font-black tracking-[0.3em]">
            MYSTERY TOUR
          </p>

          <h1 className="mt-4 text-4xl font-black text-white">
            여행이 시작되었습니다 🚀
          </h1>

          <p className="mt-3 text-sm text-slate-500">
            목적지는 아직 비밀입니다. 퀘스트를 따라 이동하세요.
          </p>
        </div>
        {tourCompleted ? (
          <div className="mt-10 rounded-3xl border border-violet-500/30 bg-gradient-to-br from-slate-900 to-indigo-950 p-10 text-center">
            <div className="text-6xl">🏆</div>

            <h2 className="mt-6 text-3xl font-black text-white">
              미스터리 투어 완료!
            </h2>

            <p className="mt-3 text-sm text-slate-400">
              모든 퀘스트를 완료했습니다.
            </p>

            <button
              type="button"
              onClick={() => navigate("/")}
              className="mt-8 px-8 py-4 rounded-2xl bg-gradient-to-r from-violet-500 to-indigo-500 text-white font-black text-sm"
            >
              🏠 홈으로 돌아가기
            </button>
          </div>
        ) : loading ? (
          <div className="mt-10 rounded-3xl border border-white/10 bg-white/5 p-10 text-center">
            <p className="text-slate-400">🎲 퀘스트를 불러오는 중...</p>
          </div>
        ) : quest ? (
          <div className="mt-10 relative overflow-hidden rounded-3xl border border-violet-500/30 bg-gradient-to-br from-slate-900 to-indigo-950 p-8 shadow-2xl">
            <div className="absolute -top-20 -right-20 w-52 h-52 rounded-full bg-violet-500/10 blur-3xl" />

            <div className="relative z-10">
              <div className="flex items-center justify-between">
                <span className="px-3 py-1 rounded-full bg-violet-500/20 border border-violet-400/20 text-violet-300 text-[11px] font-black tracking-widest">
                  MISSION {String(quest.questOrder).padStart(2, "0")}
                </span>

                <span className="text-amber-300 text-xs font-bold">
                  🏆 +{quest.rewardPoint}P
                </span>
              </div>

              <div className="mt-8 text-center">
                <div className="text-5xl">🎯</div>

                <h2 className="mt-5 text-2xl md:text-3xl font-black text-white">
                  {quest.questName}
                </h2>

                <p className="mt-4 text-sm md:text-base text-slate-300 leading-7">
                  {quest.questDesc}
                </p>
              </div>

              {quest.verifyType === "GPS" &&
                quest.targetLat != null &&
                quest.targetLng != null && (
                  <div className="mt-6">
                    <MysteryQuestMapView
                      questName={quest.questName}
                      targetLat={quest.targetLat}
                      targetLng={quest.targetLng}
                      rewardPoint={quest.rewardPoint}
                      clearRadiusMeters={quest.clearRadiusMeters}
                    />
                  </div>
                )}

              <div className="mt-8 rounded-2xl bg-black/20 border border-white/10 p-5">
                <div className="flex justify-between items-center">
                  <span className="text-xs text-slate-500">인증 방법</span>

                  <span className="text-xs font-bold text-violet-300">
                    {quest.verifyType === "GPS"
                      ? "📍 GPS 위치 인증"
                      : quest.verifyType === "PHOTO"
                        ? "📸 사진 인증"
                        : "✅ 미션 완료 인증"}
                  </span>
                </div>
              </div>

              {errorMessage && (
                <div className="mt-4 rounded-2xl bg-rose-500/10 border border-rose-500/30 p-4 text-xs text-rose-300 leading-relaxed">
                  {errorMessage}
                </div>
              )}

              <button
                type="button"
                onClick={handleCompleteQuest}
                disabled={completing}
                className="mt-6 w-full py-4 rounded-2xl bg-gradient-to-r from-violet-500 to-indigo-500 text-white font-black text-sm hover:scale-[1.01] transition-all"
              >
                {completing
                  ? "⏳ 인증 처리 중..."
                  : quest.verifyType === "GPS"
                    ? "📍 현재 위치 인증하기"
                    : quest.verifyType === "PHOTO"
                      ? "📸 사진으로 인증하기"
                      : "✅ 미션 완료하기"}
              </button>
            </div>
          </div>
        ) : (
          <div className="mt-10 rounded-3xl border border-white/10 bg-white/5 p-10 text-center">
            <p className="text-slate-400">
              현재 진행할 수 있는 퀘스트가 없습니다.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
