import React, { useEffect, useRef, useState } from "react";

// 미스터리 퀘스트 GPS 인증 화면 전용 지도 (Kakao Maps).
//
// QuestMapView.tsx와 동일하게 "지금 어디에 있고 목표까지 얼마나 남았는지 보여주는 것"만
// 담당한다. 실제 클리어 인증(완료 API 호출)은 MysteryTourPlay.tsx의 handleCompleteQuest가
// 단일 책임으로 담당하고, 이 컴포넌트는 그 결과에 관여하지 않는다.
//
// index.html에 카카오 SDK <script>가 autoload=false로 이미 실려 있으므로 별도 API 키나
// <script> 태그가 필요 없다. 다만 autoload=false 환경에서는 kakao.maps.* 를 쓰기 전에
// kakao.maps.load()를 명시적으로 호출해야 하므로 (QuestWorldMap.tsx와 동일한 패턴),
// QuestMapView.tsx처럼 window.kakao 존재 여부만 보고 바로 kakao.maps.LatLng를 만들지 않는다 -
// 이 화면에 먼저 들어온 다른 지도 컴포넌트가 로드를 트리거해준 적이 없으면 실패할 수 있어서다.
interface MysteryQuestMapViewProps {
  questName: string;
  targetLat: number;
  targetLng: number;
  rewardPoint: number;
  // MysteryQuestResponse.clearRadiusMeters. 혹시 비어 있으면 여행 퀘스트 기본값(100m)으로 대체.
  clearRadiusMeters: number | null;
  active?: boolean;
}

const FALLBACK_CLEAR_RADIUS_METERS = 100;

