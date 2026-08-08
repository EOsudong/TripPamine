// 메인 페이지 최상단 히어로 섹션.
// - 배경 이미지 + "AI 여행 조건 입력 폼"(인원/일정/예산/테마)이 핵심
// - 폼 제출 시 더미로 2단계 로딩 → 결과 카드로 전환되는 흐름을 보여줌 (실제 AI 연동은 아직 없음)
// - 하단에는 AI 테마 칩과 통계 바가 있음
import { useState } from "react"
import type { ReactNode } from "react"
import { travelTypes } from "../data/categories"
import type { PlannerForm } from "../types"

export default function Hero() {
  // 폼 입력값을 하나의 객체 상태로 관리 (인원/기간/예산/테마/추가요청)
  const [form, setForm] = useState<PlannerForm>({
    people: "",
    startDate: "",
    endDate: "",
    budget: "",
    travelType: "",
    extra: "",
  })
  const [aiLoading, setAiLoading] = useState(false) // "AI가 플랜을 생성 중"인지 여부 (버튼 로딩 스피너 표시용)
  const [aiResult, setAiResult] = useState("") // AI가 만들어준 추천 플랜 텍스트. 값이 있으면 폼 대신 결과 화면을 보여줌

  // 필수 항목(인원/시작일/종료일/예산/테마)이 모두 채워졌는지 확인 → 제출 버튼 활성화 조건
  function canSubmit() {
    return !!form.people && !!form.startDate && !!form.endDate && !!form.budget && !!form.travelType
  }

  // 제출 버튼 클릭 시 실행.
  // 지금은 실제 AI API 대신 1.8초 대기 후 미리 정해둔 문구를 채워 넣는 "가짜(mock) 응답"입니다.
  // 나중에 실제 AI 추천 기능을 붙일 때는 이 부분을 fetch(API 호출)로 교체하면 됩니다.
  async function handleSubmit() {
    setAiLoading(true)
    setAiResult("")
    // TODO: 실제 AI 추천 API 연동
    await new Promise((r) => setTimeout(r, 1800))
    setAiResult(
      `✈️ **${form.people} · ${form.startDate} ~ ${form.endDate} · ${form.travelType}** 여행 추천 플랜\n\n` +
        `예산 **${form.budget}** 기준으로 아래 코스를 추천드립니다:\n\n` +
        `📍 **1일차** — 제주 공항 도착 → 성산일출봉 트레킹 → 우도 자전거 투어 → 숙소 체크인\n` +
        `📍 **2일차** — 한라산 영실 코스 (3시간) → 천지연 폭포 → 서귀포 매일올레시장 저녁\n` +
        `📍 **3일차** — 협재 해수욕장 → 오설록 티뮤지엄 → 귀가\n\n` +
        `💡 ${form.extra ? `"${form.extra}" 요청을 반영해 일정을 구성했습니다.` : "추가 요청 사항이 없어 기본 코스로 구성했습니다."}`,
    )
    setAiLoading(false)
  }

  // "다시 만들기" 클릭 시 결과를 지우고 입력 폼을 초기 상태로 되돌림
  function handleReset() {
    setAiResult("")
    setForm({ people: "", startDate: "", endDate: "", budget: "", travelType: "", extra: "" })
  }

  return (
    <section id="ai-planner" className="relative min-h-[680px] flex items-center justify-center overflow-hidden">
      {/* 배경 사진 + 위에 어두운 그라데이션을 덮어서 흰색 글씨가 잘 보이도록 처리 */}
      <img
        src="https://images.unsplash.com/photo-1559827291-72ee739d0d9a?w=1600&h=900&fit=crop&auto=format"
        alt="Korean coastal travel"
        className="absolute inset-0 w-full h-full object-cover"
      />
      <div className="absolute inset-0 bg-gradient-to-b from-sky-900/55 via-sky-800/35 to-slate-900/70" />

      <div className="relative z-10 w-full max-w-3xl mx-auto px-4 pt-14 pb-24 text-center">
        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/15 border border-white/25 text-sky-200 text-xs font-semibold tracking-widest uppercase mb-4 backdrop-blur-sm">
          <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
            <path d="M13 10V3L4 14h7v7l9-11h-7z" />
          </svg>
          AI Travel Planner
        </span>
        <h1 className="text-3xl md:text-5xl font-bold text-white leading-tight mb-2 drop-shadow-lg">
          어떤 여행을 하고싶으신가요?
        </h1>
        <p className="text-white/75 text-base mb-8">AI가 당신만의 국내 여행 플랜을 짜드립니다</p>

        {/* AI 여행 플래너 카드 — 흰색 카드 하나 안에서 "입력 폼"과 "결과 화면"을
            aiResult 값의 유무로 서로 바꿔가며 보여줌 (조건부 렌더링) */}
        <div className="bg-white/96 backdrop-blur-sm rounded-3xl shadow-2xl overflow-hidden text-left">
          {!aiResult ? (
            // ── 결과가 아직 없을 때: 입력 폼 화면 ──────────────────────
            <div className="p-6 space-y-5">
              {/* Card header */}
              <div className="flex items-center gap-2.5 pb-3 border-b border-slate-100">
                <div className="w-8 h-8 rounded-xl bg-sky-500 flex items-center justify-center shrink-0">
                  <svg className="w-4 h-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                  </svg>
                </div>
                <div>
                  <p className="font-bold text-slate-800 text-sm">AI 여행 플래너</p>
                  <p className="text-xs text-slate-400">조건을 입력하면 맞춤 국내 여행 코스를 추천해드려요</p>
                </div>
              </div>

              {/* Row 1: 인원 + 일정
                  FieldBlock = "①번 매겨진 라벨 + 내용" 틀을 잡아주는 공용 컴포넌트 (파일 맨 아래 정의)
                  ChipBtn    = 선택형 버튼 하나 (선택되면 파란 테두리로 강조되는 공용 컴포넌트) */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                <FieldBlock num={1} label="여행 인원">
                  <div className="grid grid-cols-4 gap-2">
                    {["1명", "2명", "3~4명", "5명+"].map((opt) => (
                      <ChipBtn
                        key={opt}
                        label={opt}
                        active={form.people === opt}
                        onClick={() => setForm((f) => ({ ...f, people: opt }))}
                      />
                    ))}
                  </div>
                </FieldBlock>

                <FieldBlock num={2} label="여행 일정">
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="text-[10px] text-slate-400 block mb-1">출발일</label>
                      <input
                        type="date"
                        value={form.startDate}
                        onChange={(e) => setForm((f) => ({ ...f, startDate: e.target.value }))}
                        className="w-full px-3 py-2 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-xs text-slate-700 transition-colors"
                      />
                    </div>
                    <div>
                      <label className="text-[10px] text-slate-400 block mb-1">귀가일</label>
                      <input
                        type="date"
                        value={form.endDate}
                        onChange={(e) => setForm((f) => ({ ...f, endDate: e.target.value }))}
                        className="w-full px-3 py-2 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-xs text-slate-700 transition-colors"
                      />
                    </div>
                  </div>
                </FieldBlock>
              </div>

              {/* Row 2: 예산 */}
              <FieldBlock num={3} label="1인당 예산">
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                  {["10만원 이하", "10~30만원", "30~50만원", "50만원 이상"].map((opt) => (
                    <ChipBtn
                      key={opt}
                      label={opt}
                      active={form.budget === opt}
                      onClick={() => setForm((f) => ({ ...f, budget: opt }))}
                    />
                  ))}
                </div>
              </FieldBlock>

              {/* Row 3: 여행 유형(테마) */}
              <FieldBlock num={4} label="여행 유형">
                <div className="grid grid-cols-3 sm:grid-cols-6 gap-2">
                  {travelTypes.map((opt) => (
                    <ChipBtn
                      key={opt}
                      label={opt}
                      active={form.travelType === opt}
                      onClick={() => setForm((f) => ({ ...f, travelType: opt }))}
                    />
                  ))}
                </div>
              </FieldBlock>

              {/* Row 4: 추가 요청 */}
              <FieldBlock num={5} label="추가 요청" optional>
                <textarea
                  value={form.extra}
                  onChange={(e) => setForm((f) => ({ ...f, extra: e.target.value }))}
                  placeholder="예) 반려동물 동반, 어린이 포함, 특정 지역 꼭 방문 등"
                  rows={2}
                  className="w-full px-4 py-3 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-xs text-slate-700 resize-none transition-colors placeholder-slate-400"
                />
              </FieldBlock>

              {/* 제출 버튼: 필수값 미입력(canSubmit()==false) 또는 로딩 중이면 비활성화(회색) 처리 */}
              <button
                onClick={handleSubmit}
                disabled={!canSubmit() || aiLoading}
                className={`w-full py-3.5 rounded-2xl font-bold text-sm flex items-center justify-center gap-2 transition-all ${
                  canSubmit() && !aiLoading
                    ? "bg-sky-500 hover:bg-sky-600 text-white shadow-md shadow-sky-200"
                    : "bg-slate-100 text-slate-400 cursor-not-allowed"
                }`}
              >
                {aiLoading ? (
                  <>
                    <span className="w-4 h-4 rounded-full border-2 border-white border-t-transparent animate-spin" />
                    AI가 플랜을 생성하고 있어요...
                  </>
                ) : (
                  <>
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                    </svg>
                    AI 여행 플랜 생성하기
                  </>
                )}
              </button>
            </div>
          ) : (
            // ── aiResult에 값이 채워지면: 결과 화면으로 전환 ──────────────
            <div className="p-6">
              <div className="flex items-center gap-2 mb-4">
                <div className="w-7 h-7 rounded-full bg-sky-500 flex items-center justify-center shrink-0">
                  <svg className="w-4 h-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                </div>
                <p className="font-bold text-slate-800">AI 여행 플랜이 완성됐습니다!</p>
              </div>
              <div className="bg-sky-50 rounded-2xl p-4 text-sm text-slate-700 leading-relaxed whitespace-pre-line mb-4">
                {aiResult}
              </div>
              <div className="flex gap-3">
                <button
                  onClick={handleReset}
                  className="flex-1 py-2.5 rounded-xl border-2 border-slate-200 text-slate-600 text-sm font-semibold hover:border-sky-300 transition-colors"
                >
                  다시 만들기
                </button>
                <button className="flex-1 py-2.5 rounded-xl bg-sky-500 hover:bg-sky-600 text-white text-sm font-semibold transition-colors">
                  🔖 플랜 저장하기
                </button>
              </div>
            </div>
          )}
        </div>

        {/* AI 테마 추천 칩 — 클릭하면 위 폼의 '여행 유형'이 자동으로 채워집니다 */}
        <div className="flex flex-wrap justify-center items-center gap-2 mt-7">
          <span className="hidden sm:inline text-white/60 text-xs mr-1">이런 테마는 어때요?</span>
          {travelTypes.map((t) => (
            <button
              key={t}
              onClick={() => setForm((f) => ({ ...f, travelType: t }))}
              className={`px-3 py-1 text-xs font-semibold rounded-full backdrop-blur-sm border transition-colors ${
                form.travelType === t
                  ? "bg-white text-sky-600 border-white"
                  : "text-white/90 bg-white/20 hover:bg-white/30 border-white/30"
              }`}
            >
              #{t}
            </button>
          ))}
        </div>
      </div>

      {/* Stats bar — AI/축제 연계 강점 중심 지표 */}
      <div className="absolute bottom-0 left-0 right-0 bg-slate-900/40 backdrop-blur-md border-t border-white/20">
        <div className="max-w-4xl mx-auto px-4 py-4 flex justify-around text-center">
          {[
            ["12,400+", "AI 추천 완료"],
            ["32곳", "연계 축제"],
            [`${travelTypes.length}가지`, "여행 테마"],
            ["4.8★", "평균 추천 만족도"],
          ].map(([val, label]) => (
            <div key={label}>
              <div className="text-white font-bold text-base md:text-lg">{val}</div>
              <div className="text-white/70 text-xs">{label}</div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

// 폼의 각 항목(①여행 인원, ②여행 일정 ...)마다 반복되는
// "번호 배지 + 라벨 + 내용" 레이아웃을 재사용하기 위해 분리한 작은 컴포넌트
interface FieldBlockProps {
  num: number
  label: string
  optional?: boolean
  children: ReactNode
}

function FieldBlock({ num, label, optional, children }: FieldBlockProps) {
  return (
    <div>
      <p className="text-xs font-bold text-slate-600 mb-2 flex items-center gap-1.5">
        <span
          className={`w-4 h-4 rounded-full text-[10px] font-bold flex items-center justify-center ${
            optional ? "bg-slate-100 text-slate-400" : "bg-sky-100 text-sky-600"
          }`}
        >
          {num}
        </span>
        {label}
        {optional && <span className="text-slate-400 font-normal">(선택)</span>}
      </p>
      {children}
    </div>
  )
}

// 인원/예산/여행유형 선택에 공통으로 쓰이는 "선택형 칩 버튼".
// active가 true면 파란 테두리+배경으로 선택된 상태를 표시
interface ChipBtnProps {
  label: string
  active: boolean
  onClick: () => void
}

function ChipBtn({ label, active, onClick }: ChipBtnProps) {
  return (
    <button
      onClick={onClick}
      className={`py-2.5 px-1 rounded-xl text-xs font-semibold border-2 transition-all ${
        active
          ? "border-sky-500 bg-sky-50 text-sky-600"
          : "border-slate-200 text-slate-500 hover:border-sky-300 hover:text-sky-500"
      }`}
    >
      {label}
    </button>
  )
}
