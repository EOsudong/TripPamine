import {
    useEffect,
    useState,
} from "react";

import {
    Link,
    useParams,
} from "react-router-dom";


import {
    getMyInfoApi,
} from "../api/auth";

import {
    extractQuestErrorMessage,
    questApi,
    type QuestResponse,
    type QuestStepResponse,
    type RpgStatsResponse,
    type UserQuestLogResponse,
} from "../api/quest";


import {
    usePreciseGeolocation,
} from "../custom/usePreciseGeolocation";

import AppLayout from "../layout/AppLayout";

import {
    QuestMapView,
} from "../components/QuestMapView";

import {
    RealtimeQuestVerifier,
} from "../components/RealtimeQuestVerifier";

import {
    ChainStepVerifier,
} from "../components/ChainStepVerifier";

import RpgStatsBar from "../components/RpgStatsBar";


// 난이도(1~3 권장)를 ★☆ 문자열로 변환. 범위를 벗어나도 깨지지 않게 1~5로 클램프.
function difficultyStars(difficulty: number) {
    const level = Math.min(5, Math.max(1, difficulty || 1));
    return "★".repeat(level) + "☆".repeat(Math.max(0, 5 - level));
}

export default function QuestDetailPage() {
    const {
        questId:
            questIdParam,
    } =
        useParams<{
            questId:
                string;
        }>();


    const questId =
        Number(
            questIdParam,
        );

    const [
        quest,
        setQuest,
    ] =
        useState<QuestResponse | null>(
            null,
        );


    const [
        questLog,
        setQuestLog,
    ] =
        useState<UserQuestLogResponse | null>(
            null,
        );


    /*
     * CHAIN 퀘스트의 단계 목록 (상태 끌어올림).
     * QuestMapView와 ChainStepVerifier가 같은 "현재 단계" 좌표를 보도록
     * 이 페이지가 직접 소유하고 두 컴포넌트에 props로 내려준다.
     * 일반(CHAIN이 아닌) 퀘스트는 null로 유지된다.
     */
    const [
        steps,
        setSteps,
    ] =
        useState<QuestStepResponse[] | null>(
            null,
        );


    const [
        totalPoints,
        setTotalPoints,
    ] =
        useState(0);


    const [
        stats,
        setStats,
    ] =
        useState<RpgStatsResponse | null>(
            null,
        );


    const [
        levelUpMessage,
        setLevelUpMessage,
    ] =
        useState<string | null>(
            null,
        );


    const [
        loading,
        setLoading,
    ] =
        useState(true);


    const [
        starting,
        setStarting,
    ] =
        useState(false);


    const [
        error,
        setError,
    ] =
        useState<string | null>(
            null,
        );


    /*
     * 퀘스트를 수락했고 아직 완료하지 않은 동안만
     * GPS를 수집한다.
     *
     * 이 위치 Context를 MapView와 Verifier가 공유한다.
     */
    const trackingActive =
        Boolean(
            questLog &&
            questLog.status !==
            "SUCCESS",
        );


    const location =
        usePreciseGeolocation(
            trackingActive,
        );


    /* =====================================================
     * Quest 조회
     * ===================================================== */

    useEffect(() => {
        if (
            !Number.isInteger(
                questId,
            ) ||
            questId <= 0
        ) {
            setError(
                "올바르지 않은 퀘스트 번호입니다.",
            );

            setLoading(
                false,
            );

            return;
        }


        let cancelled =
            false;


        const loadQuest =
            async () => {
                setLoading(
                    true,
                );

                setError(
                    null,
                );


                try {
                    /*
                     * Quest와 내 Quest Log는
                     * 화면 상태 결정에 필수 데이터이므로 함께 조회한다.
                     */
                    const [
                        questResponse,
                        logs,
                    ] =
                        await Promise.all([
                            questApi.getQuest(
                                questId,
                            ),

                            questApi.getMyLogs(),
                        ]);


                    if (cancelled) {
                        return;
                    }


                    setQuest(
                        questResponse,
                    );


                    setQuestLog(
                        logs.find(
                            (log) =>
                                log.questId ===
                                questResponse.questId,
                        ) ?? null,
                    );


                    /*
                     * CHAIN 퀘스트일 때만 단계 목록을 조회한다.
                     * 이 조회가 실패해도 Quest 상세 화면 자체는 계속 보여줘야 하므로
                     * 별도 try/catch로 분리한 비필수(non-critical) 조회로 둔다.
                     */
                    if (
                        questResponse.questType ===
                        "CHAIN"
                    ) {
                        try {
                            const stepList =
                                await questApi.getSteps(
                                    questResponse.questId,
                                );

                            if (!cancelled) {
                                setSteps(
                                    stepList,
                                );
                            }
                        } catch (stepsError) {
                            console.error(
                                "연쇄 퀘스트 단계 조회 실패:",
                                stepsError,
                            );
                        }
                    }


                    /*
                     * 포인트/RPG 스탯 조회 실패는
                     * Quest 전체 화면을 막을 정도의 오류가 아니다.
                     */
                    try {
                        const [myInfo, myStats] =
                            await Promise.all([
                                getMyInfoApi(),
                                questApi.getMyStats(),
                            ]);

                        if (!cancelled) {
                            setTotalPoints(
                                myInfo.totalPoints,
                            );
                            setStats(myStats);
                        }
                    } catch (profileError) {
                        console.error(
                            "퀘스트 포인트/스탯 조회 실패:",
                            profileError,
                        );
                    }
                } catch (loadError) {
                    console.error(
                        "퀘스트 상세 조회 실패:",
                        loadError,
                    );


                    if (!cancelled) {
                        setError(
                            extractQuestErrorMessage(
                                loadError,
                                "퀘스트 상세 정보를 불러오지 못했습니다.",
                            ),
                        );
                    }
                } finally {
                    if (!cancelled) {
                        setLoading(
                            false,
                        );
                    }
                }
            };


        void loadQuest();


        return () => {
            cancelled =
                true;
        };
    }, [questId]);


    /* =====================================================
     * Quest Start
     * ===================================================== */

    const handleStartQuest =
        async () => {
            if (!quest || quest.locked) {
                return;
            }


            try {
                setStarting(
                    true,
                );

                setError(
                    null,
                );


                const log =
                    await questApi.startQuest(
                        quest.questId,
                    );


                setQuestLog(
                    log,
                );
            } catch (startError) {
                setError(
                    extractQuestErrorMessage(
                        startError,
                        "퀘스트를 시작하지 못했습니다.",
                    ),
                );
            } finally {
                setStarting(
                    false,
                );
            }
        };


    /* =====================================================
     * Quest Verification Result
     * ===================================================== */

    const handleLogUpdate =
        async (
            log:
            UserQuestLogResponse,
        ) => {
            setQuestLog(
                log,
            );


            /*
             * SUCCESS가 되면 trackingActive가 false로 바뀌면서
             * usePreciseGeolocation의 watch가 자동 종료된다.
             */
            if (
                log.status ===
                "SUCCESS"
            ) {
                try {
                    const [myInfo, myStats] =
                        await Promise.all([
                            getMyInfoApi(),
                            questApi.getMyStats(),
                        ]);


                    setTotalPoints(
                        myInfo.totalPoints,
                    );

                    setStats((previousStats) => {
                        if (
                            previousStats &&
                            myStats.level > previousStats.level
                        ) {
                            setLevelUpMessage(
                                `레벨 업! Lv.${previousStats.level} → Lv.${myStats.level}`,
                            );
                        }
                        return myStats;
                    });
                } catch (pointError) {
                    console.error(
                        "퀘스트 완료 후 포인트/스탯 갱신 실패:",
                        pointError,
                    );
                }
            }
        };


    /* =====================================================
     * CHAIN Quest - 현재 단계 기준 Map 좌표 override
     *
     * QuestMapView는 quest.targetLat/targetLng/clearRadius를 보고 마커와
     * 거리 계산을 표시한다. CHAIN 퀘스트는 "퀘스트 전체"가 아니라
     * "지금 클리어해야 할 단계"의 좌표를 보여줘야 하므로, 아직 클리어하지
     * 않은 가장 앞 단계를 찾아 그 좌표로 덮어쓴 파생 객체를 만들어 넘긴다.
     * QuestMapView/RealtimeQuestVerifier 자체는 그대로 둔다.
     * ===================================================== */

    const currentStep =
        quest &&
        quest.questType ===
        "CHAIN"
            ? steps?.find(
            (step) =>
                !step.cleared,
        ) ?? null
            : null;


    const isChainQuest =
        Boolean(
            quest &&
            quest.questType ===
            "CHAIN",
        );


    return (
        <AppLayout
            pageClassName="bg-night-950 text-white"
        >
            <div
                className="
            mx-auto
            max-w-6xl
            px-4
            py-8
            md:px-5
          "
            >
                {/* Breadcrumb */}

                <Link
                    to="/quests"

                    className="
              inline-flex
              items-center
              gap-2

              text-sm
              font-semibold
              text-slate-500

              transition

              hover:text-cyan-400
            "
                >
            <span>
              ←
            </span>

                    퀘스트 목록
                </Link>


                {levelUpMessage && (
                    <div
                        className="
                  mt-4
                  rounded-2xl
                  border
                  border-amber-400/30
                  bg-amber-400/10
                  px-5
                  py-3
                  text-center
                  text-sm
                  font-black
                  text-amber-300
                "
                    >
                        🎉 {levelUpMessage}
                        <button
                            type="button"
                            onClick={() => setLevelUpMessage(null)}
                            className="ml-3 text-xs font-bold text-amber-400/70 underline"
                        >
                            닫기
                        </button>
                    </div>
                )}


                {loading ? (
                    <QuestDetailSkeleton />
                ) : error &&
                !quest ? (
                    <div
                        className="
                mt-6
                rounded-2xl
                border
                border-rose-500/20
                bg-rose-500/10
                p-10
                text-center
              "
                    >
                        <p
                            className="
                  text-sm
                  text-rose-300
                "
                        >
                            {error}
                        </p>
                    </div>
                ) : quest ? (
                    <div
                        className="
                mt-6
                space-y-6
              "
                    >
                        {/* =================================================
                  Quest Header
                 ================================================= */}

                        <section
                            className="
                  overflow-hidden
                  rounded-2xl
                  border
                  border-slate-800
                  bg-slate-900
                "
                        >
                            <div
                                className="
                    flex
                    flex-wrap
                    items-start
                    justify-between
                    gap-6
                    p-6
                    md:p-8
                  "
                            >
                                <div>
                                    <div
                                        className="
                        flex
                        flex-wrap
                        items-center
                        gap-2
                      "
                                    >
                      <span
                          className="
                          text-[10px]
                          font-black
                          tracking-[0.18em]
                          text-cyan-400
                        "
                      >
                        {quest.questType} QUEST
                      </span>

                                        <span className="text-xs text-amber-400">
                            {difficultyStars(quest.difficulty)}
                          </span>

                                        <QuestStatusBadge
                                            status={
                                                questLog?.status ??
                                                "NOT_STARTED"
                                            }
                                        />

                                        {quest.locked && (
                                            <span className="rounded-full bg-slate-800 px-2.5 py-1 text-[10px] font-black text-slate-400">
                                🔒 Lv.{quest.requiredLevel} 필요
                              </span>
                                        )}
                                    </div>


                                    <h1
                                        className="
                        mt-4
                        text-2xl
                        font-black
                        tracking-tight
                        md:text-3xl
                      "
                                    >
                                        {quest.questName}
                                    </h1>


                                    <p
                                        className="
                        mt-3
                        max-w-2xl
                        text-sm
                        leading-6
                        text-slate-400
                      "
                                    >
                                        {quest.description ?? (
                                            <>
                                                목표 위치의{" "}
                                                <strong className="text-slate-200">
                                                    {quest.clearRadius}m
                                                </strong>{" "}
                                                인증 반경 안에서 현재 GPS 위치를 확인하면
                                                퀘스트를 완료할 수 있습니다.
                                            </>
                                        )}
                                    </p>
                                </div>


                                <div
                                    className="
                      flex
                      flex-col
                      items-end
                      gap-3
                    "
                                >
                                    <RpgStatsBar stats={stats} compact />

                                    <div className="flex gap-3">
                                        <HeaderMetric
                                            label="보상"
                                            value={`+${quest.rewardPoint.toLocaleString()} P`}
                                            accent
                                        />

                                        <HeaderMetric
                                            label="경험치"
                                            value={`+${quest.rewardExp.toLocaleString()} EXP`}
                                        />

                                        <HeaderMetric
                                            label="보유 포인트"
                                            value={`${totalPoints.toLocaleString()} P`}
                                        />
                                    </div>
                                </div>
                            </div>
                        </section>


                        {error && (
                            <div
                                className="
                    rounded-xl
                    border
                    border-rose-500/20
                    bg-rose-500/10
                    px-4
                    py-3
                    text-sm
                    text-rose-300
                  "
                            >
                                {error}
                            </div>
                        )}


                        {/* =================================================
                  Before Accept
                 ================================================= */}

                        {!questLog ? (
                            <MissionAcceptCard
                                quest={
                                    quest
                                }

                                starting={
                                    starting
                                }

                                onStart={
                                    handleStartQuest
                                }
                            />
                        ) : (
                            /*
                             * 퀘스트 시작 이후.
                             *
                             * 하나의 GPS Context를:
                             *
                             * 1. Map
                             * 2. Verify
                             *
                             * 두 컴포넌트가 공유한다.
                             */
                            <section
                                className="
                    grid
                    gap-6

                    lg:grid-cols-[minmax(0,1fr)_360px]
                    lg:items-start
                  "
                            >
                                <QuestMapView
                                    quest={
                                        currentStep
                                            ? {
                                                ...quest,
                                                questName: `${quest.questName} · ${currentStep.stepOrder}단계: ${currentStep.stepName}`,
                                                targetLat: currentStep.targetLat,
                                                targetLng: currentStep.targetLng,
                                                clearRadius: currentStep.clearRadius,
                                            }
                                            : quest
                                    }

                                    active={
                                        questLog.status !==
                                        "SUCCESS"
                                    }

                                    latitude={
                                        location.latitude
                                    }

                                    longitude={
                                        location.longitude
                                    }

                                    accuracy={
                                        location.accuracy
                                    }
                                />


                                <div
                                    className="
                      space-y-4

                      lg:sticky
                      lg:top-24
                    "
                                >
                                    {isChainQuest ? (
                                        steps ? (
                                            <ChainStepVerifier
                                                quest={quest}

                                                steps={
                                                    steps
                                                }

                                                onStepsChange={
                                                    setSteps
                                                }

                                                initialStatus={
                                                    questLog.status
                                                }

                                                onLogUpdate={
                                                    handleLogUpdate
                                                }

                                                latitude={
                                                    location.latitude
                                                }

                                                longitude={
                                                    location.longitude
                                                }

                                                accuracy={
                                                    location.accuracy
                                                }

                                                gpsSignalStrength={
                                                    location.gpsSignalStrength
                                                }

                                                gpsError={
                                                    location.error
                                                }
                                            />
                                        ) : (
                                            <div
                                                className="
                                h-48
                                animate-pulse
                                rounded-2xl
                                border
                                border-slate-800
                                bg-slate-900
                              "
                                            />
                                        )
                                    ) : quest.questType !== "LOCATION" ? (
                                        // 인증기가 준비되지 않은 유형은 GPS만으로 완료하도록 안내하지 않는다.
                                        <div className="rounded-2xl border border-amber-400/20 bg-amber-400/10 p-6 text-sm leading-7 text-amber-100">
                                            현장 복합 인증 기능을 준비 중입니다.
                                            현재 이 퀘스트는 GPS만으로 완료할 수 없습니다.
                                            <Link to="/quests" className="mt-4 block font-semibold text-cyan-300 underline">
                                                다른 퀘스트 확인하기
                                            </Link>
                                        </div>
                                    ) : (
                                        <>
                                            <MissionProgressCard
                                                status={
                                                    questLog.status
                                                }

                                                clearRadius={
                                                    quest.clearRadius
                                                }
                                            />


                                            <RealtimeQuestVerifier
                                                quest={quest}

                                                initialStatus={
                                                    questLog.status
                                                }

                                                onLogUpdate={
                                                    handleLogUpdate
                                                }

                                                latitude={
                                                    location.latitude
                                                }

                                                longitude={
                                                    location.longitude
                                                }

                                                accuracy={
                                                    location.accuracy
                                                }

                                                gpsSignalStrength={
                                                    location.gpsSignalStrength
                                                }

                                                gpsError={
                                                    location.error
                                                }
                                            />
                                        </>
                                    )}
                                </div>
                            </section>
                        )}
                    </div>
                ) : null}
            </div>


        </AppLayout>
    );
}


