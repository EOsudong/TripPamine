import api from "./axios";

// 백엔드 DailyWeatherSummary 형식 그대로
export interface DailyWeatherSummary {
    tempMin: number | null;
    tempMax: number | null;
    feelsLike: number | null;
    windSpeed: number | null;
    humidity: number | null;
    rainProbability: number | null;
    rainMm: number | null;
    notices: string[];
    available: boolean;
}

// 백엔드 TripDayWeather 형식 그대로
export interface TripDayWeather {
    day: number;
    date: string;          // "2026-10-31" 과 같은 형태의 문자열
    placeName: string | null;
    summary: DailyWeatherSummary;
}

export const getTripWeatherApi = async (planId: number): Promise<TripDayWeather[]> => {
    const response = await api.get<TripDayWeather[]>(`/weather/travel-plans/${planId}`);
    return response.data;
};

export interface HourlyForecast {
    time: string;                 // "12시" 는 문자열
    temperature: number | null;   // Double
    humidity: number | null;      // Integer
}

export interface RainAlert {
    rainExpected: boolean;
    message: string | null;
    precipitationType: string | null;
    forecastTime: string | null;
    rainProbability: number | null;
    rainAmount: string | null;
}

export const getUltraShortForecastApi = async (
    lat: number,
    lng: number,
): Promise<UltraShortForecastResult> => {
    const response = await api.get<UltraShortForecastResult>("/api/weather/ultra-short", {
        params: { lat, lng },
    });
    return response.data;
};

export interface NowWeather {
    weather: "CLEAR" | "RAIN" | "SNOW" | "CLOUDY";
    description: string;
    temperature: number | null;
}

export interface UltraShortForecastResult {
    hourly: HourlyForecast[];
    rain: RainAlert;
    current: NowWeather;
}