export const MysteryQuestMapView: React.FC<MysteryQuestMapViewProps> = ({
  questName,
  targetLat,
  targetLng,
  rewardPoint,
  clearRadiusMeters,
  active = true,
}) => {
  const radius = clearRadiusMeters ?? FALLBACK_CLEAR_RADIUS_METERS;

  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const userMarkerRef = useRef<any>(null);
  const boundaryCircleRef = useRef<any>(null);
  const customOverlayRef = useRef<any>(null);
  const [mapReady, setMapReady] = useState(false);
  const [mapError, setMapError] = useState<string | null>(null);
  const [gpsAccuracy, setGpsAccuracy] = useState<number | null>(null);
  const [distanceToTarget, setDistanceToTarget] = useState<number | null>(null);

  // 근사 거리 계산 (지도 위 실시간 표시용. 실제 클리어 판정은 서버가 함)
  const calculateApproximateDistance = (
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number,
  ) => {
    const R = 6371000;
    const dLat = ((lat2 - lat1) * Math.PI) / 180;
    const dLon = ((lon2 - lon1) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos((lat1 * Math.PI) / 180) *
        Math.cos((lat2 * Math.PI) / 180) *
        Math.sin(dLon / 2) *
        Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  };

  // 1. SDK 로드 확인 후 지도 초기화 (목표 지점 마커 + 인증 반경 원)
  useEffect(() => {
    if (!mapContainerRef.current) return;

    if (typeof window.kakao?.maps?.load !== "function") {
      setMapError(
        "Kakao Map SDK를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.",
      );
      return;
    }

    const kakao = window.kakao;
    let disposed = false;
    setMapError(null);

    kakao.maps.load(() => {
      if (disposed || !mapContainerRef.current) return;

      const centerPosition = new kakao.maps.LatLng(targetLat, targetLng);
      const newMap = new kakao.maps.Map(mapContainerRef.current, {
        center: centerPosition,
        level: 3,
      });
      mapRef.current = newMap;

      const targetMarkerImage = new kakao.maps.MarkerImage(
        "https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/marker_red.png",
        new kakao.maps.Size(35, 39),
      );
      new kakao.maps.Marker({
        position: centerPosition,
        image: targetMarkerImage,
        map: newMap,
      });

      // 미스터리 투어 톤(violet)에 맞춰 여행 퀘스트 지도(cyan)와 색만 구분
      const circle = new kakao.maps.Circle({
        center: centerPosition,
        radius,
        strokeWeight: 2,
        strokeColor: "#A78BFA",
        strokeOpacity: 0.8,
        strokeStyle: "dashed",
        fillColor: "#A78BFA",
        fillOpacity: 0.15,
      });
      circle.setMap(newMap);
      boundaryCircleRef.current = circle;

      const overlayContent = `
        <div style="padding: 5px 10px; background: #0f172a; border: 1px solid #A78BFA; border-radius: 8px; color: #fff; font-size: 11px; font-weight: bold; transform: translateY(-40px);">
          ${questName} (${rewardPoint}P · 반경 ${radius}m)
        </div>
      `;
      const customOverlay = new kakao.maps.CustomOverlay({
        position: centerPosition,
        content: overlayContent,
      });
      customOverlay.setMap(newMap);
      customOverlayRef.current = customOverlay;

      setMapReady(true);
    });

    return () => {
      disposed = true;
      customOverlayRef.current?.setMap(null);
      boundaryCircleRef.current?.setMap(null);
      userMarkerRef.current?.setMap(null);
      customOverlayRef.current = null;
      boundaryCircleRef.current = null;
      userMarkerRef.current = null;
      mapRef.current = null;
      setMapReady(false);
    };
  }, [targetLat, targetLng, questName, rewardPoint, radius]);

  // 2. 실시간 위치 추적 (시각화 전용 - 서버로 전송하지 않음. 전송/검증은 handleCompleteQuest가 담당)
  useEffect(() => {
    if (!mapReady || !active) return;

    const kakao = window.kakao;
    let watchId: number;

    if (navigator.geolocation) {
      watchId = navigator.geolocation.watchPosition(
        (position) => {
          const map = mapRef.current;
          if (!map) return;

          const { latitude, longitude, accuracy } = position.coords;
          setGpsAccuracy(accuracy);

          const dist = calculateApproximateDistance(
            latitude,
            longitude,
            targetLat,
            targetLng,
          );
          setDistanceToTarget(dist);

          const userPosition = new kakao.maps.LatLng(latitude, longitude);

          if (userMarkerRef.current) {
            userMarkerRef.current.setPosition(userPosition);
          } else {
            const userMarkerImage = new kakao.maps.MarkerImage(
              "https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/pink_b.png",
              new kakao.maps.Size(30, 32),
            );
            userMarkerRef.current = new kakao.maps.Marker({
              position: userPosition,
              image: userMarkerImage,
              map,
            });
          }
        },
        (error) => {
          console.error("실시간 GPS 수집 에러:", error);
        },
        { enableHighAccuracy: true, timeout: 8000, maximumAge: 0 },
      );
    }

    return () => {
      if (watchId) navigator.geolocation.clearWatch(watchId);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mapReady, active, targetLat, targetLng]);

  return (
    <div className="rounded-2xl bg-black/20 border border-white/10 p-4">
      {mapError ? (
        <div className="rounded-xl bg-rose-500/10 border border-rose-500/30 p-3 text-xs text-rose-300">
          {mapError}
        </div>
      ) : (
        <>
          <div className="relative h-56 w-full overflow-hidden rounded-xl border border-white/10">
            <div ref={mapContainerRef} className="h-full w-full" />
            {!mapReady && (
              <div className="absolute inset-0 flex items-center justify-center bg-slate-950/80 text-xs text-slate-400">
                지도를 불러오는 중...
              </div>
            )}
            {distanceToTarget !== null && (
              <div className="absolute right-3 top-3 z-10 rounded-full bg-slate-950/90 px-3 py-1 text-[11px] font-black text-white border border-violet-400/40">
                목표지까지{" "}
                {distanceToTarget > 1000
                  ? `${(distanceToTarget / 1000).toFixed(1)}km`
                  : `${Math.round(distanceToTarget)}m`}
              </div>
            )}
          </div>
          <p className="mt-2 text-[11px] text-slate-500">
            {gpsAccuracy != null
              ? `GPS 오차 범위 ±${Math.round(gpsAccuracy)}m · 인증 반경 ${radius}m`
              : "GPS 신호를 찾는 중..."}
          </p>
        </>
      )}
    </div>
  );
};
