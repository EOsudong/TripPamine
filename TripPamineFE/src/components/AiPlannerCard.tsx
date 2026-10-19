// "AI 코스 추천 기능 소개/미리보기" 섹션.
// Hero.jsx의 실제 입력 폼과는 별개로, "AI가 어떻게 추천해주는지"를 설명하는 정적인 소개 카드입니다.
// (사용자 입력을 받지 않는 순수 소개용 컴포넌트라 상태(state)가 없음)

// 좌측에 표시할 3단계 설명 (조건 입력 → AI 추천 → 축제 연계)
const steps = [
  {
    num: "01",
    title: "조건 입력",
    desc: "인원, 일정, 예산, 테마를 한 번에 선택하세요",
  },
  {
    num: "02",
    title: "AI 코스 추천",
    desc: "조건에 맞는 여행지와 일자별 코스를 자동으로 생성해요",
  },
  {
    num: "03",
    title: "축제·행사 연계",
    desc: "추천 지역의 진행 중·예정 축제 정보를 함께 보여드려요",
  },
];

export default function AiPlannerCard() {
  return (
    <section className="relative overflow-hidden bg-night-950 px-4 py-20 text-white">
      <div className="pointer-events-none absolute inset-0 tp-aurora opacity-70" />
      <div className="pointer-events-none absolute inset-0 tp-dot-grid opacity-30" />

      <div className="relative mx-auto grid max-w-6xl grid-cols-1 items-center gap-12 lg:grid-cols-2">
        {/* 좌측: 소개 텍스트 + 단계 */}
        <div>
          <p className="mb-3 text-[11px] font-black uppercase tracking-[0.22em] text-cyan-300">
            How the AI works
          </p>
          <h2 className="font-display text-3xl font-bold leading-tight tracking-tight md:text-5xl">
            조건만 말하면,
            <br />
            <span className="tp-gradient-text">코스는 AI가 완성</span>
          </h2>
          <p className="mb-10 mt-4 max-w-md text-sm leading-relaxed text-white/60">
            복잡한 검색 없이 조건만 입력하면, AI가 나만의 국내 여행 코스와 주변
            축제 정보까지 한 번에 만들어드립니다.
          </p>

          <div className="relative space-y-3">
            {/* 단계 연결선 */}
            <span className="absolute bottom-6 left-[27px] top-6 w-px bg-gradient-to-b from-violet-400 via-cyan-300 to-pink-400 opacity-50" />

            {steps.map((step) => (
              <div
                key={step.num}
                className="relative flex items-start gap-4 rounded-2xl border border-white/10 bg-white/[0.04] p-4 backdrop-blur transition hover:border-white/25 hover:bg-white/[0.07]"
              >
                <span className="relative flex h-6 w-6 shrink-0 items-center justify-center rounded-lg tp-gradient-bg font-display text-[10px] font-bold shadow-[0_0_16px_rgba(124,108,255,0.7)]">
                  {step.num}
                </span>
                <div>
                  <p className="mb-0.5 text-sm font-bold">{step.title}</p>
                  <p className="text-xs leading-relaxed text-white/55">
                    {step.desc}
                  </p>
                </div>
              </div>
            ))}
          </div>

          {/* "지금 AI 추천받기" 버튼: 페이지 이동 없이 Hero 섹션(id="ai-planner")으로 스크롤 이동 */}
          <a
            href="#ai-planner"
            className="mt-10 inline-flex items-center gap-2 rounded-2xl bg-white px-6 py-3.5 text-sm font-bold text-night-950 transition hover:shadow-[0_0_40px_rgba(167,139,250,0.7)]"
          >
            <svg
              className="h-4 w-4 text-violet-600"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M13 10V3L4 14h7v7l9-11h-7z"
              />
            </svg>
            지금 AI 추천받기
          </a>
        </div>

        {/* 우측: AI가 만들어줄 법한 추천 결과를 미리 보여주는 예시 카드 (하드코딩된 샘플 데이터, 실제 추천 아님) */}
        <div className="relative">
          <div className="absolute -inset-6 -z-0 rounded-[2.5rem] bg-gradient-to-br from-violet-500/30 via-cyan-400/10 to-pink-500/20 blur-3xl" />

          <div className="relative animate-float rounded-3xl border border-white/15 bg-white/[0.06] p-6 shadow-2xl backdrop-blur-xl">
            <div className="mb-5 flex items-center gap-3 border-b border-white/10 pb-4">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl tp-gradient-bg">
                <svg
                  className="h-5 w-5 text-white"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M5 13l4 4L19 7"
                  />
                </svg>
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-bold">제주도 2박 3일 · 힐링·휴양</p>
                <p className="text-xs text-white/50">2명 · 1인당 30~50만원</p>
              </div>
              <span className="rounded-full border border-cyan-300/30 bg-cyan-300/10 px-2.5 py-1 text-[10px] font-black tracking-wider text-cyan-200">
                AI 생성
              </span>
            </div>

            <ul className="space-y-3">
              {[
                { day: "DAY 1", plan: "성산일출봉 트레킹 → 우도 자전거 투어" },
                { day: "DAY 2", plan: "한라산 영실 코스 → 천지연 폭포" },
                { day: "DAY 3", plan: "협재 해수욕장 → 오설록 티뮤지엄" },
              ].map((item) => (
                <li
                  key={item.day}
                  className="flex gap-3 rounded-xl bg-white/[0.04] p-3 text-sm"
                >
                  <span className="h-fit shrink-0 rounded-md bg-white/10 px-2 py-0.5 font-display text-[10px] font-bold tracking-wider text-violet-200">
                    {item.day}
                  </span>
                  <span className="leading-relaxed text-white/80">
                    {item.plan}
                  </span>
                </li>
              ))}
            </ul>

            <div className="mt-5 flex items-center gap-2 border-t border-white/10 pt-4 text-xs text-white/50">
              <svg
                className="h-3.5 w-3.5 text-cyan-300"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M12 8v8m-4-5v5m8-9v9M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"
                />
              </svg>
              추천 지역 인근 축제 정보도 함께 제공돼요
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
