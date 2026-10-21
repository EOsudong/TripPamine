import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useLocation, useNavigate } from "react-router-dom";
import {
  ArrowRight,
  ArrowUpRight,
  CalendarDays,
  Check,
  ChevronDown,
  CloudSun,
  Compass,
  Gift,
  LifeBuoy,
  Loader2,
  LocateFixed,
  Map,
  MapPin,
  Plus,
  RefreshCw,
  Route,
  Sparkles,
  Users,
  Wallet,
} from "lucide-react";
import type { TravelPlan } from "../types";
import { useAuth } from "../context/AuthContext";
import AppLayout from "../layout/AppLayout";
import { getTravelPlansApi } from "../api/travel";
import {
  getAiRecommendationApi,
  regenerateAiRecommendationApi,
  regenerateDayRecommendationApi,
  saveCustomRecommendationApi,
  type AiRecommendationResponse,
} from "../api/recommendation";
import { KakaoMapModal, type PlaceItem } from "../components/KakaoMapModal";
import TravelReplanningModal from "../components/TravelReplanningModal";
import AiRecommendDealsHub from "../components/AiRecommendDealsHub";
import {
  getTripWeatherApi,
  getUltraShortForecastApi,
  type TripDayWeather,
  type UltraShortForecastResult,
} from "../api/weather";
import "./AiRecommendPage.css";

interface RecommendedPlace {
  name: string;
  description: string;
  estimatedCost: number;
}
interface ParsedRecommendation {
  title: string;
  summary: string;
  estimatedCost: number;
  days: { day: number; places: RecommendedPlace[] }[];
}
interface RecommendationResult {
  response: AiRecommendationResponse;
  parsed: ParsedRecommendation;
}
type PendingAction = "load" | "regenerate" | "save" | null;

/** 공통 레이아웃을 사용하고 계정 전환 시 여행 상태를 초기화한다. */
export default function AiRecommendPage() {
  const { isLoggedIn, userId } = useAuth();
  return (
    <AppLayout pageClassName="ar-theme" appearance="dark">
      <RecommendationWorkspace
        key={userId ?? "guest"}
        isLoggedIn={isLoggedIn}
      />
    </AppLayout>
  );
}

