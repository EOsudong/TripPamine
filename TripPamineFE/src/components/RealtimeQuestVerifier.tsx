import {
  useEffect,
  useState,
} from "react";

import {
  extractQuestErrorMessage,
  questApi,
  type QuestResponse,
  type QuestStatus,
  type UserQuestLogResponse,
} from "../api/quest";


type GpsSignalStrength =
    | "EXCELLENT"
    | "GOOD"
    | "POOR"
    | "SEARCHING";


interface RealtimeQuestVerifierProps {
  quest: QuestResponse;

  initialStatus:
      QuestStatus;

  onLogUpdate:
      (
          log:
          UserQuestLogResponse,
      ) => void;

  /*
   * QuestDetailPage가 수집한 동일 GPS Context.
   */
  latitude:
      number | null;

  longitude:
      number | null;

  accuracy:
      number | null;

  gpsSignalStrength:
      GpsSignalStrength;

  gpsError:
      string | null;
}


export function RealtimeQuestVerifier({
                                        quest,
                                        initialStatus,
                                        onLogUpdate,

                                        latitude,
                                        longitude,
                                        accuracy,
                                        gpsSignalStrength,
                                        gpsError,
                                      }: RealtimeQuestVerifierProps) {
  const [
    isVerifying,
    setIsVerifying,
  ] = useState(false);

  const [
    status,
    setStatus,
  ] =
      useState<QuestStatus>(
          initialStatus,
      );

  const [
    customError,
    setCustomError,
  ] = useState<string | null>(
      null,
  );


  /*
   * 부모의 questLog 상태가 변경될 경우
   * 내부 status도 동기화한다.
   */
  useEffect(() => {
    setStatus(
        initialStatus,
    );
  }, [initialStatus]);


  const questCleared =
      status === "SUCCESS";


  const handleVerifyLocation =
      async () => {
        if (
            latitude === null ||
            longitude === null
        ) {
          setCustomError(
              "현재 위치를 확인하고 있습니다. GPS 좌표가 수신된 뒤 다시 시도해주세요.",
          );

          return;
        }


        /*
         * GPS 정확도가 낮은 상황에서는
         * 불필요한 Backend 요청을 먼저 막는다.
         *
         * Backend에서도 동일하게 검증하므로
         * 이것은 보안 로직이 아닌 UX 선제 검증이다.
         */
        if (
            gpsSignalStrength ===
            "POOR"
        ) {
          setCustomError(
              "GPS 정확도가 낮아 위치 인증이 어렵습니다. 가능하면 실외로 이동한 뒤 다시 시도해주세요.",
          );

          return;
        }


        try {
          setIsVerifying(
              true,
          );

          setCustomError(
              null,
          );


          const log =
              await questApi.clearQuest(
                  quest.questId,
                  {
                    currentLat:
                    latitude,

                    currentLng:
                    longitude,

                    accuracyMeters:
                        accuracy ??
                        undefined,
                  },
              );


          setStatus(
              log.status,
          );

          onLogUpdate(
              log,
          );


          if (
              log.status ===
              "FAILED"
          ) {
            setCustomError(
                `아직 인증 반경 ${quest.clearRadius}m 밖에 있습니다. 목표 위치에 조금 더 가까이 이동해주세요.`,
            );
          }
        } catch (error) {
          console.error(
              "퀘스트 위치 인증 실패:",
              error,
          );

          setCustomError(
              extractQuestErrorMessage(
                  error,
                  "현재 위치를 인증하는 중 문제가 발생했습니다.",
              ),
          );
        } finally {
          setIsVerifying(
              false,
          );
        }
      };


  const signal =
      getSignalPresentation(
          gpsSignalStrength,
          accuracy,
      );


  return (
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
          border-b
          border-white/5
          p-5
        "
        >
          <p
              className="
            text-[10px]
            font-black
            tracking-[0.18em]
            text-cyan-400
          "
          >
            LOCATION VERIFY
          </p>

          <h3
              className="
            mt-2
            text-lg
            font-black
            text-white
          "
          >
            위치 인증
          </h3>

          <p
              className="
            mt-1
            text-xs
            leading-5
            text-slate-400
          "
          >
            현재 GPS 위치를 서버에서 다시 검증해
            퀘스트 완료 여부를 판정합니다.
          </p>
        </div>


        {/* GPS signal */}

        <div
            className="
          p-5
        "
        >
          <div
              className="
            rounded-xl
            border
            border-white/5
            bg-slate-950/60
            p-4
          "
          >
            <div
                className="
              flex
              items-center
              justify-between
              gap-3
            "
            >
              <div>
                <p
                    className="
                  text-[10px]
                  font-bold
                  text-slate-500
                "
                >
                  GPS SIGNAL
                </p>

                <p
                    className="
                  mt-1
                  text-sm
                  font-bold
                  text-white
                "
                >
                  {signal.label}
                </p>
              </div>

              <span
                  className={`
                h-3
                w-3
                rounded-full
                ${signal.dotClass}
              `}
              />
            </div>

            <div
                className="
              mt-4
              grid
              grid-cols-2
              gap-2
            "
            >
              <SmallMetric
                  label="측위 오차"
                  value={
                    accuracy === null
                        ? "--"
                        : `±${Math.round(
                            accuracy,
                        )}m`
                  }
              />

              <SmallMetric
                  label="인증 범위"
                  value={`${quest.clearRadius}m`}
              />
            </div>
          </div>


          {(gpsError ||
              customError) && (
              <div
                  className="
              mt-4
              rounded-xl
              border
              border-rose-500/20
              bg-rose-500/10
              p-3
              text-xs
              leading-5
              text-rose-300
            "
              >
                {gpsError ??
                    customError}
              </div>
          )}


          <div className="mt-5">
            {questCleared ? (
                <div
                    className="
                rounded-xl
                border
                border-emerald-400/20
                bg-emerald-400/10
                p-5
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
                  bg-emerald-400
                  text-slate-950
                "
                  >
                    ✓
                  </div>

                  <p
                      className="
                  mt-3
                  text-sm
                  font-black
                  text-emerald-300
                "
                  >
                    퀘스트 완료
                  </p>

                  <p
                      className="
                  mt-1
                  text-xs
                  text-slate-400
                "
                  >
                    보상 포인트가 반영되었습니다.
                  </p>
                </div>
            ) : (
                <button
                    type="button"

                    onClick={
                      handleVerifyLocation
                    }

                    disabled={
                        isVerifying ||
                        latitude === null ||
                        longitude === null
                    }

                    className="
                flex
                w-full
                items-center
                justify-center
                gap-2

                rounded-xl
                bg-cyan-500
                px-4
                py-3.5

                text-sm
                font-black
                text-slate-950

                transition

                hover:bg-cyan-400

                disabled:cursor-not-allowed
                disabled:bg-slate-800
                disabled:text-slate-500
              "
                >
                  {isVerifying ? (
                      <>
                  <span
                      className="
                      h-4
                      w-4
                      animate-spin
                      rounded-full
                      border-2
                      border-current
                      border-t-transparent
                    "
                  />

                        위치 확인 중...
                      </>
                  ) : latitude ===
                  null ? (
                      "GPS 위치 수신 대기 중"
                  ) : status ===
                  "FAILED" ? (
                      "다시 위치 인증하기"
                  ) : (
                      "현재 위치 인증하기"
                  )}
                </button>
            )}
          </div>


          <p
              className="
            mt-4
            text-center
            text-[10px]
            leading-4
            text-slate-500
          "
          >
            실제 성공 여부는 서버에서 현재 좌표,
            GPS 정확도와 퀘스트 인증 범위를 기준으로
            최종 판정합니다.
          </p>
        </div>
      </section>
  );
}


