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
    getAiManagerHomeApi,
    type AiManagerActivityEvent,
    type AiManagerFocusTravel,
    type AiManagerHomeResponse,
} from "../../api/aiManagerHome";

import {
    useAuth,
} from "../../context/AuthContext";

import aiManagerBg
    from "../../assets/images/ai-travel-bg.png";


/**
 * =========================================================
 * AI Manager Frontend Domain
 * =========================================================
 *
 * Backend Activity의 Domain과 화면용 Domain을 동일하게 맞춘다.
 *
 * finance / diary는 아직 Home 기능이 완전히 연결되지 않았지만
 * Event Timeline 확장을 위해 미리 표시 가능하게 둔다.
 */
type ManagerDomain =
    | "travel"
    | "quest"
    | "emotion"
    | "finance"
    | "diary"
    | "system";


/**
 * "지금 확인할 것" 영역의 한 항목.
 *
 * Activity와 다른 개념이다.
 *
 * Activity:
 * 이미 발생한 사건
 *
 * Attention:
 * 지금 사용자가 확인하거나 행동할 필요가 있는 항목
 */
interface ManagerAttentionItem {
    id: string;

    domain:
        ManagerDomain;

    title: string;

    description: string;

    actionLabel: string;

    to: string;

    priority:
        | "high"
        | "normal";
}


/**
 * =========================================================
 * AI Manager v3 Home
 * =========================================================
 *
 * 주요 구조:
 *
 * Compact Header
 *      ↓
 * Needs Attention
 *      ↓
 * AI Activity
 *      ↓
 * Current Context
 *      ↓
 * Workspaces
 *      ↓
 * Manager Command
 *
 *
 * 이번 STEP C-2의 가장 중요한 변화:
 *
 * 이전:
 *
 * latestEmotion
 * +
 * recentCompletedQuests
 *      ↓
 * Frontend buildActivityItems()
 *
 *
 * 현재:
 *
 * AI_MANAGER_EVENTS
 *      ↓
 * Backend AiManagerActivityService
 *      ↓
 * homeContext.activities
 *      ↓
 * Frontend Render
 *
 *
 * 따라서 Frontend에서는 Activity의 의미를 다시 계산하지 않는다.
 */
