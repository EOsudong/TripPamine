import { useEffect, useRef, useState } from "react";
import {
  MASCOT_RARITY_COLOR,
  resolveMascotImageUrl,
  type MascotSpawnPointResponse,
} from "../api/mascot";

// 마스코트 탐험 지도 (Kakao Maps).
// QuestWorldMap.tsx와 같은 패턴: index.html에 autoload=false로 실린 SDK를 kakao.maps.load()로 초기화하고,
// 스폰 지점은 CustomOverlay(마스코트 이미지 동그라미 + 희귀도 테두리)로, 포획 반경은 Circle로 그린다.
// 위치 추적(usePreciseGeolocation)은 부모(MascotHuntPage)가 소유하고 좌표만 props로 받는다
// - 지도 아래 패널의 "조우하기" 버튼도 같은 좌표를 써야 하기 때문.

interface MascotHuntMapProps {
  spawns: MascotSpawnPointResponse[];
  selectedSpawnId: number | null;
  latitude: number | null;
  longitude: number | null;
  accuracy: number | null;
  onSelect: (spawnId: number) => void;
}

const SEOUL_CENTER = { latitude: 37.5665, longitude: 126.978 };

export default function MascotHuntMap({
  spawns,
  selectedSpawnId,
  latitude,
  longitude,
  accuracy,
  onSelect,
}: MascotHuntMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const spawnObjectsRef = useRef<any[]>([]);
  const userOverlayRef = useRef<any>(null);
  const accuracyCircleRef = useRef<any>(null);
  const centeredOnUserRef = useRef(false);
  const [mapReady, setMapReady] = useState(false);
  const [mapError, setMapError] = useState<string | null>(null);

  // 1) 지도 생성 (최초 1회)
  useEffect(() => {
    if (!containerRef.current) return;
    if (typeof window.kakao?.maps?.load !== "function") {
      setMapError("Kakao Map SDK를 불러오지 못했습니다. JavaScript 키와 등록 도메인을 확인해주세요.");
      return;
    }

    let disposed = false;
    const kakao = window.kakao;

    kakao.maps.load(() => {
      if (disposed || !containerRef.current) return;
      try {
        const first = spawns[0];
        const map = new kakao.maps.Map(containerRef.current, {
          center: new kakao.maps.LatLng(
            first?.targetLat ?? SEOUL_CENTER.latitude,
            first?.targetLng ?? SEOUL_CENTER.longitude,
          ),
          level: 6,
        });
        mapRef.current = map;
        setMapReady(true);
      } catch (error) {
        console.error("Kakao Map 초기화 실패:", error);
        setMapError("지도를 초기화하지 못했습니다. 잠시 후 다시 시도해주세요.");
      }
    });

    return () => {
      disposed = true;
      spawnObjectsRef.current.forEach((object) => object.setMap(null));
      spawnObjectsRef.current = [];
      userOverlayRef.current?.setMap(null);
      accuracyCircleRef.current?.setMap(null);
      userOverlayRef.current = null;
      accuracyCircleRef.current = null;
      mapRef.current = null;
      centeredOnUserRef.current = false;
    };
    // 지도는 한 번만 만들고, 스폰/위치 갱신은 아래 effect들이 담당한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 2) 스폰 지점 마커 + 포획 반경
  useEffect(() => {
    if (!mapReady || !mapRef.current) return;
    const kakao = window.kakao;
    const map = mapRef.current;

    spawnObjectsRef.current.forEach((object) => object.setMap(null));
    spawnObjectsRef.current = [];

    spawns.forEach((spawn) => {
      const position = new kakao.maps.LatLng(spawn.targetLat, spawn.targetLng);
      const color = MASCOT_RARITY_COLOR[spawn.mascotRarity];
      const selected = spawn.spawnId === selectedSpawnId;

      const circle = new kakao.maps.Circle({
        center: position,
        radius: spawn.catchRadius,
        strokeWeight: selected ? 2 : 1,
        strokeColor: color,
        strokeOpacity: selected ? 0.9 : 0.5,
        strokeStyle: "dashed",
        fillColor: color,
        fillOpacity: selected ? 0.18 : 0.08,
      });
      circle.setMap(map);

      const button = document.createElement("button");
      button.type = "button";
      button.title = spawn.spotName ? `${spawn.mascotName} · ${spawn.spotName}` : spawn.mascotName;
      const size = selected ? 58 : 46;
      button.style.cssText = `
        position: relative;
        width: ${size}px;
        height: ${size}px;
        border-radius: 50%;
        border: 3px solid ${color};
        background: #0b0b16;
        box-shadow: 0 0 0 4px rgba(255,255,255,0.85), 0 10px 24px rgba(15,23,42,0.5);
        cursor: pointer;
        padding: 0;
        overflow: visible;
        transition: transform .15s;
      `;

      const imageUrl = resolveMascotImageUrl(spawn.mascotImageUrl);
      if (imageUrl) {
        const image = document.createElement("img");
        image.src = imageUrl;
        image.alt = spawn.mascotName;
        image.style.cssText = "width:100%;height:100%;object-fit:contain;border-radius:50%;";
        image.onerror = () => {
          image.remove();
          button.textContent = "✦";
          button.style.color = color;
          button.style.fontSize = "20px";
        };
        button.appendChild(image);
      } else {
        button.textContent = "✦";
        button.style.color = color;
        button.style.fontSize = "20px";
      }

      if (spawn.alreadyCaught) {
        const badge = document.createElement("span");
        badge.textContent = "✓";
        badge.style.cssText = `
          position:absolute; right:-4px; bottom:-4px;
          width:18px; height:18px; border-radius:50%;
          background:#10b981; color:#fff; font-size:11px; font-weight:900;
          display:flex; align-items:center; justify-content:center;
          border:2px solid #fff;
        `;
        button.appendChild(badge);
      }

      button.addEventListener("click", () => onSelect(spawn.spawnId));

      const overlay = new kakao.maps.CustomOverlay({
        position,
        content: button,
        yAnchor: 0.5,
        zIndex: selected ? 10 : 3,
      });
      overlay.setMap(map);

      spawnObjectsRef.current.push(circle, overlay);
    });
  }, [mapReady, spawns, selectedSpawnId, onSelect]);

  // 3) 선택한 스폰으로 지도 이동
  useEffect(() => {
    if (!mapReady || !mapRef.current || selectedSpawnId == null) return;
    const spawn = spawns.find((item) => item.spawnId === selectedSpawnId);
    if (!spawn) return;
    mapRef.current.panTo(new window.kakao.maps.LatLng(spawn.targetLat, spawn.targetLng));
  }, [mapReady, selectedSpawnId, spawns]);

  // 4) 내 위치 마커 + 정확도 원
  useEffect(() => {
    if (!mapReady || !mapRef.current || latitude == null || longitude == null) return;
    const kakao = window.kakao;
    const map = mapRef.current;
    const position = new kakao.maps.LatLng(latitude, longitude);

    if (!userOverlayRef.current) {
      const marker = document.createElement("div");
      marker.style.cssText = `
        width: 20px; height: 20px; border-radius: 50%;
        border: 4px solid #ffffff; background: #2563eb;
        box-shadow: 0 0 0 7px rgba(37, 99, 235, 0.25);
      `;
      userOverlayRef.current = new kakao.maps.CustomOverlay({ position, content: marker, yAnchor: 0.5, zIndex: 20 });
      userOverlayRef.current.setMap(map);
    } else {
      userOverlayRef.current.setPosition(position);
    }

    if (!accuracyCircleRef.current) {
      accuracyCircleRef.current = new kakao.maps.Circle({
        center: position,
        radius: accuracy ?? 0,
        strokeWeight: 1,
        strokeColor: "#2563eb",
        strokeOpacity: 0.7,
        fillColor: "#3b82f6",
        fillOpacity: 0.1,
      });
      accuracyCircleRef.current.setMap(map);
    } else {
      accuracyCircleRef.current.setPosition(position);
      accuracyCircleRef.current.setRadius(accuracy ?? 0);
    }

    if (!centeredOnUserRef.current) {
      map.panTo(position);
      centeredOnUserRef.current = true;
    }
  }, [mapReady, latitude, longitude, accuracy]);

  function moveToMyLocation() {
    if (!mapRef.current || latitude == null || longitude == null) return;
    mapRef.current.panTo(new window.kakao.maps.LatLng(latitude, longitude));
  }

  return (
    <div className="relative h-[420px] w-full overflow-hidden rounded-3xl border border-white/10 bg-night-900 md:h-[520px]">
      <div ref={containerRef} className="h-full w-full" />

      {mapError && (
        <div className="absolute inset-0 flex items-center justify-center bg-night-900/90 p-6 text-center text-sm text-rose-300">
          {mapError}
        </div>
      )}

      <button
        type="button"
        onClick={moveToMyLocation}
        disabled={latitude == null}
        className="absolute bottom-4 right-4 z-10 rounded-full bg-white px-4 py-2 text-xs font-bold text-slate-700 shadow-lg disabled:opacity-50"
      >
        ◎ 내 위치
      </button>

      <div className="absolute left-4 top-4 z-10 flex gap-1.5 rounded-full bg-night-950/80 px-3 py-1.5 text-[11px] font-semibold text-white/80 backdrop-blur">
        <span style={{ color: MASCOT_RARITY_COLOR.COMMON }}>● 일반</span>
        <span style={{ color: MASCOT_RARITY_COLOR.RARE }}>● 희귀</span>
        <span style={{ color: MASCOT_RARITY_COLOR.EPIC }}>● 에픽</span>
      </div>
    </div>
  );
}
