import {
  useEffect,
  useRef,
  useState,
} from "react";

import type {
  QuestResponse,
} from "../api/quest";


interface QuestMapViewProps {
  quest: QuestResponse;

  /**
   * QuestDetailPage에서 한 번만 수집한 GPS 위치.
   *
   * MapView 자체에서는 더 이상
   * navigator.geolocation.watchPosition()을 호출하지 않는다.
   */
  latitude: number | null;
  longitude: number | null;
  accuracy: number | null;

  /**
   * 퀘스트 완료 여부.
   *
   * false일 때는 완료된 Mission UI로 표시한다.
   */
  active?: boolean;
}


/**
 * 두 좌표 사이의 근사 거리 계산.
 *
 * 실제 퀘스트 성공 판정은 Backend가 담당하며,
 * 이 값은 사용자에게 남은 거리를 보여주기 위한 UI 전용이다.
 */
function calculateApproximateDistance(
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number,
): number {
  const earthRadius =
      6_371_000;

  const dLat =
      ((lat2 - lat1) * Math.PI) /
      180;

  const dLon =
      ((lon2 - lon1) * Math.PI) /
      180;

  const a =
      Math.sin(dLat / 2) *
      Math.sin(dLat / 2) +
      Math.cos(
          (lat1 * Math.PI) / 180,
      ) *
      Math.cos(
          (lat2 * Math.PI) / 180,
      ) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);

  const c =
      2 *
      Math.atan2(
          Math.sqrt(a),
          Math.sqrt(1 - a),
      );

  return earthRadius * c;
}


/**
 * Kakao CustomOverlay DOM 생성.
 *
 * HTML 문자열에 questName을 직접 삽입하지 않고
 * textContent를 사용해 안전하게 표시한다.
 */
function createTargetOverlayElement(
    questName: string,
    rewardPoint: number,
    clearRadius: number,
): HTMLDivElement {
  const element =
      document.createElement("div");

  element.textContent =
      `${questName} · ` +
      `${rewardPoint.toLocaleString()}P · ` +
      `${clearRadius}m`;

  element.style.cssText = `
    padding: 7px 11px;
    background: rgba(15, 23, 42, 0.94);
    border: 1px solid rgba(34, 211, 238, 0.80);
    border-radius: 10px;
    color: #ffffff;
    font-size: 11px;
    font-weight: 700;
    white-space: nowrap;
    box-shadow: 0 6px 20px rgba(15, 23, 42, 0.20);
  `;

  return element;
}


