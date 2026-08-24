import { useState, useEffect } from "react";
import type { ChangeEvent, FormEvent } from "react";
import type { TravelPlan, TravelPlanFormState } from "../types";
import {
  getTravelPlansApi,
  createTravelPlanApi,
  updateTravelPlanApi,
  deleteTravelPlanApi,
} from "../api/travel";
import { useNavigate } from "react-router-dom";

// 가계부(AccountBook)는 마이페이지(/mypage)로 이동했습니다.
// 로그인 여부와 무관하게 노출되던 메인 페이지 대신, 로그인 후에만 접근 가능한
// 마이페이지 탭에서 확인할 수 있어요. (src/pages/MyPage.tsx 참고)

export default function Hero() {
  const [plans, setPlans] = useState<TravelPlan[]>([]);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);

  const [form, setForm] = useState<TravelPlanFormState>({
    planName: "",
    totalBudget: "",
    companionType: "",
    blindYn: "N",
    startDate: "",
    endDate: "",
  });

  // 여행 목록 조회
  const loadPlans = async () => {
    try {
      const data = await getTravelPlansApi();
      setPlans(data);
    } catch (error) {
      console.error("여행 계획 조회 실패:", error);
    }
  };

  useEffect(() => {
    loadPlans();
  }, []);

  // 입력값 변경 핸들러
  const handleChange = (
      e: ChangeEvent<HTMLInputElement | HTMLSelectElement>,
  ) => {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  };

  // 폼 초기화
  const resetForm = () => {
    setEditingId(null);
    setForm({
      planName: "",
      totalBudget: "",
      companionType: "",
      blindYn: "N",
      startDate: "",
      endDate: "",
    });
  };

  // 등록 / 수정 제출
  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();

    const requestData = {
      planName: form.planName,
      totalBudget: Number(form.totalBudget),
      companionType: form.companionType || null,
      blindYn: form.blindYn,
      startDate:
          form.startDate && form.startDate.trim() !== "" ? form.startDate : null,
      endDate: form.endDate && form.endDate.trim() !== "" ? form.endDate : null,
    };

    try {
      setLoading(true);
      if (editingId) {
        await updateTravelPlanApi(editingId, requestData);
        alert("여행 계획이 수정되었습니다!");
      } else {
        await createTravelPlanApi(requestData);
        alert("여행 계획이 등록되었습니다!");
      }
      resetForm();
      loadPlans();
    } catch (error) {
      console.error("저장 실패:", error);
      alert("저장 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  // 수정 시작
  const handleEdit = (plan: TravelPlan) => {
    setEditingId(plan.planId);
    setForm({
      planName: plan.planName,
      totalBudget: String(plan.totalBudget),
      companionType: plan.companionType || "",
      blindYn: plan.blindYn || "N",
      startDate: plan.startDate ? plan.startDate.slice(0, 16) : "",
      endDate: plan.endDate ? plan.endDate.slice(0, 16) : "",
    });

    document
        .getElementById("ai-planner")
        ?.scrollIntoView({ behavior: "smooth" });
  };

  // 삭제
  const handleDelete = async (planId: number) => {
    if (!window.confirm("정말 이 여행 계획을 삭제하시겠습니까?")) return;

    try {
      await deleteTravelPlanApi(planId);
      alert("여행 계획이 삭제되었습니다.");
      loadPlans();
    } catch (error) {
      console.error("삭제 실패:", error);
      alert("삭제 중 오류가 발생했습니다.");
    }
  };

  const companionLabel = (type: string | null) => {
    switch (type) {
      case "ALONE":
        return "혼자";
      case "FRIEND":
        return "친구";
      case "FAMILY":
        return "가족";
      case "COUPLE":
        return "연인";
      default:
        return type || "미선택";
    }
  };

  const navigate = useNavigate();

  const now = new Date();
  const minDateTime = new Date(now.getTime() - now.getTimezoneOffset() * 60000)
      .toISOString()
      .slice(0, 16);

  return (
      <section
          id="ai-planner"
          className="relative min-h-[680px] flex items-center justify-center overflow-hidden py-10"
      >
        {/* 배경 사진 + 그라데이션 */}
        <img
            src="https://images.unsplash.com/photo-1559827291-72ee739d0d9a?w=1600&h=900&fit=crop&auto=format"
            alt="Korean coastal travel"
            className="absolute inset-0 w-full h-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-b from-sky-900/55 via-sky-800/35 to-slate-900/70" />

        <div className="relative z-10 w-full max-w-6xl mx-auto px-4 pt-10 pb-16">
          {/* 상단 텍스트 */}
          <div className="text-center mb-6">
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/15 border border-white/25 text-sky-200 text-xs font-semibold tracking-widest uppercase mb-3 backdrop-blur-sm">
            ✈ TripPamine Manager
          </span>
            <h1 className="text-3xl md:text-5xl font-bold text-white leading-tight mb-2 drop-shadow-lg">
              나의 여행 계획 관리
            </h1>
            <p className="text-white/80 text-sm md:text-base">
              여행 일정과 예산을 등록하고 간편하게 관리해보세요
            </p>
          </div>

          {/* 여행 플래너 2열 레이아웃 */}
          <div className="grid grid-cols-1 lg:grid-cols-[400px_1fr] gap-6 items-start text-left">
            {/* 1열: 등록 / 수정 폼 */}
            <div className="bg-white/95 backdrop-blur-sm rounded-3xl shadow-2xl p-6 py-13 border border-white/40">
              <div className="flex items-center justify-between mb-5 pb-3 border-b border-slate-100">
                <span className="px-3 py-1 rounded-full bg-sky-100 text-sky-700 text-xs font-bold">
                  {editingId ? "EDIT PLAN" : "NEW PLAN"}
                </span>
                <h3 className="font-bold text-slate-800 text-lg">
                  {editingId ? "여행 계획 수정" : "새 여행 계획 생성"}
                </h3>
              </div>

              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    여행 이름
                  </label>
                  <input
                      type="text"
                      name="planName"
                      value={form.planName}
                      onChange={handleChange}
                      placeholder="예: 제주도 3박 4일 여행"
                      required
                      className="w-full px-3.5 py-2.5 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-xs text-slate-800 transition-colors bg-white"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    총 예산
                  </label>
                  <div className="relative">
                    <input
                        type="number"
                        name="totalBudget"
                        min="0"
                        value={form.totalBudget}
                        onChange={handleChange}
                        placeholder="500000"
                        required
                        className="w-full px-3.5 py-2.5 pr-8 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-xs text-slate-800 transition-colors bg-white"
                    />
                    <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-slate-400">
                      원
                    </span>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      동행자
                    </label>
                    <select
                        name="companionType"
                        value={form.companionType}
                        onChange={handleChange}
                        className="w-full px-3 py-2.5 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-xs text-slate-800 transition-colors bg-white"
                    >
                      <option value="">선택</option>
                      <option value="ALONE">혼자</option>
                      <option value="FRIEND">친구</option>
                      <option value="FAMILY">가족</option>
                      <option value="COUPLE">연인</option>
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      미스터리 투어
                    </label>
                    <select
                        name="blindYn"
                        value={form.blindYn}
                        onChange={handleChange}
                        className="w-full px-3 py-2.5 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-xs text-slate-800 transition-colors bg-white"
                    >
                      <option value="N">신청 안 함</option>
                      <option value="Y">신청</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    출발 일시
                  </label>
                  <input
                      type="datetime-local"
                      name="startDate"
                      value={form.startDate}
                      onChange={handleChange}
                      min={minDateTime}
                      className="w-full px-3 py-2.5 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-xs text-slate-800 transition-colors bg-white"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    종료 일시
                  </label>
                  <input
                      type="datetime-local"
                      name="endDate"
                      value={form.endDate}
                      onChange={handleChange}
                      min={form.startDate || minDateTime}
                      className="w-full px-3 py-2.5 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-xs text-slate-800 transition-colors bg-white"
                  />
                </div>

                <div className="flex gap-2 pt-2">
                  <button
                      type="submit"
                      disabled={loading}
                      className="flex-1 py-3 bg-sky-500 hover:bg-sky-600 text-white font-bold text-xs rounded-xl transition-colors shadow-md shadow-sky-200"
                  >
                    {loading
                        ? "저장 중..."
                        : editingId
                            ? "수정 완료"
                            : "여행 등록"}
                  </button>
                  {editingId && (
                      <button
                          type="button"
                          onClick={resetForm}
                          className="px-4 py-3 border-2 border-slate-200 text-slate-600 font-semibold text-xs rounded-xl hover:bg-slate-50 transition-colors"
                      >
                        취소
                      </button>
                  )}
                </div>
              </form>
            </div>

            {/* 2열: 등록된 여행 카드리스트 */}
            <div className="space-y-4 max-h-[600px] overflow-y-auto pr-1">
              <div className="flex items-center justify-between text-white mb-2">
                <h3 className="font-bold text-lg drop-shadow">
                  등록된 여행 목록
                </h3>

                <div className="flex items-center gap-2">
                  <span className="px-3 py-1 bg-white/20 backdrop-blur-md rounded-full text-xs font-semibold">
                    총 {plans.length}개
                  </span>

                  <button
                      type="button"
                      onClick={() => navigate("/ai-recommend")}
                      className="  px-4 py-2 bg-gradient-to-r from-sky-500 to-indigo-500 hover:from-sky-600 hover:to-indigo-600 text-white text-xs font-bold rounded-xl shadow-lg shadow-sky-500/30 border border-white/30 transition-all duration-300 hover:scale-105 active:scale-95"
                  >
                    ✨ AI 추천 보기
                  </button>
                </div>
              </div>

              {plans.length === 0 ? (
                  <div className="bg-white/90 backdrop-blur-sm rounded-3xl p-10 text-center border border-white/50 shadow-xl">
                    <div className="text-4xl mb-3">🌏</div>
                    <h4 className="font-bold text-slate-700 text-base">
                      등록된 여행이 없습니다.
                    </h4>
                    <p className="text-xs text-slate-400 mt-1">
                      좌측 폼에서 첫 번째 여행 계획을 세워보세요!
                    </p>
                  </div>
              ) : (
                  plans.map((plan) => (
                      <div
                          key={plan.planId}
                          className="bg-white rounded-2xl p-5 shadow-lg border border-slate-100 flex flex-col justify-between transition-all hover:shadow-xl"
                      >
                        <div>
                          <div className="flex items-center justify-between gap-2 mb-2">
                        <span className="px-2.5 py-0.5 bg-sky-50 text-sky-600 text-[10px] font-bold rounded-full">
                          ✈ TRAVEL
                        </span>
                            {plan.blindYn === "Y" && (
                                <span className="px-2.5 py-0.5 bg-purple-50 text-purple-600 text-[10px] font-bold rounded-full">
                            🎁 MYSTERY TOUR
                          </span>
                            )}
                          </div>
                          <h4 className="font-bold text-slate-800 text-lg mb-3">
                            {plan.planName}
                          </h4>

                          <div className="grid grid-cols-2 gap-2 text-xs text-slate-600 mb-3">
                            <div className="bg-slate-50 p-2.5 rounded-xl">
                          <span className="text-slate-400 block text-[10px] mb-0.5">
                            총 예산
                          </span>
                              <span className="font-bold text-slate-700">
                            💰 {Number(plan.totalBudget).toLocaleString()}원
                          </span>
                            </div>
                            <div className="bg-slate-50 p-2.5 rounded-xl">
                          <span className="text-slate-400 block text-[10px] mb-0.5">
                            동행자
                          </span>
                              <span className="font-bold text-slate-700">
                            👥 {companionLabel(plan.companionType)}
                          </span>
                            </div>
                          </div>

                          {(plan.startDate || plan.endDate) && (
                              <div className="bg-emerald-50 px-3 py-2 rounded-xl text-xs text-emerald-800 flex items-center gap-1.5 mb-3">
                                <span>📅</span>
                                <span>
                            {plan.startDate
                                ? plan.startDate.slice(0, 10)
                                : "미정"}{" "}
                                  ~{" "}
                                  {plan.endDate ? plan.endDate.slice(0, 10) : "미정"}
                          </span>
                              </div>
                          )}
                        </div>

                        <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
                          <button
                              onClick={() => handleEdit(plan)}
                              className="px-3 py-1.5 bg-sky-50 hover:bg-sky-100 text-sky-600 text-xs font-bold rounded-lg transition-colors"
                          >
                            수정
                          </button>
                          <button
                              onClick={() => handleDelete(plan.planId)}
                              className="px-3 py-1.5 bg-red-50 hover:bg-red-100 text-red-500 text-xs font-bold rounded-lg transition-colors"
                          >
                            삭제
                          </button>
                        </div>
                      </div>
                  ))
              )}
            </div>
          </div>
        </div>
      </section>
  );
}