/* =====================================================
 * Mission Accept
 * ===================================================== */

function MissionAcceptCard({
                               quest,
                               starting,
                               onStart,
                           }: {
    quest:
        QuestResponse;

    starting:
        boolean;

    onStart:
        () => void;
}) {
    // 공개 중단된 퀘스트는 상세 URL로 직접 들어와도 수락을 막는다.
    if (!quest.isActive) {
        return (
            <section className="mx-auto max-w-2xl rounded-2xl bg-slate-900 p-8 text-center">
                <h2 className="text-xl font-black">현재 참여할 수 없는 퀘스트입니다</h2>
                <Link to="/quests" className="mt-5 inline-block text-cyan-300 underline">퀘스트 목록으로 이동</Link>
            </section>
        );
    }

    // 아직 현장 인증이 구현되지 않은 유형은 수락 버튼을 노출하지 않는다.
    if (quest.questType !== "LOCATION" && quest.questType !== "CHAIN") {
        return (
            <section className="mx-auto max-w-2xl rounded-2xl border border-slate-800 bg-slate-900 p-8 text-center">
                <h2 className="text-xl font-black text-white">현장 인증 준비 중</h2>
                <p className="mt-4 text-sm leading-7 text-slate-400">
                    이 유형은 사진·QR 등 추가 인증 방식이 필요합니다.
                    인증 기능이 준비될 때까지 수락과 보상이 제한됩니다.
                </p>
                <Link to="/quests" className="mt-6 inline-block rounded-xl bg-slate-800 px-6 py-3 text-sm font-bold text-slate-200">
                    다른 퀘스트 보러 가기
                </Link>
            </section>
        );
    }

    if (quest.locked) {
        return (
            <section
                className="
          mx-auto
          max-w-2xl
          rounded-2xl
          border
          border-slate-800
          bg-slate-900
          p-6
          text-center
          md:p-9
        "
            >
                <div
                    className="
            mx-auto
            flex
            h-12
            w-12
            items-center
            justify-center
            rounded-2xl
            bg-slate-800
            text-xl
          "
                >
                    🔒
                </div>

                <p className="mt-5 text-[10px] font-black tracking-[0.18em] text-slate-500">
                    LEVEL LOCKED
                </p>

                <h2 className="mt-2 text-xl font-black md:text-2xl">
                    아직 도전할 수 없는 퀘스트예요
                </h2>

                <p className="mx-auto mt-3 max-w-lg text-sm leading-6 text-slate-400">
                    이 퀘스트는 레벨 {quest.requiredLevel} 이상부터 시작할 수 있습니다.
                    다른 퀘스트를 먼저 클리어해서 레벨을 올려보세요.
                </p>

                <Link
                    to="/quests"
                    className="mt-7 inline-block w-full max-w-sm rounded-xl bg-slate-800 px-5 py-3.5 text-sm font-black text-slate-300 transition hover:bg-slate-700"
                >
                    다른 퀘스트 보러 가기
                </Link>
            </section>
        );
    }

    return (
        <section
            className="
        mx-auto
        max-w-2xl
        rounded-2xl
        border
        border-slate-800
        bg-slate-900
        p-6
        text-center
        md:p-9
      "
        >
            <div
                className="
          mx-auto
          flex
          h-12
          w-12
          items-center
          justify-center
          rounded-2xl
          bg-cyan-400/10
          text-xl
          text-cyan-300
        "
            >
                ◇
            </div>


            <p
                className="
          mt-5
          text-[10px]
          font-black
          tracking-[0.18em]
          text-cyan-400
        "
            >
                MISSION READY
            </p>


            <h2
                className="
          mt-2
          text-xl
          font-black
          md:text-2xl
        "
            >
                퀘스트를 시작할까요?
            </h2>


            <p
                className="
          mx-auto
          mt-3
          max-w-lg
          text-sm
          leading-6
          text-slate-400
        "
            >
                시작하면 현재 위치 추적이 활성화됩니다.
                목표 지점 반경 {quest.clearRadius}m 안에서
                위치 인증을 완료하세요.
            </p>


            <button
                type="button"

                onClick={
                    onStart
                }

                disabled={
                    starting
                }

                className="
          mt-7
          w-full
          max-w-sm

          rounded-xl

          bg-cyan-500

          px-5
          py-3.5

          text-sm
          font-black
          text-slate-950

          transition

          hover:bg-cyan-400

          disabled:cursor-not-allowed
          disabled:opacity-50
        "
            >
                {starting
                    ? "퀘스트 시작 중..."
                    : "퀘스트 시작"}
            </button>


            <p
                className="
          mt-4
          text-[10px]
          text-slate-500
        "
            >
                위치 정보는 퀘스트 진행과 인증에 사용됩니다.
            </p>
        </section>
    );
}


