import {
    useEffect,
    useMemo,
    useState,
    type FormEvent,
} from "react";

import {
    useNavigate,
} from "react-router-dom";

import {
    getMyInfoApi,
    type MyInfoResponse,
} from "../../api/auth";

import {
    getLatestEmotionApi,
    type LatestEmotionResponse,
} from "../../api/aiManager";

import {
    getTravelPlansApi,
} from "../../api/travel";

import {
    questApi,
    type UserQuestLogResponse,
} from "../../api/quest";

import type {
    TravelPlan,
} from "../../types";

import {
    useAuth,
} from "../../context/AuthContext";

import aiManagerBg from "../../assets/images/ai-travel-bg.png";


type ManagerDomain =
    | "travel"
    | "quest"
    | "emotion"
    | "system";


interface ManagerAttentionItem {
    id: string;
    domain: ManagerDomain;
    title: string;
    description: string;
    actionLabel: string;
    to: string;
    priority: "high" | "normal";
}


interface ManagerActivityItem {
    id: string;
    domain: ManagerDomain;
    title: string;
    description: string;
    timeLabel: string;
}


/**
 * AI Manager v3 Home
 *
 * 기존 Dashboard 중심 화면을:
 *
 * 1. Compact Personal Header
 * 2. Needs Attention
 * 3. Activity Feed
 * 4. Quick Workspaces
 * 5. Persistent Manager Command
 *
 * 중심 구조로 변경한다.
 *
 * 현재 단계에서는 실제 Manager LLM이나 Action API를
 * 호출하지 않는다.
 *
 * 이미 구현된 실제 Context만 사용한다:
 *
 * - Profile
 * - Emotion
 * - Travel
 * - Quest
 */
