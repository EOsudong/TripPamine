import React, { useEffect, useRef, useState } from "react";
import { questApi } from "../api/quest";

interface QuestMapViewProps {
  questId: number;
  questName: string;
  targetLat: number; // 퀘스트 목적지 위도
  targetLng: number; // 퀘스트 목적지 경도
  rewardPoint: number;
  userId: number;
  onSuccess?: () => void;
}

export const QuestMapView: React.FC<QuestMapViewProps> = ({
  questId,
  questName,
  targetLat,
  targetLng,
  rewardPoint,
  userId,
  onSuccess,
}) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const [map, setMap] = useState<any>(null);
  const [userMarker, setUserMarker] = useState<any>(null);
  const [userCoords, setUserCoords] = useState<{
    lat: number;
    lng: number;
  } | null>(null);
  const [gpsAccuracy, setGpsAccuracy] = useState<number | null>(null);
  const [distanceToTarget, setDistanceToTarget] = useState<number | null>(null);
  const [isVerifying, setIsVerifying] = useState(false);
  const [verificationStatus, setVerificationStatus] = useState<
    "idle" | "success" | "failed"
  >("idle");

  // 근사 거리 계산 (Front-end 하버스인 공식 사용)
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

  // 1. 지도 초기화
  useEffect(() => {
    if (!mapContainerRef.current || !window.kakao) return;

    const kakao = window.kakao;
    const centerPosition = new kakao.maps.LatLng(targetLat, targetLng);

    const options = {
      center: centerPosition,
      level: 3,
    };

    const newMap = new kakao.maps.Map(mapContainerRef.current, options);
    setMap(newMap);

    const targetMarkerImage = new kakao.maps.MarkerImage(
      "https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/marker_red.png",
      new kakao.maps.Size(35, 39),
    );

    new kakao.maps.Marker({
      position: centerPosition,
      image: targetMarkerImage,
      map: newMap,
    });

    const boundaryCircle = new kakao.maps.Circle({
      center: centerPosition,
      radius: 50,
      strokeWeight: 2,
      strokeColor: "#00F0FF",
      strokeOpacity: 0.8,
      strokeStyle: "dashed",
      fillColor: "#00F0FF",
      fillOpacity: 0.15,
    });

    boundaryCircle.setMap(newMap);

    const overlayContent = `
      <div style="padding: 5px 10px; background: #0f172a; border: 1px solid #00f0ff; border-radius: 8px; color: #fff; font-size: 11px; font-weight: bold; transform: translateY(-40px);">
        ${questName} (${rewardPoint}P)
      </div>
    `;

    const customOverlay = new kakao.maps.CustomOverlay({
      position: centerPosition,
      content: overlayContent,
    });

    customOverlay.setMap(newMap);
  }, [targetLat, targetLng, questName, rewardPoint]);

  // 2. 실시간 위치 추적
  useEffect(() => {
    if (!map) return;

    const kakao = window.kakao;
    let watchId: number;

    if (navigator.geolocation) {
      watchId = navigator.geolocation.watchPosition(
        (position) => {
          const { latitude, longitude, accuracy } = position.coords;
          setUserCoords({ lat: latitude, lng: longitude });
          setGpsAccuracy(accuracy);

          const dist = calculateApproximateDistance(
            latitude,
            longitude,
            targetLat,
            targetLng,
          );
          setDistanceToTarget(dist);

          const userPosition = new kakao.maps.LatLng(latitude, longitude);

          if (userMarker) {
            userMarker.setPosition(userPosition);
          } else {
            const userMarkerImage = new kakao.maps.MarkerImage(
              "https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/pink_b.png",
              new kakao.maps.Size(30, 32),
            );
            const marker = new kakao.maps.Marker({
              position: userPosition,
              image: userMarkerImage,
              map: map,
            });
            setUserMarker(marker);
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
  }, [map, userMarker, targetLat, targetLng]);

  // 3. 백엔드 위치 검증 요청
  const handleVerifyLocation = async () => {
    if (!userCoords) {
      alert("사용자의 현재 GPS 좌표를 받아오지 못했습니다.");
      return;
    }

    setIsVerifying(true);
    setVerificationStatus("idle");

    try {
      const isVerified = await questApi.verifyQuest(
        questId,
        userId,
        userCoords.lat,
        userCoords.lng,
      );

      if (isVerified) {
        setVerificationStatus("success");
        if (onSuccess) onSuccess();
      } else {
        setVerificationStatus("failed");
      }
    } catch (err) {
      console.error(err);
      alert("위치 인증 처리 도중 네트워크 장애가 발생하였습니다.");
    } finally {
      setIsVerifying(false);
    }
  };

  const getGpsStatusLabel = () => {
    if (!gpsAccuracy)
      return {
        text: "측위 상태 측정중",
        color: "text-gray-400 border-gray-800 bg-gray-900",
      };
    if (gpsAccuracy <= 15)
      return {
        text: "GPS 상태: 매우 우수 (정밀 위성)",
        color: "text-emerald-400 border-emerald-500 bg-emerald-950/30",
      };
    if (gpsAccuracy <= 25)
      return {
        text: "GPS 상태: 보통 (다중 기지국 보정)",
        color: "text-yellow-400 border-yellow-500 bg-yellow-950/30",
      };
    return {
      text: "GPS 상태: 신호 취약 (오차 높음)",
      color: "text-rose-400 border-rose-500 bg-rose-950/30",
    };
  };

  const gpsStatus = getGpsStatusLabel();

  return (
    <div className="flex flex-col gap-4 rounded-3xl bg-slate-900 p-6 text-white border border-slate-800 shadow-2xl">
      <div className="flex items-center justify-between">
        <div>
          <span className="text-xs font-semibold text-cyan-400 tracking-wider">
            REALTIME GPS VERIFY
          </span>
          <h3 className="text-xl font-black mt-1">📍 {questName}</h3>
        </div>
        <div className="text-right">
          <span className="text-xs text-gray-400 block">완료 보상</span>
          <span className="text-lg font-bold text-yellow-400">
            +{rewardPoint.toLocaleString()} P
          </span>
        </div>
      </div>

      <div
        className={`rounded-xl border px-3 py-2 text-xs font-bold ${gpsStatus.color}`}
      >
        {gpsStatus.text} (오차 범위: ±
        {gpsAccuracy ? Math.round(gpsAccuracy) : "--"}m)
      </div>

      <div className="relative h-72 w-full overflow-hidden rounded-2xl border-2 border-slate-800">
        <div ref={mapContainerRef} className="h-full w-full" />
        {distanceToTarget !== null && (
          <div className="absolute right-4 top-4 z-10 rounded-full bg-slate-950/90 px-4 py-1.5 text-xs font-black text-white border border-cyan-500/50 shadow-lg">
            목표지까지{" "}
            {distanceToTarget > 1000
              ? `${(distanceToTarget / 1000).toFixed(1)}km`
              : `${Math.round(distanceToTarget)}m`}
          </div>
        )}
      </div>

      <div className="space-y-4">
        {distanceToTarget !== null && distanceToTarget <= 50 ? (
          <div className="rounded-xl bg-cyan-500/10 border border-cyan-500 p-3 text-center text-xs text-cyan-300 font-medium">
            퀘스트 인증 사정거리(50m) 내에 도달했습니다!
          </div>
        ) : (
          <div className="rounded-xl bg-slate-950 p-3 text-center text-xs text-gray-400 border border-slate-800">
            아직 퀘스트 장소와 떨어져 있습니다. 퀘스트 인증 영역으로 진입하세요.
          </div>
        )}

        {verificationStatus === "idle" && (
          <button
            onClick={handleVerifyLocation}
            disabled={
              isVerifying || distanceToTarget === null || distanceToTarget > 50
            }
            className="w-full rounded-2xl py-4 text-sm font-black transition text-black bg-cyan-400 hover:bg-cyan-300 disabled:bg-slate-800 disabled:text-gray-500 disabled:cursor-not-allowed shadow-lg shadow-cyan-500/10"
          >
            {isVerifying ? "📡 위치 검증 중..." : "현 위치 미션 완료 인증하기"}
          </button>
        )}

        {verificationStatus === "success" && (
          <div className="rounded-2xl bg-emerald-500 py-4 text-center text-sm font-black text-black shadow-lg shadow-emerald-500/20">
            🎉 축하합니다! 포인트가 지급되었습니다.
          </div>
        )}

        {verificationStatus === "failed" && (
          <div className="flex flex-col gap-2">
            <div className="rounded-2xl bg-rose-500/20 border border-rose-500/50 p-4 text-center text-xs text-rose-400 font-bold">
              🚫 위치 인증에 실패했거나 GPS 조작 신호가 감지되었습니다.
            </div>
            <button
              onClick={handleVerifyLocation}
              className="w-full rounded-xl bg-slate-800 py-3 text-xs font-black text-gray-200 hover:bg-slate-700 transition border border-slate-700"
            >
              다시 시도
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
