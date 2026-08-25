import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { TravelPlan } from "../types";
import { getTravelPlansApi } from "../api/travel";
import {
    getAiRecommendationApi,
    regenerateAiRecommendationApi,
    type AiRecommendationResponse,
} from "../api/recommendation";
import aiTravelBg from "../assets/images/ai-travel-bg.png";
import { KakaoMapModal } from '../components/KakaoMapModal';

export default function AiRecommendPage() {
    const navigate = useNavigate();

    const [plans, setPlans] = useState<TravelPlan[]>([]);
    const [selectedPlan, setSelectedPlan] = useState<TravelPlan | null>(null);
    const [loading, setLoading] = useState(true);
    const [recommendation, setRecommendation] =
        useState<AiRecommendationResponse | null>(null);
    const [parsedRecommendation, setParsedRecommendation] =
        useState<ParsedRecommendation | null>(null);

    const [recommendLoading, setRecommendLoading] = useState(false);
    const [recommendError, setRecommendError] = useState("");

    // 지도 모달 열림/닫힘 상태
    const [isMapOpen, setIsMapOpen] = useState(false);

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
        setSelectedPlan(plan);
        setRecommendation(null);
        setParsedRecommendation(null);
        setRecommendError("");
        setRecommendLoading(true);

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

    // KakaoMapModal에 전달할 1차원 평탄화(Flat) 장소 리스트 변환
    const mapPlaces = parsedRecommendation?.days.flatMap((d) =>
        d.places.map((p) => ({
            name: p.name,
            address: p.name,
            day: d.day,
        }))
    ) || [];

    return (
        <div
            className="min-h-screen bg-cover bg-center bg-fixed"
            style={{
                backgroundImage: `url(${aiTravelBg})`,
            }}
        >
            <div className="min-h-screen bg-white/75 backdrop-blur-[1px] p-8">
                <div className="max-w-7xl mx-auto">
                    {/* 페이지 헤더 영역 */}
                    <div className="mb-8 flex items-center justify-between">
                        <div>
                            <p className="text-sky-500 text-xs font-bold tracking-widest uppercase">
                                AI Travel Planner
                            </p>

                            <h1 className="mt-2 text-3xl font-bold text-slate-800">
                                ✨ AI 여행 추천
                            </h1>

                            <p className="mt-2 text-sm text-slate-500">
                                등록된 여행을 선택하면 AI가 조건에 맞는 여행 일정을
                                추천해드립니다.
                            </p>
                        </div>

                        {/* 메인페이지(Hero.tsx) 이동 버튼 */}
                        <button
                            type="button"
                            onClick={() => navigate("/")}
                            className="
                                px-4 py-2.5
                                bg-white hover:bg-slate-50
                                border border-slate-200 hover:border-sky-300
                                text-slate-700 hover:text-sky-600 text-xs font-bold
                                rounded-2xl shadow-sm hover:shadow
                                transition-all duration-200
                                flex items-center gap-2
                            "
                        >
                            🏠 메인으로 돌아가기
                        </button>
                    </div>

                    <div className="grid grid-cols-1 lg:grid-cols-[360px_1fr] gap-6">
                        {/* 왼쪽 - 등록된 여행 목록 */}
                        <div className="bg-white rounded-3xl border border-slate-100 p-5 shadow-sm">
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

                        {/* 오른쪽 - AI 추천 영역 */}
                        <div className="bg-white rounded-3xl border border-slate-100 p-6 shadow-sm">
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
                                                                {/* 지도로 일정 보기 버튼 */}
                                                                <button
                                                                    type="button"
                                                                    onClick={() => setIsMapOpen(true)}
                                                                    className="
                                                                        px-4 py-2
                                                                        bg-sky-500 hover:bg-sky-600
                                                                        text-white text-xs font-bold
                                                                        rounded-xl shadow-md shadow-sky-500/20
                                                                        transition-all duration-300
                                                                        hover:scale-105
                                                                    "
                                                                >
                                                                    🗺️ 지도로 일정 보기
                                                                </button>

                                                                {/* 추천 다시 받기 버튼 */}
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

                                                        <div className="space-y-4">
                                                            {parsedRecommendation.days.map((day) => (
                                                                <div
                                                                    key={day.day}
                                                                    className="bg-white border border-slate-200 rounded-2xl p-5"
                                                                >
                                                                    <div className="flex items-center gap-2 mb-4">
                                                                        <span className="w-8 h-8 flex items-center justify-center rounded-xl bg-sky-500 text-white text-sm font-bold">
                                                                          {day.day}
                                                                        </span>

                                                                        <h3 className="font-bold text-slate-800">
                                                                            {day.day}일차 일정
                                                                        </h3>
                                                                    </div>

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

            {/* 카카오 지도 모달 */}
            <KakaoMapModal
                isOpen={isMapOpen}
                onClose={() => setIsMapOpen(false)}
                places={mapPlaces}
            />
        </div>
    );
}