import React, { useState, useRef, useEffect } from "react";
import AppLayout from "../layout/AppLayout";

const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");

// 도메인 배포 환경(상대 경로) 및 로컬 환경 동적 대응
const getApiBaseUrl = () => {
    if (BASE_URL) {
        return `${BASE_URL}/api/v1/ai-photo`;
    }
    if (typeof window !== "undefined" && (window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1")) {
        return "http://localhost:8080/api/v1/ai-photo";
    }
    return "/api/v1/ai-photo";
};

const API_BASE_URL = getApiBaseUrl();

// 🔑 인증 헤더 생성 공통 헬퍼 함수
const getAuthHeaders = (): Record<string, string> => {
    const token = localStorage.getItem("accessToken") || localStorage.getItem("token");
    return token ? { Authorization: `Bearer ${token}` } : {};
};

type TabType = "best-shot" | "multi-mosaic" | "manual-mosaic";

interface BestShotResult {
  rank: number;
  total_score: number;
  blur_score: number;
  color_score: number;
  comp_score: number;
  tags: string[];
  filename: string;
  image_data: string;
}

export default function AiPhotoEditorPage() {
  const [activeTab, setActiveTab] = useState<TabType>("best-shot");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 이미지 다운로드 공통 헬퍼 함수
  const handleDownload = (dataUrl: string, defaultFilename: string) => {
    const link = document.createElement("a");
    link.href = dataUrl;
    link.download = defaultFilename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  // ==========================================
  // 1. 베스트 샷 추천 State
  // ==========================================
  const [bestShotFiles, setBestShotFiles] = useState<File[]>([]);
  const [bestShotPreviews, setBestShotPreviews] = useState<string[]>([]);
  const [bestShotResults, setBestShotResults] = useState<BestShotResult[]>([]);

  const handleBestShotFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!e.target.files) return;
    const newFiles = Array.from(e.target.files);
    const updatedFiles = [...bestShotFiles, ...newFiles];
    setBestShotFiles(updatedFiles);
    setBestShotResults([]);

    const newPreviews = updatedFiles.map((file) => URL.createObjectURL(file));
    setBestShotPreviews(newPreviews);
  };

  const handleRemoveBestShotFile = (index: number) => {
    const updatedFiles = bestShotFiles.filter((_, i) => i !== index);
    setBestShotFiles(updatedFiles);
    setBestShotPreviews(updatedFiles.map((file) => URL.createObjectURL(file)));
    setBestShotResults([]);
  };

  const handleRunBestShot = async () => {
    if (bestShotFiles.length === 0) {
      alert("사진을 1장 이상 선택해 주세요.");
      return;
    }

    setLoading(true);
    setError(null);

    const formData = new FormData();
    bestShotFiles.forEach((file) => formData.append("images", file));

    try {
      const res = await fetch(`${API_BASE_URL}/best-shot`, {
        method: "POST",
        headers: getAuthHeaders(), // 🔒 Authorization 헤더 전달
        body: formData,
      });

      if (res.status === 401) {
        throw new Error("로그인이 만료되었거나 권한이 없습니다. 다시 로그인해 주세요.");
      }

      if (!res.ok) throw new Error("베스트 샷 분석 중 오류가 발생했습니다.");

      const data = await res.json();
      setBestShotResults(data.results || []);
    } catch (err: any) {
      setError(err.message || "서버 통신 실패");
    } finally {
      setLoading(false);
    }
  };

  // ==========================================
  // 2. 다중 사진 자동 행인 모자이크 State
  // ==========================================
  const [multiMosaicFiles, setMultiMosaicFiles] = useState<File[]>([]);
  const [multiMosaicPreviews, setMultiMosaicPreviews] = useState<string[]>([]);
  const [multiMosaicResults, setMultiMosaicResults] = useState<string[]>([]);

  const handleMultiMosaicFileChange = (
    e: React.ChangeEvent<HTMLInputElement>,
  ) => {
    if (!e.target.files) return;
    const newFiles = Array.from(e.target.files);
    const updatedFiles = [...multiMosaicFiles, ...newFiles];
    setMultiMosaicFiles(updatedFiles);
    setMultiMosaicResults([]);

    const newPreviews = updatedFiles.map((file) => URL.createObjectURL(file));
    setMultiMosaicPreviews(newPreviews);
  };

  const handleRemoveMultiMosaicFile = (index: number) => {
    const updatedFiles = multiMosaicFiles.filter((_, i) => i !== index);
    setMultiMosaicFiles(updatedFiles);
    setMultiMosaicPreviews(
      updatedFiles.map((file) => URL.createObjectURL(file)),
    );
    setMultiMosaicResults([]);
  };

  const handleRunMultiMosaic = async () => {
    if (multiMosaicFiles.length < 2) {
      alert("공통 인물(일행) 판별을 위해 사진을 2장 이상 선택해 주세요.");
      return;
    }

    setLoading(true);
    setError(null);

    const formData = new FormData();
    multiMosaicFiles.forEach((file) => formData.append("images", file));

    try {
      const res = await fetch(`${API_BASE_URL}/multi-mosaic`, {
        method: "POST",
        headers: getAuthHeaders(), // 🔒 Authorization 헤더 전달
        body: formData,
      });

      if (res.status === 401) {
        throw new Error("로그인이 만료되었거나 권한이 없습니다. 다시 로그인해 주세요.");
      }

      if (!res.ok)
        throw new Error("자동 모자이크 처리 중 오류가 발생했습니다.");

      const data = await res.json();
      setMultiMosaicResults(data.results || []);
    } catch (err: any) {
      setError(err.message || "서버 통신 실패");
    } finally {
      setLoading(false);
    }
  };

  // ==========================================
  // 3. 수동 브러시 핀포인트 모자이크 State & Canvas
  // ==========================================
  const [manualFile, setManualFile] = useState<File | null>(null);
  const [manualResult, setManualResult] = useState<string | null>(null);
  const [brushSize, setBrushSize] = useState<number>(30);
  const [isDrawing, setIsDrawing] = useState(false);

  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const imgRef = useRef<HTMLImageElement | null>(null);

  const handleManualFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!e.target.files || e.target.files.length === 0) return;
    const file = e.target.files[0];
    setManualFile(file);
    setManualResult(null);
  };

  useEffect(() => {
    if (!manualFile || !canvasRef.current || !imgRef.current) return;
    const img = imgRef.current;
    img.src = URL.createObjectURL(manualFile);
    img.onload = () => {
      if (!canvasRef.current) return;
      canvasRef.current.width = img.naturalWidth;
      canvasRef.current.height = img.naturalHeight;
      const ctx = canvasRef.current.getContext("2d");
      if (ctx) {
        ctx.clearRect(0, 0, img.naturalWidth, img.naturalHeight);
      }
    };
  }, [manualFile]);

  const startDrawing = (e: React.MouseEvent<HTMLCanvasElement>) => {
    setIsDrawing(true);
    draw(e);
  };

  const stopDrawing = () => {
    setIsDrawing(false);
  };

  const draw = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!isDrawing || !canvasRef.current || !imgRef.current) return;
    const canvas = canvasRef.current;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;

    const x = (e.clientX - rect.left) * scaleX;
    const y = (e.clientY - rect.top) * scaleY;

    ctx.fillStyle = "#FFFFFF";
    ctx.beginPath();
    ctx.arc(
      x,
      y,
      brushSize * (canvas.width / rect.width) * 0.5,
      0,
      Math.PI * 2,
    );
    ctx.fill();
  };

  const handleClearCanvas = () => {
    if (!canvasRef.current) return;
    const ctx = canvasRef.current.getContext("2d");
    if (ctx) {
      ctx.clearRect(0, 0, canvasRef.current.width, canvasRef.current.height);
    }
  };

  const handleRunManualMosaic = async () => {
    if (!manualFile || !canvasRef.current) {
      alert("사진을 선택해 주세요.");
      return;
    }

    setLoading(true);
    setError(null);

    canvasRef.current.toBlob(async (blob) => {
      if (!blob) {
        setLoading(false);
        alert("마스크 데이터 생성 실패");
        return;
      }

      const formData = new FormData();
      formData.append("image", manualFile);
      formData.append("mask", blob, "mask.png");

      try {
        const res = await fetch(`${API_BASE_URL}/mosaic`, {
          method: "POST",
          headers: getAuthHeaders(), // 🔒 Authorization 헤더 전달
          body: formData,
        });

        if (res.status === 401) {
          throw new Error("로그인이 만료되었거나 권한이 없습니다. 다시 로그인해 주세요.");
        }

        if (!res.ok) throw new Error("수동 모자이크 처리 실패");

        const resultBlob = await res.blob();
        setManualResult(URL.createObjectURL(resultBlob));
      } catch (err: any) {
        setError(err.message || "서버 통신 실패");
      } finally {
        setLoading(false);
      }
    }, "image/png");
  };

  return (
    <AppLayout pageClassName="bg-slate-950 text-white">
      <div className="p-4 lg:p-8">
        {/* Header */}
        <div className="max-w-7xl mx-auto mb-8">
          <div className="flex items-center gap-3 mb-2">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-cyan-400 shadow-lg shadow-violet-500/30">
              <svg
                className="w-5 h-5 text-white"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1.8}
                  d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"
                />
                <circle cx="12" cy="13" r="4" />
              </svg>
            </div>
            <div>
              <h1 className="text-2xl lg:text-3xl font-black bg-gradient-to-r from-white via-slate-100 to-slate-400 bg-clip-text text-transparent">
                AI 사진 편집 스튜디오
              </h1>
              <p className="text-xs lg:text-sm text-slate-400">
                YOLOv8 & OpenCV AI를 활용한 여행 사진 베스트 샷 선별 및 초상권
                보호 모자이크 툴
              </p>
            </div>
          </div>

          {/* Navigation Tabs */}
          <div className="flex border-b border-white/10 mt-6 gap-2">
            <button
              onClick={() => setActiveTab("best-shot")}
              className={`px-4 py-3 text-sm font-bold border-b-2 transition-colors ${
                activeTab === "best-shot"
                  ? "border-cyan-400 text-cyan-300 bg-white/5 rounded-t-lg"
                  : "border-transparent text-slate-400 hover:text-white"
              }`}
            >
              📸 베스트 샷 추천
            </button>
            <button
              onClick={() => setActiveTab("multi-mosaic")}
              className={`px-4 py-3 text-sm font-bold border-b-2 transition-colors ${
                activeTab === "multi-mosaic"
                  ? "border-cyan-400 text-cyan-300 bg-white/5 rounded-t-lg"
                  : "border-transparent text-slate-400 hover:text-white"
              }`}
            >
              👥 자동 행인 모자이크 (일행 보호)
            </button>
            <button
              onClick={() => setActiveTab("manual-mosaic")}
              className={`px-4 py-3 text-sm font-bold border-b-2 transition-colors ${
                activeTab === "manual-mosaic"
                  ? "border-cyan-400 text-cyan-300 bg-white/5 rounded-t-lg"
                  : "border-transparent text-slate-400 hover:text-white"
              }`}
            >
              🎨 수동 핀포인트 모자이크
            </button>
          </div>
        </div>

        {/* 콘텐츠 영역 */}
        <div className="max-w-7xl mx-auto">
          {error && (
            <div className="mb-6 p-4 bg-rose-500/10 border border-rose-500/30 rounded-xl text-rose-300 text-sm">
              ⚠️ {error}
            </div>
          )}

          {/* TAB 1: 베스트 샷 추천 */}
          {activeTab === "best-shot" && (
            <div className="space-y-6">
              <div className="p-6 bg-slate-900/60 backdrop-blur-xl rounded-2xl border border-white/10 space-y-4">
                <div>
                  <h2 className="text-lg font-bold text-white mb-1">
                    여러 장의 여행 사진 중 인공지능이 뽑은 BEST 샷
                  </h2>
                  <p className="text-xs text-slate-400">
                    해상도, 구도, 색감, 얼굴 선명도를 종합 분석하여 점수 순으로
                    나열합니다.
                  </p>
                </div>

                <div className="flex items-center gap-4">
                  <label className="cursor-pointer px-4 py-2.5 bg-violet-600 hover:bg-violet-500 text-white rounded-xl font-bold text-sm transition">
                    사진 추가 선택 ➕
                    <input
                      type="file"
                      multiple
                      accept="image/*"
                      onChange={handleBestShotFileChange}
                      className="hidden"
                    />
                  </label>
                  <span className="text-xs text-slate-400">
                    {bestShotFiles.length}장 선택됨
                  </span>
                  <button
                    onClick={handleRunBestShot}
                    disabled={loading || bestShotFiles.length === 0}
                    className="ml-auto px-6 py-2.5 bg-gradient-to-r from-violet-500 to-cyan-400 hover:opacity-90 disabled:opacity-50 text-white rounded-xl font-bold text-sm transition shadow-lg shadow-cyan-500/20"
                  >
                    {loading ? "AI 평가 분석 중..." : "베스트 샷 평가하기 🚀"}
                  </button>
                </div>

                {/* ✨ 선택한 업로드 사진 미리보기 리스트 */}
                {bestShotPreviews.length > 0 && (
                  <div className="pt-2 border-t border-white/10">
                    <p className="text-xs font-bold text-slate-300 mb-2">
                      선택된 사진 목록 ({bestShotPreviews.length})
                    </p>
                    <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 gap-3">
                      {bestShotPreviews.map((url, idx) => (
                        <div
                          key={idx}
                          className="relative group aspect-square rounded-xl overflow-hidden bg-slate-950 border border-white/10"
                        >
                          <img
                            src={url}
                            alt={`Preview ${idx}`}
                            className="w-full h-full object-cover"
                          />
                          <button
                            onClick={() => handleRemoveBestShotFile(idx)}
                            className="absolute top-1 right-1 h-5 w-5 bg-rose-600/90 hover:bg-rose-500 text-white text-[10px] rounded-full flex items-center justify-center opacity-80 group-hover:opacity-100 transition"
                            title="사진 제외"
                          >
                            ✕
                          </button>
                          <span className="absolute bottom-1 left-1 text-[9px] bg-slate-900/80 px-1 rounded text-slate-300 truncate max-w-[80%]">
                            {bestShotFiles[idx]?.name}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {/* 평가 결과 렌더링 */}
              {bestShotResults.length > 0 && (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                  {bestShotResults.map((item) => (
                    <div
                      key={item.rank}
                      className={`relative overflow-hidden rounded-2xl border p-4 transition ${item.rank === 1 ? "bg-slate-900 border-amber-400/60 shadow-[0_0_25px_rgba(251,191,36,0.15)]" : "bg-slate-900/40 border-white/10"}`}
                    >
                      <div className="absolute top-3 left-3 z-10">
                        <span
                          className={`px-3 py-1 rounded-full text-xs font-black ${item.rank === 1 ? "bg-amber-400 text-slate-950" : "bg-slate-800 text-white"}`}
                        >
                          RANK #{item.rank}
                        </span>
                      </div>

                      {/* ✨ 다운로드 버튼 추가 */}
                      <div className="absolute top-3 right-3 z-10">
                        <button
                          onClick={() =>
                            handleDownload(
                              item.image_data,
                              `best_shot_rank_${item.rank}.jpg`,
                            )
                          }
                          className="px-2.5 py-1 bg-slate-900/80 hover:bg-cyan-500 text-white rounded-lg text-xs font-bold border border-white/10 shadow transition"
                        >
                          다운로드 💾
                        </button>
                      </div>

                      <div className="aspect-video w-full overflow-hidden rounded-xl bg-slate-950 mb-3 mt-8">
                        <img
                          src={item.image_data}
                          alt={item.filename}
                          className="w-full h-full object-cover"
                        />
                      </div>
                      <div className="space-y-2">
                        <div className="flex justify-between items-center">
                          <span className="text-xs font-bold text-slate-300 truncate max-w-[180px]">
                            {item.filename}
                          </span>
                          <span className="text-lg font-black text-cyan-300">
                            {item.total_score}점
                          </span>
                        </div>
                        <div className="grid grid-cols-3 gap-2 text-[10px] text-center">
                          <div className="bg-white/5 p-1.5 rounded-lg">
                            <p className="text-slate-400">선명도</p>
                            <p className="font-bold text-white">
                              {item.blur_score}
                            </p>
                          </div>
                          <div className="bg-white/5 p-1.5 rounded-lg">
                            <p className="text-slate-400">색감</p>
                            <p className="font-bold text-white">
                              {item.color_score}
                            </p>
                          </div>
                          <div className="bg-white/5 p-1.5 rounded-lg">
                            <p className="text-slate-400">구도</p>
                            <p className="font-bold text-white">
                              {item.comp_score}
                            </p>
                          </div>
                        </div>
                        <div className="flex flex-wrap gap-1 pt-1">
                          {item.tags.map((tag, i) => (
                            <span
                              key={i}
                              className="text-[10px] bg-cyan-500/20 text-cyan-300 px-2 py-0.5 rounded-md font-semibold border border-cyan-500/30"
                            >
                              {tag}
                            </span>
                          ))}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* TAB 2: 다중 사진 자동 행인 모자이크 */}
          {activeTab === "multi-mosaic" && (
            <div className="space-y-6">
              <div className="p-6 bg-slate-900/60 backdrop-blur-xl rounded-2xl border border-white/10 space-y-4">
                <div>
                  <h2 className="text-lg font-bold text-white mb-1">
                    일행은 보호하고 지나가는 행인만 자동 모자이크
                  </h2>
                  <p className="text-xs text-slate-400">
                    2장 이상 연속으로 등장하는 인물을 '일행(주인공)'으로
                    판단하여 보호하고 배경 속 불특정 다수만 블러 처리합니다.
                  </p>
                </div>

                <div className="flex items-center gap-4">
                  <label className="cursor-pointer px-4 py-2.5 bg-violet-600 hover:bg-violet-500 text-white rounded-xl font-bold text-sm transition">
                    연속 사진 추가 ➕
                    <input
                      type="file"
                      multiple
                      accept="image/*"
                      onChange={handleMultiMosaicFileChange}
                      className="hidden"
                    />
                  </label>
                  <span className="text-xs text-slate-400">
                    {multiMosaicFiles.length}장 선택됨
                  </span>
                  <button
                    onClick={handleRunMultiMosaic}
                    disabled={loading || multiMosaicFiles.length < 2}
                    className="ml-auto px-6 py-2.5 bg-gradient-to-r from-violet-500 to-cyan-400 hover:opacity-90 disabled:opacity-50 text-white rounded-xl font-bold text-sm transition shadow-lg shadow-cyan-500/20"
                  >
                    {loading
                      ? "AI 행인 탐지 및 모자이크 중..."
                      : "자동 모자이크 실행 🛡️"}
                  </button>
                </div>

                {/* ✨ 선택한 업로드 사진 미리보기 리스트 */}
                {multiMosaicPreviews.length > 0 && (
                  <div className="pt-2 border-t border-white/10">
                    <p className="text-xs font-bold text-slate-300 mb-2">
                      선택된 사진 목록 ({multiMosaicPreviews.length})
                    </p>
                    <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 gap-3">
                      {multiMosaicPreviews.map((url, idx) => (
                        <div
                          key={idx}
                          className="relative group aspect-square rounded-xl overflow-hidden bg-slate-950 border border-white/10"
                        >
                          <img
                            src={url}
                            alt={`Preview ${idx}`}
                            className="w-full h-full object-cover"
                          />
                          <button
                            onClick={() => handleRemoveMultiMosaicFile(idx)}
                            className="absolute top-1 right-1 h-5 w-5 bg-rose-600/90 hover:bg-rose-500 text-white text-[10px] rounded-full flex items-center justify-center opacity-80 group-hover:opacity-100 transition"
                            title="사진 제외"
                          >
                            ✕
                          </button>
                          <span className="absolute bottom-1 left-1 text-[9px] bg-slate-900/80 px-1 rounded text-slate-300 truncate max-w-[80%]">
                            {multiMosaicFiles[idx]?.name}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {/* 결과 출력 */}
              {multiMosaicResults.length > 0 && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  {multiMosaicResults.map((b64Img, idx) => (
                    <div
                      key={idx}
                      className="bg-slate-900/40 rounded-2xl border border-white/10 p-4 space-y-3"
                    >
                      <div className="flex justify-between items-center text-xs text-slate-400">
                        <span>결과 이미지 #{idx + 1}</span>
                        {/* ✨ 다운로드 버튼 */}
                        <button
                          onClick={() =>
                            handleDownload(
                              b64Img,
                              `auto_mosaic_result_${idx + 1}.jpg`,
                            )
                          }
                          className="px-3 py-1 bg-violet-600 hover:bg-violet-500 text-white font-bold rounded-lg transition"
                        >
                          다운로드 💾
                        </button>
                      </div>
                      <div className="aspect-video w-full overflow-hidden rounded-xl bg-slate-950">
                        <img
                          src={b64Img}
                          alt={`Result ${idx}`}
                          className="w-full h-full object-contain"
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* TAB 3: 수동 핀포인트 모자이크 */}
          {activeTab === "manual-mosaic" && (
            <div className="space-y-6">
              <div className="p-6 bg-slate-900/60 backdrop-blur-xl rounded-2xl border border-white/10">
                <h2 className="text-lg font-bold text-white mb-2">
                  보존할 얼굴 위에 브러시를 칠하세요
                </h2>
                <p className="text-xs text-slate-400 mb-4">
                  칠해진 얼굴(주인공)을 제외한 나머지 모든 얼굴에 핀포인트
                  타원형 가우시안 블러가 적용됩니다.
                </p>

                <div className="flex flex-wrap items-center gap-4">
                  <label className="cursor-pointer px-4 py-2.5 bg-violet-600 hover:bg-violet-500 text-white rounded-xl font-bold text-sm transition">
                    단일 사진 선택
                    <input
                      type="file"
                      accept="image/*"
                      onChange={handleManualFileChange}
                      className="hidden"
                    />
                  </label>
                  {manualFile && (
                    <>
                      <div className="flex items-center gap-2">
                        <span className="text-xs text-slate-300">
                          브러시 크기:
                        </span>
                        <input
                          type="range"
                          min="10"
                          max="100"
                          value={brushSize}
                          onChange={(e) => setBrushSize(Number(e.target.value))}
                          className="w-24 accent-cyan-400"
                        />
                      </div>
                      <button
                        onClick={handleClearCanvas}
                        className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-xs rounded-lg text-slate-300"
                      >
                        지우기 🔄
                      </button>
                      <button
                        onClick={handleRunManualMosaic}
                        disabled={loading}
                        className="ml-auto px-6 py-2.5 bg-gradient-to-r from-violet-500 to-cyan-400 hover:opacity-90 disabled:opacity-50 text-white rounded-xl font-bold text-sm transition shadow-lg shadow-cyan-500/20"
                      >
                        {loading
                          ? "핀포인트 블러 적용 중..."
                          : "모자이크 적용 🖌️"}
                      </button>
                    </>
                  )}
                </div>
              </div>

              {/* Canvas 마스킹 영역 및 결과 표시 */}
              {manualFile && (
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                  <div className="bg-slate-900/40 p-4 rounded-2xl border border-white/10 space-y-2">
                    <span className="text-xs font-bold text-slate-300">
                      원본 & 브러시 마스킹
                    </span>
                    <div className="relative aspect-video w-full overflow-hidden rounded-xl bg-slate-950 flex items-center justify-center">
                      <img
                        ref={imgRef}
                        alt="Original"
                        className="absolute inset-0 w-full h-full object-contain pointer-events-none"
                      />
                      <canvas
                        ref={canvasRef}
                        onMouseDown={startDrawing}
                        onMouseUp={stopDrawing}
                        onMouseLeave={stopDrawing}
                        onMouseMove={draw}
                        className="absolute inset-0 w-full h-full object-contain cursor-crosshair opacity-60"
                      />
                    </div>
                  </div>

                  <div className="bg-slate-900/40 p-4 rounded-2xl border border-white/10 space-y-2">
                    <div className="flex justify-between items-center">
                      <span className="text-xs font-bold text-slate-300">
                        AI 모자이크 최종 결과
                      </span>
                      {/* ✨ 수동 모자이크 다운로드 버튼 */}
                      {manualResult && (
                        <button
                          onClick={() =>
                            handleDownload(
                              manualResult,
                              "manual_mosaic_result.jpg",
                            )
                          }
                          className="px-3 py-1 bg-violet-600 hover:bg-violet-500 text-white text-xs font-bold rounded-lg transition"
                        >
                          다운로드 💾
                        </button>
                      )}
                    </div>
                    <div className="aspect-video w-full overflow-hidden rounded-xl bg-slate-950 flex items-center justify-center">
                      {manualResult ? (
                        <img
                          src={manualResult}
                          alt="Mosaic Result"
                          className="w-full h-full object-contain"
                        />
                      ) : (
                        <span className="text-xs text-slate-500">
                          브러시 마스킹 후 [모자이크 적용]을 눌러주세요.
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </AppLayout>
  );
}