/* =====================================================
 * Progress
 * ===================================================== */

function MissionProgressCard({
                                 status,
                                 clearRadius,
                             }: {
    status:
        UserQuestLogResponse["status"];

    clearRadius:
        number;
}) {
    const data =
        {
            PROGRESS: {
                title:
                    "미션 진행 중",

                description:
                    `목표의 ${clearRadius}m 인증 범위 안으로 이동하세요.`,

                dot:
                    "bg-cyan-400 animate-pulse",
            },

            FAILED: {
                title:
                    "다시 도전할 수 있어요",

                description:
                    "이전 인증에서 목표 범위 밖으로 판정되었습니다.",

                dot:
                    "bg-amber-400",
            },

            SUCCESS: {
                title:
                    "미션 완료",

                description:
                    "위치 인증과 보상 지급이 완료되었습니다.",

                dot:
                    "bg-emerald-400",
            },
        }[status];


    return (
        <div
            className="
        rounded-2xl
        border
        border-slate-800
        bg-slate-900
        p-5
      "
        >
            <p
                className="
          text-[10px]
          font-black
          tracking-[0.18em]
          text-slate-500
        "
            >
                MISSION STATUS
            </p>


            <div
                className="
          mt-3
          flex
          items-start
          gap-3
        "
            >
        <span
            className={`
            mt-1.5
            h-2
            w-2
            shrink-0
            rounded-full
            ${data.dot}
          `}
        />


                <div>
                    <p
                        className="
              text-sm
              font-black
              text-white
            "
                    >
                        {data.title}
                    </p>

                    <p
                        className="
              mt-1
              text-xs
              leading-5
              text-slate-400
            "
                    >
                        {data.description}
                    </p>
                </div>
            </div>
        </div>
    );
}