function RecommendationWorkspace({ isLoggedIn }: { isLoggedIn: boolean }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [plans, setPlans] = useState<TravelPlan[]>([]);
  const [selectedPlan, setSelectedPlan] = useState<TravelPlan | null>(null);
  const [loading, setLoading] = useState(isLoggedIn);
  const [planError, setPlanError] = useState("");
  const [reloadCount, setReloadCount] = useState(0);
  const [result, setResult] = useState<RecommendationResult | null>(null);
  const [pendingAction, setPendingAction] = useState<PendingAction>(null);
  const [recommendError, setRecommendError] = useState("");
  const [notice, setNotice] = useState("");
  const [confirmRegenerate, setConfirmRegenerate] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(true);
  const [helpOpen, setHelpOpen] = useState(false);
  const [tripWeather, setTripWeather] = useState<TripDayWeather[] | null>(null);
  const [weatherLoading, setWeatherLoading] = useState(false);
  const [weatherError, setWeatherError] = useState("");
  const [ultraShort, setUltraShort] = useState<UltraShortForecastResult | null>(
    null,
  );
  const [geoLoading, setGeoLoading] = useState(false);
  const [geoError, setGeoError] = useState("");
  const [isMapOpen, setIsMapOpen] = useState(false);
  const [isSosOpen, setIsSosOpen] = useState(false);
  const selectionRef = useRef(0);
  const selectedIdRef = useRef<number | null>(null);
  const pendingRef = useRef<PendingAction>(null);
  const geoRequestRef = useRef(0);
  const weatherRequestRef = useRef(0);
  const geoBusyRef = useRef(false);
  const resultHeadingRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    let cancelled = false;
    if (!isLoggedIn) return;
    setLoading(true);
    setPlanError("");
    void getTravelPlansApi()
      .then((data) => {
        if (!cancelled) setPlans(data);
      })
      .catch(() => {
        if (!cancelled)
          setPlanError("여행 목록을 불러오지 못했어요. 다시 시도해 주세요.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [isLoggedIn, reloadCount]);

  useEffect(
    () => () => {
      selectionRef.current += 1;
      geoRequestRef.current += 1;
    },
    [],
  );

  // 모바일 목록이 접힌 뒤에도 선택한 일정에서 키보드 탐색을 이어간다.
  useEffect(() => {
    if (!selectedPlan) return;
    resultHeadingRef.current?.focus({ preventScroll: true });
    if (window.matchMedia("(max-width: 900px)").matches)
      resultHeadingRef.current?.scrollIntoView({ block: "start" });
  }, [selectedPlan?.planId]);

  // 지도와 SOS는 공통 헤더보다 위에 열고, 닫으면 호출한 버튼으로 포커스를 돌려준다.
  useEffect(() => {
    if (!isMapOpen && !isSosOpen) return;
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previousOverflow;
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected)
        previousFocus.focus({ preventScroll: true });
    };
  }, [isMapOpen, isSosOpen]);

  const loadWeather = async (planId: number, selection: number) => {
    const request = ++weatherRequestRef.current;
    setWeatherLoading(true);
    setWeatherError("");
    try {
      const weather = await getTripWeatherApi(planId);
      if (
        selection === selectionRef.current &&
        request === weatherRequestRef.current
      )
        setTripWeather(weather);
    } catch {
      if (
        selection === selectionRef.current &&
        request === weatherRequestRef.current
      )
        setWeatherError(
          "여행지 날씨를 불러오지 못했어요. 추천 일정은 계속 확인할 수 있어요.",
        );
    } finally {
      if (
        selection === selectionRef.current &&
        request === weatherRequestRef.current
      )
        setWeatherLoading(false);
    }
  };

  const handleSelectPlan = async (plan: TravelPlan, retry = false) => {
    if (pendingRef.current === "save" || pendingRef.current === "regenerate")
      return;
    if (
      selectedIdRef.current === plan.planId &&
      (pendingRef.current === "load" || (!retry && result))
    )
      return;
    const selection = ++selectionRef.current;
    selectedIdRef.current = plan.planId;
    pendingRef.current = "load";
    geoRequestRef.current += 1;
    geoBusyRef.current = false;
    setSelectedPlan(plan);
    setResult(null);
    setRecommendError("");
    setNotice("");
    setPendingAction("load");
    setTripWeather(null);
    setUltraShort(null);
    setGeoError("");
    setGeoLoading(false);
    setConfirmRegenerate(false);
    setPickerOpen(false);
    setHelpOpen(false);
    setIsMapOpen(false);
    setIsSosOpen(false);
    void loadWeather(plan.planId, selection);
    try {
      const response = await getAiRecommendationApi(plan.planId);
      if (selection !== selectionRef.current) return;
      setResult({
        response,
        parsed: parseRecommendation(response.recommendJson),
      });
    } catch (error) {
      if (selection === selectionRef.current)
        setRecommendError(
          error instanceof SyntaxError
            ? "추천 일정의 형식을 읽지 못했어요. 다시 불러오거나 새 추천을 요청해 주세요."
            : "추천 일정을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
        );
    } finally {
      if (selection === selectionRef.current) {
        pendingRef.current = null;
        setPendingAction(null);
      }
    }
  };

  const handleRegenerate = async () => {
    if (!selectedPlan || pendingRef.current) return;
    const selection = selectionRef.current;
    pendingRef.current = "regenerate";
    setPendingAction("regenerate");
    setConfirmRegenerate(false);
    setRecommendError("");
    setNotice("");
    setResult(null);
    try {
      const response = await regenerateAiRecommendationApi(selectedPlan.planId);
      if (selection !== selectionRef.current) return;
      setResult({
        response,
        parsed: parseRecommendation(response.recommendJson),
      });
      setNotice("새 추천 일정이 준비됐어요.");
      void loadWeather(selectedPlan.planId, selection);
    } catch (error) {
      if (selection === selectionRef.current)
        setRecommendError(
          error instanceof SyntaxError
            ? "새 추천 일정의 형식을 읽지 못했어요. 다시 불러오거나 새 추천을 요청해 주세요."
            : "새 추천 결과를 확인하지 못했어요. 다시 불러오기를 눌러 저장된 결과를 확인해 주세요.",
        );
    } finally {
      if (selection === selectionRef.current) {
        pendingRef.current = null;
        setPendingAction(null);
      }
    }
  };

  // 위치 권한은 사용자가 현재 위치 날씨를 요청한 경우에만 묻는다.
  const loadUltraShort = () => {
    if (geoBusyRef.current) return;
    if (!navigator.geolocation) {
      setGeoError("이 환경에서는 현재 위치를 확인할 수 없어요.");
      return;
    }
    const request = ++geoRequestRef.current;
    const selection = selectionRef.current;
    geoBusyRef.current = true;
    setGeoLoading(true);
    setGeoError("");
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        if (
          request !== geoRequestRef.current ||
          selection !== selectionRef.current
        )
          return;
        try {
          const weather = await getUltraShortForecastApi(
            position.coords.latitude,
            position.coords.longitude,
          );
          if (
            request === geoRequestRef.current &&
            selection === selectionRef.current
          )
            setUltraShort(weather);
        } catch {
          if (
            request === geoRequestRef.current &&
            selection === selectionRef.current
          )
            setGeoError(
              "현재 위치의 날씨를 불러오지 못했어요. 다시 시도해 주세요.",
            );
        } finally {
          if (
            request === geoRequestRef.current &&
            selection === selectionRef.current
          ) {
            geoBusyRef.current = false;
            setGeoLoading(false);
          }
        }
      },
      (error) => {
        if (
          request !== geoRequestRef.current ||
          selection !== selectionRef.current
        )
          return;
        geoBusyRef.current = false;
        setGeoLoading(false);
        setGeoError(
          error.code === error.PERMISSION_DENIED
            ? "위치 접근이 허용되지 않았어요. 브라우저의 위치 권한을 확인해 주세요."
            : "현재 위치를 확인하지 못했어요. 다시 시도해 주세요.",
        );
      },
      { timeout: 10000, maximumAge: 300000 },
    );
  };

  const handleSavePlaces = async (updatedPlaces: PlaceItem[]) => {
    if (!result || !selectedPlan || pendingRef.current) return;
    const selection = selectionRef.current;
    pendingRef.current = "save";
    setPendingAction("save");
    setRecommendError("");
    setNotice("");
    setIsMapOpen(false);
    const dayMap: Record<number, RecommendedPlace[]> = {};
    updatedPlaces.forEach((place) => {
      const day = place.day || 1;
      const existing = result.parsed.days
        .flatMap((item) => item.places)
        .find((item) => item.name === place.name);
      (dayMap[day] ??= []).push({
        name: place.name,
        description:
          existing?.description || place.address || "사용자 추가 장소",
        estimatedCost: existing?.estimatedCost || 0,
      });
    });
    const updatedParsed: ParsedRecommendation = {
      ...result.parsed,
      title: result.parsed.title.startsWith("(수정)")
        ? result.parsed.title
        : `(수정) ${result.parsed.title}`,
      days: Object.keys(dayMap).map((day) => ({
        day: Number(day),
        places: dayMap[Number(day)],
      })),
    };
    let saved = false;
    try {
      const response = await saveCustomRecommendationApi(
        selectedPlan.planId,
        JSON.stringify(updatedParsed),
      );
      saved = true;
      if (selection !== selectionRef.current) return;
      const parsed = parseRecommendation(response.recommendJson);
      const updatedPlans = await getTravelPlansApi();
      if (selection !== selectionRef.current) return;
      setPlans(updatedPlans);
      const newPlan = updatedPlans.find(
        (plan) => plan.planId === response.planId,
      );
      if (!newPlan) throw new Error("저장된 여행을 목록에서 찾지 못했습니다.");
      selectedIdRef.current = newPlan.planId;
      setSelectedPlan(newPlan);
      setResult({ response, parsed });
      setNotice("수정한 경로를 새 여행으로 저장했어요.");
      setTripWeather(null);
      void loadWeather(newPlan.planId, selection);
    } catch {
      if (selection !== selectionRef.current) return;
      if (saved) {
        selectedIdRef.current = null;
        setSelectedPlan(null);
        setResult(null);
        setPickerOpen(true);
        setPlanError(
          "경로는 저장했지만 여행 목록을 갱신하지 못했어요. 목록을 다시 불러와 주세요.",
        );
        setNotice("수정 경로 저장은 완료됐어요. 다시 저장할 필요는 없어요.");
      } else {
        setRecommendError(
          "경로 저장을 확인하지 못했어요. 여행 목록에서 저장 여부를 확인한 뒤 다시 시도해 주세요.",
        );
      }
    } finally {
      if (selection === selectionRef.current) {
        pendingRef.current = null;
        setPendingAction(null);
      }
    }
  };

  const handleRegenerateDay = async (
    dayNum: number,
    currentPlaces: PlaceItem[],
  ): Promise<PlaceItem[]> => {
    if (!selectedPlan) return [];
    const response = await regenerateDayRecommendationApi(
      selectedPlan.planId,
      dayNum,
      currentPlaces.map((place) => place.name),
    );
    return response.places.map((place) => ({
      name: place.name,
      address: place.description,
      day: dayNum,
    }));
  };
  const mapPlaces = useMemo(
    () =>
      result?.parsed.days.flatMap((day) =>
        day.places.map((place) => ({
          name: place.name,
          address: place.name,
          day: day.day,
        })),
      ) ?? [],
    [result],
  );
  const mutating = pendingAction === "regenerate" || pendingAction === "save";
  const canUseItinerary = Boolean(result && !pendingAction);
  const openLogin = () => navigate("/login", { state: { from: location } });

  return (
    <>
      <div className="ar-page">
        <section className="ar-hero" aria-labelledby="ar-heading">
          <div className="absolute inset-0 -z-10 tp-aurora" aria-hidden="true" />
          <div className="absolute inset-0 -z-10 tp-dot-grid opacity-40" aria-hidden="true" />
          <div className="ar-hero-copy">
            <p className="ar-eyebrow">
              <Sparkles size={14} aria-hidden="true" />
              TRIPPAMINE AI TRAVEL
            </p>
            <h1 id="ar-heading">AI 여행 추천</h1>
            <p className="ar-hero-description">
              나의 여행을 고르고, 예산과 일정에 맞는 코스를 확인하세요.
              <br className="ar-desktop-break" /> 지도에서 장소를 바꾸고 새 여행으로 저장할 수 있어요.
            </p>
          </div>
          <button
            type="button"
            className="ar-button ar-button--secondary"
            onClick={() => navigate("/travel")}
          >
            <Plus size={15} aria-hidden="true" />
            여행 계획 관리
            <ArrowUpRight size={14} aria-hidden="true" />
          </button>
        </section>
        {notice && (
          <p className="ar-notice" role="status">
            <Check size={16} aria-hidden="true" />
            {notice}
          </p>
        )}

        {!isLoggedIn ? (
          <section className="ar-empty ar-sign-in tp-card-dark">
            <Compass size={36} aria-hidden="true" />
            <h2>나의 여행으로 시작해 볼까요?</h2>
            <p>로그인하면 등록한 여행의 추천 일정을 확인할 수 있어요.</p>
            <button
              className="ar-button ar-button--primary"
              type="button"
              onClick={openLogin}
            >
              로그인하고 시작하기
              <ArrowRight size={16} aria-hidden="true" />
            </button>
          </section>
        ) : (
          <div className="ar-workspace">
            <aside
              className="ar-plan-picker tp-card-dark"
              aria-labelledby="ar-plans-heading"
            >
              <div className="ar-panel-heading">
                <h2 id="ar-plans-heading">
                  나의 여행{" "}
                  <span>{loading || planError ? "—" : plans.length}</span>
                </h2>
                <button
                  className="ar-picker-toggle"
                  type="button"
                  aria-expanded={pickerOpen}
                  aria-controls="ar-plan-list"
                  onClick={() => setPickerOpen((value) => !value)}
                >
                  {pickerOpen ? "목록 접기" : "여행 바꾸기"}
                  <ChevronDown size={14} aria-hidden="true" />
                </button>
              </div>
              {selectedPlan && (
                <p className="ar-picker-selection">
                  선택한 여행 · <strong>{selectedPlan.planName}</strong>
                </p>
              )}
              <div
                id="ar-plan-list"
                className={`ar-plan-list ${pickerOpen ? "" : "ar-plan-list--collapsed"}`}
                aria-busy={loading}
              >
                {loading ? (
                  <p className="ar-list-state" role="status">
                    <Loader2
                      size={18}
                      className="animate-spin"
                      aria-hidden="true"
                    />
                    여행 목록을 불러오고 있어요…
                  </p>
                ) : planError ? (
                  <div className="ar-list-state">
                    <p role="alert">{planError}</p>
                    <button
                      type="button"
                      className="ar-button ar-button--secondary"
                      onClick={() => setReloadCount((value) => value + 1)}
                    >
                      <RefreshCw size={14} aria-hidden="true" />
                      목록 다시 불러오기
                    </button>
                  </div>
                ) : !plans.length ? (
                  <div className="ar-list-state">
                    <MapPin size={25} aria-hidden="true" />
                    <strong>등록된 여행이 없어요</strong>
                    <p>여행 계획을 먼저 만들어 주세요.</p>
                    <button
                      type="button"
                      className="ar-button ar-button--primary"
                      onClick={() => navigate("/travel")}
                    >
                      <Plus size={15} aria-hidden="true" />
                      여행 만들기
                    </button>
                  </div>
                ) : (
                  plans.map((plan) => (
                    <button
                      key={plan.planId}
                      type="button"
                      className="ar-plan"
                      aria-pressed={selectedPlan?.planId === plan.planId}
                      disabled={mutating}
                      onClick={() => void handleSelectPlan(plan)}
                    >
                      <span className="ar-plan-top">
                        <span>
                          <MapPin size={12} aria-hidden="true" />
                          여행 계획
                        </span>
                        {plan.blindYn === "Y" ? (
                          <span>
                            <Gift size={12} aria-hidden="true" />
                            미스터리
                          </span>
                        ) : selectedPlan?.planId === plan.planId ? (
                          <Check size={15} aria-hidden="true" />
                        ) : null}
                      </span>
                      <strong>{plan.planName}</strong>
                      <span className="ar-plan-date">
                        <CalendarDays size={13} aria-hidden="true" />
                        {formatDate(plan.startDate)} ~{" "}
                        {formatDate(plan.endDate)}
                      </span>
                      <span className="ar-plan-meta">
                        <span>
                          <Wallet size={13} aria-hidden="true" />
                          {formatWon(plan.totalBudget)}
                        </span>
                        <span>
                          <Users size={13} aria-hidden="true" />
                          {companionLabel(plan.companionType)}
                        </span>
                      </span>
                    </button>
                  ))
                )}
              </div>
              <p className="ar-picker-help">
                여행을 선택하면 저장된 추천을 불러와요.
                <br />
                추천이 없는 여행은 새로 생성합니다.
              </p>
            </aside>

            <section
              className="ar-results tp-card-dark"
              aria-labelledby="ar-result-heading"
              aria-busy={Boolean(pendingAction)}
            >
              <div className="ar-result-heading">
                <p className="ar-eyebrow">
                  <Route size={13} aria-hidden="true" />
                  RECOMMENDED ITINERARY
                </p>
                <h2 id="ar-result-heading" ref={resultHeadingRef} tabIndex={-1}>
                  {selectedPlan?.planName ?? "어떤 여행을 떠나볼까요?"}
                </h2>
                {selectedPlan && (
                  <p>
                    <CalendarDays size={14} aria-hidden="true" />
                    {formatDate(selectedPlan.startDate)} ~{" "}
                    {formatDate(selectedPlan.endDate)}
                    <span>·</span>
                    {companionLabel(selectedPlan.companionType)}
                  </p>
                )}
              </div>
              {/* 서로 중복되던 맞춤 추천/자유 검색을 여행 콘텐츠 안의 단일 탭으로 표시한다. */}
              <AiRecommendDealsHub key={selectedPlan?.planId ?? "general"} plan={selectedPlan} />
              {!selectedPlan ? (
                <div className="ar-empty">
                  <span className="ar-empty-icon">
                    <Compass size={44} strokeWidth={1.2} aria-hidden="true" />
                  </span>
                  <h3>여행을 선택해 주세요</h3>
                  <p>
                    나의 여행 목록에서 계획을 고르면
                    <br />
                    이곳에 추천 코스와 예상 비용이 표시돼요.
                  </p>
                  <div className="ar-empty-features">
                    <span>
                      <Wallet size={15} aria-hidden="true" />
                      예산에 맞는 코스
                    </span>
                    <span>
                      <Map size={15} aria-hidden="true" />
                      지도에서 경로 조정
                    </span>
                    <span>
                      <CloudSun size={15} aria-hidden="true" />
                      여행지 날씨
                    </span>
                  </div>
                </div>
              ) : pendingAction ? (
                <div className="ar-empty ar-loading" role="status">
                  <span className="ar-empty-icon">
                    <Loader2
                      size={32}
                      className="animate-spin"
                      aria-hidden="true"
                    />
                  </span>
                  <h3>
                    {pendingAction === "save"
                      ? "수정한 경로를 저장하고 있어요"
                      : pendingAction === "regenerate"
                        ? "새로운 코스를 만들고 있어요"
                        : "여행에 맞는 코스를 확인하고 있어요"}
                  </h3>
                  <p>
                    {pendingAction === "save"
                      ? "새 여행으로 저장한 뒤 목록을 갱신합니다."
                      : "새 추천은 생성에 시간이 걸릴 수 있어요."}
                  </p>
                </div>
              ) : (
                <>
                  {recommendError && (
                    <div className="ar-error">
                      <p role="alert">{recommendError}</p>
                      <div className="ar-actions">
                        <button
                          className="ar-button ar-button--secondary"
                          type="button"
                          onClick={() =>
                            void handleSelectPlan(selectedPlan, true)
                          }
                        >
                          <RefreshCw size={14} aria-hidden="true" />
                          추천 다시 불러오기
                        </button>
                        {!result && (
                          <button
                            className="ar-button ar-button--quiet"
                            type="button"
                            onClick={() => setConfirmRegenerate(true)}
                          >
                            새 추천 요청
                          </button>
                        )}
                      </div>
                    </div>
                  )}
                  {confirmRegenerate && (
                    <div
                      className="ar-confirm"
                      role="region"
                      aria-label="추천 다시 생성 확인"
                    >
                      <strong>새로운 추천으로 바꿀까요?</strong>
                      <p>
                        새 추천이 생성되면 현재 저장된 추천 일정이 바뀝니다.
                      </p>
                      <div className="ar-actions">
                        <button
                          type="button"
                          className="ar-button ar-button--primary"
                          onClick={() => void handleRegenerate()}
                        >
                          새 추천 생성
                        </button>
                        <button
                          type="button"
                          className="ar-button ar-button--secondary"
                          onClick={() => setConfirmRegenerate(false)}
                        >
                          취소
                        </button>
                      </div>
                    </div>
                  )}
                  {result && (
                    <>
                      <div className="ar-recommendation-intro">
                        <span className="ar-ready">
                          <Check size={13} aria-hidden="true" />
                          추천 일정
                        </span>
                        <h3>{result.parsed.title}</h3>
                        <p>{result.parsed.summary}</p>
                      </div>
                      <dl className="ar-trip-facts">
                        <div>
                          <dt>예상 총 비용</dt>
                          <dd>{formatWon(result.parsed.estimatedCost)}</dd>
                        </div>
                        <div>
                          <dt>등록한 예산</dt>
                          <dd>{formatWon(selectedPlan.totalBudget)}</dd>
                        </div>
                        <div>
                          <dt>추천 구성</dt>
                          <dd>
                            {result.parsed.days.length}
                            <small>일</small> · {mapPlaces.length}
                            <small>곳</small>
                          </dd>
                        </div>
                      </dl>
                      <div className="ar-itinerary-toolbar">
                        <button
                          className="ar-button ar-button--primary"
                          type="button"
                          disabled={!mapPlaces.length}
                          onClick={() => setIsMapOpen(true)}
                        >
                          <Map size={16} aria-hidden="true" />
                          지도에서 일정 수정·저장
                        </button>
                        <button
                          className="ar-button ar-button--secondary"
                          type="button"
                          onClick={() => setConfirmRegenerate(true)}
                        >
                          <RefreshCw size={14} aria-hidden="true" />
                          추천 다시 받기
                        </button>
                      </div>
                      <p className="ar-cost-note">
                        예상 비용은 실제 지출과 다를 수 있어요. 방문 전
                        운영시간과 가격을 확인해 주세요.
                      </p>
                      {weatherLoading && (
                        <p className="ar-weather-status" role="status">
                          여행지 날씨를 확인하고 있어요…
                        </p>
                      )}
                      {weatherError && (
                        <div className="ar-weather-error">
                          <p role="alert">{weatherError}</p>
                          <button
                            className="ar-button ar-button--quiet"
                            type="button"
                            disabled={weatherLoading}
                            onClick={() =>
                              void loadWeather(
                                selectedPlan.planId,
                                selectionRef.current,
                              )
                            }
                          >
                            날씨 다시 불러오기
                          </button>
                        </div>
                      )}
                      <div className="ar-days">
                        {result.parsed.days.length ? (
                          result.parsed.days.map((day) => (
                            <article className="ar-day" key={day.day}>
                              <div className="ar-day-heading">
                                <span>
                                  DAY {String(day.day).padStart(2, "0")}
                                </span>
                                <h3>{day.day}일차 일정</h3>
                                <small>{day.places.length}곳</small>
                              </div>
                              <DayWeather
                                weather={tripWeather?.find(
                                  (weather) => weather.day === day.day,
                                )}
                              />
                              <ol className="ar-places">
                                {day.places.map((place, index) => (
                                  <li key={`${day.day}-${index}`}>
                                    <span className="ar-place-number">
                                      {index + 1}
                                    </span>
                                    <div className="ar-place-copy">
                                      <div>
                                        <h4>{place.name}</h4>
                                        <span>
                                          {formatWon(place.estimatedCost)}
                                        </span>
                                      </div>
                                      <p>{place.description}</p>
                                    </div>
                                  </li>
                                ))}
                              </ol>
                              {!day.places.length && (
                                <p className="ar-weather-status">
                                  이 날짜에는 등록된 추천 장소가 없어요.
                                </p>
                              )}
                            </article>
                          ))
                        ) : (
                          <div className="ar-empty">
                            <h3>추천에 포함된 장소가 없어요</h3>
                            <p>
                              새 추천을 요청하거나 다른 여행을 선택해 주세요.
                            </p>
                          </div>
                        )}
                      </div>
                      <details
                        className="ar-help"
                        open={helpOpen}
                        onToggle={(event) => setHelpOpen(event.currentTarget.open)}
                      >
                        <summary>
                          <LifeBuoy size={18} aria-hidden="true" />
                          <span>
                            <strong>여행 중 도움</strong>
                            <small>일정 대안 찾기 · 현재 위치 날씨</small>
                          </span>
                          <ChevronDown size={16} aria-hidden="true" />
                        </summary>
                        <div className="ar-help-content">
                          <div className="ar-weather-tools">
                            <div>
                              <LifeBuoy size={18} aria-hidden="true" />
                              <span>
                                <strong>계획대로 이동하기 어렵다면</strong>
                                <small>여행 SOS에서 대체 일정을 찾아보세요.</small>
                              </span>
                            </div>
                            <button
                              className="ar-button ar-button--sos"
                              type="button"
                              disabled={!canUseItinerary}
                              onClick={() => setIsSosOpen(true)}
                            >
                              <LifeBuoy size={16} aria-hidden="true" />
                              여행 SOS
                            </button>
                          </div>
                          <div className="ar-weather-tools">
                            <div>
                              <CloudSun size={18} aria-hidden="true" />
                              <span>
                                <strong>현재 위치의 날씨</strong>
                                <small>
                                  위치 확인을 허용하면 앞으로 6시간의 예보를 볼 수
                                  있어요.
                                </small>
                              </span>
                            </div>
                            <button
                              className="ar-button ar-button--quiet"
                              type="button"
                              disabled={geoLoading}
                              onClick={loadUltraShort}
                            >
                              {geoLoading ? (
                                <Loader2
                                  size={14}
                                  className="animate-spin"
                                  aria-hidden="true"
                                />
                              ) : (
                                <LocateFixed size={14} aria-hidden="true" />
                              )}
                              {geoLoading
                                ? "위치 확인 중…"
                                : ultraShort
                                  ? "날씨 새로고침"
                                  : "날씨 확인"}
                            </button>
                          </div>
                          {geoError && (
                            <p className="ar-weather-error" role="alert">
                              {geoError}
                            </p>
                          )}
                          {ultraShort && (
                            <CurrentWeather
                              weather={ultraShort}
                              onSos={() => setIsSosOpen(true)}
                            />
                          )}
                        </div>
                      </details>
                    </>
                  )}
                </>
              )}
            </section>
          </div>
        )}
      </div>
      {(isMapOpen || isSosOpen) &&
        createPortal(
          <div className="ar-modal-layer">
            {isSosOpen && selectedPlan && result && (
              <TravelReplanningModal
                plan={selectedPlan}
                onClose={() => setIsSosOpen(false)}
              />
            )}
            {isMapOpen && (
              <KakaoMapModal
                isOpen
                onClose={() => setIsMapOpen(false)}
                places={mapPlaces}
                onSavePlaces={handleSavePlaces}
                onRegenerateDay={handleRegenerateDay}
              />
            )}
          </div>,
          document.body,
        )}
    </>
  );
}