export function QuestMapView({
                               quest,
                               latitude,
                               longitude,
                               accuracy,
                               active = true,
                             }: QuestMapViewProps) {
  const {
    questName,
    targetLat,
    targetLng,
    rewardPoint,
    clearRadius,
  } = quest;


  const mapContainerRef =
      useRef<HTMLDivElement>(null);

  const mapRef =
      useRef<any>(null);

  const targetMarkerRef =
      useRef<any>(null);

  const targetOverlayRef =
      useRef<any>(null);

  const boundaryCircleRef =
      useRef<any>(null);

  const userMarkerRef =
      useRef<any>(null);

  const accuracyCircleRef =
      useRef<any>(null);

  /**
   * 최초 사용자 위치 수신 시 한 번만
   * 목표 + 현재 위치를 함께 보여주기 위한 flag.
   */
  const hasAdjustedBoundsRef =
      useRef(false);

  const [
    mapReady,
    setMapReady,
  ] = useState(false);

  const [
    distanceToTarget,
    setDistanceToTarget,
  ] = useState<number | null>(
      null,
  );


  /* =====================================================
   * 1. Map 초기화
   * ===================================================== */

  useEffect(() => {
    if (
        !mapContainerRef.current ||
        !window.kakao?.maps
    ) {
      return;
    }

    const kakao =
        window.kakao;

    setMapReady(false);
    setDistanceToTarget(null);

    hasAdjustedBoundsRef.current =
        false;


    const targetPosition =
        new kakao.maps.LatLng(
            targetLat,
            targetLng,
        );


    const map =
        new kakao.maps.Map(
            mapContainerRef.current,
            {
              center:
              targetPosition,

              level:
                  3,
            },
        );

    mapRef.current =
        map;


    /* 목표 마커 */

    const targetMarkerImage =
        new kakao.maps.MarkerImage(
            "https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/marker_red.png",

            new kakao.maps.Size(
                35,
                39,
            ),
        );


    const targetMarker =
        new kakao.maps.Marker({
          position:
          targetPosition,

          image:
          targetMarkerImage,

          map,
        });

    targetMarkerRef.current =
        targetMarker;


    /* 실제 퀘스트 인증 반경 */

    const boundaryCircle =
        new kakao.maps.Circle({
          center:
          targetPosition,

          radius:
          clearRadius,

          strokeWeight:
              2,

          strokeColor:
              "#06B6D4",

          strokeOpacity:
              0.85,

          strokeStyle:
              "dashed",

          fillColor:
              "#22D3EE",

          fillOpacity:
              0.11,
        });

    boundaryCircle.setMap(
        map,
    );

    boundaryCircleRef.current =
        boundaryCircle;


    /* 목표 Overlay */

    const overlayElement =
        createTargetOverlayElement(
            questName,
            rewardPoint,
            clearRadius,
        );


    const overlay =
        new kakao.maps.CustomOverlay({
          position:
          targetPosition,

          content:
          overlayElement,

          yAnchor:
              1.7,
        });

    overlay.setMap(
        map,
    );

    targetOverlayRef.current =
        overlay;


    setMapReady(true);


    return () => {
      targetMarkerRef.current?.setMap(
          null,
      );

      targetOverlayRef.current?.setMap(
          null,
      );

      boundaryCircleRef.current?.setMap(
          null,
      );

      userMarkerRef.current?.setMap(
          null,
      );

      accuracyCircleRef.current?.setMap(
          null,
      );


      targetMarkerRef.current =
          null;

      targetOverlayRef.current =
          null;

      boundaryCircleRef.current =
          null;

      userMarkerRef.current =
          null;

      accuracyCircleRef.current =
          null;

      mapRef.current =
          null;
    };
  }, [
    targetLat,
    targetLng,
    questName,
    rewardPoint,
    clearRadius,
  ]);


  /* =====================================================
   * 2. 부모가 전달한 GPS 위치를 지도에 반영
   * ===================================================== */

  useEffect(() => {
    if (
        !mapReady ||
        !window.kakao?.maps ||
        latitude === null ||
        longitude === null
    ) {
      return;
    }


    const map =
        mapRef.current;

    if (!map) {
      return;
    }


    const kakao =
        window.kakao;


    const userPosition =
        new kakao.maps.LatLng(
            latitude,
            longitude,
        );


    /*
     * 남은 거리 계산.
     */
    const distance =
        calculateApproximateDistance(
            latitude,
            longitude,
            targetLat,
            targetLng,
        );

    setDistanceToTarget(
        distance,
    );


    /* 사용자 위치 Marker */

    if (
        userMarkerRef.current
    ) {
      userMarkerRef.current.setPosition(
          userPosition,
      );
    } else {
      const userMarkerImage =
          new kakao.maps.MarkerImage(
              "https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/pink_b.png",

              new kakao.maps.Size(
                  30,
                  32,
              ),
          );


      userMarkerRef.current =
          new kakao.maps.Marker({
            position:
            userPosition,

            image:
            userMarkerImage,

            map,
          });
    }


    /* GPS 오차 원 */

    if (
        accuracy !== null
    ) {
      if (
          accuracyCircleRef.current
      ) {
        accuracyCircleRef.current.setPosition(
            userPosition,
        );

        accuracyCircleRef.current.setRadius(
            accuracy,
        );
      } else {
        const accuracyCircle =
            new kakao.maps.Circle({
              center:
              userPosition,

              radius:
              accuracy,

              strokeWeight:
                  1,

              strokeColor:
                  "#3B82F6",

              strokeOpacity:
                  0.5,

              fillColor:
                  "#60A5FA",

              fillOpacity:
                  0.08,
            });


        accuracyCircle.setMap(
            map,
        );

        accuracyCircleRef.current =
            accuracyCircle;
      }
    }


    /*
     * 최초 GPS 수신 시 한 번만
     * 현재 위치와 목표 위치를 함께 보여준다.
     *
     * 매 GPS 갱신마다 setBounds()를 호출하면
     * 사용자가 지도를 움직여도 강제로 원위치되므로
     * 최초 한 번만 처리한다.
     */
    if (
        !hasAdjustedBoundsRef.current &&
        distance <= 20_000
    ) {
      const bounds =
          new kakao.maps.LatLngBounds();


      bounds.extend(
          userPosition,
      );

      bounds.extend(
          new kakao.maps.LatLng(
              targetLat,
              targetLng,
          ),
      );


      map.setBounds(
          bounds,
      );


      hasAdjustedBoundsRef.current =
          true;
    }
  }, [
    mapReady,
    latitude,
    longitude,
    accuracy,
    targetLat,
    targetLng,
  ]);


  const insideClearRadius =
      distanceToTarget !== null &&
      distanceToTarget <=
      clearRadius;


  const gpsStatus =
      getGpsStatus(
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
        text-white
      "
      >
        {/* Header */}

        <div
            className="
          flex
          flex-wrap
          items-start
          justify-between
          gap-4
          border-b
          border-white/5
          px-5
          py-5
          md:px-6
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
                className="
                flex
                h-7
                w-7
                items-center
                justify-center
                rounded-lg
                bg-cyan-400/10
                text-cyan-300
              "
            >
              <LocationIcon />
            </span>

              <span
                  className="
                text-[10px]
                font-black
                tracking-[0.18em]
                text-cyan-400
              "
              >
              LIVE MISSION
            </span>
            </div>

            <h3
                className="
              mt-3
              text-xl
              font-black
              tracking-tight
            "
            >
              {questName}
            </h3>
          </div>


          <div
              className="
            rounded-xl
            border
            border-amber-400/15
            bg-amber-400/10
            px-4
            py-2
            text-right
          "
          >
            <p
                className="
              text-[10px]
              font-bold
              text-amber-300/70
            "
            >
              REWARD
            </p>

            <p
                className="
              mt-0.5
              font-black
              text-amber-300
            "
            >
              +{rewardPoint.toLocaleString()} P
            </p>
          </div>
        </div>


        {/* Metrics */}

        <div
            className="
          grid
          grid-cols-3
          border-b
          border-white/5
        "
        >
          <MissionMetric
              label="목표까지"
              value={formatDistance(
                  distanceToTarget,
              )}
          />

          <MissionMetric
              label="GPS 오차"
              value={
                accuracy === null
                    ? "--"
                    : `±${Math.round(
                        accuracy,
                    )}m`
              }
          />

          <MissionMetric
              label="인증 반경"
              value={`${clearRadius}m`}
              last
          />
        </div>


        {/* GPS status */}

        <div
            className="
          flex
          items-center
          justify-between
          gap-3
          px-5
          py-3
          md:px-6
        "
        >
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
              ${gpsStatus.dotClass}
            `}
          />

            <span
                className="
              text-xs
              font-semibold
              text-slate-300
            "
            >
            {active
                ? gpsStatus.label
                : "위치 추적 종료"}
          </span>
          </div>

          <span
              className="
            text-[10px]
            text-slate-500
          "
          >
          {active
              ? "LIVE GPS"
              : "QUEST COMPLETE"}
        </span>
        </div>


        {/* Map */}

        <div
            className="
          relative
          mx-4
          h-80
          overflow-hidden
          rounded-xl
          border
          border-slate-700
          bg-slate-950
          md:mx-6
          md:h-[420px]
        "
        >
          <div
              ref={mapContainerRef}
              className="h-full w-full"
          />

          {!mapReady && (
              <div
                  className="
              absolute
              inset-0
              flex
              items-center
              justify-center
              bg-slate-950
              text-xs
              text-slate-500
            "
              >
                지도를 불러오고 있습니다...
              </div>
          )}
        </div>


        {/* Guidance */}

        <div
            className="
          px-4
          pb-5
          pt-4
          md:px-6
          md:pb-6
        "
        >
          {!active ? (
              <MissionNotice
                  variant="complete"
                  title="퀘스트 완료"
                  description="위치 추적이 종료되었습니다."
              />
          ) : insideClearRadius ? (
              <MissionNotice
                  variant="ready"
                  title="인증 가능 범위에 도착했습니다"
                  description={`현재 목표의 ${clearRadius}m 인증 반경 안에 있습니다. 오른쪽 위치 인증 패널에서 인증을 진행하세요.`}
              />
          ) : (
              <MissionNotice
                  variant="move"
                  title="목표 지점으로 이동하세요"
                  description={
                    distanceToTarget ===
                    null
                        ? "현재 위치를 확인하고 있습니다."
                        : `인증 반경 ${clearRadius}m 안으로 이동하면 위치 인증을 진행할 수 있습니다.`
                  }
              />
          )}
        </div>
      </section>
  );
}


function getGpsStatus(
    accuracy: number | null,
) {
  if (accuracy === null) {
    return {
      label:
          "GPS 신호 확인 중",
      dotClass:
          "bg-slate-500",
    };
  }

  if (accuracy <= 10) {
    return {
      label:
          "GPS 정확도 매우 좋음",
      dotClass:
          "bg-emerald-400",
    };
  }

  if (accuracy <= 25) {
    return {
      label:
          "GPS 정확도 양호",
      dotClass:
          "bg-cyan-400",
    };
  }

  return {
    label:
        "GPS 정확도가 낮습니다",
    dotClass:
        "bg-rose-400",
  };
}


function formatDistance(
    distance: number | null,
) {
  if (distance === null) {
    return "--";
  }

  if (distance >= 1_000) {
    return `${(
        distance / 1_000
    ).toFixed(1)}km`;
  }

  return `${Math.round(
      distance,
  )}m`;
}


function MissionMetric({
                         label,
                         value,
                         last = false,
                       }: {
  label: string;
  value: string;
  last?: boolean;
}) {
  return (
      <div
          className={`
        px-3
        py-4
        text-center
        ${
              !last
                  ? "border-r border-white/5"
                  : ""
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
            className="
          mt-1
          text-sm
          font-black
          text-white
          md:text-base
        "
        >
          {value}
        </p>
      </div>
  );
}


function MissionNotice({
                         variant,
                         title,
                         description,
                       }: {
  variant:
      | "ready"
      | "move"
      | "complete";

  title: string;
  description: string;
}) {
  const style =
      {
        ready:
            "border-cyan-400/30 bg-cyan-400/10",

        move:
            "border-white/5 bg-slate-950/60",

        complete:
            "border-emerald-400/20 bg-emerald-400/10",
      }[variant];


  const dot =
      {
        ready:
            "bg-cyan-400",

        move:
            "bg-slate-500",

        complete:
            "bg-emerald-400",
      }[variant];


  return (
      <div
          className={`
        flex
        items-start
        gap-3
        rounded-xl
        border
        p-4
        ${style}
      `}
      >
      <span
          className={`
          mt-1.5
          h-2
          w-2
          shrink-0
          rounded-full
          ${dot}
        `}
      />

        <div>
          <p
              className="
            text-sm
            font-bold
            text-white
          "
          >
            {title}
          </p>

          <p
              className="
            mt-1
            text-xs
            leading-5
            text-slate-400
          "
          >
            {description}
          </p>
        </div>
      </div>
  );
}


function LocationIcon() {
  return (
      <svg
          className="h-4 w-4"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
      >
        <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.8}
            d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1116 0z"
        />

        <circle
            cx="12"
            cy="10"
            r="2.5"
            strokeWidth={1.8}
        />
      </svg>
  );
}