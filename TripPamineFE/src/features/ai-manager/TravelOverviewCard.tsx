
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import {
    getAiManagerTravelOverviewApi,
    type AiManagerTravelOverview,
    type AiManagerTravelItem,
} from "../../api/aiManagerTravel";

/**
 * 금액을 한글 사용자 화면에 맞게 표시한다.
 *
 * 예:
 * 400000 → 400,000원
 */
function formatWon(amount: number | null): string {
    if (amount === null || !Number.isFinite(amount)) {
        return "예산 미설정";
    }

    return `${amount.toLocaleString("ko-KR")}원`;
}

/**
 * 서버에서 받은 날짜를 읽기 쉽게 표시한다.
 *
 * 예:
 * 2026-10-17T09:00:00
 * → 2026.10.17
 *
 * 시간대 변환을 하지 않고 저장된 날짜 부분을 사용한다.
 */
function formatDate(date: string | null): string {
    if (!date) {
        return "미정";
    }

    return date.slice(0, 10).replaceAll("-", ".");
}

/**
 * 출발까지 남은 날짜 표시.
 *
 * 서버에서 계산한 값을 그대로 사용한다.
 * 지난 여행에는 남은 날짜를 표시하지 않는다.
 */
function formatDday(plan: AiManagerTravelItem): string | null {
    if (plan.phase !== "PLANNED") {
        return null;
    }

    if (plan.daysUntilStart === null) {
        return null;
    }

    if (plan.daysUntilStart === 0) {
        return "오늘 출발";
    }

    return `D-${plan.daysUntilStart}`;
}

/**
 * AI 매니저 여행 현황 카드.
 *
 * 하는 일:
 * 1. 현재 사용자의 여행 정보 가져오기
 * 2. 여행 상태와 예산 표시
 * 3. 여행 관리 화면으로 이동
 *
 * 하지 않는 일:
 * - 여행 기록 추가
 * - 여행 정보 수정
 * - 자동 결제
 * - 외부 AI 모델로 정보 전송
 */
export default function TravelOverviewCard() {

    const navigate = useNavigate();

    const [overview, setOverview] =
        useState<AiManagerTravelOverview | null>(null);

    const [loading, setLoading] = useState(true);

    const [error, setError] =
        useState<string | null>(null);

    // 다시 불러오기 버튼을 위한 상태
    const [reloadCount, setReloadCount] = useState(0);

    /**
     * 카드가 나타나면 여행 정보를 가져온다.
     *
     * 화면을 떠난 뒤 늦게 도착한 결과는
     * 화면 상태에 반영하지 않는다.
     */
    useEffect(() => {
        let cancelled = false;

        setLoading(true);
        setError(null);
        setOverview(null);

        getAiManagerTravelOverviewApi()
            .then((result) => {
                if (!cancelled) {
                    setOverview(result);
                }
            })
            .catch(() => {
                if (!cancelled) {
                    setError(
                        "여행 정보를 불러오지 못했어요."
                    );
                }
            })
            .finally(() => {
                if (!cancelled) {
                    setLoading(false);
                }
            });

        return () => {
            cancelled = true;
        };
    }, [reloadCount]);

    return (
        <section
            className="
                rounded-3xl
                bg-white/90
                p-5
                shadow-[0_20px_50px_-35px_rgba(60,40,180,0.35)]
                md:p-6
            "
        >
            {/* 제목 영역 */}
            <div
                className="
                    flex
                    flex-wrap
                    items-center
                    justify-between
                    gap-3
                "
            >
                <div>
                    <p
                        className="
                            text-[11px]
                            font-bold
                            tracking-wide
                            text-sky-600
                        "
                    >
                        나의 여행
                    </p>

                    <h2
                        className="
                            mt-1
                            text-lg
                            font-bold
                            text-slate-900
                        "
                    >
                        여행 일정 한눈에 보기
                    </h2>

                    <p className="mt-2 text-sm text-slate-500">
                        예정된 여행과 지난 여행을
                        한곳에서 확인하세요.
                    </p>
                </div>

                <button
                    type="button"
                    onClick={() => navigate("/travel")}
                    className="
                        rounded-xl
                        bg-sky-600
                        px-4
                        py-2.5
                        text-sm
                        font-bold
                        text-white
                        shadow-sm
                        transition
                        hover:bg-sky-700
                    "
                >
                    여행 관리하기
                </button>
            </div>

            {/* 처음 정보를 가져오는 중 */}
            {loading && (
                <p
                    className="
                        mt-6
                        py-8
                        text-center
                        text-sm
                        text-slate-400
                    "
                >
                    여행 일정을 살펴보고 있어요.
                </p>
            )}

            {/* 조회 실패 시 재시도 */}
            {!loading && error && (
                <div className="mt-6 text-center">
                    <p
                        role="alert"
                        className="text-sm text-rose-600"
                    >
                        {error}
                    </p>

                    <button
                        type="button"
                        onClick={() =>
                            setReloadCount((count) => count + 1)
                        }
                        className="
                            mt-3
                            rounded-xl
                            bg-slate-100
                            px-4
                            py-2
                            text-sm
                            font-bold
                            text-slate-700
                        "
                    >
                        다시 불러오기
                    </button>
                </div>
            )}

            {/* 여행 정보가 정상 조회된 경우 */}
            {!loading && !error && overview && (
                <>
                    {/* 전체 여행 상태 요약 */}
                    <div
                        className="
                            mt-6
                            grid
                            grid-cols-3
                            gap-2
                            rounded-2xl
                            bg-slate-50
                            p-4
                        "
                    >
                        <SummaryItem
                            label="준비 중"
                            count={overview.plannedCount}
                        />

                        <SummaryItem
                            label="여행 중"
                            count={overview.activeCount}
                        />

                        <SummaryItem
                            label="지난 여행"
                            count={overview.completedCount}
                        />
                    </div>

                    {/* 여행이 없으면 안내 문구 표시 */}
                    {overview.plans.length === 0 ? (
                        <div className="py-10 text-center">
                            <p className="text-sm text-slate-500">
                                아직 등록한 여행이 없어요.
                            </p>

                            <p className="mt-1 text-xs text-slate-400">
                                새로운 여행을 계획해 보세요.
                            </p>
                        </div>
                    ) : (
                        <div
                            className="
                                mt-5
                                grid
                                grid-cols-1
                                gap-3
                                md:grid-cols-2
                            "
                        >
                            {overview.plans.map((plan) => (
                                <TravelItemCard
                                    key={plan.planId}
                                    plan={plan}
                                />
                            ))}
                        </div>
                    )}
                </>
            )}
        </section>
    );
}