function CurrentWeather({
  weather,
  onSos,
}: {
  weather: UltraShortForecastResult;
  onSos: () => void;
}) {
  return (
    <details className="ar-current-weather">
      <summary>
        <CloudSun size={17} aria-hidden="true" />
        <span>
          <strong>
            {weather.rain.rainExpected
              ? weather.rain.message || "현재 위치에 비 소식이 있어요"
              : `현재 위치 ${weather.current.temperature ?? "—"}° · 6시간 내 비 예보 없음`}
          </strong>
          <small>현재 위치 기준 · 여행지 예보와 다를 수 있어요</small>
        </span>
        <ChevronDown size={16} aria-hidden="true" />
      </summary>
      <div className="ar-hourly">
        {weather.hourly.map((hour) => (
          <div key={hour.time}>
            <span>{hour.time}</span>
            <strong>{hour.temperature ?? "—"}°</strong>
            <small>습도 {hour.humidity ?? "—"}%</small>
          </div>
        ))}
      </div>
      {weather.rain.rainExpected && (
        <div className="ar-rain-detail">
          <p>
            {weather.rain.precipitationType ?? "강수"} ·{" "}
            {weather.rain.forecastTime ?? "시각 정보 없음"} · 강수확률{" "}
            {weather.rain.rainProbability === null
              ? "—"
              : `${weather.rain.rainProbability}%`}{" "}
            · 강수량 {weather.rain.rainAmount ?? "—"}
          </p>
          <button
            type="button"
            className="ar-button ar-button--quiet"
            onClick={onSos}
          >
            <LifeBuoy size={14} aria-hidden="true" />
            여행 SOS로 대안 찾기
          </button>
        </div>
      )}
    </details>
  );
}