function getSignalPresentation(
    signal:
    GpsSignalStrength,

    accuracy:
        number | null,
) {
  switch (signal) {
    case "EXCELLENT":
      return {
        label:
            accuracy === null
                ? "매우 좋음"
                : `매우 좋음 · ±${Math.round(
                    accuracy,
                )}m`,

        dotClass:
            "bg-emerald-400",
      };


    case "GOOD":
      return {
        label:
            accuracy === null
                ? "양호"
                : `양호 · ±${Math.round(
                    accuracy,
                )}m`,

        dotClass:
            "bg-cyan-400",
      };


    case "POOR":
      return {
        label:
            accuracy === null
                ? "신호 불안정"
                : `정확도 낮음 · ±${Math.round(
                    accuracy,
                )}m`,

        dotClass:
            "bg-rose-400",
      };


    case "SEARCHING":
    default:
      return {
        label:
            "위치 탐색 중",

        dotClass:
            "bg-slate-500 animate-pulse",
      };
  }
}


function SmallMetric({
                       label,
                       value,
                     }: {
  label: string;
  value: string;
}) {
  return (
      <div
          className="
        rounded-lg
        bg-white/[0.03]
        px-3
        py-2.5
      "
      >
        <p
            className="
          text-[10px]
          text-slate-500
        "
        >
          {label}
        </p>

        <p
            className="
          mt-1
          text-xs
          font-bold
          text-slate-200
        "
        >
          {value}
        </p>
      </div>
  );
}