/* =====================================================
 * Header UI
 * ===================================================== */

function HeaderMetric({
                          label,
                          value,
                          accent = false,
                      }: {
    label:
        string;

    value:
        string;

    accent?:
        boolean;
}) {
    return (
        <div
            className={`
        min-w-28
        rounded-xl
        border
        px-4
        py-3
        text-right

        ${
                accent
                    ? `
              border-amber-400/15
              bg-amber-400/10
            `
                    : `
              border-white/5
              bg-slate-950/50
            `
            }
      `}
        >
            <p
                className="
          text-[10px]
          font-bold
          text-slate-500
        "
            >
                {label}
            </p>


            <p
                className={`
          mt-1
          text-sm
          font-black

          ${
                    accent
                        ? "text-amber-300"
                        : "text-slate-200"
                }
        `}
            >
                {value}
            </p>
        </div>
    );
}


function QuestStatusBadge({
                              status,
                          }: {
    status:
        | UserQuestLogResponse["status"]
        | "NOT_STARTED";
}) {
    const data =
        {
            NOT_STARTED: {
                label:
                    "시작 전",

                className:
                    "bg-slate-800 text-slate-400",
            },

            PROGRESS: {
                label:
                    "진행 중",

                className:
                    "bg-cyan-400/10 text-cyan-300",
            },

            FAILED: {
                label:
                    "재도전",

                className:
                    "bg-amber-400/10 text-amber-300",
            },

            SUCCESS: {
                label:
                    "완료",

                className:
                    "bg-emerald-400/10 text-emerald-300",
            },
        }[status];


    return (
        <span
            className={`
        rounded-full
        px-2.5
        py-1

        text-[10px]
        font-black

        ${data.className}
      `}
        >
      {data.label}
    </span>
    );
}


/* =====================================================
 * Loading
 * ===================================================== */

function QuestDetailSkeleton() {
    return (
        <div
            className="
        mt-6
        space-y-6
      "
        >
            <div
                className="
          h-40
          animate-pulse
          rounded-2xl
          border
          border-slate-800
          bg-slate-900
        "
            />

            <div
                className="
          grid
          gap-6
          lg:grid-cols-[minmax(0,1fr)_360px]
        "
            >
                <div
                    className="
            h-[620px]
            animate-pulse
            rounded-2xl
            border
            border-slate-800
            bg-slate-900
          "
                />

                <div
                    className="
            h-80
            animate-pulse
            rounded-2xl
            border
            border-slate-800
            bg-slate-900
          "
                />
            </div>
        </div>
    );
}