/**
 * 상태별 여행 건수 표시.
 */
function SummaryItem({
                         label,
                         count,
                     }: {
    label: string;
    count: number;
}) {
    return (
        <div className="text-center">
            <p className="text-xs text-slate-500">
                {label}
            </p>

            <p
                className="
                    mt-1
                    text-xl
                    font-bold
                    text-slate-900
                "
            >
                {count}
                <span className="ml-0.5 text-xs">개</span>
            </p>
        </div>
    );
}

/**
 * 실제 여행 계획 한 건을 보여주는 카드.
 *
 * 현재 여행인지, 준비 중인지에 따라
 * 상태 색상만 가볍게 구분한다.
 */
function TravelItemCard({
                            plan,
                        }: {
    plan: AiManagerTravelItem;
}) {
    const dday = formatDday(plan);

    return (
        <article
            className="
                rounded-2xl
                bg-slate-50
                p-4
            "
        >
            <div
                className="
                    flex
                    items-start
                    justify-between
                    gap-2
                "
            >
                <div className="min-w-0">
                    <p
                        className="
                            break-words
                            text-sm
                            font-bold
                            text-slate-900
                        "
                    >
                        {plan.planName}
                    </p>

                    <p className="mt-1 text-xs text-slate-500">
                        {plan.statusLabel}
                    </p>
                </div>

                {dday && (
                    <span
                        className="
                            shrink-0
                            rounded-full
                            bg-sky-100
                            px-3
                            py-1
                            text-xs
                            font-bold
                            text-sky-700
                        "
                    >
                        {dday}
                    </span>
                )}
            </div>

            <div className="mt-4 space-y-2">
                <div
                    className="
                        flex
                        justify-between
                        gap-3
                        text-xs
                    "
                >
                    <span className="text-slate-400">
                        여행 일정
                    </span>

                    <span
                        className="
                            text-right
                            font-medium
                            text-slate-700
                        "
                    >
                        {formatDate(plan.startDate)}
                        {" ~ "}
                        {formatDate(plan.endDate)}
                    </span>
                </div>

                <div
                    className="
                        flex
                        justify-between
                        gap-3
                        text-xs
                    "
                >
                    <span className="text-slate-400">
                        여행 예산
                    </span>

                    <span
                        className="
                            font-bold
                            text-slate-800
                        "
                    >
                        {formatWon(plan.totalBudget)}
                    </span>
                </div>
            </div>
        </article>
    );
}
