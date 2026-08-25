import { useState, useEffect, useCallback } from "react";

interface GeolocationState {
  latitude: number | null;
  longitude: number | null;
  accuracy: number | null; // 오차 범위 (미터 단위)
  gpsSignalStrength: "EXCELLENT" | "GOOD" | "POOR" | "SEARCHING";
  error: string | null;
}

export const usePreciseGeolocation = (active: boolean = true) => {
  const [state, setState] = useState<GeolocationState>({
    latitude: null,
    longitude: null,
    accuracy: null,
    gpsSignalStrength: "SEARCHING",
    error: null,
  });

  const getSignalStrength = (
    accuracy: number,
  ): "EXCELLENT" | "GOOD" | "POOR" => {
    if (accuracy <= 10) return "EXCELLENT"; // 오차범위 10m 이내 (최고 고정)
    if (accuracy <= 25) return "GOOD"; // 오차범위 25m 이내 (양호)
    return "POOR"; // 오차범위 25m 초과 (부적합)
  };

  const handleSuccess = useCallback((position: GeolocationPosition) => {
    const { latitude, longitude, accuracy } = position.coords;

    setState({
      latitude,
      longitude,
      accuracy,
      gpsSignalStrength: getSignalStrength(accuracy),
      error: null,
    });
  }, []);

  const handleError = useCallback((error: GeolocationPositionError) => {
    let errorMsg = "GPS 정보를 가져올 수 없습니다.";
    switch (error.code) {
      case error.PERMISSION_DENIED:
        errorMsg =
          "위치 정보 공유 권한이 거부되었습니다. 설정에서 권한을 허용해 주세요.";
        break;
      case error.POSITION_UNAVAILABLE:
        errorMsg = "네트워크 불안정으로 GPS 위성 신호를 감지할 수 없습니다.";
        break;
      case error.TIMEOUT:
        errorMsg = "GPS 좌표 요청 시간 초과가 발생했습니다.";
        break;
    }
    setState((prev) => ({
      ...prev,
      gpsSignalStrength: "POOR",
      error: errorMsg,
    }));
  }, []);

  useEffect(() => {
    if (!active) return;

    if (!navigator.geolocation) {
      setState((prev) => ({
        ...prev,
        gpsSignalStrength: "POOR",
        error: "기기가 GPS 센서 및 브라우저 위치 서비스를 지원하지 않습니다.",
      }));
      return;
    }

    // 정밀한 위치 추적 옵션
    const options: PositionOptions = {
      enableHighAccuracy: true, // 하드웨어 GPS 우선 활성화
      timeout: 8000, // 8초 대기
      maximumAge: 0, // 위치 Refresh 강제
    };

    const watchId = navigator.geolocation.watchPosition(
      handleSuccess,
      handleError,
      options,
    );

    return () => navigator.geolocation.clearWatch(watchId);
  }, [active, handleSuccess, handleError]);

  return state;
};
