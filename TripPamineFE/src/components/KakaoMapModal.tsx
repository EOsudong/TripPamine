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

// 주요 지역 중심 좌표 및 키워드 맵
const REGION_MAP: { [key: string]: { lat: number; lng: number } } = {
  "서울": { lat: 37.5665, lng: 126.9780 },
  "제주": { lat: 33.3617, lng: 126.5292 },
  "부산": { lat: 35.1796, lng: 129.0756 },
  "인천": { lat: 37.4563, lng: 126.7052 },
  "대구": { lat: 35.8714, lng: 128.6014 },
  "대전": { lat: 36.3504, lng: 127.3845 },
  "광주": { lat: 35.1595, lng: 126.8526 },
  "울산": { lat: 35.5384, lng: 129.3114 },
  "수원": { lat: 37.2636, lng: 127.0286 },
  "경주": { lat: 35.8562, lng: 129.2247 },
  "전주": { lat: 35.8242, lng: 127.1480 },
  "여수": { lat: 34.7604, lng: 127.6622 },
  "강릉": { lat: 37.7519, lng: 128.8761 },
  "속초": { lat: 38.2070, lng: 128.5918 },
  "가평": { lat: 37.8315, lng: 127.5095 },
  "춘천": { lat: 37.8813, lng: 127.7298 },
};

// 여행 장소 목록을 기반으로 대표 지역 자동 감지
const detectTripRegion = (places: PlaceItem[]) => {
  for (const place of places) {
    for (const region of Object.keys(REGION_MAP)) {
      if (place.name.includes(region) || (place.address && place.address.includes(region))) {
        return { name: region, center: REGION_MAP[region] };
      }
    }
  }
  // 기본값: 서울
  return { name: "서울", center: REGION_MAP["서울"] };
};

// 카카오 장소 검색용 정밀 키워드 정제 함수
const sanitizePlaceName = (rawName: string): string => {
  if (!rawName) return "";
  return rawName
    .split("->")[0]                                             // '->' 앞쪽 주요 장소 추출
    .split("또는")[0]                                           // '또는' 앞쪽 장소 추출
    .replace(/\(.*?\)/g, "")                                    // 괄호 내용 제거
    .split("&")[0]                                              // '&' 기준 앞쪽만 추출
    .split("/")[0]                                              // '/' 기준 앞쪽만 추출
    .replace(/(아침|점심|저녁|숙소|기타|일정|코스|한식|일식|중식|양식|식사|가벼운|한\s*끼|전통주|혼밥)/g, "") // 식사/시간 키워드 제거
    .replace(/(산책|체크인|해결|선택|이동|방문|구경|복귀|카페거리|재방문|가능|식당|근처|추천|1인|일몰|일출|야경|관람|체험|투어|드라이브)/g, "") // 서술어 및 풍경 단어 제거
    .trim();
};

