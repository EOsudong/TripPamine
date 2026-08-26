import axios from "axios";

export const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080"
).replace(/\/$/, "");

export const api = axios.create({
  baseURL: API_BASE_URL, // 백엔드 서버
  headers: {
    "Content-Type": "application/json",
  },
  // withCredentials: true, // Refresh Token 쿠키를 주고 받을 경우 주석 해제
});

// 요청 인터셉터 설정 (요청 전 처리)
api.interceptors.request.use(
  (config) => {
    // 요청 전 처리 로직 (예: 토큰 첨부)
    const accessToken = localStorage.getItem("accessToken");
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

// 응답 인터셉터 설정 (응답 후 처리)
api.interceptors.response.use(
  (response) => {
    // 응답 후 처리 로직 (예: 공통 에러 처리)
    return response;
  },
  (error) => {
    // 에러 응답 처리 로직
    if (error.response) {
      // 서버 응답이 있는 경우
      console.error("API Error:", error.response.status, error.response.data);
    }
    return Promise.reject(error);
  },
);

export default api;
