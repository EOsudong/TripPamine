import { useCallback, useEffect, useRef, useState } from "react";
import {
  MASCOT_RARITY_COLOR,
  MASCOT_RARITY_LABEL,
  extractMascotErrorMessage,
  extractMascotErrorStatus,
  hasAndroidArCatch,
  mascotApi,
  resolveMascotImageUrl,
  startAndroidArCatch,
  type MascotCatchResponse,
  type MascotEncounterResponse,
} from "../api/mascot";

// 마스코트 포획 미니게임 (웹 버전).
//
// 규칙: 마스코트 주변의 "조준 링"이 커졌다 작아졌다를 반복한다. 링이 작을 때 던질수록 점수가 높다(0~100).
// 점수는 서버로 보내고, 실제 포획 여부는 서버(MascotCatchJudge)가 희귀도 + 점수로 확률 판정한다.

interface MascotCatchModalProps {
  encounter: MascotEncounterResponse;
  onClose: (changed: boolean) => void;
}

type Phase = "aiming" | "throwing" | "escaped" | "caught" | "failed" | "expired";

const RING_MIN = 0.32;
const RING_MAX = 1;

// 희귀도가 높을수록 링이 빨리 움직인다 (한 번 줄었다 커지는 주기, ms)
const RING_PERIOD_MS: Record<MascotEncounterResponse["mascotRarity"], number> = {
  COMMON: 1700,
  RARE: 1250,
  EPIC: 900,
};

function scoreLabel(score: number) {
  if (score >= 90) return "Excellent!";
  if (score >= 70) return "Great!";
  if (score >= 40) return "Nice!";
  return "Miss…";
}

