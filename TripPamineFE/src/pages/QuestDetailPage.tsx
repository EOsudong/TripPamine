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


  const [
    totalPoints,
    setTotalPoints,
  ] =
      useState(0);


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
             * 포인트 조회 실패는
             * Quest 전체 화면을 막을 정도의 오류가 아니다.
             */
            try {
              const myInfo =
                  await getMyInfoApi();

              if (!cancelled) {
                setTotalPoints(
                    myInfo.totalPoints,
                );
              }
            } catch (profileError) {
              console.error(
                  "퀘스트 포인트 조회 실패:",
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
        if (!quest) {
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
            const myInfo =
                await getMyInfoApi();


            setTotalPoints(
                myInfo.totalPoints,
            );
          } catch (pointError) {
            console.error(
                "퀘스트 완료 후 포인트 갱신 실패:",
                pointError,
            );
          }
        }
      };


  return (
      <AppLayout
          pageClassName="bg-slate-950 text-white"
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
                        LOCATION QUEST
                      </span>

                          <QuestStatusBadge
                              status={
                                  questLog?.status ??
                                  "NOT_STARTED"
                              }
                          />
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
                          목표 위치의{" "}
                          <strong
                              className="
                          text-slate-200
                        "
                          >
                            {quest.clearRadius}m
                          </strong>{" "}
                          인증 반경 안에서
                          현재 GPS 위치를
                          확인하면 퀘스트를
                          완료할 수 있습니다.
                        </p>
                      </div>


                      <div
                          className="
                      flex
                      gap-3
                    "
                      >
                        <HeaderMetric
                            label="보상"
                            value={`+${quest.rewardPoint.toLocaleString()} P`}
                            accent
                        />

                        <HeaderMetric
                            label="보유 포인트"
                            value={`${totalPoints.toLocaleString()} P`}
                        />
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
                            quest={quest}

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