import React, { useEffect, useRef, useState, useMemo } from 'react';

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

// 주요 지역 중심 좌표
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
  "양양": { lat: 38.0754, lng: 128.6189 },
  "가평": { lat: 37.8315, lng: 127.5095 },
  "춘천": { lat: 37.8813, lng: 127.7298 },
  "횡성": { lat: 37.4918, lng: 127.9846 },
};

// 기존 카카오 클래식 물방울 핀 형태 커스텀 SVG 마커 생성기
const createMarkerImage = (fillColor: string, strokeColor: string) => {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="29" height="42" viewBox="0 0 29 42">
    <path fill="${fillColor}" stroke="${strokeColor}" stroke-width="1.5" d="M14.5 0C6.49 0 0 6.49 0 14.5C0 25.38 14.5 42 14.5 42C14.5 42 29 25.38 29 14.5C29 6.49 22.51 0 14.5 0Z"/>
    <circle cx="14.5" cy="14.5" r="5.5" fill="#FFFFFF"/>
  </svg>`;
  const src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
  
  if (
    window.kakao &&
    window.kakao.maps &&
    typeof window.kakao.maps.Size === 'function' &&
    typeof window.kakao.maps.Point === 'function'
  ) {
    const size = new window.kakao.maps.Size(29, 42);
    const option = { offset: new window.kakao.maps.Point(14.5, 42) };
    return new window.kakao.maps.MarkerImage(src, size, option);
  }
  return null;
};

// 두 위/경도 간 직선 거리 계산 (단위: km)
const calculateDistance = (lat1: number, lng1: number, lat2: number, lng2: number): number => {
  const R = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLng / 2) *
      Math.sin(dLng / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
};

// 대표 지역 자동 감지
const detectTripRegion = (places: PlaceItem[]) => {
  for (const place of places) {
    for (const region of Object.keys(REGION_MAP)) {
      if (place.name.includes(region) || (place.address && place.address.includes(region))) {
        return { name: region, center: REGION_MAP[region] };
      }
    }
  }
  return { name: "서울", center: REGION_MAP["서울"] };
};

// 키워드 정제 함수
const sanitizePlaceName = (rawName: string): string => {
  if (!rawName) return "";
  
  if (/(차량\s*이용|대중교통|도보\s*이동|자차\s*이용)/g.test(rawName)) {
    return "";
  }

  return rawName
    .split("->")[0]
    .split("또는")[0]
    .replace(/\(.*?\)/g, "")
    .split("&")[0]
    .split("/")[0]
    .replace(/(아침|점심|저녁|숙소|기타|일정|코스|한식|일식|중식|양식|식사|가벼운|한\s*끼|전통주|혼밥)/g, "")
    .replace(/(산책|체크인|해결|선택|이동|방문|구경|복귀|카페거리|재방문|가능|식당|근처|추천|1인|일몰|일출|야경|관람|체험|투어|드라이브)/g, "")
    .trim();
};

export const KakaoMapModal: React.FC<KakaoMapModalProps> = ({ isOpen, onClose, places }) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<any>(null);
  const placeCoordsRef = useRef<{ [key: number]: any }>({});
  
  // 지도 객체(마커, 오버레이, 폴리라인) 참조 관리
  const mapElementsRef = useRef<{
    markers: { index: number; day: number; marker: any; customOverlay: any; coords: any; name: string }[];
    polylines: { fromDay: number; toDay: number; polyline: any }[];
  }>({ markers: [], polylines: [] });

  const [isSdkLoaded, setIsSdkLoaded] = useState(false);
  const [mapError, setMapError] = useState<string | null>(null);
  const [selectedPlaceIdx, setSelectedPlaceIdx] = useState<number | null>(null);
  const [estimatedIndices, setEstimatedIndices] = useState<{ [key: number]: boolean }>({});

  // Day별 영역 상태 (접기/펼치기 & 체크박스 필터링)
  const [collapsedDays, setCollapsedDays] = useState<{ [key: number]: boolean }>({});
  const [selectedDays, setSelectedDays] = useState<number[]>([]);

  // places를 Day별로 그룹화
  const groupedPlaces = useMemo(() => {
    const groups: { [key: number]: { place: PlaceItem; originalIndex: number }[] } = {};
    places.forEach((place, index) => {
      const dayKey = place.day || 1;
      if (!groups[dayKey]) groups[dayKey] = [];
      groups[dayKey].push({ place, originalIndex: index });
    });
    return groups;
  }, [places]);

  // SDK 동적 로드
  useEffect(() => {
    if (!isOpen) return;

    setMapError(null);
    setSelectedPlaceIdx(null);
    setEstimatedIndices({});
    setSelectedDays([]);
    setCollapsedDays({});
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

  // 툴팁 HTML 템플릿 생성기
  const getShortHtml = (dayNum: number, isDimmed = false) => `
    <div style="
      display: inline-block;
      white-space: nowrap;
      padding: 3px 8px;
      font-size: 11px;
      font-weight: 800;
      color: ${isDimmed ? '#94a3b8' : '#0f172a'};
      background: #ffffff;
      border: 1.5px solid ${isDimmed ? '#cbd5e1' : '#2b82f6'};
      border-radius: 12px;
      box-shadow: 0 2px 6px rgba(0,0,0,0.15);
      line-height: 1.2;
      pointer-events: none;
      transform: translateY(6px);
      opacity: ${isDimmed ? '0.7' : '1'};
    ">
      Day ${dayNum}
    </div>`;

  const getFullHtml = (dayNum: number, name: string, isDimmed = false) => `
    <div style="
      display: inline-block;
      white-space: nowrap;
      padding: 5px 10px;
      font-size: 12px;
      font-weight: 700;
      color: ${isDimmed ? '#64748b' : '#1e293b'};
      background: #ffffff;
      border: 1.5px solid ${isDimmed ? '#94a3b8' : '#2b82f6'};
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.25);
      line-height: 1.3;
      pointer-events: none;
      transform: translateY(6px);
    ">
      Day ${dayNum} : ${name}
    </div>`;

  // 활성화된 필터 조건에 맞춰 카메라 위치 재설정하는 공통 함수
  const fitMapToActiveDays = () => {
    const map = mapInstanceRef.current;
    if (!map || mapElementsRef.current.markers.length === 0) return;

    const allDays = Object.keys(groupedPlaces).map(Number);
    const activeDays = selectedDays.length === 0 ? allDays : selectedDays;

    const bounds = new window.kakao.maps.LatLngBounds();
    const activeCoords: any[] = [];

    mapElementsRef.current.markers.forEach(({ day, marker, customOverlay, coords }) => {
      if (activeDays.includes(day)) {
        marker.setMap(map);
        customOverlay.setMap(map);
        bounds.extend(coords);
        activeCoords.push(coords);
      } else {
        marker.setMap(null);
        customOverlay.setMap(null);
      }
    });

    mapElementsRef.current.polylines.forEach(({ fromDay, toDay, polyline }) => {
      if (activeDays.includes(fromDay) && activeDays.includes(toDay)) {
        polyline.setMap(map);
      } else {
        polyline.setMap(null);
      }
    });

    if (activeCoords.length === 1) {
      map.setLevel(6, { animate: { duration: 800 } });
      map.panTo(activeCoords[0]);
    } else if (activeCoords.length > 1 && window.kakao) {
      const sw = bounds.getSouthWest();
      const ne = bounds.getNorthEast();

      const lat1 = sw.getLat();
      const lat2 = ne.getLat();
      const y1 = Math.log(Math.tan(Math.PI / 4 + (lat1 * Math.PI / 180) / 2));
      const y2 = Math.log(Math.tan(Math.PI / 4 + (lat2 * Math.PI / 180) / 2));
      const yMid = (y1 + y2) / 2;
      const centerLat = (2 * Math.atan(Math.exp(yMid)) - Math.PI / 2) * (180 / Math.PI);
      const centerLng = (sw.getLng() + ne.getLng()) / 2;
      const targetCenter = new window.kakao.maps.LatLng(centerLat, centerLng);

      const container = mapContainerRef.current;
      const widthPx = container ? container.clientWidth : 600;
      const heightPx = container ? container.clientHeight : 600;

      const latRad = centerLat * (Math.PI / 180);
      const metersPerLngDeg = 111320 * Math.cos(latRad);
      const widthMeters = Math.abs(ne.getLng() - sw.getLng()) * metersPerLngDeg;
      const heightMeters = Math.abs(ne.getLat() - sw.getLat()) * 111320;

      const reqMetersPxX = widthMeters / (widthPx * 0.75);
      const reqMetersPxY = heightMeters / (heightPx * 0.75);
      const reqMetersPx = Math.max(reqMetersPxX, reqMetersPxY, 0.25);

      let targetLevel = Math.ceil(Math.log2(reqMetersPx / 0.25)) + 1;
      targetLevel = Math.max(1, Math.min(14, targetLevel));

      map.panTo(targetCenter);
      setTimeout(() => {
        map.setLevel(targetLevel, {
          animate: { duration: 800 },
          anchor: targetCenter,
        });
      }, 100);
    }
  };

  // 지도 생성 및 마커/경로 렌더링 (비동기 취소 플래그 및 cleanup 처리)
  useEffect(() => {
    if (!isOpen || !isSdkLoaded || !window.kakao || !window.kakao.maps) return;

    let isCancelled = false;

    window.kakao.maps.load(async () => {
      if (isCancelled || !mapContainerRef.current) return;

      // 기존 남아있을 수 있는 마커/오버레이 안전하게 정리
      mapElementsRef.current.markers.forEach(({ marker, customOverlay }) => {
        if (marker) marker.setMap(null);
        if (customOverlay) customOverlay.setMap(null);
      });
      mapElementsRef.current.polylines.forEach(({ polyline }) => {
        if (polyline) polyline.setMap(null);
      });
      mapElementsRef.current = { markers: [], polylines: [] };

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
        if (!isCancelled && mapInstanceRef.current) {
          map.relayout();
          map.setCenter(defaultCenter);
        }
      }, 150);

      if (!places || places.length === 0) return;

      let lastValidCoords: any = defaultCenter;
      const pathInfoList: { coords: any; day: number }[] = [];

      const renderMarker = (coords: any, originalName: string, index: number, day = 1) => {
        if (isCancelled) return;

        placeCoordsRef.current[index] = coords;
        pathInfoList.push({ coords, day });

        lastValidCoords = coords;

        const blueMarkerImg = createMarkerImage('#2b82f6', '#1d4ed8');

        const markerOptions: any = {
          map: map,
          position: coords,
        };
        if (blueMarkerImg) {
          markerOptions.image = blueMarkerImg;
        }

        const marker = new window.kakao.maps.Marker(markerOptions);

        const customOverlay = new window.kakao.maps.CustomOverlay({
          map: map,
          position: coords,
          content: getShortHtml(day, false),
          zIndex: 1,
        });

        window.kakao.maps.event.addListener(marker, 'click', () => {
          handlePlaceClick(index);
        });

        window.kakao.maps.event.addListener(marker, 'mouseover', () => {
          customOverlay.setContent(getFullHtml(day, originalName, false));
          customOverlay.setZIndex(100);
        });

        window.kakao.maps.event.addListener(marker, 'mouseout', () => {
          const isDimmed = selectedPlaceIdx !== null && selectedPlaceIdx !== index;
          customOverlay.setContent(getShortHtml(day, isDimmed));
          customOverlay.setZIndex(isDimmed ? 1 : 10);
        });

        mapElementsRef.current.markers.push({ index, day, marker, customOverlay, coords, name: originalName });
        bounds.extend(coords);
      };

      const createFallbackMarker = (originalName: string, index: number) => {
        if (isCancelled) return;
        setEstimatedIndices((prev) => ({ ...prev, [index]: true }));
      };

      const findNearestPlace = (items: any[]): any => {
        if (!items || items.length === 0) return null;
        if (!lastValidCoords) return items[0];

        const refLat = lastValidCoords.getLat();
        const refLng = lastValidCoords.getLng();

        let minDistance = Infinity;
        let nearestItem = items[0];

        for (const item of items) {
          const dist = calculateDistance(refLat, refLng, parseFloat(item.y), parseFloat(item.x));
          if (dist < minDistance) {
            minDistance = dist;
            nearestItem = item;
          }
        }
        return nearestItem;
      };

      const searchKeywordAsync = (keyword: string): Promise<any[]> => {
        return new Promise((resolve) => {
          ps.keywordSearch(keyword, (data: any, status: any) => {
            if (status === window.kakao.maps.services.Status.OK && data.length > 0) resolve(data);
            else resolve([]);
          });
        });
      };

      const searchAddressAsync = (addr: string): Promise<any[]> => {
        return new Promise((resolve) => {
          geocoder.addressSearch(addr, (result: any, status: any) => {
            if (status === window.kakao.maps.services.Status.OK && result.length > 0) resolve(result);
            else resolve([]);
          });
        });
      };

      for (let index = 0; index < places.length; index++) {
        if (isCancelled) break;

        const place = places[index];
        const day = place.day || 1;

        if (place.lat && place.lng) {
          const coords = new window.kakao.maps.LatLng(place.lat, place.lng);
          renderMarker(coords, place.name, index, day);
          continue;
        }

        const query = sanitizePlaceName(place.name);
        const firstWord = query.split(" ")[0];

        if (!query) {
          createFallbackMarker(place.name, index);
          continue;
        }

        let searchResults: any[] = await searchKeywordAsync(`${detectedRegion} ${query}`);
        if (isCancelled) break;

        if (searchResults.length === 0) {
          searchResults = await searchKeywordAsync(query);
          if (isCancelled) break;
        }
        if (searchResults.length === 0 && firstWord) {
          searchResults = await searchKeywordAsync(`${detectedRegion} ${firstWord}`);
          if (isCancelled) break;
        }
        if (searchResults.length === 0) {
          searchResults = await searchAddressAsync(place.address || query);
          if (isCancelled) break;
        }

        if (searchResults.length > 0) {
          const bestMatch = findNearestPlace(searchResults);
          const coords = new window.kakao.maps.LatLng(bestMatch.y, bestMatch.x);
          renderMarker(coords, place.name, index, day);
        } else {
          createFallbackMarker(place.name, index);
        }
      }

      if (isCancelled) return;

      // 📍 이동 경로 화살표
      if (pathInfoList.length > 1) {
        for (let i = 0; i < pathInfoList.length - 1; i++) {
          const fromItem = pathInfoList[i];
          const toItem = pathInfoList[i + 1];

          const polyline = new window.kakao.maps.Polyline({
            map: map,
            path: [fromItem.coords, toItem.coords],
            strokeWeight: 4,
            strokeColor: "#FF6B35",
            strokeOpacity: 0.9,
            strokeStyle: "solid",
            endArrow: true,
          });

          mapElementsRef.current.polylines.push({
            fromDay: fromItem.day,
            toDay: toItem.day,
            polyline,
          });
        }
      }

      if (!bounds.isEmpty()) {
        map.setBounds(bounds);
      }
    });

    return () => {
      // 언마운트되거나 modal이 닫힐 때 비동기 로직 및 지도 객체 메모리 해제
      isCancelled = true;
      mapInstanceRef.current = null;

      mapElementsRef.current.markers.forEach(({ marker, customOverlay }) => {
        if (marker) marker.setMap(null);
        if (customOverlay) customOverlay.setMap(null);
      });
      mapElementsRef.current.polylines.forEach(({ polyline }) => {
        if (polyline) polyline.setMap(null);
      });
      mapElementsRef.current = { markers: [], polylines: [] };
    };
  }, [isOpen, isSdkLoaded, places]);

  // 클릭 선택 시 마커 색상 변경
  useEffect(() => {
    if (
      !window.kakao ||
      !window.kakao.maps ||
      typeof window.kakao.maps.Size !== 'function' ||
      !mapInstanceRef.current
    ) {
      return;
    }

    const blueMarkerImg = createMarkerImage('#2b82f6', '#1d4ed8');
    const grayMarkerImg = createMarkerImage('#94a3b8', '#64748b');

    mapElementsRef.current.markers.forEach(({ index, day, marker, customOverlay, name }) => {
      if (selectedPlaceIdx === null) {
        if (blueMarkerImg) marker.setImage(blueMarkerImg);
        marker.setZIndex(10);
        customOverlay.setContent(getShortHtml(day, false));
        customOverlay.setZIndex(10);
      } else if (selectedPlaceIdx === index) {
        if (blueMarkerImg) marker.setImage(blueMarkerImg);
        marker.setZIndex(100);
        customOverlay.setContent(getFullHtml(day, name, false));
        customOverlay.setZIndex(100);
      } else {
        if (grayMarkerImg) marker.setImage(grayMarkerImg);
        marker.setZIndex(1);
        customOverlay.setContent(getShortHtml(day, true));
        customOverlay.setZIndex(1);
      }
    });
  }, [selectedPlaceIdx]);

  // Day 체크박스 선택 변경 시 카메라 시점 재설정
  useEffect(() => {
    fitMapToActiveDays();
  }, [selectedDays, groupedPlaces]);

  const toggleDayFilter = (day: number) => {
    setSelectedDays((prev) =>
      prev.includes(day) ? prev.filter((d) => d !== day) : [...prev, day]
    );
  };

  const toggleDayCollapse = (day: number) => {
    setCollapsedDays((prev) => ({ ...prev, [day]: !prev[day] }));
  };

  // 장소 클릭 핸들러
  const handlePlaceClick = (index: number) => {
    const map = mapInstanceRef.current;
    if (!map) return;

    if (selectedPlaceIdx === index) {
      setSelectedPlaceIdx(null);
      fitMapToActiveDays();
    } else {
      const isFirstSelection = selectedPlaceIdx === null;
      setSelectedPlaceIdx(index);

      const coords = placeCoordsRef.current[index];

      if (coords) {
        map.panTo(coords);

        if (isFirstSelection) {
          const currentLevel = map.getLevel();
          const targetLevel = Math.max(1, currentLevel - 1);

          if (currentLevel > targetLevel) {
            setTimeout(() => {
              if (mapInstanceRef.current) {
                map.setLevel(targetLevel, { animate: { duration: 500 } });
              }
            }, 150);
          }
        }
      }
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white w-full max-w-6xl h-[720px] rounded-3xl p-6 flex flex-col shadow-2xl relative">
        {/* 상단 헤더 */}
        <div className="flex items-center justify-between mb-4 pb-3 border-b border-slate-100 shrink-0">
          <div>
            <h2 className="text-xl font-bold text-slate-800">🗺️ AI 추천 여행 경로 지도</h2>
            <p className="text-xs text-slate-400 mt-0.5">목록을 클릭하면 해당 위치로 지도가 이동하며, Day 체크박스로 경로를 선택 감상할 수 있습니다.</p>
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
          {/* 좌측 카카오 지도 */}
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

          {/* 우측 추천 장소 목록 */}
          <div className="w-1/3 h-full bg-slate-50 rounded-2xl border border-slate-200 p-4 flex flex-col overflow-hidden">
            <div className="flex items-center justify-between mb-3 shrink-0">
              <h3 className="font-bold text-slate-800 text-sm">📍 추천 장소 목록</h3>
              <div className="flex items-center gap-2">
                {selectedDays.length > 0 && (
                  <button
                    onClick={() => setSelectedDays([])}
                    className="text-[11px] font-bold text-sky-600 hover:underline"
                  >
                    전체 보기
                  </button>
                )}
                <span className="px-2 py-0.5 bg-sky-100 text-sky-700 text-xs font-bold rounded-full">
                  총 {places.length}곳
                </span>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto space-y-3 pr-1">
              {Object.keys(groupedPlaces).map((dayStr) => {
                const dayNum = Number(dayStr);
                const dayItems = groupedPlaces[dayNum];
                const isCollapsed = collapsedDays[dayNum];
                const isChecked = selectedDays.includes(dayNum);

                return (
                  <div key={`day-group-${dayNum}`} className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
                    {/* Day 헤더 */}
                    <div className="p-3 bg-slate-100/70 border-b border-slate-200/60 flex items-center justify-between select-none">
                      <label className="flex items-center gap-2.5 cursor-pointer font-bold text-sm text-slate-800">
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={() => toggleDayFilter(dayNum)}
                          className="w-4 h-4 rounded text-sky-600 focus:ring-sky-500 cursor-pointer accent-[#FF6B35]"
                        />
                        <span>Day {dayNum}</span>
                        <span className="text-xs font-medium text-slate-400">({dayItems.length}곳)</span>
                      </label>

                      <button
                        type="button"
                        onClick={() => toggleDayCollapse(dayNum)}
                        className="p-1 hover:bg-slate-200/80 rounded-lg text-slate-500 transition-all text-xs font-bold"
                      >
                        {isCollapsed ? "▼" : "▲"}
                      </button>
                    </div>

                    {/* Day 장소 아이템 목록 */}
                    {!isCollapsed && (
                      <div className="p-2 space-y-2 bg-white">
                        {dayItems.map(({ place, originalIndex }) => {
                          const isSelected = selectedPlaceIdx === originalIndex;
                          const isEstimated = estimatedIndices[originalIndex];

                          return (
                            <div
                              key={`${dayNum}-${place.name}-${originalIndex}`}
                              onClick={() => handlePlaceClick(originalIndex)}
                              className={`p-3 rounded-xl border transition-all select-none cursor-pointer ${
                                isSelected
                                  ? "bg-sky-50 border-sky-500 shadow-md ring-2 ring-sky-200"
                                  : isEstimated
                                  ? "bg-amber-50/60 border-amber-200 hover:border-amber-400"
                                  : "bg-white border-slate-100 shadow-sm hover:border-sky-300 hover:shadow"
                              }`}
                            >
                              <div className="flex items-center justify-between gap-2">
                                <h4 className={`font-bold text-xs truncate ${isSelected ? "text-sky-900" : "text-slate-800"}`}>
                                  {place.name}
                                </h4>

                                {isEstimated && (
                                  <span className="text-[9px] font-bold text-amber-600 shrink-0 bg-amber-100/80 px-1.5 py-0.5 rounded-full">
                                    🍊 추정
                                  </span>
                                )}
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    )}
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