export default function MascotCatchModal({ encounter, onClose }: MascotCatchModalProps) {
  const [phase, setPhase] = useState<Phase>("aiming");
  const [secondsLeft, setSecondsLeft] = useState(encounter.expiresInSeconds);
  const [attemptsLeft, setAttemptsLeft] = useState(encounter.maxAttempts);
  const [lastScore, setLastScore] = useState<number | null>(null);
  const [result, setResult] = useState<MascotCatchResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cameraOn, setCameraOn] = useState(false);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [imageBroken, setImageBroken] = useState(false);

  const ringRef = useRef<HTMLDivElement>(null);
  const ringScaleRef = useRef(RING_MAX);
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const changedRef = useRef(false);

  const rarityColor = MASCOT_RARITY_COLOR[encounter.mascotRarity];
  const imageUrl = resolveMascotImageUrl(encounter.mascotImageUrl);
  const finished = phase === "caught" || phase === "failed" || phase === "expired";

  // ---------- 조우 토큰 카운트다운 ----------
  useEffect(() => {
    if (finished) return;
    const timer = window.setInterval(() => {
      setSecondsLeft((current) => Math.max(0, current - 1));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [finished]);

  useEffect(() => {
    if (secondsLeft === 0 && (phase === "aiming" || phase === "escaped")) {
      setPhase("expired");
    }
  }, [secondsLeft, phase]);

  // ---------- 조준 링 애니메이션 (requestAnimationFrame) ----------
  useEffect(() => {
    if (phase !== "aiming" && phase !== "escaped") return;
    let frame = 0;
    const period = RING_PERIOD_MS[encounter.mascotRarity];
    const startedAt = performance.now();

    const tick = (now: number) => {
      // 0 → 1 → 0 로 부드럽게 왕복하는 값
      const wave = (1 - Math.cos(((now - startedAt) / period) * Math.PI * 2)) / 2;
      const scale = RING_MAX - (RING_MAX - RING_MIN) * wave;
      ringScaleRef.current = scale;
      if (ringRef.current) {
        ringRef.current.style.transform = `translate(-50%, -50%) scale(${scale})`;
        ringRef.current.style.borderColor = scale < 0.5 ? "#facc15" : scale < 0.75 ? "#a3e635" : "rgba(255,255,255,0.85)";
      }
      frame = requestAnimationFrame(tick);
    };

    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [phase, encounter.mascotRarity]);

  // ---------- 카메라 (의사 AR 배경) ----------
  const stopCamera = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    setCameraOn(false);
  }, []);

  useEffect(() => stopCamera, [stopCamera]);

  async function toggleCamera() {
    if (cameraOn) {
      stopCamera();
      return;
    }
    setCameraError(null);
    try {
      if (!navigator.mediaDevices?.getUserMedia) {
        throw new Error("unsupported");
      }
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: "environment" } },
        audio: false,
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play().catch(() => undefined);
      }
      setCameraOn(true);
    } catch {
      setCameraError("카메라를 사용할 수 없어요. (HTTPS 접속과 카메라 권한이 필요합니다)");
    }
  }

  // ---------- 포획 확정 ----------
  const submitScore = useCallback(
    async (score: number) => {
      setLastScore(score);
      setPhase("throwing");
      setError(null);

      // 공이 날아가는 연출 시간
      await new Promise((resolve) => window.setTimeout(resolve, 750));

      try {
        const response = await mascotApi.catchMascot(encounter.spawnId, {
          encounterToken: encounter.encounterToken,
          score,
        });
        setResult(response);

        if (response.caught) {
          changedRef.current = true;
          setPhase("caught");
        } else if (response.remainingAttempts > 0) {
          setAttemptsLeft(response.remainingAttempts);
          setPhase("escaped");
        } else {
          setAttemptsLeft(0);
          setPhase("failed");
        }
      } catch (err) {
        // 409: 토큰 만료 / 이미 처리됨 → 다시 조우해야 함
        if (extractMascotErrorStatus(err) === 409) {
          setPhase("expired");
          setError(extractMascotErrorMessage(err, "포획 시간이 지났어요. 다시 다가가서 조우해주세요."));
        } else {
          setPhase("escaped");
          setError(extractMascotErrorMessage(err, "포획 요청 중 오류가 발생했어요. 다시 던져보세요."));
        }
      }
    },
    [encounter.encounterToken, encounter.spawnId],
  );

  function handleThrow() {
    if (phase !== "aiming" && phase !== "escaped") return;
    const scale = ringScaleRef.current;
    const score = Math.round(((RING_MAX - scale) / (RING_MAX - RING_MIN)) * 100);
    submitScore(Math.max(0, Math.min(100, score)));
  }

  function handleAndroidAr() {
    stopCamera();
    startAndroidArCatch(encounter, (score) => {
      submitScore(score);
    });
  }

  function close() {
    stopCamera();
    window.onArCatchResult = undefined;
    onClose(changedRef.current);
  }

  const canThrow = phase === "aiming" || phase === "escaped";

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/80 p-3">
      <style>{`
        @keyframes tp-mascot-bob { 0%,100% { transform: translateY(0) } 50% { transform: translateY(-10px) } }
        @keyframes tp-mascot-shake { 0%,100% { transform: translateX(0) } 25% { transform: translateX(-10px) rotate(-6deg) } 75% { transform: translateX(10px) rotate(6deg) } }
        @keyframes tp-ball-throw { 0% { transform: translate(-50%, 0) scale(1); opacity: 1 } 80% { transform: translate(-50%, -230px) scale(.55); opacity: 1 } 100% { transform: translate(-50%, -230px) scale(.2); opacity: 0 } }
        @keyframes tp-pop { 0% { transform: scale(.6); opacity: 0 } 60% { transform: scale(1.08); opacity: 1 } 100% { transform: scale(1) } }
        .tp-mascot-bob { animation: tp-mascot-bob 1.6s ease-in-out infinite }
        .tp-mascot-shake { animation: tp-mascot-shake .45s ease-in-out 2 }
        .tp-ball-throw { animation: tp-ball-throw .75s ease-out forwards }
        .tp-pop { animation: tp-pop .45s ease-out both }
      `}</style>

      <div className="relative flex w-full max-w-md flex-col overflow-hidden rounded-[2rem] border border-white/10 bg-night-950 text-white shadow-2xl">
        {/* 상단 정보 */}
        <div className="flex items-center justify-between gap-3 px-5 pt-5">
          <div className="min-w-0">
            <span
              className="rounded-full px-2 py-0.5 text-[10px] font-black"
              style={{ background: `${rarityColor}33`, color: rarityColor }}
            >
              {MASCOT_RARITY_LABEL[encounter.mascotRarity]}
            </span>
            <h2 className="mt-1 truncate text-lg font-black">{encounter.mascotName}</h2>
            <p className="text-xs text-white/50">
              {encounter.alreadyCollected
                ? "이미 도감에 있는 마스코트예요 · 포인트 없음"
                : `첫 포획 보상 +${encounter.rewardPoint.toLocaleString()}P`}
            </p>
          </div>
          <button
            type="button"
            onClick={close}
            aria-label="닫기"
            className="h-9 w-9 shrink-0 rounded-full bg-white/10 text-white/70 hover:bg-white/20"
          >
            ✕
          </button>
        </div>

        {/* 포획 무대 */}
        <div
          className="relative mx-5 mt-4 h-80 overflow-hidden rounded-3xl"
          style={{
            background: cameraOn
              ? "#000"
              : `radial-gradient(circle at 50% 40%, ${rarityColor}40, transparent 60%), linear-gradient(180deg, #13132a, #06060c)`,
          }}
          onClick={canThrow ? handleThrow : undefined}
        >
          <video
            ref={videoRef}
            playsInline
            muted
            className={`absolute inset-0 h-full w-full object-cover ${cameraOn ? "opacity-100" : "opacity-0"}`}
          />

          {/* 마스코트 */}
          {phase !== "caught" && (
            <div className="absolute left-1/2 top-[42%] -translate-x-1/2 -translate-y-1/2">
              <div className={phase === "escaped" ? "tp-mascot-shake" : "tp-mascot-bob"}>
                {imageUrl && !imageBroken ? (
                  <img
                    src={imageUrl}
                    alt={encounter.mascotName}
                    onError={() => setImageBroken(true)}
                    className="h-36 w-36 object-contain drop-shadow-[0_12px_20px_rgba(0,0,0,0.6)]"
                    draggable={false}
                  />
                ) : (
                  <div
                    className="flex h-36 w-36 items-center justify-center rounded-full text-6xl"
                    style={{ background: `${rarityColor}33`, color: rarityColor }}
                  >
                    ✦
                  </div>
                )}
              </div>
            </div>
          )}

          {/* 조준 링 */}
          {canThrow && (
            <div
              ref={ringRef}
              className="pointer-events-none absolute left-1/2 top-[42%] h-48 w-48 rounded-full border-4"
              style={{ transform: "translate(-50%, -50%) scale(1)", boxShadow: "0 0 18px rgba(255,255,255,0.35)" }}
            />
          )}

          {/* 던지는 공 */}
          {phase === "throwing" && (
            <div className="tp-ball-throw absolute bottom-6 left-1/2 h-12 w-12 rounded-full border-4 border-white bg-gradient-to-b from-rose-500 from-50% to-white to-50% shadow-lg" />
          )}

          {/* 점수 표시 */}
          {lastScore != null && phase === "throwing" && (
            <p className="tp-pop absolute left-1/2 top-4 -translate-x-1/2 rounded-full bg-black/60 px-4 py-1 text-sm font-black text-yellow-300">
              {scoreLabel(lastScore)} {lastScore}점
            </p>
          )}

          {/* 포획 성공 */}
          {phase === "caught" && result && (
            <div className="tp-pop absolute inset-0 flex flex-col items-center justify-center gap-2 bg-black/40 text-center">
              {imageUrl && !imageBroken && <img src={imageUrl} alt="" className="h-24 w-24 object-contain" />}
              {result.firstCatch ? (
                <>
                  <p className="text-xs font-black tracking-[0.3em] text-yellow-300">NEW!</p>
                  <p className="text-2xl font-black">도감에 등록됐어요</p>
                  <p className="text-lg font-black text-emerald-300">+{result.rewardPoint.toLocaleString()}P</p>
                </>
              ) : (
                <>
                  <p className="text-2xl font-black">또 만났어요!</p>
                  <p className="text-sm text-white/70">{result.catchCount}번째 포획 · 재포획은 포인트가 없어요</p>
                </>
              )}
              <p className="text-xs text-white/50">보유 포인트 {result.totalPoints.toLocaleString()}P</p>
            </div>
          )}

          {/* 실패/만료 */}
          {(phase === "failed" || phase === "expired") && (
            <div className="tp-pop absolute inset-0 flex flex-col items-center justify-center gap-2 bg-black/60 px-6 text-center">
              <p className="text-2xl font-black">{phase === "failed" ? "놓쳤어요…" : "시간이 지났어요"}</p>
              <p className="text-sm text-white/70">
                {phase === "failed"
                  ? "기회를 모두 썼어요. 지도로 돌아가 다시 조우해보세요."
                  : "마스코트가 떠났어요. 반경 안에서 다시 조우해주세요."}
              </p>
            </div>
          )}

          {canThrow && (
            <p className="absolute bottom-3 left-0 right-0 text-center text-xs font-semibold text-white/60">
              링이 가장 작을 때 화면을 터치하세요
            </p>
          )}
        </div>

        {/* 상태 줄 */}
        {!finished && (
          <div className="mx-5 mt-3 flex items-center justify-between text-xs font-semibold text-white/60">
            <span>
              남은 시간 <b className={secondsLeft <= 10 ? "text-rose-300" : "text-white"}>{secondsLeft}초</b>
            </span>
            <span>
              남은 기회{" "}
              {Array.from({ length: encounter.maxAttempts }).map((_, index) => (
                <span key={index} className={index < attemptsLeft ? "text-rose-400" : "text-white/20"}>
                  ●
                </span>
              ))}
            </span>
          </div>
        )}

        {phase === "escaped" && !error && (
          <p className="mx-5 mt-2 rounded-xl bg-amber-400/10 px-3 py-2 text-center text-xs font-semibold text-amber-200">
            앗, 도망쳤어요! 한 번 더 던져보세요.
          </p>
        )}
        {error && (
          <p className="mx-5 mt-2 rounded-xl bg-rose-500/10 px-3 py-2 text-center text-xs font-semibold text-rose-200">
            {error}
          </p>
        )}
        {cameraError && !finished && (
          <p className="mx-5 mt-2 text-center text-[11px] text-white/40">{cameraError}</p>
        )}

        {/* 하단 버튼 */}
        <div className="flex flex-col gap-2 p-5">
          {finished ? (
            <button
              type="button"
              onClick={close}
              className="rounded-2xl bg-cyan-400 py-3.5 text-sm font-black text-night-950 hover:bg-cyan-300"
            >
              {phase === "caught" ? "도감에 담기" : "지도로 돌아가기"}
            </button>
          ) : (
            <>
              <button
                type="button"
                onClick={handleThrow}
                disabled={!canThrow}
                className="rounded-2xl bg-rose-500 py-3.5 text-sm font-black text-white hover:bg-rose-400 disabled:bg-white/10 disabled:text-white/40"
              >
                {phase === "throwing" ? "던지는 중..." : "볼 던지기"}
              </button>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={toggleCamera}
                  disabled={!canThrow}
                  className="flex-1 rounded-2xl bg-white/10 py-2.5 text-xs font-bold text-white/80 hover:bg-white/15 disabled:opacity-40"
                >
                  {cameraOn ? "카메라 끄기" : "📷 카메라로 보기"}
                </button>
                {hasAndroidArCatch() && (
                  <button
                    type="button"
                    onClick={handleAndroidAr}
                    disabled={!canThrow}
                    className="flex-1 rounded-2xl bg-white/10 py-2.5 text-xs font-bold text-white/80 hover:bg-white/15 disabled:opacity-40"
                  >
                    앱 AR로 포획
                  </button>
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