function DayWeather({ weather }: { weather: TripDayWeather | undefined }) {
  if (!weather) return null;
  const { summary } = weather;
  if (!summary.available)
    return (
      <p className="ar-weather-status">
        {summary.notices[0] || "이 날짜의 날씨 정보는 아직 제공되지 않아요."}
      </p>
    );
  return (
    <details className="ar-day-weather" open={summary.notices.length > 0}>
      <summary>
        <CloudSun size={15} aria-hidden="true" />
        <strong>
          {formatDate(weather.date)} · 최저 {summary.tempMin ?? "—"}° / 최고{" "}
          {summary.tempMax ?? "—"}°
          {summary.rainProbability !== null &&
            summary.rainProbability >= 0 &&
            ` · 강수 ${summary.rainProbability}%`}
        </strong>
        <ChevronDown size={14} aria-hidden="true" />
      </summary>
      <p>
        체감 {summary.feelsLike ?? "—"}° · 습도 {summary.humidity ?? "—"}% ·
        풍속 {summary.windSpeed ?? "—"}m/s
        {summary.rainProbability !== null &&
          summary.rainProbability >= 0 &&
          ` · 강수 ${summary.rainProbability}%`}
        {summary.rainMm !== null && ` · ${summary.rainMm}mm`}
      </p>
      {summary.notices.length > 0 && (
        <ul>
          {summary.notices.map((notice, index) => (
            <li key={index}>{notice}</li>
          ))}
        </ul>
      )}
    </details>
  );
}

