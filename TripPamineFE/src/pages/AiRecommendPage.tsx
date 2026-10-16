import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { TravelPlan } from "../types";
import { getTravelPlansApi } from "../api/travel";
import {
    getAiRecommendationApi,
    regenerateAiRecommendationApi,
    regenerateDayRecommendationApi,
    saveCustomRecommendationApi,
    type AiRecommendationResponse,
} from "../api/recommendation";
import aiTravelBg from "../assets/images/ai-travel-bg.png";
import { KakaoMapModal, type PlaceItem } from '../components/KakaoMapModal';
import TravelReplanningModal from "../components/TravelReplanningModal";
import { getTripWeatherApi, type TripDayWeather, getUltraShortForecastApi, type UltraShortForecastResult } from "../api/weather";
import PageHero from "../components/PageHero";

export default function AiRecommendPage() {
    const navigate = useNavigate();

    const [plans, setPlans] = useState<TravelPlan[]>([]);
    const [selectedPlan, setSelectedPlan] = useState<TravelPlan | null>(null);
    const [loading, setLoading] = useState(true);
    const [recommendation, setRecommendation] =
        useState<AiRecommendationResponse | null>(null);
    const [parsedRecommendation, setParsedRecommendation] =
        useState<ParsedRecommendation | null>(null);
    const [tripWeather, setTripWeather] =
        useState<TripDayWeather[] | null>(null);
    const [ultraShort, setUltraShort] =
        useState<UltraShortForecastResult | null>(null);

    const [recommendLoading, setRecommendLoading] = useState(false);
    const [recommendError, setRecommendError] = useState("");

    const [isMapOpen, setIsMapOpen] = useState(false);
    const [isSosOpen, setIsSosOpen] = useState(false);
    const [sosNotice, setSosNotice] = useState(false);

    useEffect(() => {
        const loadPlans = async () => {
            try {
                const data = await getTravelPlansApi();
                setPlans(data);
            } catch (error) {
                console.error("여행 계획 조회 실패:", error);
            } finally {
                setLoading(false);
            }
        };

        loadPlans();
    }, []);

    const companionLabel = (type: string | null) => {
        switch (type) {
            case "ALONE":
                return "혼자";
            case "FRIEND":
                return "친구";
            case "FAMILY":
                return "가족";
            case "COUPLE":
                return "연인";
            default:
                return type || "미선택";
        }
    };

    const handleSelectPlan = async (plan: TravelPlan) => {
        setIsSosOpen(false);
        setSosNotice(false);
        setSelectedPlan(plan);
        setRecommendation(null);
        setParsedRecommendation(null);
        setRecommendError("");
        setRecommendLoading(true);
        setTripWeather(null);
        setUltraShort(null);

        try {
            const data = await getAiRecommendationApi(plan.planId);

            setRecommendation(data);

            try {
                const parsed = JSON.parse(data.recommendJson);
                setParsedRecommendation(parsed);
            } catch (parseError) {
                console.error("AI 추천 JSON 파싱 실패:", parseError);
                setRecommendError("AI 추천 결과 형식을 읽는 중 문제가 발생했습니다.");
            }
        } catch (error) {
            console.error("AI 추천 조회 실패:", error);
            setRecommendError("AI 여행 추천을 생성하는 중 문제가 발생했습니다.");
        } finally {
            setRecommendLoading(false);
        }

        // 날씨는 추천과 별개로 불러온다. 실패해도 일정 화면은 그대로 보여준다.
        try {
            const weather = await getTripWeatherApi(plan.planId);
            setTripWeather(weather);
        } catch (error) {
            console.error("날씨 조회 실패:", error);
            setTripWeather(null);
        }
        loadUltraShort();
    };

    const handleRegenerate = async () => {
        if (!selectedPlan) return;

        setRecommendLoading(true);
        setRecommendError("");

        try {
            const data = await regenerateAiRecommendationApi(selectedPlan.planId);

            setRecommendation(data);

            try {
                const parsed = JSON.parse(data.recommendJson);
                setParsedRecommendation(parsed);
            } catch (parseError) {
                console.error("재추천 JSON 파싱 실패:", parseError);
                setRecommendError("새 추천 결과 형식을 읽는 중 문제가 발생했습니다.");
            }
        } catch (error) {
            console.error("추천 다시 받기 실패:", error);
            setRecommendError("추천을 다시 생성하는 중 문제가 발생했습니다.");
        } finally {
            setRecommendLoading(false);
        }
    };

    const loadUltraShort = () => {
        if (!navigator.geolocation) return;   // GPS를 지원하지 않는 브라우저면 중단

        navigator.geolocation.getCurrentPosition(
            async (position) => {
                try {
                    const result = await getUltraShortForecastApi(
                        position.coords.latitude,    // 위도
                        position.coords.longitude,    // 경도
                    );
                    setUltraShort(result);
                } catch (error) {
                    console.error("초단기예보 조회 실패:", error);
                    setUltraShort(null);   // 실패해도 일정 화면은 그대로 보여준다
                }
            },
            (error) => {
                console.warn("GPS 확인 실패:", error);
            },
        );
    };

    interface ParsedRecommendation {
        title: string;
        summary: string;
        estimatedCost: number;
        days: {
            day: number;
            places: {
                name: string;
                description: string;
                estimatedCost: number;
            }[];
        }[];
    }

    // 모달에서 수정 완료 시 실행할 경로 저장 핸들러
    const handleSavePlaces = async (updatedPlaces: PlaceItem[]) => {
        if (!parsedRecommendation || !selectedPlan) return;

        // 1. 모달에서 넘어온 장소 데이터를 Day별로 재그룹화
        const dayMap: { [key: number]: { name: string; description: string; estimatedCost: number }[] } = {};

        updatedPlaces.forEach((p) => {
            const day = p.day || 1;
            if (!dayMap[day]) dayMap[day] = [];

            const existingPlace = parsedRecommendation.days
                .flatMap((d) => d.places)
                .find((orig) => orig.name === p.name);

            dayMap[day].push({
                name: p.name,
                description: existingPlace?.description || p.address || "사용자 추가 장소",
                estimatedCost: existingPlace?.estimatedCost || 0,
            });
        });

        const newDays = Object.keys(dayMap).map((dayStr) => ({
            day: Number(dayStr),
            places: dayMap[Number(dayStr)],
        }));

        const newTitle = parsedRecommendation.title.startsWith("(수정)")
            ? parsedRecommendation.title
            : `(수정) ${parsedRecommendation.title}`;

        const updatedParsed: ParsedRecommendation = {
            ...parsedRecommendation,
            title: newTitle,
            days: newDays,
        };

        try {
            setRecommendLoading(true); // 로딩 스피너 활성화
            setIsMapOpen(false); // 지도는 먼저 닫기

            // 2. 백엔드 API 호출하여 DB에 실제 저장
            const responseData = await saveCustomRecommendationApi(
                selectedPlan.planId, 
                JSON.stringify(updatedParsed)
            );

            // 3. 여행 계획 목록 전체 재조회 (새로고침 효과)
            const updatedPlans = await getTravelPlansApi();
            setPlans(updatedPlans);

            // 4. 방금 생성된 새로운 '(수정)' 플랜을 자동으로 선택된 상태로 만들기
            const newlyCreatedPlan = updatedPlans.find((p) => p.planId === responseData.planId);
            if (newlyCreatedPlan) {
                setSelectedPlan(newlyCreatedPlan);
                setRecommendation(responseData);
                setParsedRecommendation(JSON.parse(responseData.recommendJson));
            }
            
            alert("성공적으로 여행 경로가 추가(수정) 되었습니다!");
        } catch (error) {
            console.error("경로 DB 저장 실패:", error);
            alert("경로 저장 중 오류가 발생했습니다.");
        } finally {
            setRecommendLoading(false);
        }
    };

    const mapPlaces = parsedRecommendation?.days.flatMap((d) =>
        d.places.map((p) => ({
            name: p.name,
            address: p.name,
            day: d.day,
        }))
    ) || [];

    const handleRegenerateDay = async (dayNum: number, currentPlaces: PlaceItem[]): Promise<PlaceItem[]> => {
        if (!selectedPlan) return [];

        const placeNames = currentPlaces.map((p) => p.name);
        const result = await regenerateDayRecommendationApi(selectedPlan.planId, dayNum, placeNames);

        // 백엔드 결과를 PlaceItem 배열로 전환
        return result.places.map((p) => ({
            name: p.name,
            address: p.description,
            day: dayNum,
        }));
    };

    return (
        <div
            className="min-h-screen bg-cover bg-center bg-fixed"
            style={{
                backgroundImage: `url(${aiTravelBg})`,
            }}
        >
            <div className="min-h-screen bg-[#f6f6fb]/85 p-4 backdrop-blur-sm md:p-8">
                <div className="max-w-7xl mx-auto">
                    <PageHero
                        className="mb-8"
                        tone="cyan"
                        eyebrow="AI Travel Planner"
                        icon="✨"
                        title={<>AI가 짜주는 <span className="tp-gradient-text">여행 코스</span></>}
                        description="등록된 여행을 선택하면 AI가 조건에 맞는 여행 일정을 추천해드립니다."
                        aside={
                            <button
                                type="button"
                                onClick={() => navigate("/")}
                                className="flex items-center gap-2 rounded-2xl border border-white/15 bg-white/10 px-4 py-2.5 text-xs font-bold text-white backdrop-blur transition hover:bg-white/20"
                            >
                                🏠 메인으로 돌아가기
                            </button>
                        }
                    />

                    <div className="grid grid-cols-1 lg:grid-cols-[360px_1fr] gap-6">
                        <div className="rounded-3xl border border-white bg-white/85 p-5 shadow-[0_20px_50px_-30px_rgba(60,40,180,0.45)] backdrop-blur-xl">
                            <div className="flex items-center justify-between mb-4">
                                <h2 className="font-bold text-slate-800">등록된 여행</h2>

                                <span className="px-2.5 py-1 bg-sky-50 text-sky-600 text-xs font-bold rounded-full">
                                  {plans.length}개
                                </span>
                            </div>

                            {loading ? (
                                <div className="py-10 text-center text-sm text-slate-400">
                                    여행 목록을 불러오는 중...
                                </div>
                            ) : plans.length === 0 ? (
                                <div className="py-10 text-center text-sm text-slate-400">
                                    등록된 여행이 없습니다.
                                </div>
                            ) : (
                                <div className="space-y-3">
                                    {plans.map((plan) => (
                                        <button
                                            key={plan.planId}
                                            type="button"
                                            onClick={() => handleSelectPlan(plan)}
                                            className={`w-full text-left p-4 rounded-2xl border transition-all ${
                                                selectedPlan?.planId === plan.planId
                                                    ? "border-sky-500 bg-sky-50 shadow-sm"
                                                    : "border-slate-200 bg-white hover:border-sky-300 hover:bg-sky-50/40"
                                            }`}
                                        >
                                            <div className="flex items-center justify-between mb-2">
                                                <span className="text-[10px] font-bold text-sky-600">
                                                  ✈ TRAVEL
                                                </span>

                                                {plan.blindYn === "Y" && (
                                                    <span className="text-[10px] font-bold text-purple-600">
                                                    🎁 MYSTERY
                                                  </span>
                                                )}
                                            </div>

                                            <h3 className="font-bold text-slate-800">
                                                {plan.planName}
                                            </h3>

                                            <div className="mt-2 space-y-1 text-xs text-slate-500">
                                                <p>💰 {Number(plan.totalBudget).toLocaleString()}원</p>

                                                <p>👥 {companionLabel(plan.companionType)}</p>

                                                <p>
                                                    📅{" "}
                                                    {plan.startDate
                                                        ? plan.startDate.slice(0, 10)
                                                        : "미정"}
                                                    {" ~ "}
                                                    {plan.endDate ? plan.endDate.slice(0, 10) : "미정"}
                                                </p>
                                            </div>
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>

                        <div className="rounded-3xl border border-white bg-white/85 p-6 shadow-[0_20px_50px_-30px_rgba(60,40,180,0.45)] backdrop-blur-xl">
                            {!selectedPlan ? (
                                <div className="min-h-[420px] flex flex-col items-center justify-center text-center">
                                    <div className="text-5xl mb-4">🤖</div>

                                    <h2 className="text-xl font-bold text-slate-800">
                                        여행을 선택해주세요
                                    </h2>

                                    <p className="mt-2 text-sm text-slate-400">
                                        왼쪽 목록에서 AI 추천을 받고 싶은 여행을 선택하세요.
                                    </p>
                                </div>
                            ) : (
                                <div>
                                  <span className="px-3 py-1 bg-sky-50 text-sky-600 text-xs font-bold rounded-full">
                                    선택된 여행
                                  </span>

                                    <h2 className="mt-3 text-2xl font-bold text-slate-800">
                                        {selectedPlan.planName}
                                    </h2>

                                    <p className="mt-2 text-sm text-slate-500">
                                        이 여행 정보를 기반으로 AI 추천을 생성할 예정입니다.
                                    </p>

                                    <div className="mt-6">
                                        {recommendLoading ? (
                                            <div className="min-h-[300px] flex flex-col items-center justify-center">
                                                <div className="w-10 h-10 border-4 border-sky-100 border-t-sky-500 rounded-full animate-spin" />

                                                <p className="mt-4 text-sm font-semibold text-slate-600">
                                                    AI가 여행 일정을 만들고 있어요...
                                                </p>

                                                <p className="mt-1 text-xs text-slate-400">
                                                    여행 조건을 분석하고 있습니다.
                                                </p>
                                            </div>
                                        ) : recommendError ? (
                                            <div className="bg-red-50 border border-red-100 rounded-2xl p-5">
                                                <p className="text-sm text-red-500">{recommendError}</p>
                                            </div>
                                        ) : recommendation ? (
                                            <div className="bg-slate-50 rounded-2xl p-5">
                                                <p className="text-xs font-bold text-sky-500 mb-3">
                                                    ✨ AI RECOMMENDATION
                                                </p>

                                                {parsedRecommendation && (
                                                    <div className="space-y-6">
                                                        <div className="flex items-start justify-between gap-4">
                                                            <div>
                                                                <span className="px-3 py-1 bg-sky-50 text-sky-600 text-xs font-bold rounded-full">
                                                                  ✨ AI 추천 완료
                                                                </span>

                                                                <h2 className="mt-3 text-2xl font-bold text-slate-800">
                                                                    {parsedRecommendation.title}
                                                                </h2>

                                                                <p className="mt-2 text-sm text-slate-500 leading-relaxed">
                                                                    {parsedRecommendation.summary}
                                                                </p>
                                                            </div>

                                                            <div className="flex flex-col sm:flex-row items-end gap-2 shrink-0">
                                                                <button
                                                                    type="button"
                                                                    onClick={() => setIsMapOpen(true)}
                                                                    className="
                                                                        px-4 py-2
                                                                        tp-gradient-bg hover:brightness-110
                                                                        text-white text-xs font-bold
                                                                        rounded-xl shadow-md shadow-sky-500/20
                                                                        transition-all duration-300
                                                                        hover:scale-105
                                                                    "
                                                                >
                                                                    🗺️ 지도로 일정 보기
                                                                </button>

                                                                <button
                                                                    type="button"
                                                                    onClick={handleRegenerate}
                                                                    disabled={recommendLoading}
                                                                    className="
                                                                        px-4 py-2
                                                                        bg-gradient-to-r from-sky-500 to-indigo-500
                                                                        hover:from-sky-600 hover:to-indigo-600
                                                                        disabled:opacity-50
                                                                        text-white text-xs font-bold
                                                                        rounded-xl
                                                                        shadow-lg shadow-sky-500/20
                                                                        transition-all duration-300
                                                                        hover:scale-105
                                                                    "
                                                                >
                                                                    🔄 추천 다시 받기
                                                                </button>
                                                            </div>
                                                        </div>

                                                        <div className="bg-emerald-50 rounded-2xl p-4">
                                                            <p className="text-xs text-emerald-600 font-semibold">
                                                                예상 총 비용
                                                            </p>

                                                            <p className="mt-1 text-xl font-bold text-emerald-800">
                                                                {Number(
                                                                    parsedRecommendation.estimatedCost,
                                                                ).toLocaleString()}
                                                                원
                                                            </p>
                                                        </div>

                                                        {ultraShort && (
                                                            <div className="mb-4 rounded-2xl bg-sky-50 px-5 py-4 text-slate-700">
                                                                {/*비 알림 여부*/}
                                                                <p className="text-base font-bold">
                                                                    {ultraShort.rain.rainExpected
                                                                        ? "☔ " + ultraShort.rain.message
                                                                        : "☀ 6시간 내에 비 소식은 없어요"}
                                                                </p>

                                                                {ultraShort.rain.rainExpected && (
                                                                    <p className="mt-2 text-sm font-semibold text-sky-700">
                                                                        🆘 일정에 문제가 생긴 경우 여행 SOS를 활용해보세요 🆘
                                                                    </p>
                                                                )}

                                                                <p className="mt-0.5 text-xs text-slate-400">현재 위치 기준 · 앞으로 6시간 초단기예보</p>

                                                                {/* 시간별 예보*/}
                                                                <div className="mt-4 grid grid-cols-6 gap-2 text-center">
                                                                    {ultraShort.hourly.map((h) => (
                                                                        <div key={h.time} className="rounded-xl bg-white px-1 py-2 shadow-sm">
                                                                            <p className="text-xs text-slate-400">{h.time}</p>
                                                                            <p className="mt-1 text-lg font-bold text-slate-800">{h.temperature}°</p>
                                                                            <p className="mt-0.5 text-xs text-sky-600">💧습도 {h.humidity}%</p>
                                                                        </div>
                                                                    ))}
                                                                </div>
                                                            </div>
                                                        )}

                                                        <div className="space-y-4">
                                                            {parsedRecommendation.days.map((day) => (
                                                                <div
                                                                    key={day.day}
                                                                    className="tp-card rounded-3xl p-5"
                                                                >
                                                                    <div className="flex items-center gap-2 mb-4">
                                                                        <span className="w-8 h-8 flex items-center justify-center rounded-xl bg-sky-500 text-white text-sm font-bold">
                                                                          {day.day}
                                                                        </span>

                                                                        <h3 className="font-bold text-slate-800">
                                                                            {day.day}일차 일정
                                                                        </h3>
                                                                    </div>

                                                                    {/*날씨 표시*/}
                                                                    {(() => {
                                                                        const w = tripWeather?.find((t) => t.day === day.day);
                                                                        if (!w) return null;

                                                                        const getTodayString = () => {
                                                                            const now = new Date();
                                                                            const pad = (n: number) => n.toString().padStart(2, '0');
                                                                            return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
                                                                        };
                                                                        const isToday = w.date === getTodayString();

                                                                        // 날씨를 제공할 수 없는 날(범위 밖 등)은 안내 문구만 보여준다
                                                                        if (!w.summary.available) {
                                                                            return (
                                                                                <p className="mb-3 text-xs text-slate-400">
                                                                                    {w.summary.notices[0]}
                                                                                </p>
                                                                            );
                                                                        }
                                                                        return (
                                                                            <div className="mb-3 rounded-xl bg-sky-50 px-4 py-3 text-sm text-slate-700">
                                                                                <p className="font-bold">
                                                                                    최저기온 {w.summary.tempMin}° , 최고기온 {w.summary.tempMax}°
                                                                                </p>
                                                                                <p className="mt-1 text-xs text-slate-500">
                                                                                    체감온도 {w.summary.feelsLike}° · 습도 {w.summary.humidity}% · 풍속 {w.summary.windSpeed}m/s
                                                                                </p>
                                                                                {w.summary.rainProbability !== null && w.summary.rainProbability >= 0 && (
                                                                                    <p className="mt-1 text-xs text-sky-700">
                                                                                        ☔ 강수확률 약 {w.summary.rainProbability}%
                                                                                        {w.summary.rainMm !== null && ` · 예상 ${w.summary.rainMm}mm`}
                                                                                    </p>
                                                                                )}
                                                                                {w.summary.notices.length > 0 && (
                                                                                    <ul className="mt-2 space-y-1">
                                                                                    {w.summary.notices.map((msg, i) => (
                                                                                        <li key={i} className="text-xs font-semibold text-amber-600">
                                                                                        ⚠ {msg}
                                                                                        </li>
                                                                                    ))}
                                                                                    </ul>
                                                                                )}
                                                                                {isToday && (
                                                                                    <p className="mt-2 text-xs font-semibold text-sky-700">
                                                                                        💡 여행 당일은 오늘의 초단기예보가 더 정확합니다. 초단기예보를 활용해보세요!
                                                                                    </p>
                                                                                )}
                                                                            </div>
                                                                        );
                                                                    })()}

                                                                    <div className="space-y-3">
                                                                        {day.places.map((place, index) => (
                                                                            <div
                                                                                key={`${day.day}-${index}`}
                                                                                className="bg-slate-50 rounded-xl p-4"
                                                                            >
                                                                                <div className="flex items-start justify-between gap-3">
                                                                                    <div>
                                                                                        <p className="font-bold text-slate-800">
                                                                                            📍 {place.name}
                                                                                        </p>

                                                                                        <p className="mt-1 text-xs text-slate-500 leading-relaxed">
                                                                                            {place.description}
                                                                                        </p>
                                                                                    </div>

                                                                                    <span className="shrink-0 text-xs font-bold text-sky-600">
                                                                                        {Number(
                                                                                            place.estimatedCost || 0,
                                                                                        ).toLocaleString()}
                                                                                        원
                                                                                    </span>
                                                                                </div>
                                                                            </div>
                                                                        ))}
                                                                    </div>
                                                                </div>
                                                            ))}
                                                        </div>
                                                    </div>
                                                )}
                                            </div>
                                        ) : (
                                            <div className="bg-slate-50 rounded-2xl p-5">
                                                <p className="text-sm text-slate-400">
                                                    AI 추천 결과가 여기에 표시됩니다.
                                                </p>
                                            </div>
                                        )}
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>

            {/* 선택한 여행의 추천 일정 유무에 따라 SOS 활성/안내 상태를 구분 */}
            {selectedPlan && (
                <div className="fixed right-4 top-1/2 z-40 flex -translate-y-1/2 flex-col items-end gap-3 sm:right-7">
                    <button
                        type="button"
                        onClick={() => {
                            if (recommendation && parsedRecommendation && !recommendLoading) {
                                setSosNotice(false);
                                setIsSosOpen(true);
                            } else {
                                setSosNotice(true);
                            }
                        }}
                        aria-label="여행 SOS"
                        aria-disabled={!(recommendation && parsedRecommendation && !recommendLoading)}
                        className={`flex items-center gap-3 rounded-full border-2 border-white px-5 py-4 text-base font-extrabold shadow-2xl transition-all focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-red-500 sm:px-6 sm:py-5 ${
                            recommendation && parsedRecommendation && !recommendLoading
                                ? "bg-red-600 text-white shadow-red-900/30 hover:scale-105 hover:bg-red-700"
                                : "cursor-pointer bg-slate-300 text-slate-600 shadow-slate-900/10 hover:bg-slate-400"
                        }`}
                    >
                        <span className="text-3xl leading-none" aria-hidden="true">🆘</span>
                        <span>여행 SOS</span>
                    </button>
                    {sosNotice && !(recommendation && parsedRecommendation && !recommendLoading) && (
                        <div role="alert" className="relative max-w-[280px] rounded-2xl border border-slate-200 bg-white px-5 py-4 pr-9 text-sm font-semibold leading-relaxed text-slate-700 shadow-xl">
                            {recommendLoading
                                ? "AI 추천 일정을 확인하는 중입니다. 잠시 후 다시 시도해 주세요."
                                : recommendError
                                    ? "AI 추천 일정을 확인할 수 없습니다. 추천 화면의 오류를 확인해 주세요."
                                    : "여행 SOS는 AI 추천 일정이 있어야 이용할 수 있습니다."}
                            <button
                                type="button"
                                onClick={() => setSosNotice(false)}
                                aria-label="안내 닫기"
                                className="absolute right-3 top-3 text-slate-400 hover:text-slate-700"
                            >
                                ✕
                            </button>
                        </div>
                    )}
                </div>
            )}

            {isSosOpen && selectedPlan && recommendation && parsedRecommendation && (
                <TravelReplanningModal
                    plan={selectedPlan}
                    onClose={() => setIsSosOpen(false)}
                />
            )}

            {/* KakaoMapModal 호출 시 저장 핸들러(onSavePlaces) 전달 */}
            <KakaoMapModal
                isOpen={isMapOpen}
                onClose={() => setIsMapOpen(false)}
                places={mapPlaces}
                onSavePlaces={handleSavePlaces}
                onRegenerateDay={handleRegenerateDay}
            />
        </div>
    );
}
