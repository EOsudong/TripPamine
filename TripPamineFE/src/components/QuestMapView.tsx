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
  preview?: boolean;
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
                               preview = false,
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

  // 카카오 커스텀 오버레이 DOM을 보관해 인증 반경 도착 시 강조한다.
  const targetBeaconElementRef = useRef<HTMLDivElement | null>(null);
  const guideLineRef = useRef<any>(null);

  const targetOverlayRef =
      useRef<any>(null);

  const boundaryCircleRef =
      useRef<any>(null);

  const userMarkerRef =
      useRef<any>(null);

  const accuracyCircleRef =
      useRef<any>(null);

  const [
    mapReady,
    setMapReady,
  ] = useState(false);

  // 카카오 SDK 로딩 실패를 무한 로딩 대신 사용자에게 안내한다.
  const [mapError, setMapError] = useState<string | null>(null);
  const [startingDistance, setStartingDistance] = useState<number | null>(null);

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
    if (!mapContainerRef.current) {
      return;
    }

    // index.html의 autoload=false 설정에서는 maps 존재만으로 SDK 준비가 끝난 것이 아니다.
    if (typeof window.kakao?.maps?.load !== "function") {
      setMapError("카카오맵 SDK를 불러오지 못했습니다. JavaScript 키와 등록 도메인을 확인해주세요.");
      return;
    }

    const kakao = window.kakao;
    let disposed = false;

    setMapError(null);
    setMapReady(false);
    setDistanceToTarget(null);
    setStartingDistance(null);

    // SDK 초기화 콜백 안에서만 LatLng/Map/Marker 등의 생성자를 사용한다.
    kakao.maps.load(() => {
      // 퀘스트 변경/화면 이탈 후 늦게 실행되는 콜백은 무시한다.
      if (disposed || !mapContainerRef.current) return;
      try {
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

        // 외부 이미지가 404/차단될 때 마커가 깨지는 문제를 없앤다.
        // 퀘스트 목적지는 이미지 대신 Kakao CustomOverlay로 그린다.
        const targetPin = document.createElement("div");
        targetPin.className = "tp-quest-beacon";
        targetPin.setAttribute("aria-label", "퀘스트 목표 위치");
        targetPin.setAttribute("role", "img");
        const targetCore = document.createElement("span");
        targetCore.className = "tp-quest-beacon-core";
        targetCore.textContent = "◆";
        targetPin.appendChild(targetCore);
        targetBeaconElementRef.current = targetPin;
        const targetMarker = new kakao.maps.CustomOverlay({
          position: targetPosition,
          content: targetPin,
          yAnchor: 1,
        });
        targetMarker.setMap(map);

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
                  0.14,
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
      } catch (initializationError) {
        console.error("퀘스트 지도 초기화 실패:", initializationError);
        setMapReady(false);
        setMapError("퀘스트 지도를 초기화하지 못했습니다. 잠시 후 다시 시도해주세요.");
      }
    });

    return () => {
      disposed = true;
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

      // GPS 기반 직선 가이드도 지도 이탈 시 해제해 오버레이 누수를 방지한다.
      guideLineRef.current?.setMap(null);
      guideLineRef.current = null;
      targetBeaconElementRef.current = null;


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
    setStartingDistance((previous) => previous ?? Math.max(distance, clearRadius));


    /* 사용자 위치 Marker */

    if (
        userMarkerRef.current
    ) {
      userMarkerRef.current.setPosition(
          userPosition,
      );
    } else {
      // 깨진 외부 핀 이미지를 사용하지 않는 '내 위치' 원형 오버레이.
      const userPin = document.createElement("div");
      userPin.className = "tp-quest-user-pulse";
      userPin.setAttribute("aria-label", "내 현재 위치");
      userPin.setAttribute("role", "img");
      const userCore = document.createElement("span");
      userCore.className = "tp-quest-user-core";
      userPin.appendChild(userCore);
      userMarkerRef.current = new kakao.maps.CustomOverlay({
        position: userPosition,
        content: userPin,
        yAnchor: 0.5,
      });
      userMarkerRef.current.setMap(map);
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


    // 두 좌표를 잇는 것은 실제 도로 경로가 아닌 직선 방향 가이드다.
    // 너무 먼 지점까지 선을 그리지 않고, 카메라는 사용자 조작 전까지 목적지에 고정한다.
    if (distance <= 20_000) {
      const path = [userPosition, new kakao.maps.LatLng(targetLat, targetLng)];
      if (guideLineRef.current) {
        guideLineRef.current.setPath(path);
      } else {
        guideLineRef.current = new kakao.maps.Polyline({
          path, strokeWeight: 3, strokeColor: "#0891b2",
          strokeOpacity: 0.75, strokeStyle: "dashed",
        });
        guideLineRef.current.setMap(map);
      }
    } else if (guideLineRef.current) {
      guideLineRef.current.setMap(null);
      guideLineRef.current = null;
    }

    // 먼 거리의 사용자 위치 때문에 지도가 목적지에서 벗어나지 않도록
    // GPS 갱신에 따라 setBounds()/panTo()를 실행하지 않는다.

  }, [
    mapReady,
    latitude,
    longitude,
    accuracy,
    targetLat,
    targetLng,
    clearRadius,
  ]);


  const insideClearRadius =
      distanceToTarget !== null &&
      distanceToTarget <=
      clearRadius;


  const gpsStatus =
      getGpsStatus(
          accuracy,
      );

  // 출발 시점 거리 대비 표시용 진행도이며, 실제 인증 성공 판정은 서버가 결정한다.
  const distanceProgress = distanceToTarget === null || startingDistance === null
      ? 0
      : distanceToTarget <= clearRadius ? 100
      : Math.round(Math.max(0, Math.min(100,
          (1 - distanceToTarget / Math.max(startingDistance, clearRadius, 1)) * 100)));

  useEffect(() => {
    targetBeaconElementRef.current?.classList.toggle(
        "tp-quest-beacon--ready", !preview && active && insideClearRadius,
    );
  }, [active, insideClearRadius, preview, mapReady]);


  // 퀘스트 지도는 목적지가 기본 중심이다. 사용자가 명시적으로 선택할 때만 전환한다.
  const focusTarget = () => {
    if (!mapReady || !mapRef.current) return;
    const kakao = window.kakao;
    mapRef.current.setLevel(3);
    mapRef.current.panTo(new kakao.maps.LatLng(targetLat, targetLng));
  };

  const focusUser = () => {
    if (!mapReady || !mapRef.current || latitude === null || longitude === null) return;
    const kakao = window.kakao;
    mapRef.current.setLevel(3);
    mapRef.current.panTo(new kakao.maps.LatLng(latitude, longitude));
  };

  const showBoth = () => {
    if (!mapReady || !mapRef.current || latitude === null || longitude === null) return;
    const kakao = window.kakao;
    const bounds = new kakao.maps.LatLngBounds();
    bounds.extend(new kakao.maps.LatLng(targetLat, targetLng));
    bounds.extend(new kakao.maps.LatLng(latitude, longitude));
    mapRef.current.setBounds(bounds, 60, 60, 60, 60);
  };

  return (
      <section
          className="
        overflow-hidden
        rounded-[1.75rem]
        border
        border-white/10
        bg-slate-900
        text-white
        shadow-[0_28px_85px_-30px_rgba(34,211,238,0.34)]
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
              HOLOGRAPHIC MISSION
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
            {preview
                ? "수락 전 위치 미리보기"
                : active ? gpsStatus.label : "위치 추적 종료"}
          </span>
          </div>

          <span
              className="
            text-[10px]
            text-slate-500
          "
          >
          {preview ? "TARGET PREVIEW" : active ? "LIVE GPS" : "QUEST COMPLETE"}
        </span>
        </div>


        {/* 출발 위치 대비 이동 진행도. GPS 오차로 수치가 흔들릴 수 있으며 보상 인증과 무관하다. */}
        <div className="border-b border-white/5 bg-slate-950/45 px-5 py-4 md:px-6">
          <div className="flex items-center justify-between text-[11px] font-bold">
            <span className="text-slate-300">목표 접근 진행도 · 참고용</span>
            <span className="tabular-nums text-cyan-300">{distanceToTarget === null ? "GPS 대기" : `${distanceProgress}%`}</span>
          </div>
          <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-700/80" role="progressbar"
               aria-label="목적지 접근 진행도" aria-valuemin={0} aria-valuemax={100} aria-valuenow={distanceProgress}>
            <div className="tp-quest-progress-sheen h-full rounded-full bg-gradient-to-r from-indigo-500 via-cyan-400 to-teal-300 transition-[width] duration-700"
                 style={{ width: `${distanceProgress}%` }} />
          </div>
          <p className="mt-2 text-[10px] text-slate-500">실제 도로 길찾기가 아닌 GPS 직선 거리 기준 · 현장 인증은 서버에서 판정</p>
        </div>

        {/* 지도 중심 전환 버튼: 최초 진입은 목적지를 중심으로 보여준다. */}
        <div className="flex flex-wrap items-center gap-2 px-4 pb-3 md:px-6" role="group" aria-label="지도 보기 위치">
          <button type="button" onClick={focusTarget} disabled={!mapReady}
                  className="rounded-xl border border-cyan-300/50 bg-gradient-to-b from-cyan-300 to-cyan-600 px-3 py-2 text-xs font-bold text-slate-950 shadow-[0_6px_16px_rgba(6,182,212,0.32)] transition hover:brightness-110 disabled:opacity-40">
            목적지 보기
          </button>
          <button type="button" onClick={focusUser} disabled={!mapReady || latitude === null || longitude === null}
                  className="rounded-xl border border-white/10 bg-white/10 px-3 py-2 text-xs font-bold text-blue-200 shadow-inner backdrop-blur-md transition hover:bg-blue-500/20 disabled:opacity-40">
            내 위치 보기
          </button>
          <button type="button" onClick={showBoth} disabled={!mapReady || latitude === null || longitude === null}
                  className="rounded-xl border border-white/10 bg-white/10 px-3 py-2 text-xs font-bold text-slate-200 shadow-inner backdrop-blur-md transition hover:bg-white/20 disabled:opacity-40">
            전체 경로 보기
          </button>
          <span className="ml-auto text-[11px] text-slate-400">발광 핀: 목적지 · 파란 펄스: 내 위치</span>
        </div>

        {/* Map */}

        <div
            className="
          relative
          mx-4
          h-80
          overflow-hidden
          rounded-2xl
          border
          border-cyan-300/20
          bg-slate-950
          shadow-[inset_0_3px_15px_rgba(2,6,23,0.5),0_18px_40px_-18px_rgba(34,211,238,0.25)]
          md:mx-6
          md:h-[420px]
        "
        >
          <div
              ref={mapContainerRef}
              className="h-full w-full"
          />
          {/* 지도 위 HUD는 지도 조작을 가리지 않도록 필요한 영역만 클릭을 받는다. */}
          <div className="pointer-events-none absolute left-3 top-3 z-10 rounded-xl border border-white/20 bg-slate-950/85 px-3 py-2 text-[10px] font-black tracking-[0.12em] text-cyan-200 shadow-xl backdrop-blur-lg">
            {preview ? "LOCATION PREVIEW" : insideClearRadius ? "MISSION READY" : "FIELD NAVIGATION"}
          </div>
          <div className="pointer-events-none absolute bottom-3 right-3 z-10 rounded-xl border border-white/20 bg-slate-950/85 px-3 py-2 text-right text-[11px] text-white shadow-xl backdrop-blur-lg">
            <span className="block text-[9px] font-bold text-slate-400">목표까지 직선 거리</span>
            <strong className="tabular-nums text-lg text-cyan-200">{formatDistance(distanceToTarget)}</strong>
          </div>

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
                {mapError ?? "지도를 불러오고 있습니다..."}
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
          {preview ? (
              <MissionNotice
                  variant="move"
                  title="미션 목적지를 미리 확인하세요"
                  description="지도를 목적지 중심으로 표시합니다. 퀘스트 수락 전에는 위치 권한을 요청하지 않습니다."
              />
          ) : !active ? (
              <MissionNotice
                  variant="complete"
                  title="퀘스트 완료"
                  description="위치 추적이 종료되었습니다."
              />
          ) : insideClearRadius ? (
              <MissionNotice
                  variant="ready"
                  title="MISSION READY · 인증 가능 범위입니다"
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