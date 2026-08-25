import React, { useEffect, useRef, useState } from 'react';

declare global {
  interface Window {
    kakao: any;
  }
}

export interface PlaceItem {
  name: string;
  address?: string;
  lat?: number;
  lng?: number;
  day?: number;
}

interface KakaoMapModalProps {
  isOpen: boolean;
  onClose: () => void;
  places: PlaceItem[];
}

const KAKAO_JS_KEY = "b33fca24ae4855453ab831d8b0208dc9";

// 카카오 장소 검색용 이름 정제 함수
const sanitizePlaceName = (rawName: string): string => {
  if (!rawName) return "";
  return rawName
    .split("->")[0]
    .replace(/\(.*?\)/g, "")
    .split("&")[0]
    .split("/")[0]
    .replace(/(산책|체크인|해결|선택|이동|방문|구경|식사|복귀|카페거리)/g, "")
    .trim();
};

export const KakaoMapModal: React.FC<KakaoMapModalProps> = ({ isOpen, onClose, places }) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<any>(null); // 카카오 지도 인스턴스 저장
  const placeCoordsRef = useRef<{ [key: number]: any }>({}); // 인덱스별 지도 좌표 저장

  const [isSdkLoaded, setIsSdkLoaded] = useState(false);
  const [mapError, setMapError] = useState<string | null>(null);
  const [selectedPlaceIdx, setSelectedPlaceIdx] = useState<number | null>(null); // 현재 클릭된 장소 인덱스

  // 1. SDK 동적 로드
  useEffect(() => {
    if (!isOpen) return;

    setMapError(null);
    setSelectedPlaceIdx(null);
    placeCoordsRef.current = {};

    if (window.kakao && window.kakao.maps) {
      setIsSdkLoaded(true);
      return;
    }

    const scriptId = "kakao-map-sdk";
    let script = document.getElementById(scriptId) as HTMLScriptElement;

    if (!script) {
      script = document.createElement("script");
      script.id = scriptId;
      script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_JS_KEY}&libraries=services&autoload=false`;
      script.async = true;
      document.head.appendChild(script);
    }

    const handleLoad = () => setIsSdkLoaded(true);
    const handleError = () => {
      setMapError("카카오 지도 SDK를 불러올 수 없습니다. Kakao Developers의 Web 플랫폼 도메인을 확인하세요.");
    };

    script.addEventListener("load", handleLoad);
    script.addEventListener("error", handleError);

    return () => {
      script.removeEventListener("load", handleLoad);
      script.removeEventListener("error", handleError);
    };
  }, [isOpen]);

  // 2. 지도 생성 및 마커 랜더링
  useEffect(() => {
    if (!isOpen || !isSdkLoaded || !window.kakao || !window.kakao.maps) return;

    window.kakao.maps.load(() => {
      if (!mapContainerRef.current) return;

      const defaultCenter = new window.kakao.maps.LatLng(33.3617, 126.5292);
      const map = new window.kakao.maps.Map(mapContainerRef.current, {
        center: defaultCenter,
        level: 9,
      });

      mapInstanceRef.current = map; // 지도 객체 저장

      const ps = new window.kakao.maps.services.Places();
      const geocoder = new window.kakao.maps.services.Geocoder();
      const bounds = new window.kakao.maps.LatLngBounds();

      setTimeout(() => {
        map.relayout();
        map.setCenter(defaultCenter);
      }, 150);

      if (!places || places.length === 0) return;

      let processedCount = 0;

      const renderMarker = (coords: any, originalName: string, index: number, day?: number) => {
        // 인덱스별 좌표 정보 저장
        placeCoordsRef.current[index] = coords;

        const marker = new window.kakao.maps.Marker({
          map: map,
          position: coords,
        });

        const infowindow = new window.kakao.maps.InfoWindow({
          content: `<div style="padding:6px 10px;font-size:12px;font-weight:bold;color:#1e293b;white-space:nowrap;">${day ? `Day ${day} : ` : ''}${originalName}</div>`,
        });
        infowindow.open(map, marker);

        bounds.extend(coords);
        processedCount++;

        if (processedCount === places.length && !bounds.isEmpty()) {
          map.setBounds(bounds);
        }
      };

      places.forEach((place, index) => {
        if (place.lat && place.lng) {
          const coords = new window.kakao.maps.LatLng(place.lat, place.lng);
          renderMarker(coords, place.name, index, place.day);
        } else {
          const searchQuery = sanitizePlaceName(place.name);

          ps.keywordSearch(searchQuery, (data: any, status: any) => {
            if (status === window.kakao.maps.services.Status.OK && data.length > 0) {
              const coords = new window.kakao.maps.LatLng(data[0].y, data[0].x);
              renderMarker(coords, place.name, index, place.day);
            } else {
              geocoder.addressSearch(searchQuery, (result: any, addrStatus: any) => {
                if (addrStatus === window.kakao.maps.services.Status.OK) {
                  const coords = new window.kakao.maps.LatLng(result[0].y, result[0].x);
                  renderMarker(coords, place.name, index, place.day);
                } else {
                  processedCount++;
                  if (processedCount === places.length && !bounds.isEmpty()) {
                    map.setBounds(bounds);
                  }
                }
              });
            }
          });
        }
      });
    });
  }, [isOpen, isSdkLoaded, places]);

  // 3. 목록 장소 클릭 시 해당 위치로 카메라 이동 및 확대
  const handlePlaceClick = (index: number) => {
    const coords = placeCoordsRef.current[index];
    const map = mapInstanceRef.current;

    if (coords && map) {
      setSelectedPlaceIdx(index);
      map.setLevel(4, { animate: true }); // 확대 레벨 설정 (숫자가 작을수록 더 크게 확대)
      map.panTo(coords); // 위치로 부드럽게 이동
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white w-full max-w-6xl h-[680px] rounded-3xl p-6 flex flex-col shadow-2xl relative">
        {/* 헤더 */}
        <div className="flex items-center justify-between mb-4 pb-3 border-b border-slate-100 shrink-0">
          <div>
            <h2 className="text-xl font-bold text-slate-800">🗺️ AI 추천 여행 경로 지도</h2>
            <p className="text-xs text-slate-400 mt-0.5">목록을 클릭하면 해당 위치로 지도가 이동 및 확대됩니다.</p>
          </div>
          <button
            onClick={onClose}
            type="button"
            className="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 text-sm font-bold rounded-xl transition-all"
          >
            닫기
          </button>
        </div>

        {/* 본문 */}
        <div className="flex-1 flex gap-4 min-h-0">
          {/* 지도 영역 */}
          <div className="w-2/3 h-full rounded-2xl bg-slate-100 overflow-hidden relative border border-slate-200 flex items-center justify-center">
            {mapError ? (
              <div className="p-6 text-center text-red-500 text-sm max-w-md">
                <p className="font-bold mb-2">⚠️ 지도 로드 실패</p>
                <p className="text-xs leading-relaxed text-slate-600">{mapError}</p>
              </div>
            ) : !isSdkLoaded ? (
              <div className="text-sm font-semibold text-slate-500 animate-pulse">
                지도를 불러오는 중입니다...
              </div>
            ) : (
              <div ref={mapContainerRef} className="w-full h-full" />
            )}
          </div>

          {/* AI 추천 장소 목록 */}
          <div className="w-1/3 h-full bg-slate-50 rounded-2xl border border-slate-200 p-4 flex flex-col overflow-hidden">
            <div className="flex items-center justify-between mb-3 shrink-0">
              <h3 className="font-bold text-slate-800 text-sm">📍 추천 장소 목록</h3>
              <span className="px-2 py-0.5 bg-sky-100 text-sky-700 text-xs font-bold rounded-full">
                총 {places.length}곳
              </span>
            </div>

            <div className="flex-1 overflow-y-auto space-y-2.5 pr-1">
              {places.map((place, idx) => {
                const isSelected = selectedPlaceIdx === idx;
                return (
                  <div
                    key={`${place.day}-${place.name}-${idx}`}
                    onClick={() => handlePlaceClick(idx)}
                    className={`p-3.5 rounded-xl border transition-all cursor-pointer select-none ${
                      isSelected
                        ? "bg-sky-50 border-sky-500 shadow-md ring-2 ring-sky-200"
                        : "bg-white border-slate-100 shadow-sm hover:border-sky-300 hover:shadow"
                    }`}
                  >
                    <div className="flex items-center gap-2 mb-1">
                      {place.day && (
                        <span className={`px-2 py-0.5 text-[10px] font-bold rounded-md shrink-0 ${
                          isSelected ? "bg-sky-600 text-white" : "bg-sky-500 text-white"
                        }`}>
                          Day {place.day}
                        </span>
                      )}
                      <h4 className={`font-bold text-sm truncate ${
                        isSelected ? "text-sky-900" : "text-slate-800"
                      }`}>
                        {place.name}
                      </h4>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};