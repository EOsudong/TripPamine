import { useEffect, useState } from "react";
import type { TravelPlan } from "../types";
import { previewReplanning } from "../api/replanning";
import type { ReplanningResponse } from "../api/replanning";
import { getUltraShortForecastApi } from "../api/weather";
import axios from "axios";
import api from "../api/axios";

type ReplanningHistoryItem = {
  historyId: number;
  planId: number;
  targetDay: number;
  reason: string;
  createdAt: string;
};

const KAKAO_JS_KEY = import.meta.env.VITE_KAKAO_MAP_JAVASCRIPT_KEY;

function loadKakaoSdk(): Promise<void> {
  return new Promise((resolve, reject) => {
    const kakao = window.kakao;

    if (kakao?.maps?.services?.Geocoder) {
      resolve();
      return;
    }

    if (!KAKAO_JS_KEY) {
      reject(new Error("카카오맵 API 키가 없습니다."));
      return;
    }

    const initialize = () => {
      if (!window.kakao?.maps?.load) {
        reject(new Error("카카오맵 SDK 초기화 실패"));
        return;
      }

      window.kakao.maps.load(() => {
        if (window.kakao?.maps?.services?.Geocoder) {
          resolve();
        } else {
          reject(new Error("카카오맵 services 라이브러리가 없습니다."));
        }
      });
    };

    const scriptId = "kakao-map-sdk";
    let script = document.getElementById(scriptId) as HTMLScriptElement | null;

    if (script) {
      if (window.kakao?.maps?.load) {
        initialize();
      } else {
        script.addEventListener("load", initialize, { once: true });
        script.addEventListener(
          "error",
          () => reject(new Error("SDK 로딩 실패")),
          { once: true },
        );
      }
      return;
    }

    script = document.createElement("script");
    script.id = scriptId;
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_JS_KEY}&libraries=services&autoload=false`;
    script.async = true;

    script.addEventListener("load", initialize, { once: true });
    script.addEventListener("error", () => reject(new Error("SDK 로딩 실패")), {
      once: true,
    });

    document.head.appendChild(script);
  });
}

async function reverseGeocode(lat: number, lng: number): Promise<string> {
  await loadKakaoSdk();

  return new Promise((resolve, reject) => {
    const kakao = window.kakao;
    const geocoder = new kakao.maps.services.Geocoder();

    geocoder.coord2Address(lng, lat, (result: any[], status: string) => {
      if (status === kakao.maps.services.Status.OK && result.length > 0) {
        const address =
          result[0].road_address?.address_name ||
          result[0].address?.address_name;

        if (address) {
          resolve(address);
        } else {
          reject(new Error("주소 정보 없음"));
        }
      } else {
        reject(new Error("주소 변환 실패"));
      }
    });
  });
}

export default function TravelReplanningModal({
  plan,
  onClose,
}: {
  plan: TravelPlan;
  onClose: () => void;
}) {
  const [placesText, setPlacesText] = useState("");
  const [location, setLocation] = useState("");
  const [gpsLoading, setGpsLoading] = useState(false);
  const [gpsMessage, setGpsMessage] = useState("");
  const [weather, setWeather] = useState("UNKNOWN");
  const [weatherMessage, setWeatherMessage] = useState("");
  const [delay, setDelay] = useState(0);
  const [result, setResult] = useState<ReplanningResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [applying, setApplying] = useState(false);
  const [histories, setHistories] = useState<ReplanningHistoryItem[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState("");
  const [restoringId, setRestoringId] = useState<number | null>(null);
  const places = placesText
    .split(/[,\n]/)
    .map((s) => s.trim())
    .filter(Boolean);

  // 장소명 비교 시 공백 차이 제거
  function normalizePlaceName(name: string) {
    return name.replace(/\s+/g, "").trim().toLowerCase();
  }

  // 기존 일정과 AI 재계획 일정 비교
  const originalPlaces = result?.originalPlaces ?? [];
  const recommendedPlaces = result?.proposal.places ?? [];

  const originalNames = originalPlaces.map(normalizePlaceName);
  const recommendedNames = recommendedPlaces.map((place) =>
    normalizePlaceName(place.name),
  );

  // AI 추천 장소의 변경 상태 확인
  function getPlaceStatus(name: string, newIndex: number) {
    const normalizedName = normalizePlaceName(name);
    const originalIndex = originalNames.indexOf(normalizedName);

    if (originalIndex === -1) {
      return {
        label: "추가",
        className: "bg-blue-100 text-blue-700",
      };
    }

    if (originalIndex !== newIndex) {
      return {
        label: "순서 변경",
        className: "bg-amber-100 text-amber-700",
      };
    }

    return {
      label: "유지",
      className: "bg-emerald-100 text-emerald-700",
    };
  }

  // 기존 일정에서 제외된 장소 확인
  const removedPlaces = originalPlaces.filter(
    (place) => !recommendedNames.includes(normalizePlaceName(place)),
  );

  async function fetchCurrentWeather(latitude: number, longitude: number) {
    setWeatherMessage("🌤️ 현재 날씨를 확인하고 있습니다...");

    try {
      const result = await getUltraShortForecastApi(latitude, longitude);
      const data = result.current;     // 위 4-1의 빈칸과 같은 이름

      if (["CLEAR", "RAIN", "SNOW", "CLOUDY"].includes(data.weather)) {
        setWeather(data.weather);
        setWeatherMessage(
          `🌤️ 현재 날씨: ${data.description} · ${data.temperature}°C`,
        );
      } else {
        setWeatherMessage("날씨 정보를 확인하지 못했습니다.");
      }
    } catch (error) {
      console.error("현재 날씨 조회 실패:", error);

      setWeatherMessage(
        "실시간 날씨 조회에 실패했습니다. 날씨를 직접 선택해주세요.",
      );
    }
  }

  async function loadHistories() {
    setHistoryLoading(true);
    setHistoryError("");
    try {
      const response = await api.get<ReplanningHistoryItem[]>(
        `/api/travel-plans/${plan.planId}/replanning/history`,
      );
      setHistories(response.data);
    } catch (err) {
      const message = axios.isAxiosError(err)
        ? err.response?.data?.message || err.message
        : "알 수 없는 오류";
      setHistoryError(`변경 이력 조회 실패: ${message}`);
    } finally {
      setHistoryLoading(false);
    }
  }

  async function restoreHistory(historyId: number) {
    if (restoringId !== null) return;
    if (
      !window.confirm(
        "이 변경 이전의 일정으로 복구할까요?\n현재 일정이 변경되며 복구 기록도 남습니다.",
      )
    )
      return;
    setRestoringId(historyId);
    setHistoryError("");
    try {
      await api.post(
        `/api/travel-plans/${plan.planId}/replanning/history/${historyId}/restore`,
      );
      window.alert("이전 일정으로 복구했어!");
      onClose();
      window.location.reload();
    } catch (err) {
      const status = axios.isAxiosError(err) ? err.response?.status : undefined;
      const message =
        status === 409
          ? "이후 일정이 변경되어 이 기록으로 바로 복구할 수 없어."
          : axios.isAxiosError(err)
            ? err.response?.data?.message || err.message
            : "알 수 없는 오류";
      setHistoryError(`일정 복구 실패: ${message}`);
    } finally {
      setRestoringId(null);
    }
  }

  useEffect(() => {
    void loadHistories();
    // planId가 바뀔 때 변경 이력을 다시 불러온다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [plan.planId]);

  useEffect(() => {
    if (!navigator.geolocation) {
      setGpsMessage(
        "GPS를 지원하지 않는 브라우저입니다. 위치를 직접 입력해주세요.",
      );
      return;
    }

    if (!window.isSecureContext) {
      setGpsMessage("GPS는 HTTPS 또는 localhost 환경에서 사용할 수 있습니다.");
      return;
    }

    setGpsLoading(true);
    setGpsMessage("현재 위치를 확인하고 있습니다...");

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const latitude = position.coords.latitude;
        const longitude = position.coords.longitude;
        void fetchCurrentWeather(latitude, longitude);

        const coordinates = `${latitude.toFixed(6)}, ${longitude.toFixed(6)}`;

        setLocation((prev) => (prev.trim() ? prev : coordinates));

        setGpsMessage("📍 GPS 확인 완료 · 주소 변환 중...");
        setGpsLoading(true);

        reverseGeocode(latitude, longitude)
          .then((address) => {
            setLocation((prev) =>
              prev === coordinates ? `${address} (${coordinates})` : prev,
            );

            setGpsMessage("📍 GPS 및 현재 주소 확인 완료");
          })
          .catch((error) => {
            console.warn("카카오맵 주소 변환 실패:", error);

            setGpsMessage("GPS 좌표는 확인했지만 주소 변환에 실패했습니다.");
          })
          .finally(() => {
            setGpsLoading(false);
          });
      },
      (error) => {
        let message = "현재 위치를 확인하지 못했습니다.";

        if (error.code === 1) {
          message = "위치 권한이 거부되었습니다.";
        } else if (error.code === 2) {
          message = "현재 위치 정보를 가져올 수 없습니다.";
        } else if (error.code === 3) {
          message = "위치 확인 시간이 초과되었습니다.";
        }

        setGpsMessage(`${message} 위치를 직접 입력해주세요.`);
        setGpsLoading(false);
      },
      {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 60000,
      },
    );
  }, []);

  async function run() {
    setError("");
    setResult(null);
    setLoading(true);
    try {
      setResult(
        await previewReplanning(plan.planId, {
          remainingPlaces: places,
          currentLocation: location.trim(),
          weather,
          delayMinutes: delay,
        }),
      );
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const status = err.response?.status;
        const message = err.response?.data?.message || err.message;

        if (
          status === 404 &&
          message.includes("기존 AI 여행 추천 일정이 없습니다")
        ) {
          setError(
            "⚠️ 아직 AI 추천 일정이 없습니다. 먼저 AI 여행 일정을 생성한 후 재계획을 이용해 주세요.",
          );
          return;
        }

        if (status === 401) {
          setError("로그인 인증이 만료되었습니다. 다시 로그인해 주세요.");
          return;
        }

        setError(`재계획 요청 실패: ${message}`);
        return;
      }

      setError("재계획 요청 중 알 수 없는 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function applyReplanning() {
    if (!result?.previewId || applying) return;

    const confirmed = window.confirm(
      "AI가 제안한 일정으로 변경하시겠습니까?\n기존 해당 날짜의 일정이 변경됩니다.",
    );

    if (!confirmed) return;

    setError("");
    setApplying(true);

    try {
      await api.post(`/api/travel-replanning/${plan.planId}/apply`, {
        previewId: result.previewId,
      });

      window.alert("AI 재계획 일정이 성공적으로 반영되었습니다.");

      onClose();
      window.location.reload();
    } catch (err) {
      const message = axios.isAxiosError(err)
        ? err.response?.data?.message || err.message
        : "알 수 없는 오류";

      setError(`일정 변경 실패: ${message}`);
    } finally {
      setApplying(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/60 p-4"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-label="여행 SOS"
        className="w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-3xl bg-white p-6 shadow-2xl"
      >
        <div className="flex items-start justify-between gap-3 mb-5">
          <div>
            <p className="text-xs font-semibold text-sky-600">
              TripPamine AI Agent · 미리보기
            </p>
            <h2 className="text-xl font-extrabold text-slate-900">
              🆘 여행 SOS
            </h2>
            <p className="text-sm text-slate-500 mt-1">
              {plan.planName} · 총 예산{" "}
              {Number(plan.totalBudget).toLocaleString()}원
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-slate-500 hover:text-slate-900 text-2xl"
            aria-label="닫기"
          >
            ×
          </button>
        </div>
        <div className="space-y-4">
          <label className="block text-sm font-semibold text-slate-700">
            남은 방문 장소 (쉼표 또는 줄바꿈 구분)
            <textarea
              className="mt-1 w-full min-h-24 rounded-xl border border-slate-200 p-3 font-normal outline-none focus:border-sky-400"
              placeholder="경복궁, 북촌한옥마을, 인사동"
              value={placesText}
              onChange={(e) => setPlacesText(e.target.value)}
            />
          </label>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <label className="text-sm font-semibold text-slate-700">
              현재 위치 (GPS 자동 인식)
              <input
                className="mt-1 w-full rounded-xl border border-slate-200 p-2.5 font-normal"
                placeholder="GPS 확인 중 또는 위치 직접 입력"
                value={location}
                onChange={(e) => setLocation(e.target.value)}
              />
              <span
                className={`mt-1 block text-xs font-normal ${
                  gpsLoading ? "text-sky-600" : "text-slate-500"
                }`}
              >
                {gpsMessage}
              </span>
            </label>
            <label className="text-sm font-semibold text-slate-700">
              현재 날씨 (자동 조회)
              <select
                className="mt-1 w-full rounded-xl border border-slate-200 p-2.5 font-normal"
                value={weather}
                onChange={(e) => setWeather(e.target.value)}
              >
                <option value="UNKNOWN">날씨 확인 안 됨</option>
                <option value="RAIN">비</option>
                <option value="CLEAR">맑음</option>
                <option value="SNOW">눈</option>
                <option value="CLOUDY">흐림</option>
              </select>
              <span className="mt-1 block text-xs font-normal text-sky-600">
                {weatherMessage}
              </span>
            </label>
            <label className="text-sm font-semibold text-slate-700">
              일정 지연 (분)
              <input
                type="number"
                min="0"
                max="1440"
                className="mt-1 w-full rounded-xl border border-slate-200 p-2.5 font-normal"
                value={delay}
                onChange={(e) =>
                  setDelay(Math.max(0, Number(e.target.value) || 0))
                }
              />
            </label>
          </div>
          <p className="text-xs text-amber-700 bg-amber-50 rounded-xl p-3">
            GPS 위치는 브라우저에서 확인한 좌표입니다. 남은 방문 장소는 입력하지
            않으면 기존 여행 일정에서 자동 조회합니다. 날씨와 일정 지연은 아직
            테스트 입력값이며, 실제 이동 시간과 영업시간은 확인하지 않습니다.
          </p>
          <div className="rounded-2xl border border-slate-200 p-4 space-y-3">
            <div className="flex items-center justify-between gap-2">
              <h3 className="font-bold text-slate-900">
                📋 AI 재계획 변경 이력
              </h3>
              <button
                type="button"
                onClick={() => void loadHistories()}
                disabled={historyLoading}
                className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold hover:bg-slate-50 disabled:opacity-50"
              >
                {historyLoading ? "불러오는 중..." : "새로고침"}
              </button>
            </div>
            {historyError && (
              <p
                role="alert"
                className="rounded-lg bg-red-50 p-3 text-sm text-red-700"
              >
                {historyError}
              </p>
            )}
            {!historyLoading && !historyError && histories.length === 0 && (
              <p className="text-sm text-slate-500">
                아직 AI 재계획 변경 이력이 없어.
              </p>
            )}
            {histories.map((history) => (
              <div
                key={history.historyId}
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-slate-50 p-3"
              >
                <div className="text-sm">
                  <p className="font-semibold text-slate-800">
                    {history.targetDay}일차 · {history.reason || "일정 변경"}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    {new Date(history.createdAt).toLocaleString("ko-KR")}
                  </p>
                </div>
                <button
                  type="button"
                  disabled={restoringId !== null || loading || applying}
                  onClick={() => void restoreHistory(history.historyId)}
                  className="rounded-lg bg-amber-100 px-3 py-2 text-xs font-bold text-amber-900 hover:bg-amber-200 disabled:opacity-50"
                >
                  {restoringId === history.historyId
                    ? "복구 중..."
                    : "이전 일정 복구"}
                </button>
              </div>
            ))}
            <p className="text-xs text-slate-500">
              ※ 현재 일정과 해당 변경 이력의 결과가 일치할 때만 복구할 수 있어.
            </p>
          </div>
          <button
            disabled={loading}
            onClick={run}
            className="w-full rounded-xl bg-sky-600 py-3 text-white font-bold hover:bg-sky-700 disabled:opacity-50"
          >
            {loading ? "AI가 재계획 중입니다..." : "🆘 여행 SOS 미리보기"}
          </button>
          {error && (
            <p
              role="alert"
              className="rounded-xl bg-red-50 p-3 text-sm text-red-700"
            >
              {error}
            </p>
          )}
          {result && (
            <div className="border-t border-slate-200 pt-5 space-y-4">
              <h3 className="text-lg font-bold">AI 변경 제안</h3>
              <div className="grid grid-cols-3 gap-2 text-center text-xs">
                <div className="rounded-xl bg-slate-50 p-3">
                  총 예산
                  <br />
                  <strong>
                    {Number(result.totalBudget).toLocaleString()}원
                  </strong>
                </div>
                <div className="rounded-xl bg-slate-50 p-3">
                  기존 지출
                  <br />
                  <strong>
                    {Number(result.spentAmount).toLocaleString()}원
                  </strong>
                </div>
                <div className="rounded-xl bg-emerald-50 p-3">
                  남은 예산
                  <br />
                  <strong>
                    {Number(result.remainingBudget).toLocaleString()}원
                  </strong>
                </div>
              </div>
              <p className="rounded-xl bg-sky-50 p-3 text-sm text-sky-900">
                {result.proposal.reason}
              </p>
              <div className="grid sm:grid-cols-2 gap-3">
                <div className="rounded-xl border border-slate-200 p-4">
                  <h4 className="font-bold mb-2">📍 기존 여행 일정</h4>

                  {result.originalPlaces?.length > 0 ? (
                    result.originalPlaces.map((place, i) => (
                      <p key={i} className="py-1 text-sm text-slate-600">
                        {i + 1}. {place}
                      </p>
                    ))
                  ) : (
                    <p className="text-sm text-slate-400">
                      기존 여행 장소가 없습니다.
                    </p>
                  )}
                </div>
                <div className="rounded-xl border border-sky-200 bg-sky-50/40 p-4">
                  <h4 className="font-bold mb-2">AI 추천 장소</h4>
                  {result.proposal.places?.map((p, i) => {
                    const status = getPlaceStatus(p.name, i);

                    return (
                      <div
                        key={i}
                        className="py-2 border-b border-sky-100 last:border-0"
                      >
                        <div className="flex items-center justify-between gap-2">
                          <p className="font-semibold text-sm">
                            {i + 1}. {p.name}
                          </p>

                          <span
                            className={`shrink-0 rounded-full px-2 py-1 text-xs font-bold ${status.className}`}
                          >
                            {status.label}
                          </span>
                        </div>

                        <p className="text-xs text-slate-500 mt-1">
                          예상 비용: {Number(p.estimatedCost).toLocaleString()}
                          원
                        </p>

                        <p className="text-xs text-slate-500 mt-1">
                          {p.description}
                        </p>
                      </div>
                    );
                  })}
                </div>
              </div>
              {removedPlaces.length > 0 && (
                <div className="rounded-xl border border-red-200 bg-red-50 p-4">
                  <h4 className="mb-2 font-bold text-red-700">
                    ❌ 재계획에서 제외된 장소
                  </h4>

                  {removedPlaces.map((place, i) => (
                    <p key={i} className="py-1 text-sm text-red-600">
                      • {place}
                    </p>
                  ))}
                </div>
              )}
              <p className="text-right font-bold text-sky-700">
                변경안 예상 비용:{" "}
                {Number(result.proposal.estimatedTotal).toLocaleString()}원
              </p>
              {!!result.proposal.cautions?.length && (
                <div className="text-xs text-amber-800 bg-amber-50 rounded-xl p-3">
                  {result.proposal.cautions.map((c, i) => (
                    <p key={i}>※ {c}</p>
                  ))}
                </div>
              )}
              <button
                type="button"
                disabled={applying || loading || !result.previewId}
                onClick={applyReplanning}
                className="w-full rounded-xl bg-emerald-600 py-3 text-white font-bold hover:bg-emerald-700 disabled:opacity-50"
              >
                {applying
                  ? "여행 일정을 저장하고 있습니다..."
                  : "✅ 이 일정으로 변경하기"}
              </button>

              <p className="text-xs text-slate-500">
                ※ 변경을 확정하면 현재 여행 날짜의 일정이 AI 추천 장소로
                교체됩니다.
              </p>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
