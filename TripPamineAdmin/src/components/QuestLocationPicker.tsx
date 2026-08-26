import { useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent } from "react";
import type { AdminQuest } from "../types/api";

interface QuestLocationPickerProps {
  latitude: string;
  longitude: string;
  radius: string;
  quests: AdminQuest[];
  excludeQuestId?: number;
  onCoordinateChange: (latitude: number, longitude: number) => void;
  onRadiusChange: (radius: string) => void;
  onPlaceNameSuggestion: (placeName: string) => void;
}

interface KakaoPlaceResult {
  id: string;
  place_name: string;
  address_name: string;
  road_address_name: string;
  x: string;
  y: string;
}

const SEOUL_CENTER = { latitude: 37.5665, longitude: 126.978 };
const RADIUS_PRESETS = [50, 100, 200];
let kakaoSdkPromise: Promise<any> | null = null;

function loadKakaoMapSdk(): Promise<any> {
  if (window.kakao?.maps?.services) {
    return Promise.resolve(window.kakao);
  }

  if (kakaoSdkPromise) return kakaoSdkPromise;

  const javascriptKey = import.meta.env.VITE_KAKAO_MAP_JAVASCRIPT_KEY;
  if (!javascriptKey) {
    return Promise.reject(
      new Error("VITE_KAKAO_MAP_JAVASCRIPT_KEY 환경변수를 설정해주세요."),
    );
  }

  kakaoSdkPromise = new Promise((resolve, reject) => {
    const scriptId = "trip-pamine-admin-kakao-map-sdk";
    let script = document.getElementById(scriptId) as HTMLScriptElement | null;

    const finishLoading = () => {
      if (!window.kakao?.maps?.load) {
        reject(new Error("Kakao 지도 SDK 초기화에 실패했습니다."));
        return;
      }

      window.kakao.maps.load(() => {
        if (!window.kakao?.maps?.services) {
          reject(
            new Error("Kakao 장소 검색 라이브러리를 불러오지 못했습니다."),
          );
          return;
        }
        resolve(window.kakao);
      });
    };

    if (script) {
      if (window.kakao?.maps) {
        finishLoading();
      } else {
        script.addEventListener("load", finishLoading, { once: true });
        script.addEventListener(
          "error",
          () => reject(new Error("Kakao 지도 SDK 요청에 실패했습니다.")),
          { once: true },
        );
      }
      return;
    }

    script = document.createElement("script");
    script.id = scriptId;
    script.async = true;
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(javascriptKey)}&libraries=services&autoload=false`;
    script.addEventListener("load", finishLoading, { once: true });
    script.addEventListener(
      "error",
      () => reject(new Error("Kakao 지도 SDK 요청에 실패했습니다.")),
      { once: true },
    );
    document.head.appendChild(script);
  });

  return kakaoSdkPromise;
}

function toNumber(value: string): number | null {
  if (!value.trim()) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function distanceMeters(
  latitude1: number,
  longitude1: number,
  latitude2: number,
  longitude2: number,
) {
  const earthRadius = 6_371_000;
  const latitudeDelta = ((latitude2 - latitude1) * Math.PI) / 180;
  const longitudeDelta = ((longitude2 - longitude1) * Math.PI) / 180;
  const value =
    Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos((latitude1 * Math.PI) / 180) *
      Math.cos((latitude2 * Math.PI) / 180) *
      Math.sin(longitudeDelta / 2) ** 2;

  return (
    earthRadius *
    2 *
    Math.atan2(Math.sqrt(value), Math.sqrt(Math.max(0, 1 - value)))
  );
}

function formatDistance(distance: number) {
  return distance >= 1000
    ? `${(distance / 1000).toFixed(1)}km`
    : `${Math.round(distance)}m`;
}

export default function QuestLocationPicker({
  latitude,
  longitude,
  radius,
  quests,
  excludeQuestId,
  onCoordinateChange,
  onRadiusChange,
  onPlaceNameSuggestion,
}: QuestLocationPickerProps) {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const markerRef = useRef<any>(null);
  const circleRef = useRef<any>(null);
  const geocoderRef = useRef<any>(null);
  const coordinateChangeRef = useRef(onCoordinateChange);

  const [sdkReady, setSdkReady] = useState(false);
  const [mapError, setMapError] = useState<string | null>(null);
  const [keyword, setKeyword] = useState("");
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [places, setPlaces] = useState<KakaoPlaceResult[]>([]);
  const [selectedAddress, setSelectedAddress] = useState<string | null>(null);
  const [locating, setLocating] = useState(false);

  const latitudeNumber = toNumber(latitude);
  const longitudeNumber = toNumber(longitude);
  const radiusNumber = Math.max(1, toNumber(radius) ?? 100);

  useEffect(() => {
    coordinateChangeRef.current = onCoordinateChange;
  }, [onCoordinateChange]);

  useEffect(() => {
    let disposed = false;
    loadKakaoMapSdk()
      .then(() => {
        if (!disposed) setSdkReady(true);
      })
      .catch((error: unknown) => {
        if (!disposed) {
          setMapError(
            error instanceof Error
              ? error.message
              : "Kakao 지도 SDK를 불러오지 못했습니다.",
          );
        }
      });

    return () => {
      disposed = true;
    };
  }, []);

  useEffect(() => {
    if (!sdkReady || !mapContainerRef.current || mapRef.current) return;

    const kakao = window.kakao;
    const initialPosition = new kakao.maps.LatLng(
      latitudeNumber ?? SEOUL_CENTER.latitude,
      longitudeNumber ?? SEOUL_CENTER.longitude,
    );
    const map = new kakao.maps.Map(mapContainerRef.current, {
      center: initialPosition,
      level: 4,
    });
    const marker = new kakao.maps.Marker({
      map,
      position: initialPosition,
      draggable: true,
    });
    const circle = new kakao.maps.Circle({
      map,
      center: initialPosition,
      radius: radiusNumber,
      strokeWeight: 2,
      strokeColor: "#4f46e5",
      strokeOpacity: 0.9,
      strokeStyle: "dashed",
      fillColor: "#818cf8",
      fillOpacity: 0.18,
    });

    mapRef.current = map;
    markerRef.current = marker;
    circleRef.current = circle;
    geocoderRef.current = new kakao.maps.services.Geocoder();

    const updateAddress = (position: any) => {
      geocoderRef.current?.coord2Address(
        position.getLng(),
        position.getLat(),
        (result: any[], status: string) => {
          if (status !== kakao.maps.services.Status.OK || !result[0]) {
            setSelectedAddress("지도에서 직접 선택한 위치");
            return;
          }
          const address =
            result[0].road_address?.address_name ??
            result[0].address?.address_name ??
            "지도에서 직접 선택한 위치";
          setSelectedAddress(address);
        },
      );
    };

    const applyPosition = (position: any) => {
      marker.setPosition(position);
      circle.setPosition(position);
      coordinateChangeRef.current(position.getLat(), position.getLng());
      updateAddress(position);
    };

    const handleMapClick = (mouseEvent: any) =>
      applyPosition(mouseEvent.latLng);
    const handleMarkerDragEnd = () => applyPosition(marker.getPosition());

    kakao.maps.event.addListener(map, "click", handleMapClick);
    kakao.maps.event.addListener(marker, "dragend", handleMarkerDragEnd);

    const relayoutTimer = window.setTimeout(() => {
      map.relayout();
      map.setCenter(initialPosition);
    }, 100);

    return () => {
      window.clearTimeout(relayoutTimer);
      kakao.maps.event.removeListener(map, "click", handleMapClick);
      kakao.maps.event.removeListener(marker, "dragend", handleMarkerDragEnd);
      marker.setMap(null);
      circle.setMap(null);
      mapRef.current = null;
      markerRef.current = null;
      circleRef.current = null;
      geocoderRef.current = null;
    };
    // 지도 객체는 최초 한 번만 생성하고 좌표·반경 갱신은 아래 effect에서 처리한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sdkReady]);

  useEffect(() => {
    if (
      !sdkReady ||
      latitudeNumber == null ||
      longitudeNumber == null ||
      !mapRef.current ||
      !markerRef.current ||
      !circleRef.current
    ) {
      return;
    }

    const position = new window.kakao.maps.LatLng(
      latitudeNumber,
      longitudeNumber,
    );
    markerRef.current.setPosition(position);
    circleRef.current.setPosition(position);
    mapRef.current.panTo(position);
  }, [latitudeNumber, longitudeNumber, sdkReady]);

  useEffect(() => {
    circleRef.current?.setRadius(radiusNumber);
  }, [radiusNumber]);

  const nearbyQuests = useMemo(() => {
    if (latitudeNumber == null || longitudeNumber == null) return [];

    return quests
      .filter((quest) => quest.questId !== excludeQuestId)
      .map((quest) => ({
        quest,
        distance: distanceMeters(
          latitudeNumber,
          longitudeNumber,
          quest.targetLat,
          quest.targetLng,
        ),
      }))
      .filter(
        ({ quest, distance }) =>
          distance <= Math.max(radiusNumber, quest.clearRadius, 50),
      )
      .sort((left, right) => left.distance - right.distance)
      .slice(0, 3);
  }, [excludeQuestId, latitudeNumber, longitudeNumber, quests, radiusNumber]);

  function handleSearch(event: FormEvent) {
    event.preventDefault();
    const trimmedKeyword = keyword.trim();
    if (!trimmedKeyword) {
      setSearchError("검색할 장소명을 입력해주세요.");
      return;
    }
    if (!sdkReady) {
      setSearchError("지도를 불러오는 중입니다. 잠시 후 다시 시도해주세요.");
      return;
    }

    setSearching(true);
    setSearchError(null);
    const placesService = new window.kakao.maps.services.Places();
    placesService.keywordSearch(
      trimmedKeyword,
      (result: KakaoPlaceResult[], status: string) => {
        setSearching(false);
        if (status === window.kakao.maps.services.Status.OK) {
          setPlaces(result.slice(0, 8));
          return;
        }
        setPlaces([]);
        setSearchError(
          "검색 결과가 없습니다. 더 구체적인 장소명을 입력해주세요.",
        );
      },
    );
  }

  function handlePlaceSelect(place: KakaoPlaceResult) {
    const nextLatitude = Number(place.y);
    const nextLongitude = Number(place.x);
    if (!Number.isFinite(nextLatitude) || !Number.isFinite(nextLongitude))
      return;

    coordinateChangeRef.current(nextLatitude, nextLongitude);
    setSelectedAddress(place.road_address_name || place.address_name);
    onPlaceNameSuggestion(place.place_name);
    setPlaces([]);
    setKeyword(place.place_name);

    const position = new window.kakao.maps.LatLng(nextLatitude, nextLongitude);
    markerRef.current?.setPosition(position);
    circleRef.current?.setPosition(position);
    mapRef.current?.setLevel(3);
    mapRef.current?.panTo(position);
  }

  function handleUseCurrentLocation() {
    if (!navigator.geolocation) {
      setSearchError("현재 브라우저에서 위치 기능을 지원하지 않습니다.");
      return;
    }

    setLocating(true);
    setSearchError(null);
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setLocating(false);
        coordinateChangeRef.current(coords.latitude, coords.longitude);
        setSelectedAddress(
          `현재 위치 · GPS 오차 약 ${Math.round(coords.accuracy)}m`,
        );
      },
      () => {
        setLocating(false);
        setSearchError(
          "현재 위치를 가져오지 못했습니다. 위치 권한을 확인해주세요.",
        );
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 },
    );
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-sm font-bold text-slate-800">목표 위치 선택</p>
          <p className="text-xs text-slate-500 mt-0.5">
            장소를 검색한 뒤 지도 클릭 또는 마커 드래그로 입구 위치를
            보정하세요.
          </p>
        </div>
        <button
          type="button"
          onClick={handleUseCurrentLocation}
          disabled={locating}
          className="shrink-0 px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:text-slate-300"
        >
          {locating ? "위치 확인 중..." : "현재 위치"}
        </button>
      </div>

      <form onSubmit={handleSearch} className="flex gap-2">
        <input
          type="search"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          placeholder="예) 경복궁 광화문, 보령 머드광장"
          className="min-w-0 flex-1 px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm"
        />
        <button
          type="submit"
          disabled={searching}
          className="px-4 py-2.5 rounded-lg bg-slate-800 text-white text-sm font-semibold hover:bg-slate-700 disabled:bg-slate-300"
        >
          {searching ? "검색 중" : "검색"}
        </button>
      </form>

      {places.length > 0 && (
        <div className="max-h-48 overflow-y-auto rounded-xl border border-slate-200 divide-y divide-slate-100 bg-white shadow-sm">
          {places.map((place) => (
            <button
              key={place.id}
              type="button"
              onClick={() => handlePlaceSelect(place)}
              className="block w-full px-3.5 py-3 text-left hover:bg-indigo-50 transition-colors"
            >
              <span className="block text-sm font-semibold text-slate-800">
                {place.place_name}
              </span>
              <span className="block text-xs text-slate-500 mt-0.5">
                {place.road_address_name || place.address_name}
              </span>
            </button>
          ))}
        </div>
      )}

      {(searchError || mapError) && (
        <p className="text-xs text-red-600 rounded-lg bg-red-50 border border-red-100 px-3 py-2">
          {searchError || mapError}
        </p>
      )}

      <div className="relative h-80 rounded-xl overflow-hidden border-2 border-slate-200 bg-slate-100">
        <div ref={mapContainerRef} className="h-full w-full" />
        {!sdkReady && !mapError && (
          <div className="absolute inset-0 flex items-center justify-center text-sm text-slate-400 bg-slate-100">
            지도를 불러오는 중...
          </div>
        )}
        <div className="absolute right-3 top-3 z-10 rounded-lg bg-slate-950/85 px-3 py-2 text-xs font-bold text-white shadow-lg">
          반경 {Math.round(radiusNumber)}m
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        {RADIUS_PRESETS.map((preset) => (
          <button
            key={preset}
            type="button"
            onClick={() => onRadiusChange(String(preset))}
            className={`px-3 py-1.5 rounded-full border text-xs font-semibold transition-colors ${
              radiusNumber === preset
                ? "border-indigo-500 bg-indigo-50 text-indigo-700"
                : "border-slate-200 text-slate-500 hover:border-indigo-300"
            }`}
          >
            {preset}m
          </button>
        ))}
      </div>

      <div className="rounded-lg bg-slate-50 border border-slate-200 px-3 py-2.5">
        <p className="text-xs font-semibold text-slate-700">
          {selectedAddress || "장소를 검색하거나 지도에서 위치를 선택해주세요."}
        </p>
        <p className="text-[11px] font-mono text-slate-500 mt-1">
          {latitudeNumber != null && longitudeNumber != null
            ? `${latitudeNumber.toFixed(7)}, ${longitudeNumber.toFixed(7)}`
            : "좌표 미선택"}
        </p>
      </div>

      {nearbyQuests.length > 0 && (
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-3.5 py-3">
          <p className="text-xs font-bold text-amber-800">
            주변 퀘스트 중복 가능성
          </p>
          <ul className="mt-1.5 space-y-1">
            {nearbyQuests.map(({ quest, distance }) => (
              <li key={quest.questId} className="text-xs text-amber-700">
                #{quest.questId} {quest.questName} · {formatDistance(distance)}{" "}
                거리 · 반경 {quest.clearRadius}m
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
