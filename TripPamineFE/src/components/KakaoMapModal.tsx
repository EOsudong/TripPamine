import React, { useEffect, useRef, useState, useMemo } from 'react';

declare global {
  interface Window {
    kakao: any;
  }
}

export interface PlaceItem {
  name: string;       // 장소명
  address?: string;   // 주소
  lat?: number;       // 위도
  lng?: number;       // 경도
  day?: number;       // 여행 일차
}

interface KakaoMapModalProps {
  isOpen: boolean;
  onClose: () => void;
  places: PlaceItem[];
  onSavePlaces?: (updatedPlaces: PlaceItem[]) => void;
}

const KAKAO_JS_KEY = "b33fca24ae4855453ab831d8b0208dc9";

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

const sanitizePlaceName = (rawName: string): string => {
  if (!rawName) return "";
  if (/(차량\s*이용|대중교통|도보\s*이동|자차\s*이용)/g.test(rawName)) return "";

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

export const KakaoMapModal: React.FC<KakaoMapModalProps> = ({ isOpen, onClose, places, onSavePlaces }) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<any>(null);
  const placeCoordsRef = useRef<{ [key: number]: any }>({});
  
  const [localPlaces, setLocalPlaces] = useState<PlaceItem[]>([]);
  const [isEditing, setIsEditing] = useState(false);

  const mapElementsRef = useRef<{
    markers: { index: number; day: number; marker: any; customOverlay: any; coords: any; name: string }[];
    polylines: { fromDay: number; toDay: number; polyline: any }[];
  }>({ markers: [], polylines: [] });

  const [isSdkLoaded, setIsSdkLoaded] = useState(false);
  const [mapError, setMapError] = useState<string | null>(null);
  const [selectedPlaceIdx, setSelectedPlaceIdx] = useState<number | null>(null);

  const [collapsedDays, setCollapsedDays] = useState<{ [key: number]: boolean }>({});
  const [selectedDays, setSelectedDays] = useState<number[]>([]);

  const [searchKeyword, setSearchKeyword] = useState("");
  const [searchResults, setSearchResults] = useState<any[]>([]);
  const [isSearching, setIsSearching] = useState(false);

  const [draggedIdx, setDraggedIdx] = useState<number | null>(null);
  const [dropTarget, setDropTarget] = useState<{ index: number; position: 'above' | 'swap' | 'below' } | null>(null);

  useEffect(() => {
    if (isOpen) {
      setLocalPlaces(places || []);
      setIsEditing(false);
      setSearchResults([]);
      setSearchKeyword("");
    }
  }, [isOpen, places]);

  const groupedPlaces = useMemo(() => {
    const groups: { [key: number]: { place: PlaceItem; originalIndex: number }[] } = {};
    localPlaces.forEach((place, index) => {
      const dayKey = place.day || 1;
      if (!groups[dayKey]) groups[dayKey] = [];
      groups[dayKey].push({ place, originalIndex: index });
    });
    return groups;
  }, [localPlaces]);

  useEffect(() => {
    if (!isOpen) return;

    setMapError(null);
    setSelectedPlaceIdx(null);
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
      setMapError("카카오 지도 SDK를 불러올 수 없습니다.");
    };

    script.addEventListener("load", handleLoad);
    script.addEventListener("error", handleError);

    return () => {
      script.removeEventListener("load", handleLoad);
      script.removeEventListener("error", handleError);
    };
  }, [isOpen]);

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
      map.setBounds(bounds);
    }
  };

  useEffect(() => {
    if (!isOpen || !isSdkLoaded || !window.kakao || !window.kakao.maps) return;

    let isCancelled = false;

    window.kakao.maps.load(async () => {
      if (isCancelled || !mapContainerRef.current) return;

      mapElementsRef.current.markers.forEach(({ marker, customOverlay }) => {
        if (marker) marker.setMap(null);
        if (customOverlay) customOverlay.setMap(null);
      });
      mapElementsRef.current.polylines.forEach(({ polyline }) => {
        if (polyline) polyline.setMap(null);
      });
      mapElementsRef.current = { markers: [], polylines: [] };

      const { center: detectedCenter } = detectTripRegion(localPlaces);
      const defaultCenter = new window.kakao.maps.LatLng(detectedCenter.lat, detectedCenter.lng);

      if (!mapInstanceRef.current) {
        mapInstanceRef.current = new window.kakao.maps.Map(mapContainerRef.current, {
          center: defaultCenter,
          level: 9,
        });
      }
      const map = mapInstanceRef.current;

      const ps = new window.kakao.maps.services.Places();
      const geocoder = new window.kakao.maps.services.Geocoder();
      const bounds = new window.kakao.maps.LatLngBounds();

      if (!localPlaces || localPlaces.length === 0) return;

      let lastValidCoords: any = defaultCenter;
      const pathInfoList: { coords: any; day: number }[] = [];

      const renderMarker = (coords: any, originalName: string, index: number, day = 1) => {
        if (isCancelled) return;

        placeCoordsRef.current[index] = coords;
        pathInfoList.push({ coords, day });
        lastValidCoords = coords;

        const blueMarkerImg = createMarkerImage('#2b82f6', '#1d4ed8');
        const markerOptions: any = { map: map, position: coords };
        if (blueMarkerImg) markerOptions.image = blueMarkerImg;

        const marker = new window.kakao.maps.Marker(markerOptions);
        const customOverlay = new window.kakao.maps.CustomOverlay({
          map: map,
          position: coords,
          content: getShortHtml(day, false),
          zIndex: 1,
        });

        window.kakao.maps.event.addListener(marker, 'click', () => handlePlaceClick(index));
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

      for (let index = 0; index < localPlaces.length; index++) {
        if (isCancelled) break;

        const place = localPlaces[index];
        const day = place.day || 1;

        if (place.lat && place.lng) {
          const coords = new window.kakao.maps.LatLng(place.lat, place.lng);
          renderMarker(coords, place.name, index, day);
          continue;
        }

        const query = sanitizePlaceName(place.name);
        if (!query) continue;

        let searchResults: any[] = await searchKeywordAsync(query);
        if (searchResults.length === 0 && place.address) {
          searchResults = await searchAddressAsync(place.address);
        }

        if (searchResults.length > 0) {
          const bestMatch = findNearestPlace(searchResults);
          const coords = new window.kakao.maps.LatLng(bestMatch.y, bestMatch.x);
          renderMarker(coords, place.name, index, day);
        }
      }

      if (isCancelled) return;

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
      isCancelled = true;
      // 🔑 핵심 수정: 언마운트/닫힘 시 지도 인스턴스 초기화
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
  }, [isOpen, isSdkLoaded, localPlaces]);

  const handleSearchPlaces = (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchKeyword.trim() || !window.kakao || !window.kakao.maps) return;

    setIsSearching(true);
    const ps = new window.kakao.maps.services.Places();
    ps.keywordSearch(searchKeyword, (data: any, status: any) => {
      setIsSearching(false);
      if (status === window.kakao.maps.services.Status.OK) {
        setSearchResults(data);
      } else {
        setSearchResults([]);
      }
    });
  };

  const handleAddPlaceToRoute = (searchItem: any, targetDay = 1) => {
    const newPlace: PlaceItem = {
      name: searchItem.place_name,
      address: searchItem.road_address_name || searchItem.address_name,
      lat: parseFloat(searchItem.y),
      lng: parseFloat(searchItem.x),
      day: targetDay,
    };

    setLocalPlaces((prev) => [...prev, newPlace]);
  };

  const handleDeletePlace = (indexToDelete: number) => {
    setLocalPlaces((prev) => prev.filter((_, idx) => idx !== indexToDelete));
  };

  const handleDragStart = (e: React.DragEvent, index: number) => {
    if (!isEditing) return;
    setDraggedIdx(index);
    e.dataTransfer.effectAllowed = "move";
  };

  const handleDragOver = (e: React.DragEvent, targetIndex: number) => {
    if (!isEditing || draggedIdx === null || draggedIdx === targetIndex) return;
    e.preventDefault();

    const rect = e.currentTarget.getBoundingClientRect();
    const offsetY = e.clientY - rect.top;
    const height = rect.height;
    const ratio = offsetY / height;

    let position: 'above' | 'swap' | 'below' = 'swap';
    if (ratio < 0.25) position = 'above';
    else if (ratio > 0.75) position = 'below';

    setDropTarget({ index: targetIndex, position });
  };

  const handleDrop = (e: React.DragEvent, targetIndex: number, targetDay: number) => {
    if (!isEditing || draggedIdx === null || dropTarget === null) return;
    e.preventDefault();

    const updatedPlaces = [...localPlaces];
    const draggedItem = { ...updatedPlaces[draggedIdx], day: targetDay };

    if (dropTarget.position === 'swap') {
      const targetItem = { ...updatedPlaces[targetIndex], day: updatedPlaces[draggedIdx].day || 1 };
      updatedPlaces[draggedIdx] = targetItem;
      updatedPlaces[targetIndex] = draggedItem;
    } else {
      updatedPlaces.splice(draggedIdx, 1);
      let insertIndex = targetIndex;
      if (draggedIdx < targetIndex) insertIndex -= 1;
      if (dropTarget.position === 'below') insertIndex += 1;

      updatedPlaces.splice(Math.max(0, insertIndex), 0, draggedItem);
    }

    setLocalPlaces(updatedPlaces);
    setDraggedIdx(null);
    setDropTarget(null);
  };

  const handleDragEnd = () => {
    setDraggedIdx(null);
    setDropTarget(null);
  };

  const handleSaveEdit = () => {
    const isConfirmed = window.confirm("변경사항을 저장하시겠습니까?");
    if (isConfirmed) {
      if (onSavePlaces) {
        onSavePlaces(localPlaces);
      }
      setIsEditing(false);
    }
  };
  
  const handlePlaceClick = (index: number) => {
    const map = mapInstanceRef.current;
    if (!map) return;

    if (selectedPlaceIdx === index) {
      setSelectedPlaceIdx(null);
      fitMapToActiveDays();
    } else {
      setSelectedPlaceIdx(index);
      const coords = placeCoordsRef.current[index];
      if (coords) {
        map.panTo(coords);
      }
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white w-full max-w-6xl h-[760px] rounded-3xl p-6 flex flex-col shadow-2xl relative">
        <div className="flex items-center justify-between mb-4 pb-3 border-b border-slate-100 shrink-0">
          <div>
            <h2 className="text-xl font-bold text-slate-800 flex items-center gap-2">
              🗺️ {isEditing ? "여행 경로 편집 중..." : "AI 추천 여행 경로 지도"}
            </h2>
            <p className="text-xs text-slate-400 mt-0.5">
              {isEditing
                ? "드래그 앤 드롭으로 순서를 바꾸거나 목적지를 삭제/추가할 수 있습니다."
                : "목록을 클릭하면 해당 위치로 지도가 이동하며, 경로를 한눈에 확인할 수 있습니다."}
            </p>
          </div>

          <div className="flex items-center gap-2">
            {!isEditing ? (
              <>
                <button
                  type="button"
                  onClick={() => setIsEditing(true)}
                  className="px-4 py-2 bg-sky-500 hover:bg-sky-600 text-white text-sm font-bold rounded-xl transition-all shadow-md shadow-sky-200"
                >
                  ✏️ 여행 경로 편집하기
                </button>
                <button
                  onClick={onClose}
                  type="button"
                  className="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 text-sm font-bold rounded-xl transition-all"
                >
                  닫기
                </button>
              </>
            ) : (
              <>
                <button
                  type="button"
                  onClick={() => {
                    setLocalPlaces(places);
                    setIsEditing(false);
                  }}
                  className="px-4 py-2 bg-slate-200 hover:bg-slate-300 text-slate-700 text-sm font-bold rounded-xl transition-all"
                >
                  취소
                </button>
                <button
                  type="button"
                  onClick={handleSaveEdit}
                  className="px-4 py-2 bg-emerald-500 hover:bg-emerald-600 text-white text-sm font-bold rounded-xl transition-all shadow-md shadow-emerald-200"
                >
                  💾 경로 저장하기
                </button>
              </>
            )}
          </div>
        </div>

        <div className="flex-1 flex gap-4 min-h-0">
          <div className="w-2/3 h-full rounded-2xl bg-slate-100 overflow-hidden relative border border-slate-200 flex flex-col">
            {isEditing && (
              <div className="absolute top-3 left-3 right-3 z-20 bg-white/90 backdrop-blur-md rounded-2xl p-3 shadow-lg border border-slate-200">
                <form onSubmit={handleSearchPlaces} className="flex gap-2">
                  <input
                    type="text"
                    value={searchKeyword}
                    onChange={(e) => setSearchKeyword(e.target.value)}
                    placeholder="추가할 장소를 검색하세요 (예: 해운대 해수욕장)"
                    className="flex-1 px-3 py-1.5 text-xs rounded-xl border border-slate-300 focus:outline-none focus:ring-2 focus:ring-sky-500"
                  />
                  <button
                    type="submit"
                    className="px-3 py-1.5 bg-sky-500 text-white font-bold text-xs rounded-xl hover:bg-sky-600 shrink-0"
                  >
                    {isSearching ? "검색 중..." : "검색"}
                  </button>
                </form>

                {searchResults.length > 0 && (
                  <div className="mt-2 max-h-40 overflow-y-auto divide-y divide-slate-100 bg-white rounded-xl border border-slate-200">
                    {searchResults.map((result) => (
                      <div key={result.id} className="p-2 flex items-center justify-between text-xs hover:bg-sky-50">
                        <div className="truncate pr-2">
                          <p className="font-bold text-slate-800">{result.place_name}</p>
                          <p className="text-[10px] text-slate-400 truncate">{result.road_address_name || result.address_name}</p>
                        </div>
                        <div className="flex gap-1 shrink-0">
                          {Object.keys(groupedPlaces).map((dayStr) => (
                            <button
                              key={`add-day-${dayStr}`}
                              type="button"
                              onClick={() => handleAddPlaceToRoute(result, Number(dayStr))}
                              className="px-2 py-0.5 bg-sky-100 hover:bg-sky-200 text-sky-700 font-bold text-[10px] rounded-lg"
                            >
                              + Day {dayStr}
                            </button>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            <div className="w-full h-full relative">
              {mapError ? (
                <div className="p-6 text-center text-red-500 text-sm h-full flex flex-col justify-center items-center">
                  <p className="font-bold mb-2">⚠️ 지도 로드 실패</p>
                  <p className="text-xs leading-relaxed text-slate-600">{mapError}</p>
                </div>
              ) : !isSdkLoaded ? (
                <div className="text-sm font-semibold text-slate-500 animate-pulse h-full flex justify-center items-center">
                  지도를 불러오는 중입니다...
                </div>
              ) : (
                <div ref={mapContainerRef} className="w-full h-full" />
              )}
            </div>
          </div>

          <div className="w-1/3 h-full bg-slate-50 rounded-2xl border border-slate-200 p-4 flex flex-col overflow-hidden">
            <div className="flex items-center justify-between mb-3 shrink-0">
              <h3 className="font-bold text-slate-800 text-sm">📍 추천 장소 목록</h3>
              <span className="px-2 py-0.5 bg-sky-100 text-sky-700 text-xs font-bold rounded-full">
                총 {localPlaces.length}곳
              </span>
            </div>

            <div className="flex-1 overflow-y-auto space-y-3 pr-1">
              {Object.keys(groupedPlaces).map((dayStr) => {
                const dayNum = Number(dayStr);
                const dayItems = groupedPlaces[dayNum];
                const isCollapsed = collapsedDays[dayNum];

                return (
                  <div key={`day-group-${dayNum}`} className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
                    <div className="p-3 bg-slate-100/70 border-b border-slate-200/60 flex items-center justify-between select-none">
                      <div className="flex items-center gap-2.5 font-bold text-sm text-slate-800">
                        <span>Day {dayNum}</span>
                        <span className="text-xs font-medium text-slate-400">({dayItems.length}곳)</span>
                      </div>

                      <button
                        type="button"
                        onClick={() => setCollapsedDays((prev) => ({ ...prev, [dayNum]: !prev[dayNum] }))}
                        className="p-1 hover:bg-slate-200/80 rounded-lg text-slate-500 transition-all text-xs font-bold"
                      >
                        {isCollapsed ? "▼" : "▲"}
                      </button>
                    </div>

                    {!isCollapsed && (
                      <div className="p-2 space-y-2 bg-white">
                        {dayItems.map(({ place, originalIndex }) => {
                          const isSelected = selectedPlaceIdx === originalIndex;
                          const isTarget = dropTarget?.index === originalIndex;
                          const pos = isTarget ? dropTarget?.position : null;

                          return (
                            <div
                              key={`${dayNum}-${place.name}-${originalIndex}`}
                              draggable={isEditing}
                              onDragStart={(e) => handleDragStart(e, originalIndex)}
                              onDragOver={(e) => handleDragOver(e, originalIndex)}
                              onDrop={(e) => handleDrop(e, originalIndex, dayNum)}
                              onDragEnd={handleDragEnd}
                              onClick={() => !isEditing && handlePlaceClick(originalIndex)}
                              className={`p-3 rounded-xl border transition-all select-none relative ${
                                isEditing ? "cursor-grab active:cursor-grabbing" : "cursor-pointer"
                              } ${
                                isSelected
                                  ? "bg-sky-50 border-sky-500 shadow-md ring-2 ring-sky-200"
                                  : "bg-white border-slate-100 shadow-sm hover:border-sky-300"
                              } ${
                                isEditing && isTarget && pos === 'swap' ? "bg-amber-100 border-amber-400 ring-2 ring-amber-300" : ""
                              }`}
                            >
                              {isEditing && isTarget && pos === 'above' && (
                                <div className="absolute top-0 left-0 right-0 h-1 bg-sky-500 rounded-t-xl" />
                              )}
                              {isEditing && isTarget && pos === 'below' && (
                                <div className="absolute bottom-0 left-0 right-0 h-1 bg-sky-500 rounded-b-xl" />
                              )}

                              <div className="flex items-center justify-between gap-2">
                                <div className="flex items-center gap-2 truncate">
                                  {isEditing && <span className="text-slate-400 font-bold text-xs">≡</span>}
                                  <h4 className={`font-bold text-xs truncate ${isSelected ? "text-sky-900" : "text-slate-800"}`}>
                                    {place.name}
                                  </h4>
                                </div>

                                {isEditing && (
                                  <button
                                    type="button"
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      handleDeletePlace(originalIndex);
                                    }}
                                    className="text-xs text-red-500 hover:bg-red-50 p-1 rounded-lg shrink-0"
                                  >
                                    🗑️
                                  </button>
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