export const KakaoMapModal: React.FC<KakaoMapModalProps> = ({ isOpen, onClose, places }) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<any>(null);
  const placeCoordsRef = useRef<{ [key: number]: any }>({});

  const [isSdkLoaded, setIsSdkLoaded] = useState(false);
  const [mapError, setMapError] = useState<string | null>(null);
  const [selectedPlaceIdx, setSelectedPlaceIdx] = useState<number | null>(null);
  const [estimatedIndices, setEstimatedIndices] = useState<{ [key: number]: boolean }>({});

  // SDK 동적 로드
  useEffect(() => {
    if (!isOpen) return;

    setMapError(null);
    setSelectedPlaceIdx(null);
    setEstimatedIndices({});
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

  // 지도 생성 및 동적 지역 감지 기반 다중 검색
  useEffect(() => {
    if (!isOpen || !isSdkLoaded || !window.kakao || !window.kakao.maps) return;

    window.kakao.maps.load(() => {
      if (!mapContainerRef.current) return;

      // 동적 대표 지역 감지 (예: 서울, 제주 등)
      const { name: detectedRegion, center: detectedCenter } = detectTripRegion(places);
      const defaultCenter = new window.kakao.maps.LatLng(detectedCenter.lat, detectedCenter.lng);

      const map = new window.kakao.maps.Map(mapContainerRef.current, {
        center: defaultCenter,
        level: 9,
      });

      mapInstanceRef.current = map;

      const ps = new window.kakao.maps.services.Places();
      const geocoder = new window.kakao.maps.services.Geocoder();
      const bounds = new window.kakao.maps.LatLngBounds();

      setTimeout(() => {
        map.relayout();
        map.setCenter(defaultCenter);
      }, 150);

      if (!places || places.length === 0) return;

      let processedCount = 0;
      let lastValidCoords: any = defaultCenter;

      const renderMarker = (coords: any, originalName: string, index: number, day?: number, isEstimated = false) => {
        placeCoordsRef.current[index] = coords;
        if (!isEstimated) lastValidCoords = coords;

        const marker = new window.kakao.maps.Marker({
          map: map,
          position: coords,
        });

        const labelText = isEstimated ? `${originalName} (추정 위치)` : originalName;
        const infowindow = new window.kakao.maps.InfoWindow({
          content: `<div style="padding:6px 10px;font-size:12px;font-weight:bold;color:${isEstimated ? '#d97706' : '#1e293b'};white-space:nowrap;">${day ? `Day ${day} : ` : ''}${labelText}</div>`,
        });
        infowindow.open(map, marker);

        bounds.extend(coords);
        processedCount++;

        if (processedCount === places.length && !bounds.isEmpty()) {
          map.setBounds(bounds);
        }
      };

      const createFallbackMarker = (originalName: string, index: number, day?: number) => {
        setEstimatedIndices((prev) => ({ ...prev, [index]: true }));

        const offsetLat = (Math.random() - 0.5) * 0.008;
        const offsetLng = (Math.random() - 0.5) * 0.008;
        const fallbackCoords = new window.kakao.maps.LatLng(
          lastValidCoords.getLat() + offsetLat,
          lastValidCoords.getLng() + offsetLng
        );

        renderMarker(fallbackCoords, originalName, index, day, true);
      };

      places.forEach((place, index) => {
        if (place.lat && place.lng) {
          const coords = new window.kakao.maps.LatLng(place.lat, place.lng);
          renderMarker(coords, place.name, index, place.day);
        } else {
          const query = sanitizePlaceName(place.name);
          const firstWord = query.split(" ")[0];

          if (!query) {
            createFallbackMarker(place.name, index, place.day);
            return;
          }

          // [1차] 동적 지역 키워드 조합 검색 (예: "서울 종로")
          ps.keywordSearch(`${detectedRegion} ${query}`, (regionData: any, regionStatus: any) => {
            if (regionStatus === window.kakao.maps.services.Status.OK && regionData.length > 0) {
              renderMarker(new window.kakao.maps.LatLng(regionData[0].y, regionData[0].x), place.name, index, place.day);
            } else {
              // [2차] 순수 키워드 검색
              ps.keywordSearch(query, (data: any, status: any) => {
                if (status === window.kakao.maps.services.Status.OK && data.length > 0) {
                  renderMarker(new window.kakao.maps.LatLng(data[0].y, data[0].x), place.name, index, place.day);
                } else {
                  // [3차] 동적 지역명 + 첫 단어 검색 (예: "서울 종로")
                  ps.keywordSearch(`${detectedRegion} ${firstWord}`, (tokenData: any, tokenStatus: any) => {
                    if (tokenStatus === window.kakao.maps.services.Status.OK && tokenData.length > 0) {
                      renderMarker(new window.kakao.maps.LatLng(tokenData[0].y, tokenData[0].x), place.name, index, place.day);
                    } else {
                      // [4차] 주소 검색 시도
                      geocoder.addressSearch(place.address || query, (result: any, addrStatus: any) => {
                        if (addrStatus === window.kakao.maps.services.Status.OK) {
                          renderMarker(new window.kakao.maps.LatLng(result[0].y, result[0].x), place.name, index, place.day);
                        } else {
                          // [최종 실패] 주변 임의 위치에 추정 핀 꽂기
                          createFallbackMarker(place.name, index, place.day);
                        }
                      });
                    }
                  });
                }
              });
            }
          });
        }
      });
    });
  }, [isOpen, isSdkLoaded, places]);

  // 목록 장소 클릭 시 카메라 이동 & 확대
  const handlePlaceClick = (index: number) => {
    const coords = placeCoordsRef.current[index];
    const map = mapInstanceRef.current;

    if (coords && map) {
      setSelectedPlaceIdx(index);
      map.setLevel(4, { animate: true });
      map.panTo(coords);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white w-full max-w-6xl h-[680px] rounded-3xl p-6 flex flex-col shadow-2xl relative">
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

        <div className="flex-1 flex gap-4 min-h-0">
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
                const isEstimated = estimatedIndices[idx];

                return (
                  <div
                    key={`${place.day}-${place.name}-${idx}`}
                    onClick={() => handlePlaceClick(idx)}
                    className={`p-3.5 rounded-xl border transition-all select-none cursor-pointer ${
                      isSelected
                        ? "bg-sky-50 border-sky-500 shadow-md ring-2 ring-sky-200"
                        : isEstimated
                        ? "bg-amber-50/60 border-amber-200 hover:border-amber-400"
                        : "bg-white border-slate-100 shadow-sm hover:border-sky-300 hover:shadow"
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2 mb-1">
                      <div className="flex items-center gap-2 min-w-0">
                        {place.day && (
                          <span className={`px-2 py-0.5 text-[10px] font-bold rounded-md shrink-0 ${
                            isSelected
                              ? "bg-sky-600 text-white"
                              : isEstimated
                              ? "bg-amber-500 text-white"
                              : "bg-sky-500 text-white"
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

                      {isEstimated && (
                        <span className="text-[10px] font-bold text-amber-600 shrink-0 bg-amber-100/80 px-2 py-0.5 rounded-full">
                          🍊 추정 위치
                        </span>
                      )}
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