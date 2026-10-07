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
    placeName: string;
    summary: DailyWeatherSummary;
}

export const getTripWeatherApi = async (planId: number): Promise<TripDayWeather[]> => {
    const response = await api.get<TripDayWeather[]>(`/weather/travel-plans/${planId}`);
    return response.data;
};