import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Gift, LockKeyhole, Sparkles, Trophy } from "lucide-react";
import { useAuth } from "../context/AuthContext";
import {
  createMysteryTour,
  getActiveMysteryTour,
  cancelMysteryTour,
} from "../api/mysteryTour";
import axios from "axios";

export default function MysteryTour() {
  const navigate = useNavigate();

  const { isLoggedIn } = useAuth();

  const [isCreating, setIsCreating] = useState(false);

  const [isApplying, setIsApplying] = useState(false);

  const [createdTour, setCreatedTour] = useState<{
    mysteryTourId: number;
    travelDate: string;
    travelDays: number;
    peopleCount: number;
    budget: number;
    questCount: number;
    status: string;
    destinationLocked: boolean;
  } | null>(null);

  useEffect(() => {
    const loadActiveTour = async () => {
      if (!isLoggedIn) return;

      try {
        const tour = await getActiveMysteryTour();
        if (tour) {
          setCreatedTour(tour);
        }
      } catch (error) {
        console.error("미스터리 투어 조회 실패:", error);
      }
    };

    loadActiveTour();
  }, [isLoggedIn, navigate]);

  const [form, setForm] = useState({
    travelDate: "",
    travelDays: "1",
    peopleCount: "",
    budget: "",
    radius: "",
    departure: "",
    style: "",
  });

  const handleFormChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>,
  ) => {
    const { name, value } = e.target;

    setForm((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleApply = () => {
    if (!isLoggedIn) {
      alert("미스터리 투어는 로그인 후 신청할 수 있습니다.");
      navigate("/login");
      return;
    }

    setIsApplying(true);
  };
  const handleCreate = async () => {
    if (isCreating) return;

    if (
      !form.travelDate ||
      !form.travelDays ||
      !form.peopleCount ||
      !form.budget ||
      !form.radius ||
      !form.departure ||
      !form.style
    ) {
      alert("모든 여행 조건을 입력해주세요.");
      return;
    }

    setIsCreating(true);

    try {
      const result = await createMysteryTour({
        travelDate: form.travelDate,
        travelDays: Number(form.travelDays),
        peopleCount: Number(form.peopleCount),
        budget: Number(form.budget),
        radiusKm: Number(form.radius),
        departure: form.departure,
        travelStyle: form.style,
      });

      console.log("미스터리 투어 생성 성공:", result);

      setCreatedTour(result);
    } catch (error) {
      console.error("미스터리 투어 생성 실패:", error);
      alert("미스터리 투어 생성 중 오류가 발생했습니다.");
    } finally {
      setIsCreating(false);
    }
  };
  const handleCancelTour = async () => {
    if (!createdTour) return;

    const confirmed = window.confirm("미스터리 투어 신청을 취소하시겠습니까?");

    if (!confirmed) return;

    try {
      await cancelMysteryTour(createdTour.mysteryTourId);

      setCreatedTour(null);
      setIsApplying(false);

      alert("미스터리 투어 신청이 취소되었습니다.");
    } catch (error) {
      console.error("미스터리 투어 취소 실패:", error);
      alert("미스터리 투어 취소 중 오류가 발생했습니다.");
    }
  };
  const canStartTour =
    createdTour !== null &&
    new Date().toISOString().split("T")[0] >= createdTour.travelDate;
  return (
    <section className="w-full px-6 py-4">
      <div className="max-w-6xl mx-auto">
        {createdTour ? (
          // 완료 화면
          <div className="relative overflow-hidden rounded-[32px] bg-gradient-to-br from-slate-950 via-indigo-950 to-slate-900 px-8 py-14 md:px-14 md:py-16 shadow-2xl border border-violet-500/20">
            <div className="absolute -top-24 -right-24 w-72 h-72 rounded-full bg-violet-500/20 blur-3xl" />
            <div className="absolute -bottom-24 -left-24 w-72 h-72 rounded-full bg-indigo-500/20 blur-3xl" />

            <div className="relative z-10 max-w-2xl mx-auto text-center">
              <div className="text-5xl mb-5">🎉</div>

              <h2 className="text-3xl font-black text-white">
                미스터리 투어 준비 완료!
              </h2>

              <p className="mt-3 text-violet-300 text-sm font-bold">
                목적지는 아직 비밀입니다 🔒
              </p>

              <div className="mt-8 grid grid-cols-2 md:grid-cols-4 gap-3">
                <div className="bg-white/5 border border-white/10 rounded-2xl p-4">
                  <p className="text-[11px] text-slate-500">여행일</p>
                  <p className="mt-1 text-sm font-bold text-white">
                    {createdTour.travelDate}
                  </p>
                </div>

                <div className="bg-white/5 border border-white/10 rounded-2xl p-4">
                  <p className="text-[11px] text-slate-500">여행 기간</p>
                  <p className="mt-1 text-sm font-bold text-white">
                    {createdTour.travelDays === 1
                      ? "당일치기"
                      : `${createdTour.travelDays - 1}박 ${createdTour.travelDays}일`}
                  </p>
                </div>

                <div className="bg-white/5 border border-white/10 rounded-2xl p-4">
                  <p className="text-[11px] text-slate-500">인원</p>
                  <p className="mt-1 text-sm font-bold text-white">
                    {createdTour.peopleCount}명
                  </p>
                </div>

                <div className="bg-white/5 border border-white/10 rounded-2xl p-4">
                  <p className="text-[11px] text-slate-500">예산</p>
                  <p className="mt-1 text-sm font-bold text-white">
                    {Number(createdTour.budget).toLocaleString()}원
                  </p>
                </div>

                <div className="bg-white/5 border border-white/10 rounded-2xl p-4">
                  <p className="text-[11px] text-slate-500">퀘스트</p>
                  <p className="mt-1 text-sm font-bold text-white">
                    {createdTour.questCount}개
                  </p>
                </div>
              </div>

              <div className="mt-8 p-5 rounded-2xl bg-black/20 border border-white/10">
                <p className="text-xs text-slate-400">
                  여행 시작일까지 목적지와 퀘스트는 공개되지 않습니다.
                </p>

                {createdTour.status === "STARTED" ? (
                  <button
                    type="button"
                    onClick={() =>
                      navigate(`/mystery-tour/${createdTour.mysteryTourId}`)
                    }
                    className="mt-4 w-full py-4 rounded-2xl bg-gradient-to-r from-violet-500 to-indigo-500 text-white font-black text-sm hover:scale-[1.01] transition-all"
                  >
                    🚀 미스터리 투어 시작하기 !
                  </button>
                ) : (
                  <button
                    type="button"
                    disabled={!canStartTour}
                    onClick={async () => {
                      if (!createdTour || !canStartTour) return;

                      try {
                        const token = localStorage.getItem("accessToken");

                        await axios.post(
                          `http://localhost:8080/mystery-tours/${createdTour.mysteryTourId}/start`,
                          {},
                          {
                            headers: {
                              Authorization: `Bearer ${token}`,
                            },
                          },
                        );

                        navigate(`/mystery-tour/${createdTour.mysteryTourId}`);
                      } catch (error) {
                        console.error("미스터리 투어 시작 실패:", error);
                        alert("여행 시작 중 오류가 발생했습니다.");
                      }
                    }}
                    className={`mt-4 w-full py-4 rounded-2xl font-black text-sm transition-all ${
                      canStartTour
                        ? "bg-gradient-to-r from-violet-500 to-indigo-500 text-white hover:scale-[1.01] cursor-pointer"
                        : "bg-slate-700 text-slate-400 cursor-not-allowed"
                    }`}
                  >
                    {canStartTour
                      ? "🚀 여행 시작하기"
                      : `🔒 ${createdTour.travelDate}에 여행이 시작됩니다`}
                  </button>
                )}
                {createdTour.status === "READY" && (
                  <button
                    type="button"
                    onClick={handleCancelTour}
                    className="mt-3 w-full py-3 rounded-2xl border border-red-500/20 bg-red-500/5 text-red-300 text-sm font-bold hover:bg-red-500/10 transition-all"
                  >
                    미스터리 투어 신청 취소
                  </button>
                )}
              </div>
            </div>
          </div>
        ) : isApplying ? (
          /* =========================
           미스터리 투어 신청 화면
        ========================== */
          <div className="relative overflow-hidden rounded-[32px] bg-slate-950 px-8 py-10 md:px-14 md:py-12 shadow-2xl border border-violet-500/20">
            {/* 게임 느낌 배경 */}
            <div className="absolute inset-0 overflow-hidden pointer-events-none select-none">
              <span className="absolute top-5 left-[8%] text-7xl font-black text-white/[0.03] rotate-12">
                ?
              </span>
              <span className="absolute top-24 right-[10%] text-9xl font-black text-violet-400/[0.05] -rotate-12">
                ?
              </span>
              <span className="absolute bottom-4 left-[20%] text-8xl font-black text-indigo-400/[0.05] rotate-6">
                ?
              </span>
              <span className="absolute bottom-20 right-[25%] text-6xl font-black text-white/[0.03]">
                ?
              </span>
            </div>

            <div className="absolute -top-32 -right-32 w-80 h-80 bg-violet-600/20 rounded-full blur-3xl" />
            <div className="absolute -bottom-32 -left-32 w-80 h-80 bg-indigo-600/20 rounded-full blur-3xl" />

            <div className="relative z-10 max-w-3xl mx-auto">
              <div className="text-center mb-8">
                <span className="inline-block px-3 py-1 rounded-full bg-violet-500/10 border border-violet-400/20 text-violet-300 text-[11px] font-bold tracking-[0.2em]">
                  MYSTERY TOUR APPLICATION
                </span>

                <h2 className="mt-4 text-3xl font-black text-white">
                  당신의 미스터리 여행을 준비합니다
                </h2>

                <p className="mt-2 text-sm text-slate-400">
                  조건만 알려주세요. 목적지는 AI에게 맡겨주세요.
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                {/* 여행 날짜 */}
                <div>
                  <label className="block mb-2 text-xs font-bold text-slate-300">
                    📅 여행 날짜
                  </label>
                  <input
                    type="date"
                    name="travelDate"
                    value={form.travelDate}
                    onChange={handleFormChange}
                    min={new Date().toISOString().split("T")[0]}
                    onClick={(e) => e.currentTarget.showPicker()}
                    className="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white outline-none focus:border-violet-400 cursor-pointer"
                  />
                </div>

                <div>
                  <label className="block mb-2 text-xs font-bold text-slate-300">
                    🗓 여행 기간
                  </label>

                  <select
                    name="travelDays"
                    value={form.travelDays}
                    onChange={handleFormChange}
                    className="w-full px-4 py-3 rounded-xl bg-slate-900 border border-white/10 text-white outline-none focus:border-violet-400"
                  >
                    <option value="1">당일치기</option>
                    <option value="2">1박 2일</option>
                    <option value="3">2박 3일</option>
                    <option value="4">3박 4일</option>
                    <option value="5">4박 5일</option>
                  </select>
                </div>

                {/* 인원 */}
                <div>
                  <label className="block mb-2 text-xs font-bold text-slate-300">
                    👥 여행 인원
                  </label>
                  <input
                    type="number"
                    name="peopleCount"
                    value={form.peopleCount}
                    onChange={handleFormChange}
                    min="1"
                    placeholder="2"
                    className="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder:text-slate-600 outline-none focus:border-violet-400"
                  />
                </div>

                {/* 예산 */}
                <div>
                  <label className="block mb-2 text-xs font-bold text-slate-300">
                    💰 총 예산
                  </label>
                  <div className="relative">
                    <input
                      type="number"
                      name="budget"
                      value={form.budget}
                      onChange={handleFormChange}
                      min="0"
                      placeholder="200000"
                      className="w-full px-4 py-3 pr-10 rounded-xl bg-white/5 border border-white/10 text-white placeholder:text-slate-600 outline-none focus:border-violet-400"
                    />
                    <span className="absolute right-4 top-1/2 -translate-y-1/2 text-xs text-slate-500">
                      원
                    </span>
                  </div>
                </div>

                {/* 이동 반경 */}
                <div>
                  <label className="block mb-2 text-xs font-bold text-slate-300">
                    📍 최대 이동 반경
                  </label>
                  <select
                    className="w-full px-4 py-3 rounded-xl bg-slate-900 border border-white/10 text-white outline-none focus:border-violet-400"
                    name="radius"
                    value={form.radius}
                    onChange={handleFormChange}
                    defaultValue=""
                  >
                    <option value="" disabled>
                      반경 선택
                    </option>
                    <option value="30">30km</option>
                    <option value="50">50km</option>
                    <option value="100">100km</option>
                    <option value="200">200km</option>
                  </select>
                </div>

                {/* 출발지 */}
                <div className="md:col-span-2">
                  <label className="block mb-2 text-xs font-bold text-slate-300">
                    🏠 출발 위치
                  </label>
                  <input
                    type="text"
                    name="departure"
                    value={form.departure}
                    onChange={handleFormChange}
                    placeholder="예: 서울 마포구 상암동"
                    className="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder:text-slate-600 outline-none focus:border-violet-400"
                  />
                </div>
              </div>

              {/* 여행 스타일 */}
              <div className="mt-7">
                <label className="block mb-3 text-xs font-bold text-slate-300">
                  🎮 어떤 여행을 원하세요?
                </label>

                <div className="grid grid-cols-2 md:grid-cols-6 gap-2">
                  {[
                    ["🌿", "힐링"],
                    ["🍜", "먹방"],
                    ["🎢", "액티비티"],
                    ["📸", "감성"],
                    ["❤️", "데이트"],
                    ["🎲", "완전 랜덤"],
                  ].map(([emoji, label]) => (
                    <button
                      key={label}
                      type="button"
                      onClick={() =>
                        setForm((prev) => ({
                          ...prev,
                          style: label,
                        }))
                      }
                      className={`py-3 rounded-xl border text-xs font-bold transition-all ${
                        form.style === label
                          ? "bg-violet-500/30 border-violet-400 text-white scale-[1.03]"
                          : "bg-white/5 border-white/10 text-slate-300 hover:bg-violet-500/20 hover:border-violet-400/40 hover:text-white"
                      }`}
                    >
                      <span className="block text-xl mb-1">{emoji}</span>
                      {label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="mt-8 flex gap-3">
                <button
                  type="button"
                  onClick={() => setIsApplying(false)}
                  className="px-6 py-3.5 rounded-xl border border-white/10 text-slate-400 text-xs font-bold hover:bg-white/5 transition-all"
                >
                  ← 돌아가기
                </button>

                <button
                  type="button"
                  onClick={handleCreate}
                  disabled={isCreating}
                  className={`flex-1 py-3.5 rounded-xl text-white text-sm font-black transition-all shadow-lg ${
                    isCreating
                      ? "bg-slate-700 cursor-not-allowed"
                      : "bg-gradient-to-r from-violet-500 to-indigo-500 hover:scale-[1.01] shadow-violet-950/40"
                  }`}
                >
                  {isCreating
                    ? "🎁 AI가 미스터리 투어를 준비하고 있습니다..."
                    : "🎲 미스터리 투어 생성하기"}
                </button>
              </div>

              <p className="text-center mt-4 text-[10px] text-slate-600">
                선택한 조건을 바탕으로 AI가 목적지와 퀘스트를 비밀리에
                생성합니다.
              </p>
            </div>
          </div>
        ) : (
          <div className="relative overflow-hidden rounded-[32px] bg-gradient-to-br from-slate-900 via-indigo-950 to-slate-900 px-8 py-14 md:px-14 md:py-16 shadow-2xl">
            {/* 배경 장식 */}
            <div className="absolute -top-24 -right-24 w-72 h-72 rounded-full bg-violet-500/20 blur-3xl" />
            <div className="absolute -bottom-24 -left-24 w-72 h-72 rounded-full bg-sky-500/20 blur-3xl" />

            <div className="relative z-10 text-center">
              <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-white/10 border border-white/10 text-violet-200 text-xs font-bold tracking-widest">
                <Sparkles size={14} />
                AI MYSTERY TRAVEL
              </div>

              <div className="mt-6 flex justify-center">
                <div className="w-16 h-16 rounded-2xl bg-white/10 border border-white/10 flex items-center justify-center shadow-xl">
                  <Gift size={30} className="text-violet-300" />
                </div>
              </div>

              <h2 className="mt-6 text-3xl md:text-4xl font-black text-white">
                어디로 떠날지는 아직 비밀입니다
              </h2>

              <p className="mt-4 max-w-2xl mx-auto text-sm md:text-base text-slate-300 leading-7">
                여행 조건만 선택하세요.
                <br />
                AI가 목적지부터 일정과 특별한 퀘스트까지 당신만의 미스터리
                여행을 준비합니다.
              </p>

              {/* 특징 */}
              <div className="mt-9 flex flex-wrap justify-center gap-3">
                <div className="flex items-center gap-2 px-4 py-2 rounded-xl bg-white/5 border border-white/10 text-xs text-slate-300">
                  <LockKeyhole size={15} className="text-violet-300" />
                  목적지 비공개
                </div>

                <div className="flex items-center gap-2 px-4 py-2 rounded-xl bg-white/5 border border-white/10 text-xs text-slate-300">
                  <Sparkles size={15} className="text-sky-300" />
                  AI 맞춤 여행
                </div>

                <div className="flex items-center gap-2 px-4 py-2 rounded-xl bg-white/5 border border-white/10 text-xs text-slate-300">
                  <Trophy size={15} className="text-amber-300" />
                  퀘스트 & 포인트
                </div>
              </div>

              <button
                type="button"
                onClick={handleApply}
                className="mt-10 px-8 py-4 rounded-2xl bg-gradient-to-r from-violet-500 to-indigo-500 text-white font-black text-sm shadow-xl shadow-violet-950/30 hover:scale-105 hover:shadow-violet-500/20 transition-all duration-300"
              >
                🎁 미스터리 투어 신청하기
              </button>

              <p className="mt-4 text-[11px] text-slate-500">
                여행 당일까지 목적지는 공개되지 않습니다.
              </p>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}