export default function AiManagerDashboard() {
    const navigate =
        useNavigate();

    const {
        isLoggedIn,
    } = useAuth();


    const [
        profile,
        setProfile,
    ] =
        useState<MyInfoResponse | null>(
            null,
        );

    const [
        emotion,
        setEmotion,
    ] =
        useState<LatestEmotionResponse | null>(
            null,
        );

    const [
        travelPlans,
        setTravelPlans,
    ] =
        useState<TravelPlan[]>([]);

    const [
        questLogs,
        setQuestLogs,
    ] =
        useState<UserQuestLogResponse[]>([]);

    const [
        loading,
        setLoading,
    ] =
        useState(false);

    const [
        partialFailure,
        setPartialFailure,
    ] =
        useState(false);


    /* =====================================================
     * Context Loading
     * ===================================================== */

    useEffect(() => {
        if (!isLoggedIn) {
            setProfile(null);
            setEmotion(null);
            setTravelPlans([]);
            setQuestLogs([]);
            setPartialFailure(false);

            return;
        }


        let cancelled =
            false;


        const loadContext =
            async () => {
                setLoading(true);


                /*
                 * 하나의 도메인 API가 실패해도
                 * 전체 AI Manager를 실패시키지 않는다.
                 */
                const results =
                    await Promise.allSettled([
                        getMyInfoApi(),
                        getLatestEmotionApi(),
                        getTravelPlansApi(),
                        questApi.getMyLogs(),
                    ]);


                if (cancelled) {
                    return;
                }


                const [
                    profileResult,
                    emotionResult,
                    travelResult,
                    questResult,
                ] =
                    results;


                setProfile(
                    profileResult.status ===
                    "fulfilled"
                        ? profileResult.value
                        : null,
                );


                setEmotion(
                    emotionResult.status ===
                    "fulfilled"
                        ? emotionResult.value
                        : null,
                );


                setTravelPlans(
                    travelResult.status ===
                    "fulfilled"
                        ? travelResult.value
                        : [],
                );


                setQuestLogs(
                    questResult.status ===
                    "fulfilled"
                        ? questResult.value
                        : [],
                );


                setPartialFailure(
                    results.some(
                        (result) =>
                            result.status ===
                            "rejected",
                    ),
                );


                setLoading(false);
            };


        void loadContext();


        return () => {
            cancelled =
                true;
        };
    }, [isLoggedIn]);


    /* =====================================================
     * Manager Context
     * ===================================================== */

    const focusTravel =
        useMemo(
            () =>
                selectFocusTravel(
                    travelPlans,
                ),
            [travelPlans],
        );


    const activeQuestCount =
        useMemo(
            () =>
                questLogs.filter(
                    (log) =>
                        log.status ===
                        "PROGRESS",
                ).length,
            [questLogs],
        );


    const completedQuestCount =
        useMemo(
            () =>
                questLogs.filter(
                    (log) =>
                        log.status ===
                        "SUCCESS",
                ).length,
            [questLogs],
        );


    /*
     * 사용자가 지금 확인할 필요가 있는 항목.
     *
     * 장기적으로 이 계산은 Backend Priority Engine으로 이동한다.
     */
    const attentionItems =
        useMemo(
            () =>
                buildAttentionItems(
                    focusTravel,
                    activeQuestCount,
                ),
            [
                focusTravel,
                activeQuestCount,
            ],
        );


    /*
     * 실제 기록을 기반으로 Activity Feed 생성.
     *
     * 현재:
     * - Emotion observation
     * - Quest completion
     *
     * 향후 AI_MANAGER_EVENTS가 이 역할을 직접 담당한다.
     */
    const activityItems =
        useMemo(
            () =>
                buildActivityItems(
                    emotion,
                    questLogs,
                ),
            [
                emotion,
                questLogs,
            ],
        );


    if (!isLoggedIn) {
        return (
            <GuestManagerView
                onLogin={() =>
                    navigate("/login")
                }
                onExploreTravel={() =>
                    navigate("/travel")
                }
            />
        );
    }


    return (
        <div
            className="
        min-h-screen
        bg-[#F7F9FC]
        pb-8
      "
        >
            {/* =================================================
          1. Compact AI Header
         ================================================= */}

            <section
                className="
          relative
          min-h-[310px]
          overflow-hidden
          border-b
          border-slate-900/20
        "
            >
                <img
                    src={aiManagerBg}
                    alt=""
                    className="
            absolute
            inset-0
            h-full
            w-full
            object-cover
            object-center
          "
                />


                <div
                    className="
            absolute
            inset-0

            bg-gradient-to-r
            from-slate-950/95
            via-slate-950/80
            to-slate-950/40
          "
                />


                <div
                    className="
            absolute
            inset-0

            bg-gradient-to-t
            from-slate-950/75
            via-transparent
            to-slate-950/20
          "
                />


                <div
                    className="
            relative
            z-10

            mx-auto
            flex
            min-h-[310px]
            max-w-6xl
            items-center

            px-5
            py-10
          "
                >
                    <div
                        className="
              grid
              w-full
              gap-8

              lg:grid-cols-[minmax(0,1fr)_260px]
              lg:items-center
            "
                    >
                        <div>
                            <div
                                className="
                  flex
                  items-center
                  gap-2
                "
                            >
                <span
                    className={`
                    h-2
                    w-2
                    rounded-full

                    ${
                        loading
                            ? "animate-pulse bg-amber-300"
                            : "bg-emerald-400"
                    }
                  `}
                />

                                <span
                                    className="
                    text-[10px]
                    font-black
                    tracking-[0.18em]
                    text-sky-200
                  "
                                >
                  AI MANAGER
                </span>
                            </div>


                            <h1
                                className="
                  mt-4

                  text-3xl
                  font-black
                  tracking-[-0.035em]
                  text-white

                  md:text-4xl
                  lg:text-5xl
                "
                            >
                                {getTimeGreeting()},
                                <br />

                                <span
                                    className="
                    bg-gradient-to-r
                    from-white
                    via-sky-100
                    to-sky-300

                    bg-clip-text
                    text-transparent
                  "
                                >
                  {profile?.userName ??
                      "여행자"}님.
                </span>
                            </h1>


                            <p
                                className="
                  mt-4
                  max-w-2xl

                  text-sm
                  leading-6
                  text-slate-200/80

                  md:text-base
                "
                            >
                                {attentionItems.length >
                                0
                                    ? `오늘 확인할 내용이 ${attentionItems.length}개 있어요. 중요한 것부터 정리해드릴게요.`
                                    : "지금 바로 확인해야 할 중요한 항목은 없어요. 최근 Context를 정리해두었습니다."}
                            </p>
                        </div>


                        {/* AI Status Visual */}
                        <div
                            className="
                hidden
                items-center
                justify-center
                lg:flex
              "
                        >
                            <ManagerCore
                                loading={loading}
                            />
                        </div>
                    </div>
                </div>
            </section>


            <div
                className="
          relative
          z-20

          mx-auto
          max-w-6xl

          px-5
        "
            >
                {/* =================================================
            2. Needs Attention
           ================================================= */}

                <section
                    className="
            -mt-8

            overflow-hidden
            rounded-2xl

            border
            border-slate-200

            bg-white

            shadow-xl
            shadow-slate-200/40
          "
                >
                    <div
                        className="
              flex
              flex-wrap
              items-center
              justify-between
              gap-3

              border-b
              border-slate-100

              px-5
              py-4
              md:px-6
            "
                    >
                        <div>
                            <p
                                className="
                  text-[10px]
                  font-black
                  tracking-[0.16em]
                  text-sky-600
                "
                            >
                                NEEDS YOUR ATTENTION
                            </p>

                            <h2
                                className="
                  mt-1
                  text-lg
                  font-black
                  text-slate-950
                "
                            >
                                지금 확인할 것
                            </h2>
                        </div>


                        <span
                            className="
                rounded-full
                bg-slate-100

                px-3
                py-1

                text-[11px]
                font-bold
                text-slate-500
              "
                        >
              {attentionItems.length}개
            </span>
                    </div>


                    {attentionItems.length ===
                    0 ? (
                        <div
                            className="
                px-6
                py-10
                text-center
              "
                        >
                            <div
                                className="
                  mx-auto
                  flex
                  h-10
                  w-10
                  items-center
                  justify-center

                  rounded-full
                  bg-emerald-50

                  text-emerald-500
                "
                            >
                                ✓
                            </div>

                            <p
                                className="
                  mt-3
                  text-sm
                  font-bold
                  text-slate-700
                "
                            >
                                지금은 확인할 항목이 없어요.
                            </p>

                            <p
                                className="
                  mt-1
                  text-xs
                  text-slate-400
                "
                            >
                                필요한 일이 생기면 AI Manager가 여기에 먼저 정리합니다.
                            </p>
                        </div>
                    ) : (
                        <div>
                            {attentionItems.map(
                                (
                                    item,
                                    index,
                                ) => (
                                    <AttentionRow
                                        key={item.id}
                                        item={item}
                                        last={
                                            index ===
                                            attentionItems.length -
                                            1
                                        }
                                        onOpen={() =>
                                            navigate(
                                                item.to,
                                            )
                                        }
                                    />
                                ),
                            )}
                        </div>
                    )}
                </section>


                {/* =================================================
            3. Activity + Context
           ================================================= */}

                <section
                    className="
            grid
            gap-6

            py-8

            lg:grid-cols-[minmax(0,1fr)_340px]
          "
                >
                    {/* Activity Feed */}

                    <article
                        className="
              rounded-2xl
              border
              border-slate-200
              bg-white
            "
                    >
                        <div
                            className="
                border-b
                border-slate-100

                px-5
                py-5
                md:px-6
              "
                        >
                            <p
                                className="
                  text-[10px]
                  font-black
                  tracking-[0.16em]
                  text-slate-400
                "
                            >
                                AI ACTIVITY
                            </p>

                            <h2
                                className="
                  mt-1
                  text-lg
                  font-black
                  text-slate-950
                "
                            >
                                최근 변화
                            </h2>
                        </div>


                        <div
                            className="
                px-5
                py-2
                md:px-6
              "
                        >
                            {activityItems.map(
                                (
                                    item,
                                    index,
                                ) => (
                                    <ActivityRow
                                        key={item.id}
                                        item={item}
                                        last={
                                            index ===
                                            activityItems.length -
                                            1
                                        }
                                    />
                                ),
                            )}
                        </div>
                    </article>


                    {/* Current Context */}

                    <aside
                        className="
              rounded-2xl
              border
              border-slate-200
              bg-white
              p-5
            "
                    >
                        <p
                            className="
                text-[10px]
                font-black
                tracking-[0.16em]
                text-slate-400
              "
                        >
                            CURRENT CONTEXT
                        </p>

                        <h2
                            className="
                mt-1
                text-lg
                font-black
                text-slate-950
              "
                        >
                            상세정보
                        </h2>


                        <div
                            className="
                mt-5
                divide-y
                divide-slate-100
              "
                        >
                            <ContextRow
                                label="감정"
                                value={
                                    emotion?.fineLabelName ??
                                    "기록 없음"
                                }
                            />

                            <ContextRow
                                label="다음 여행"
                                value={
                                    focusTravel?.planName ??
                                    "예정 없음"
                                }
                            />

                            <ContextRow
                                label="진행 퀘스트"
                                value={`${activeQuestCount}개`}
                            />

                            <ContextRow
                                label="완료 퀘스트"
                                value={`${completedQuestCount}개`}
                            />

                            <ContextRow
                                label="자산"
                                value="연결 준비 중"
                                muted
                            />
                        </div>


                        {partialFailure && (
                            <div
                                className="
                  mt-5

                  rounded-xl

                  border
                  border-amber-200

                  bg-amber-50

                  px-3
                  py-2.5

                  text-xs
                  leading-5
                  text-amber-700
                "
                            >
                                일부 Context를 불러오지 못했습니다.
                            </div>
                        )}
                    </aside>
                </section>


                {/* =================================================
            4. Workspaces
           ================================================= */}

                <section
                    className="
            pb-8
          "
                >
                    <div
                        className="
              flex
              items-end
              justify-between
            "
                    >
                        <div>
                            <p
                                className="
                  text-[10px]
                  font-black
                  tracking-[0.16em]
                  text-slate-400
                "
                            >
                                WORKSPACES
                            </p>

                            <h2
                                className="
                  mt-1
                  text-lg
                  font-black
                  text-slate-950
                "
                            >
                                직접 열기
                            </h2>
                        </div>
                    </div>


                    <div
                        className="
              mt-4
              grid
              gap-2

              md:grid-cols-3
            "
                    >
                        <WorkspaceRow
                            label="여행"
                            description="여행 계획과 미스터리 투어"
                            onClick={() =>
                                navigate("/travel")
                            }
                        />

                        <WorkspaceRow
                            label="AI 여행 추천"
                            description="등록한 여행에 맞는 일정 생성"
                            onClick={() =>
                                navigate(
                                    "/ai-recommend",
                                )
                            }
                        />

                        <WorkspaceRow
                            label="퀘스트"
                            description="위치 기반 미션과 보상"
                            onClick={() =>
                                navigate("/quests")
                            }
                        />
                    </div>
                </section>


                {/* =================================================
            5. Persistent Manager Command
           ================================================= */}

                <ManagerCommandBar
                    onNavigate={navigate}
                />
            </div>
        </div>
    );
}


/* =====================================================
 * Guest
 * ===================================================== */

function GuestManagerView({
                              onLogin,
                              onExploreTravel,
                          }: {
    onLogin: () => void;
    onExploreTravel: () => void;
}) {
    return (
        <section
            className="
        relative

        min-h-[calc(100vh-4rem)]

        overflow-hidden
      "
        >
            <img
                src={aiManagerBg}
                alt=""
                className="
          absolute
          inset-0
          h-full
          w-full
          object-cover
          object-center
        "
            />


            <div
                className="
          absolute
          inset-0

          bg-gradient-to-r
          from-slate-950/95
          via-slate-950/80
          to-slate-950/45
        "
            />


            <div
                className="
          absolute
          inset-0

          bg-gradient-to-t
          from-slate-950/80
          via-transparent
          to-slate-950/20
        "
            />


            <div
                className="
          relative
          z-10

          mx-auto
          flex

          min-h-[calc(100vh-4rem)]
          max-w-6xl

          items-center

          px-5
          py-20
        "
            >
                <div
                    className="
            max-w-3xl
          "
                >
                    <div
                        className="
              inline-flex
              items-center
              gap-2

              rounded-full

              border
              border-white/15

              bg-white/10

              px-3
              py-1.5

              backdrop-blur
            "
                    >
            <span
                className="
                h-2
                w-2
                rounded-full
                bg-sky-400
              "
            />

                        <span
                            className="
                text-[10px]
                font-black
                tracking-[0.18em]
                text-sky-100
              "
                        >
              TRIPPAMINE AI MANAGER
            </span>
                    </div>


                    <h1
                        className="
              mt-6

              text-4xl
              font-black
              tracking-[-0.04em]
              text-white

              md:text-6xl
              lg:text-7xl
            "
                    >
                        여행을 넘어,
                        <br />

                        <span
                            className="
                bg-gradient-to-r
                from-white
                via-sky-100
                to-sky-300

                bg-clip-text
                text-transparent
              "
                        >
              당신의 일상을
              이해하는 AI
            </span>
                    </h1>


                    <p
                        className="
              mt-6
              max-w-2xl

              text-base
              leading-7
              text-slate-200/80

              md:text-lg
            "
                    >
                        감정, 여행, 퀘스트,
                        자산과 기록을 연결해
                        필요한 일을 먼저 정리하고
                        다음 행동을 제안합니다.
                    </p>


                    <div
                        className="
              mt-9
              flex
              flex-wrap
              gap-3
            "
                    >
                        <button
                            type="button"
                            onClick={onLogin}
                            className="
                rounded-xl

                bg-sky-500

                px-6
                py-3.5

                text-sm
                font-black
                text-white

                transition

                hover:bg-sky-400
              "
                        >
                            AI Manager 시작하기
                        </button>


                        <button
                            type="button"
                            onClick={
                                onExploreTravel
                            }
                            className="
                rounded-xl

                border
                border-white/15

                bg-white/10

                px-6
                py-3.5

                text-sm
                font-bold
                text-white

                backdrop-blur

                transition

                hover:bg-white/15
              "
                        >
                            여행 기능 둘러보기
                        </button>
                    </div>
                </div>
            </div>
        </section>
    );
}


/* =====================================================
 * Attention
 * ===================================================== */

function AttentionRow({
                          item,
                          last,
                          onOpen,
                      }: {
    item: ManagerAttentionItem;
    last: boolean;
    onOpen: () => void;
}) {
    return (
        <div
            className={`
        flex
        flex-col
        gap-4

        px-5
        py-5

        md:flex-row
        md:items-center

        md:px-6

        ${
                !last
                    ? "border-b border-slate-100"
                    : ""
            }
      `}
        >
            <DomainIndicator
                domain={
                    item.domain
                }
            />


            <div
                className="
          min-w-0
          flex-1
        "
            >
                <div
                    className="
            flex
            items-center
            gap-2
          "
                >
                    <p
                        className="
              font-black
              text-slate-900
            "
                    >
                        {item.title}
                    </p>

                    {item.priority ===
                        "high" && (
                            <span
                                className="
                rounded-full

                bg-rose-50

                px-2
                py-0.5

                text-[9px]
                font-black
                text-rose-500
              "
                            >
              PRIORITY
            </span>
                        )}
                </div>


                <p
                    className="
            mt-1

            text-sm
            leading-5
            text-slate-500
          "
                >
                    {item.description}
                </p>
            </div>


            <button
                type="button"
                onClick={onOpen}
                className="
          shrink-0

          rounded-xl

          border
          border-slate-200

          bg-white

          px-4
          py-2.5

          text-xs
          font-bold
          text-slate-700

          transition

          hover:border-sky-300
          hover:text-sky-600
        "
            >
                {item.actionLabel}
            </button>
        </div>
    );
}


/* =====================================================
 * Activity
 * ===================================================== */

function ActivityRow({
                         item,
                         last,
                     }: {
    item: ManagerActivityItem;
    last: boolean;
}) {
    return (
        <div
            className="
        flex
        gap-4
      "
        >
            <div
                className="
          relative

          flex
          w-5
          shrink-0
          justify-center
        "
            >
        <span
            className={`
            mt-6
            h-2
            w-2
            rounded-full

            ${getDomainDotClass(
                item.domain,
            )}
          `}
        />

                {!last && (
                    <span
                        className="
              absolute
              bottom-0
              top-8

              w-px
              bg-slate-100
            "
                    />
                )}
            </div>


            <div
                className="
          flex-1
          py-5
        "
            >
                <div
                    className="
            flex
            items-center
            justify-between
            gap-4
          "
                >
                    <p
                        className="
              text-sm
              font-bold
              text-slate-800
            "
                    >
                        {item.title}
                    </p>

                    <span
                        className="
              shrink-0
              text-[10px]
              text-slate-400
            "
                    >
            {item.timeLabel}
          </span>
                </div>


                <p
                    className="
            mt-1
            text-xs
            leading-5
            text-slate-500
          "
                >
                    {item.description}
                </p>
            </div>
        </div>
    );
}


/* =====================================================
 * Context
 * ===================================================== */

function ContextRow({
                        label,
                        value,
                        muted = false,
                    }: {
    label: string;
    value: string;
    muted?: boolean;
}) {
    return (
        <div
            className="
        flex
        items-center
        justify-between
        gap-4

        py-3
      "
        >
      <span
          className="
          text-xs
          text-slate-400
        "
      >
        {label}
      </span>

            <span
                className={`
          text-sm
          font-bold

          ${
                    muted
                        ? "text-slate-300"
                        : "text-slate-700"
                }
        `}
            >
        {value}
      </span>
        </div>
    );
}


/* =====================================================
 * Workspace
 * ===================================================== */

function WorkspaceRow({
                          label,
                          description,
                          onClick,
                      }: {
    label: string;
    description: string;
    onClick: () => void;
}) {
    return (
        <button
            type="button"
            onClick={onClick}
            className="
        group

        flex
        items-center
        justify-between
        gap-4

        rounded-xl

        border
        border-slate-200

        bg-white

        px-4
        py-4

        text-left

        transition

        hover:border-sky-200
        hover:bg-sky-50/30
      "
        >
            <div>
                <p
                    className="
            text-sm
            font-black
            text-slate-900
          "
                >
                    {label}
                </p>

                <p
                    className="
            mt-1
            text-xs
            text-slate-400
          "
                >
                    {description}
                </p>
            </div>

            <span
                className="
          text-slate-300

          transition

          group-hover:translate-x-0.5
          group-hover:text-sky-500
        "
            >
        →
      </span>
        </button>
    );
}


/* =====================================================
 * Persistent Command Bar
 * ===================================================== */

/**
 * Manager Chat이 구현되기 전 사용할
 * 실제 동작 가능한 Quick Command Bar.
 *
 * 현재는 단순한 Navigation Intent만 처리한다.
 *
 * 가짜 AI 답변을 만들지 않는다.
 */
function ManagerCommandBar({
                               onNavigate,
                           }: {
    onNavigate: (
        to: string,
    ) => void;
}) {
    const [
        query,
        setQuery,
    ] =
        useState("");

    const [
        hint,
        setHint,
    ] =
        useState<string | null>(
            null,
        );


    const handleSubmit = (
        event:
        FormEvent<HTMLFormElement>,
    ) => {
        event.preventDefault();


        const command =
            query
                .trim()
                .toLowerCase();


        if (!command) {
            return;
        }


        if (
            command.includes(
                "퀘스트",
            )
        ) {
            onNavigate(
                "/quests",
            );

            return;
        }


        if (
            command.includes(
                "추천",
            )
        ) {
            onNavigate(
                "/ai-recommend",
            );

            return;
        }


        if (
            command.includes(
                "여행",
            )
        ) {
            onNavigate(
                "/travel",
            );

            return;
        }


        if (
            command.includes(
                "마이",
            ) ||
            command.includes(
                "계정",
            )
        ) {
            onNavigate(
                "/mypage",
            );

            return;
        }


        setHint(
            "현재는 여행 · AI 추천 · 퀘스트 · 마이페이지 이동 명령을 지원합니다.",
        );
    };


    return (
        <div
            className="
        sticky
        bottom-4
        z-30

        mx-auto
        max-w-3xl

        pb-2
      "
        >
            <form
                onSubmit={
                    handleSubmit
                }
                className="
          rounded-2xl

          border
          border-slate-200/90

          bg-white/95

          p-2

          shadow-2xl
          shadow-slate-300/40

          backdrop-blur-xl
        "
            >
                <div
                    className="
            flex
            items-center
            gap-2
          "
                >
                    <div
                        className="
              flex
              h-10
              w-10
              shrink-0
              items-center
              justify-center

              rounded-xl

              bg-slate-950

              text-sm
              text-sky-300
            "
                    >
                        ✦
                    </div>


                    <input
                        value={query}
                        onChange={(
                            event,
                        ) => {
                            setQuery(
                                event.target.value,
                            );

                            setHint(null);
                        }}
                        placeholder="TP Manager에게 물어보세요"
                        className="
              min-w-0
              flex-1

              bg-transparent

              px-2
              py-3

              text-sm
              text-slate-800

              outline-none

              placeholder:text-slate-400
            "
                    />


                    <button
                        type="submit"
                        className="
              flex
              h-10
              w-10
              shrink-0
              items-center
              justify-center

              rounded-xl

              bg-sky-500

              text-white

              transition

              hover:bg-sky-600
            "
                        aria-label="명령 실행"
                    >
                        ↑
                    </button>
                </div>


                {hint && (
                    <p
                        className="
              px-12
              pb-1
              pt-2

              text-[10px]
              text-slate-400
            "
                    >
                        {hint}
                    </p>
                )}
            </form>
        </div>
    );
}


/* =====================================================
 * AI Core
 * ===================================================== */

function ManagerCore({
                         loading,
                     }: {
    loading: boolean;
}) {
    return (
        <div
            className="
        relative

        flex
        h-44
        w-44

        items-center
        justify-center
      "
        >
            <div
                className="
          absolute
          inset-0

          rounded-full

          border
          border-white/10
        "
            />


            <div
                className="
          absolute
          inset-5

          rounded-full

          border
          border-sky-300/20
        "
            />


            <div
                className="
          absolute
          inset-10

          rounded-full

          border
          border-indigo-300/20
        "
            />


            <div
                className={`
          relative

          flex
          h-20
          w-20

          items-center
          justify-center

          rounded-full

          bg-gradient-to-br
          from-sky-300
          via-sky-500
          to-indigo-600

          shadow-[0_0_60px_rgba(56,189,248,0.55)]

          ${
                    loading
                        ? "animate-pulse"
                        : ""
                }
        `}
            >
        <span
            className="
            h-5
            w-5

            rounded-full

            bg-white

            shadow-[0_0_25px_rgba(255,255,255,1)]
          "
        />
            </div>
        </div>
    );
}


/* =====================================================
 * Domain UI
 * ===================================================== */

function DomainIndicator({
                             domain,
                         }: {
    domain: ManagerDomain;
}) {
    const data =
        {
            travel: {
                label:
                    "TRAVEL",

                className:
                    "bg-sky-50 text-sky-600",
            },

            quest: {
                label:
                    "QUEST",

                className:
                    "bg-emerald-50 text-emerald-600",
            },

            emotion: {
                label:
                    "EMOTION",

                className:
                    "bg-indigo-50 text-indigo-600",
            },

            system: {
                label:
                    "MANAGER",

                className:
                    "bg-slate-100 text-slate-500",
            },
        }[domain];


    return (
        <span
            className={`
        flex
        h-9
        min-w-16
        shrink-0
        items-center
        justify-center

        rounded-lg

        px-2

        text-[9px]
        font-black
        tracking-wide

        ${data.className}
      `}
        >
      {data.label}
    </span>
    );
}


function getDomainDotClass(
    domain: ManagerDomain,
): string {
    switch (domain) {
        case "travel":
            return "bg-sky-500";

        case "quest":
            return "bg-emerald-500";

        case "emotion":
            return "bg-indigo-500";

        default:
            return "bg-slate-400";
    }
}


/* =====================================================
 * Attention Builder
 * ===================================================== */

function buildAttentionItems(
    travel: TravelPlan | null,
    activeQuestCount: number,
): ManagerAttentionItem[] {
    const items:
        ManagerAttentionItem[] =
        [];


    if (travel) {
        const days =
            daysUntilTravel(
                travel,
            );


        if (
            days !== null &&
            days >= 0 &&
            days <= 7
        ) {
            items.push({
                id:
                    `travel-${travel.planId}`,

                domain:
                    "travel",

                title:
                    days === 0
                        ? `${travel.planName}이 오늘 시작돼요`
                        : `${travel.planName}까지 ${days}일 남았어요`,

                description:
                    "출발 전에 등록된 일정과 여행 정보를 다시 확인해보세요.",

                actionLabel:
                    "여행 확인",

                to:
                    "/travel",

                priority:
                    days <= 1
                        ? "high"
                        : "normal",
            });
        }
    }


    if (
        activeQuestCount > 0
    ) {
        items.push({
            id:
                "active-quests",

            domain:
                "quest",

            title:
                `진행 중인 퀘스트가 ${activeQuestCount}개 있어요`,

            description:
                "아직 완료되지 않은 미션을 이어서 진행할 수 있습니다.",

            actionLabel:
                "퀘스트 보기",

            to:
                "/quests",

            priority:
                "normal",
        });
    }


    if (
        !travel &&
        activeQuestCount ===
        0
    ) {
        items.push({
            id:
                "start-travel",

            domain:
                "travel",

            title:
                "아직 예정된 여행이 없어요",

            description:
                "첫 여행 계획을 등록하면 AI Manager가 여행 Context를 연결하기 시작합니다.",

            actionLabel:
                "여행 만들기",

            to:
                "/travel",

            priority:
                "normal",
        });
    }


    return items;
}


/* =====================================================
 * Activity Builder
 * ===================================================== */

function buildActivityItems(
    emotion:
        LatestEmotionResponse | null,

    questLogs:
    UserQuestLogResponse[],
): ManagerActivityItem[] {
    const items:
        ManagerActivityItem[] =
        [];


    if (emotion) {
        items.push({
            id:
                `emotion-${emotion.observationId}`,

            domain:
                "emotion",

            title:
                "최근 감정이 기록됐어요",

            description:
                `'${emotion.fineLabelName}' 상태로 관찰되었습니다.`,

            timeLabel:
                formatRelativeTime(
                    emotion.observedAt,
                ),
        });
    }


    const completed =
        questLogs
            .filter(
                (log) =>
                    log.status ===
                    "SUCCESS" &&
                    log.clearDate,
            )
            .sort(
                (a, b) =>
                    new Date(
                        b.clearDate!,
                    ).getTime() -
                    new Date(
                        a.clearDate!,
                    ).getTime(),
            )
            .slice(
                0,
                3,
            );


    completed.forEach(
        (log) => {
            items.push({
                id:
                    `quest-${log.logId}`,

                domain:
                    "quest",

                title:
                    "퀘스트를 완료했어요",

                description:
                    `${log.questName} · +${log.rewardPoint.toLocaleString()} P`,

                timeLabel:
                    formatRelativeTime(
                        log.clearDate!,
                    ),
            });
        },
    );


    if (
        items.length === 0
    ) {
        items.push({
            id:
                "no-activity",

            domain:
                "system",

            title:
                "아직 최근 활동이 많지 않아요",

            description:
                "여행 계획과 퀘스트, 감정 기록이 쌓이면 AI Manager가 변화 흐름을 정리합니다.",

            timeLabel:
                "현재",
        });
    }


    return items.slice(
        0,
        5,
    );
}


/* =====================================================
 * Travel
 * ===================================================== */

function selectFocusTravel(
    plans: TravelPlan[],
): TravelPlan | null {
    if (
        plans.length === 0
    ) {
        return null;
    }


    const now =
        Date.now();


    const active =
        plans.find(
            (plan) => {
                if (
                    !plan.startDate ||
                    !plan.endDate
                ) {
                    return false;
                }


                const start =
                    new Date(
                        plan.startDate,
                    ).getTime();

                const end =
                    new Date(
                        plan.endDate,
                    ).getTime();


                return (
                    start <= now &&
                    end >= now
                );
            },
        );


    if (active) {
        return active;
    }


    const upcoming =
        plans
            .filter(
                (plan) =>
                    plan.startDate &&
                    new Date(
                        plan.startDate,
                    ).getTime() >
                    now,
            )
            .sort(
                (a, b) =>
                    new Date(
                        a.startDate!,
                    ).getTime() -
                    new Date(
                        b.startDate!,
                    ).getTime(),
            );


    return (
        upcoming[0] ??
        null
    );
}


function daysUntilTravel(
    plan: TravelPlan,
): number | null {
    if (!plan.startDate) {
        return null;
    }


    const start =
        new Date(
            plan.startDate,
        ).getTime();


    return Math.ceil(
        (start - Date.now()) /
        (
            1000 *
            60 *
            60 *
            24
        ),
    );
}


/* =====================================================
 * Time
 * ===================================================== */

function getTimeGreeting():
    string {
    const hour =
        new Date().getHours();


    if (hour < 5) {
        return "늦은 밤이에요";
    }


    if (hour < 12) {
        return "좋은 아침이에요";
    }


    if (hour < 18) {
        return "좋은 오후예요";
    }


    return "좋은 저녁이에요";
}


function formatRelativeTime(
    value: string,
): string {
    const date =
        new Date(value);

    const diffMs =
        Date.now() -
        date.getTime();


    const minutes =
        Math.floor(
            diffMs /
            (
                1000 *
                60
            ),
        );


    if (minutes < 1) {
        return "방금";
    }


    if (minutes < 60) {
        return `${minutes}분 전`;
    }


    const hours =
        Math.floor(
            minutes / 60,
        );


    if (hours < 24) {
        return `${hours}시간 전`;
    }


    const days =
        Math.floor(
            hours / 24,
        );


    return `${days}일 전`;
}