import { useEffect, useRef } from "react";

declare global {
  interface Window {
    kakao: any;
  }
}

interface MysteryRadiusMapProps {
  departure: string;
  radiusKm: number;
}

export default function MysteryRadiusMap({
  departure,
  radiusKm,
}: MysteryRadiusMapProps) {
  const mapRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!mapRef.current) return;
    if (!window.kakao?.maps) return;

    window.kakao.maps.load(() => {
      // 처음에는 서울 중심
      const defaultPosition = new window.kakao.maps.LatLng(
        37.5665,
        126.9780,
      );

      const map = new window.kakao.maps.Map(mapRef.current, {
        center: defaultPosition,
        level: 9,
      });

      // 출발지를 아직 입력하지 않았다면 기본 지도만 표시
      if (!departure.trim()) return;

      const geocoder = new window.kakao.maps.services.Geocoder();

      geocoder.addressSearch(
        departure,
        (result: any[], status: string) => {
          if (
            status !== window.kakao.maps.services.Status.OK ||
            result.length === 0
          ) {
            console.warn("출발지 좌표 검색 실패:", departure);
            return;
          }

          const lat = Number(result[0].y);
          const lng = Number(result[0].x);

          const position = new window.kakao.maps.LatLng(lat, lng);

          // 지도 중심 이동
          map.setCenter(position);

          // 출발지 마커
          new window.kakao.maps.Marker({
            map,
            position,
          });

          // 이동 가능 반경
          new window.kakao.maps.Circle({
            map,
            center: position,
            radius: radiusKm * 1000,
            strokeWeight: 2,
            strokeColor: "#8b5cf6",
            strokeOpacity: 0.9,
            strokeStyle: "solid",
            fillColor: "#8b5cf6",
            fillOpacity: 0.15,
          });

          // 반경 크기에 맞춰 지도 확대/축소
          const bounds = new window.kakao.maps.LatLngBounds();

          const deltaLat = radiusKm / 111;
          const deltaLng =
            radiusKm /
            (111 * Math.cos((lat * Math.PI) / 180));

          bounds.extend(
            new window.kakao.maps.LatLng(
              lat + deltaLat,
              lng + deltaLng,
            ),
          );

          bounds.extend(
            new window.kakao.maps.LatLng(
              lat - deltaLat,
              lng - deltaLng,
            ),
          );

          map.setBounds(bounds);
        },
      );
    });
  }, [departure, radiusKm]);

  return (
    <div className="overflow-hidden rounded-3xl border border-violet-500/20 bg-slate-900">
      <div className="border-b border-white/10 px-5 py-4">
        <p className="text-sm font-black text-white">
          🗺️ 여행 가능 범위
        </p>

        <p className="mt-1 text-xs text-slate-400">
          출발지를 기준으로 최대 이동 가능 범위를 확인할 수 있습니다.
        </p>
      </div>

      <div
        ref={mapRef}
        className="h-[380px] w-full"
      />

      <div className="border-t border-white/10 px-5 py-4">
        <div className="flex items-center justify-between">
          <span className="text-xs text-slate-400">
            출발지
          </span>

          <span className="text-xs font-bold text-white">
            {departure || "출발지를 입력해주세요"}
          </span>
        </div>

        <div className="mt-2 flex items-center justify-between">
          <span className="text-xs text-slate-400">
            최대 이동 반경
          </span>

          <span className="text-xs font-black text-violet-300">
            {radiusKm > 0 ? `${radiusKm}km` : "미설정"}
          </span>
        </div>
      </div>
    </div>
  );
}