function companionLabel(type: string | null): string {
  return (
    (
      {
        ALONE: "혼자",
        FRIEND: "친구",
        FAMILY: "가족",
        COUPLE: "연인",
      } as Record<string, string>
    )[type ?? ""] ??
    type ??
    "동행자 미설정"
  );
}
function formatDate(value: string | null): string {
  return value ? value.slice(0, 10).replaceAll("-", ".") : "날짜 미정";
}
function formatWon(value: number | null): string {
  return value !== null && Number.isFinite(value)
    ? `${value.toLocaleString("ko-KR")}원`
    : "금액 미정";
}

/** 실제 추천 JSON 규격을 검사해 잘못된 응답이 화면 전체를 중단하지 않게 한다. */
function parseRecommendation(json: string): ParsedRecommendation {
  const value: unknown = JSON.parse(json);
  const record = (item: unknown): item is Record<string, unknown> =>
    typeof item === "object" && item !== null && !Array.isArray(item);
  const money = (item: unknown): item is number =>
    typeof item === "number" && Number.isFinite(item) && item >= 0;
  if (
    !record(value) ||
    typeof value.title !== "string" ||
    typeof value.summary !== "string" ||
    !money(value.estimatedCost) ||
    !Array.isArray(value.days)
  )
    throw new SyntaxError("잘못된 추천 형식");
  const dayNumbers = new Set<number>();
  for (const day of value.days) {
    if (
      !record(day) ||
      typeof day.day !== "number" ||
      !Number.isInteger(day.day) ||
      day.day < 1 ||
      dayNumbers.has(day.day) ||
      !Array.isArray(day.places)
    )
      throw new SyntaxError("잘못된 일차 형식");
    dayNumbers.add(day.day);
    for (const place of day.places) {
      if (
        !record(place) ||
        typeof place.name !== "string" ||
        typeof place.description !== "string" ||
        !money(place.estimatedCost)
      )
        throw new SyntaxError("잘못된 장소 형식");
    }
  }
  return value as unknown as ParsedRecommendation;
}