export default function AiManagerDashboard() {

    const navigate =
        useNavigate();


    const {
        isLoggedIn,
    } =
        useAuth();


    /* =====================================================
     * State
     * ===================================================== */

    /**
     * AI Manager Backend가 조립한
     * Home Context 전체.
     */
    const [
        homeContext,
        setHomeContext,
    ] =
        useState<AiManagerHomeResponse | null>(
            null,
        );


    const [
        loading,
        setLoading,
    ] =
        useState(false);


    const [
        loadError,
        setLoadError,
    ] =
        useState<string | null>(
            null,
        );


    /* =====================================================
     * Context Loading
     * ===================================================== */

    useEffect(() => {

        /*
         * 로그아웃하면 이전 사용자의 Context를 제거한다.
         */
        if (!isLoggedIn) {

            setHomeContext(
                null,
            );

            setLoadError(
                null,
            );

            setLoading(
                false,
            );

            return;
        }


        /*
         * 컴포넌트 unmount 이후
         * 비동기 응답이 돌아왔을 때
         * setState를 실행하지 않기 위한 방어.
         */
        let cancelled =
            false;


        const loadContext =
            async () => {

                try {

                    setLoading(
                        true,
                    );

                    setLoadError(
                        null,
                    );


                    /*
                     * AI Manager Home Context는
                     * Backend에서 한 번에 조립한다.
                     */
                    const context =
                        await getAiManagerHomeApi();


                    if (cancelled) {
                        return;
                    }


                    setHomeContext(
                        context,
                    );

                } catch (error) {

                    console.error(
                        "AI Manager Home 조회 실패:",
                        error,
                    );


                    if (!cancelled) {

                        setHomeContext(
                            null,
                        );

                        setLoadError(
                            "필요한 정보를 불러오지 못했어요.",
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


        void loadContext();


        return () => {

            cancelled =
                true;
        };

    }, [isLoggedIn]);


    /* =====================================================
     * Context Alias
     * =====================================================
     *
     * 아래 값은 Frontend에서 새로 계산하는 것이 아니다.
     *
     * Backend 응답을 JSX에서 읽기 편하도록
     * 변수 이름만 붙여둔 것이다.
     */

    const profile =
        homeContext?.user ??
        null;


    const emotion =
        homeContext?.emotion ??
        null;


    const focusTravel =
        homeContext?.travel.focusPlan ??
        null;


    const totalPlanCount =
        homeContext?.travel.totalPlanCount ??
        0;


    const activeQuestCount =
        homeContext?.quests.activeCount ??
        0;


    const completedQuestCount =
        homeContext?.quests.completedCount ??
        0;


    const failedQuestCount =
        homeContext?.quests.failedCount ??
        0;


    /**
     * =====================================================
     * 공식 Activity Feed
     * =====================================================
     *
     * 더 이상:
     *
     * recentCompletedQuests
     * buildActivityItems()
     *
     * 를 사용하지 않는다.
     *
     * Backend AI_MANAGER_EVENTS 결과 자체가
     * Activity의 Single Source of Truth다.
     */
    const activities =
        homeContext?.activities ??
        [];


    /* =====================================================
     * Needs Attention
     * ===================================================== */

    /**
     * Attention은 아직 Frontend 규칙 기반이다.
     *
     * 이 부분은 향후 Priority Engine 단계에서
     * Backend로 이동한다.
     */
    const attentionItems =
        useMemo(
            () => {

                if (!homeContext) {
                    return [];
                }


                return buildAttentionItems(
                    focusTravel,
                    activeQuestCount,
                );

            },
            [
                homeContext,
                focusTravel,
                activeQuestCount,
            ],
        );


    /* =====================================================
     * Guest
     * ===================================================== */

    if (!isLoggedIn) {

        return (
            <GuestManagerView

                onLogin={() =>
                    navigate(
                        "/login",
                    )
                }

                onExploreTravel={() =>
                    navigate(
                        "/travel",
                    )
                }

            />
        );
    }


    /* =====================================================
     * Logged-in Home
     * ===================================================== */

    return (
        <div
            className="
        min-h-screen
        pb-8
      "
        >

            {/* =================================================
          COMPACT AI HEADER
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

            from-night-950/95
            via-night-950/80
            to-sky-950/40
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

                {/* AI 오로라 + 점 그리드 레이어 */}
                <div className="absolute inset-0 tp-aurora opacity-60 mix-blend-screen" />
                <div className="absolute inset-0 tp-dot-grid opacity-40 [mask-image:linear-gradient(90deg,black,transparent_70%)]" />
                <span className="absolute inset-x-0 bottom-0 h-px tp-gradient-bg opacity-70" />


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
                            : loadError
                                ? "bg-rose-400"
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
                  트립파민 도우미
                </span>

                            </div>


                            <h1
                                className="
                  mt-4

                  font-display
                  text-3xl
                  font-bold
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
                    tp-gradient-text
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

                                {loading
                                    ? "지금 필요한 정보를 살펴보고 있어요."
                                    : loadError
                                        ? "지금 필요한 정보를 불러오지 못했어요."
                                        : attentionItems.length > 0
                                            ? `오늘 확인할 내용이 ${attentionItems.length}개 있어요. 중요한 것부터 정리해드릴게요.`
                                            : "지금 확인해야 할 중요한 내용은 없어요. 최근 활동을 아래에서 살펴보세요."}

                            </p>

                        </div>


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
                                error={Boolean(loadError)}
                            />

                        </div>

                    </div>

                </div>

            </section>


            {/* =================================================
          MAIN
         ================================================= */}

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
            NEEDS ATTENTION
           ================================================= */}

                <section
                    className="
            -mt-8
            overflow-hidden
            rounded-3xl
            border
            border-white
            bg-white/90
            backdrop-blur-xl
            shadow-[0_30px_70px_-40px_rgba(60,40,180,0.5)]
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
                                오늘 확인할 내용
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


                    {loadError ? (

                        <EmptyPanel
                            symbol="!"
                            title="정보를 불러오지 못했어요."
                            description="잠시 후 페이지를 다시 불러와 주세요."
                            error
                        />

                    ) : loading && !homeContext ? (

                        <div
                            className="
                px-6
                py-10

                text-center

                text-sm
                text-slate-400
              "
                        >
                            나에게 필요한 정보를 살펴보고 있어요.
                        </div>

                    ) : attentionItems.length === 0 ? (

                        <EmptyPanel
                            symbol="✓"
                            title="지금은 확인할 항목이 없어요."
                            description="필요한 일이 생기면 AI Manager가 여기에 먼저 정리합니다."
                        />

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
                                            attentionItems.length - 1
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
            ACTIVITY + CONTEXT
           ================================================= */}

                <section
                    className="
            grid
            gap-6

            py-8

            lg:grid-cols-[minmax(0,1fr)_340px]
          "
                >

                    {/* =========================
              AI Activity
             ========================= */}

                    <article
                        className="
              rounded-3xl

              border
              border-white

              bg-white/85 backdrop-blur-xl shadow-[0_20px_50px_-35px_rgba(60,40,180,0.45)]
            "
                    >

                        <div
                            className="
                flex

                items-center
                justify-between

                gap-3

                border-b
                border-slate-100

                px-5
                py-5

                md:px-6
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
                                    나의 활동 기록
                                </p>


                                <h2
                                    className="
                    mt-1

                    text-lg
                    font-black
                    text-slate-950
                  "
                                >
                                    최근 업데이트
                                </h2>

                            </div>


                            {activities.length > 0 && (

                                <span
                                    className="
                    rounded-full

                    bg-slate-100

                    px-2.5
                    py-1

                    text-[10px]
                    font-bold
                    text-slate-500
                  "
                                >
                  {activities.length}건
                </span>

                            )}

                        </div>


                        {activities.length === 0 ? (

                            /*
                             * 여기서는 가짜 Activity Event를 만들지 않는다.
                             *
                             * 실제 AI_MANAGER_EVENTS가 없다면
                             * 단순 Empty State만 보여준다.
                             */
                            <div
                                className="
                  px-6
                  py-10

                  text-center
                "
                            >

                                <p
                                    className="
                    text-sm
                    font-bold
                    text-slate-700
                  "
                                >
                                    아직 기록된 활동이 없어요.
                                </p>


                                <p
                                    className="
                    mt-1

                    text-xs
                    leading-5
                    text-slate-400
                  "
                                >
                                    마음을 살펴보거나 퀘스트를 완료하면
                                    이곳에서 확인할 수 있어요.
                                </p>

                            </div>

                        ) : (

                            <div
                                className="
                  px-5
                  py-2

                  md:px-6
                "
                            >

                                {activities.map(
                                    (
                                        activity,
                                        index,
                                    ) => (

                                        <ActivityRow

                                            key={
                                                activity.eventId
                                            }

                                            activity={
                                                activity
                                            }

                                            last={
                                                index ===
                                                activities.length - 1
                                            }

                                        />

                                    ),
                                )}

                            </div>

                        )}

                    </article>


                    {/* =========================
              Current Context
             ========================= */}

                    <aside
                        className="
              rounded-3xl

              border
              border-white

              bg-white/85 backdrop-blur-xl shadow-[0_20px_50px_-35px_rgba(60,40,180,0.45)]

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
                            나의 현황
                        </p>


                        <h2
                            className="
                mt-1

                text-lg
                font-black
                text-slate-950
              "
                        >
                            관리 정보
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
                                label="여행 계획"
                                value={`${totalPlanCount}개`}
                            />


                            <ContextRow
                                label="대표 여행"
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


                            {failedQuestCount > 0 && (

                                <ContextRow
                                    label="재도전 퀘스트"
                                    value={`${failedQuestCount}개`}
                                />

                            )}


                            <ContextRow
                                label="최근 활동"
                                value={`${activities.length}개`}
                            />


                            <ContextRow
                                label="보유 포인트"
                                value={
                                    profile
                                        ? `${profile.totalPoints.toLocaleString()} P`
                                        : "-"
                                }
                            />


                            <ContextRow
                                label="자산"
                                value="자산관리에서 확인"
                                muted
                            />

                        </div>


                        {loadError && (

                            <div
                                className="
                  mt-5

                  rounded-xl

                  border
                  border-rose-200

                  bg-rose-50

                  px-3
                  py-2.5

                  text-xs
                  leading-5
                  text-rose-700
                "
                            >
                                {loadError}
                            </div>

                        )}

                    </aside>

                </section>


                {/* =================================================
            WORKSPACES
           ================================================= */}

                <section
                    className="
            pb-8
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
                        원하는 기능 바로가기
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
                                navigate(
                                    "/travel",
                                )
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
                            label="내 마음 살펴보기"
                            description="오늘의 기분을 알아보고 기록하기"
                            onClick={() =>
                                navigate(
                                    "/ai/emotion",
                                )
                            }
                        />


                        <WorkspaceRow
                            label="퀘스트"
                            description="위치 기반 미션과 보상"
                            onClick={() =>
                                navigate(
                                    "/quests",
                                )
                            }
                        />


                        {/* [자산관리 추가] 초개인화 플랫폼 — 여행 외 자산 · 커뮤니티 워크스페이스 */}
                        <WorkspaceRow
                            label="자산관리"
                            description="총자산 · 소비 패턴 · 예산 · 목표"
                            onClick={() =>
                                navigate(
                                    "/assets",
                                )
                            }
                        />


                        <WorkspaceRow
                            label="가계부 · 계좌"
                            description="수입·지출 기록과 영수증 인식"
                            onClick={() =>
                                navigate(
                                    "/mypage",
                                )
                            }
                        />


                        <WorkspaceRow
                            label="금융상품"
                            description="나에게 맞는 예금 · 적금 추천"
                            onClick={() =>
                                navigate(
                                    "/finance",
                                )
                            }
                        />

                    </div>

                </section>


                {/* =================================================
            MANAGER COMMAND
           ================================================= */}

                <ManagerCommandBar
                    onNavigate={
                        navigate
                    }
                />

            </div>

        </div>
    );
}


/* =========================================================
 * Guest
 * ========================================================= */

function GuestManagerView({
                              onLogin,
                              onExploreTravel,
                          }: {
    onLogin:
        () => void;

    onExploreTravel:
        () => void;
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

                    <p
                        className="
              text-[10px]
              font-black
              tracking-[0.18em]
              text-sky-200
            "
                    >
                        TRIPPAMINE
                    </p>


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
                        "계획은 AI가, 짜릿함은 당신이. 심장이 뛰는 대로 떠나세요."
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
                            트립파민 시작하기
                        </button>


                        <button
                            type="button"
                            onClick={onExploreTravel}
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


/* =========================================================
 * Attention
 * ========================================================= */

function AttentionRow({
                          item,
                          last,
                          onOpen,
                      }: {
    item:
        ManagerAttentionItem;

    last:
        boolean;

    onOpen:
        () => void;
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


/* =========================================================
 * Activity
 * ========================================================= */

/**
 * Backend에서 이미 완성된 Activity Event를 그대로 그린다.
 *
 * Frontend에서는:
 *
 * - Quest SUCCESS 판정
 * - Emotion Event 판정
 * - 제목 생성
 * - 설명 생성
 *
 * 을 하지 않는다.
 */
function ActivityRow({
                         activity,
                         last,
                     }: {
    activity:
        AiManagerActivityEvent;

    last:
        boolean;
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
                activity.domain,
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

                    <div
                        className="
              flex
              min-w-0

              items-center
              gap-2
            "
                    >

                        <DomainIndicator
                            domain={
                                activity.domain
                            }
                        />


                        <p
                            className="
                truncate

                text-sm
                font-bold
                text-slate-800
              "
                        >
                            {activity.title}
                        </p>

                    </div>


                    <span
                        className="
              shrink-0

              text-[10px]
              text-slate-400
            "
                    >
            {activity.occurredAt
                ? formatRelativeTime(
                    activity.occurredAt,
                )
                : "시간 정보 없음"}
          </span>

                </div>


                <p
                    className="
            mt-2

            text-xs
            leading-5
            text-slate-500
          "
                >
                    {activity.description}
                </p>

            </div>

        </div>
    );
}


/* =========================================================
 * Empty Panel
 * ========================================================= */

function EmptyPanel({
                        symbol,
                        title,
                        description,
                        error = false,
                    }: {
    symbol: string;

    title: string;

    description: string;

    error?: boolean;
}) {

    return (
        <div
            className="
        px-6
        py-10

        text-center
      "
        >

            <div
                className={`
          mx-auto

          flex
          h-10
          w-10

          items-center
          justify-center

          rounded-full

          ${
                    error
                        ? "bg-rose-50 text-rose-500"
                        : "bg-emerald-50 text-emerald-500"
                }
        `}
            >
                {symbol}
            </div>


            <p
                className="
          mt-3

          text-sm
          font-bold
          text-slate-700
        "
            >
                {title}
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
    );
}


/* =========================================================
 * Context
 * ========================================================= */

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


/* =========================================================
 * Workspace
 * ========================================================= */

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


/* =========================================================
 * Command
 * ========================================================= */

/**
 * 아직 실제 Manager Chat API를 연결하지 않았다.
 *
 * 현재는 단순 Navigation Intent만 지원한다.
 *
 * 가짜 AI 답변을 생성하지 않는 것이 중요하다.
 */
function ManagerCommandBar({
                               onNavigate,
                           }: {
    onNavigate:
        (to: string) => void;
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
            "여행 · 추천 · 퀘스트 · 마이페이지로 이동할 수 있어요.",
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
                onSubmit={handleSubmit}
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

                            setHint(
                                null,
                            );
                        }}

                        placeholder="찾고 싶은 기능을 입력해 주세요 (예: 여행, 퀘스트)"

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
                        aria-label="이동하기"

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


/* =========================================================
 * Manager Core
 * ========================================================= */

function ManagerCore({
                         loading,
                         error,
                     }: {
    loading: boolean;

    error: boolean;
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

          ${
                    error
                        ? "from-rose-300 via-rose-500 to-red-700"
                        : "from-sky-300 via-sky-500 to-indigo-600"
                }

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


/* =========================================================
 * Domain Presentation
 * ========================================================= */

function DomainIndicator({
                             domain,
                         }: {
    domain:
        ManagerDomain;
}) {

    const data =
        {

            travel: {
                label:
                    "여행",

                className:
                    "bg-sky-50 text-sky-600",
            },

            quest: {
                label:
                    "퀘스트",

                className:
                    "bg-emerald-50 text-emerald-600",
            },

            emotion: {
                label:
                    "마음",

                className:
                    "bg-indigo-50 text-indigo-600",
            },

            finance: {
                label:
                    "금융",

                className:
                    "bg-amber-50 text-amber-600",
            },

            diary: {
                label:
                    "일기",

                className:
                    "bg-violet-50 text-violet-600",
            },

            system: {
                label:
                    "소식",

                className:
                    "bg-slate-100 text-slate-500",
            },

        }[domain];


    return (
        <span
            className={`
        flex

        h-7
        min-w-14
        shrink-0

        items-center
        justify-center

        rounded-lg

        px-2

        text-[8px]
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
    domain:
    ManagerDomain,
): string {

    switch (domain) {

        case "travel":
            return "bg-sky-500";

        case "quest":
            return "bg-emerald-500";

        case "emotion":
            return "bg-indigo-500";

        case "finance":
            return "bg-amber-500";

        case "diary":
            return "bg-violet-500";

        default:
            return "bg-slate-400";
    }
}


/* =========================================================
 * Attention Builder
 * ========================================================= */

/**
 * 아직은 Frontend 기반 간단한 Priority Rule.
 *
 * 다음 Priority Engine 단계에서 Backend로 이동한다.
 */
function buildAttentionItems(
    travel:
        AiManagerFocusTravel | null,

    activeQuestCount:
    number,
):
    ManagerAttentionItem[] {

    const items:
        ManagerAttentionItem[] =
        [];


    if (travel) {

        if (
            travel.state ===
            "ACTIVE"
        ) {

            items.push({

                id:
                    `travel-${travel.planId}`,

                domain:
                    "travel",

                title:
                    `${travel.planName} 여행이 진행 중이에요`,

                description:
                    "현재 여행 일정과 필요한 정보를 확인해보세요.",

                actionLabel:
                    "여행 확인",

                to:
                    "/travel",

                priority:
                    "high",
            });
        }


        if (
            travel.state ===
            "UPCOMING" &&

            travel.daysUntilStart !==
            null &&

            travel.daysUntilStart <=
            7
        ) {

            const days =
                travel.daysUntilStart;


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
        activeQuestCount === 0
    ) {

        items.push({

            id:
                "start-travel",

            domain:
                "travel",

            title:
                "아직 예정된 여행이 없어요",

            description:
                "여행 계획을 하나 만들어 두면 앞으로의 일정을 한눈에 볼 수 있어요.",

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


/* =========================================================
 * Time Helpers
 * ========================================================= */

/**
 * 인사말은 Browser의 현재 시간을 사용한다.
 *
 * D-Day처럼 중요한 Business 시간 계산은
 * Backend가 담당한다.
 */
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


/**
 * Event 발생 시각을 상대 시간으로 표현한다.
 *
 * 예:
 *
 * 방금
 * 10분 전
 * 3시간 전
 * 2일 전
 */
function formatRelativeTime(
    value: string,
): string {

    const date =
        new Date(
            value,
        );


    const diffMs =
        Date.now() -
        date.getTime();


    const minutes =
        Math.max(
            0,
            Math.floor(
                diffMs /
                (
                    1000 *
                    60
                ),
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
