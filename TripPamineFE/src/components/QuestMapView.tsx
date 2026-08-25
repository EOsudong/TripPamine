import React, { useEffect, useRef, useState } from "react";
import type { QuestResponse } from "../api/quest";

// 지도 시각화 전용 컴포넌트 (Kakao Maps).
//
// 예전 버전에서는 이 컴포넌트가 자체적으로 "위치 인증하기" 버튼과 questApi.verifyQuest 호출을
// 갖고 있었는데, RealtimeQuestVerifier.tsx도 똑같이 별도의 인증 버튼 + API 호출을 갖고 있어서
// 화면 하나에 인증 버튼이 두 개 뜨고, 백엔드로 클리어 요청이 중복으로 나갈 수 있는 구조였다.
// 실제 클리어 인증(시작→클리어 API 호출)은 RealtimeQuestVerifier가 단일 책임으로 담당하고,
// 이 컴포넌트는 오직 "지금 어디에 있고 목표까지 얼마나 남았는지 지도로 보여주는 것"만 한다.
interface QuestMapViewProps {
    quest: QuestResponse;
}

export const QuestMapView: React.FC<QuestMapViewProps> = ({ quest }) => {
    const { questName, targetLat, targetLng, rewardPoint, clearRadius } = quest;

    const mapContainerRef = useRef<HTMLDivElement>(null);
    // 지도/마커/원은 Kakao SDK가 직접 관리하는 명령형 객체라 React state가 아니라 ref로 들고 있는다.
    // (state로 들고 있으면 setState 콜백 안에서만 값을 읽게 되어 "선언은 했지만 읽지 않는 값"이
    //  되기 쉽고, 매 위치 갱신마다 불필요한 리렌더가 발생한다)
    const mapRef = useRef<any>(null);
    const userMarkerRef = useRef<any>(null);
    const boundaryCircleRef = useRef<any>(null);
    const [mapReady, setMapReady] = useState(false);
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

    // 1. 지도 초기화 (목표 지점 마커 + 실제 클리어 반경 원)
    useEffect(() => {
        if (!mapContainerRef.current || !window.kakao) return;

        const kakao = window.kakao;
        const centerPosition = new kakao.maps.LatLng(targetLat, targetLng);

        const newMap = new kakao.maps.Map(mapContainerRef.current, {
            center: centerPosition,
            level: 3,
        });
        mapRef.current = newMap;
        setMapReady(true);

        const targetMarkerImage = new kakao.maps.MarkerImage(
            "https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/marker_red.png",
            new kakao.maps.Size(35, 39),
        );

        new kakao.maps.Marker({
            position: centerPosition,
            image: targetMarkerImage,
            map: newMap,
        });

        // 퀘스트마다 다른 clearRadius를 그대로 원으로 그려서, 사용자가 실제 인정 범위를 눈으로 확인할 수 있게 함
        const circle = new kakao.maps.Circle({
            center: centerPosition,
            radius: clearRadius,
            strokeWeight: 2,
            strokeColor: "#00F0FF",
            strokeOpacity: 0.8,
            strokeStyle: "dashed",
            fillColor: "#00F0FF",
            fillOpacity: 0.15,
        });
        circle.setMap(newMap);
        boundaryCircleRef.current = circle;

        const overlayContent = `
      <div style="padding: 5px 10px; background: #0f172a; border: 1px solid #00f0ff; border-radius: 8px; color: #fff; font-size: 11px; font-weight: bold; transform: translateY(-40px);">
        ${questName} (${rewardPoint}P · 반경 ${clearRadius}m)
      </div>
    `;

        const customOverlay = new kakao.maps.CustomOverlay({
            position: centerPosition,
            content: overlayContent,
        });

        customOverlay.setMap(newMap);

        // 컴포넌트가 사라지거나(지도 닫고 목록으로 이동) 퀘스트가 바뀌어 이 effect가 재실행되기 직전에
        // 이전 지도가 만든 마커/원/오버레이/지도 인스턴스를 확실히 정리한다.
        // (예전 코드는 이 정리가 전혀 없어서, 퀘스트를 여러 번 열고 닫으면 지도 객체가 계속 쌓였다)
        return () => {
            customOverlay.setMap(null);
            circle.setMap(null);
            if (userMarkerRef.current) {
                userMarkerRef.current.setMap(null);
                userMarkerRef.current = null;
            }
            boundaryCircleRef.current = null;
            mapRef.current = null;
            setMapReady(false);
        };
    }, [targetLat, targetLng, questName, rewardPoint, clearRadius]);

    // clearRadius가 나중에 바뀌는 경우(재선택 등) 원 반지름도 함께 갱신
    useEffect(() => {
        if (boundaryCircleRef.current) {
            boundaryCircleRef.current.setRadius(clearRadius);
        }
    }, [clearRadius]);

    // 2. 실시간 위치 추적 (시각화 전용 - 서버로 전송하지 않음. 전송은 RealtimeQuestVerifier가 담당)
    useEffect(() => {
        if (!mapReady) return;

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
    }, [mapReady, targetLat, targetLng]);

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
            REALTIME GPS MAP
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

            {distanceToTarget !== null && distanceToTarget <= clearRadius ? (
                <div className="rounded-xl bg-cyan-500/10 border border-cyan-500 p-3 text-center text-xs text-cyan-300 font-medium">
                    퀘스트 인증 사정거리({clearRadius}m) 내에 도달했습니다! 아래 인증 버튼을 눌러주세요.
                </div>
            ) : (
                <div className="rounded-xl bg-slate-950 p-3 text-center text-xs text-gray-400 border border-slate-800">
                    아직 퀘스트 장소와 떨어져 있습니다. 인증 반경({clearRadius}m) 안으로 진입하세요.
                </div>
            )}
        </div>
    );
};
