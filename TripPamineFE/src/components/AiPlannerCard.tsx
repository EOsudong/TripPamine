// "AI 코스 추천 기능 소개/미리보기" 섹션.
// Hero.jsx의 실제 입력 폼과는 별개로, "AI가 어떻게 추천해주는지"를 설명하는 정적인 소개 카드입니다.
// (사용자 입력을 받지 않는 순수 소개용 컴포넌트라 상태(state)가 없음)

// 좌측에 표시할 3단계 설명 (조건 입력 → AI 추천 → 축제 연계)
const steps = [
  { num: "1", title: "조건 입력", desc: "인원, 일정, 예산, 테마를 한 번에 선택하세요" },
  { num: "2", title: "AI 코스 추천", desc: "조건에 맞는 여행지와 일자별 코스를 자동으로 생성해요" },
  { num: "3", title: "축제·행사 연계", desc: "추천 지역의 진행 중·예정 축제 정보를 함께 보여드려요" },
]

export default function AiPlannerCard() {
  return (
    <section className="py-14 px-4 bg-gradient-to-br from-sky-50 via-white to-sky-50">
      <div className="max-w-6xl mx-auto grid grid-cols-1 lg:grid-cols-2 gap-10 items-center">
        {/* 좌측: 소개 텍스트 + 단계 */}
        <div>
          <p className="text-sky-500 text-xs font-semibold tracking-widest uppercase mb-2">How it works</p>
          <h2 className="text-2xl md:text-3xl font-bold text-slate-900 mb-3">
            AI가 이렇게 여행 코스를 추천해요
          </h2>
          <p className="text-slate-500 text-sm mb-8 leading-relaxed">
            복잡한 검색 없이 조건만 입력하면, AI가 나만의 국내 여행 코스와 주변 축제 정보까지 한 번에 만들어드립니다.
          </p>

          <div className="space-y-4">
            {steps.map((step) => (
              <div key={step.num} className="flex items-start gap-4">
                <span className="shrink-0 w-8 h-8 rounded-xl bg-sky-500 text-white text-sm font-bold flex items-center justify-center shadow-sm shadow-sky-200">
                  {step.num}
                </span>
                <div>
                  <p className="font-bold text-slate-800 text-sm mb-0.5">{step.title}</p>
                  <p className="text-xs text-slate-500 leading-relaxed">{step.desc}</p>
                </div>
              </div>
            ))}
          </div>

          {/* "지금 AI 추천받기" 버튼: 페이지 이동 없이 Hero 섹션(id="ai-planner")으로 스크롤 이동 */}
          <a
            href="#ai-planner"
            className="inline-flex items-center gap-2 mt-8 px-5 py-3 bg-sky-500 hover:bg-sky-600 text-white text-sm font-bold rounded-2xl transition-colors shadow-md shadow-sky-200"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
            지금 AI 추천받기
          </a>
        </div>

        {/* 우측: AI가 만들어줄 법한 추천 결과를 미리 보여주는 예시 카드 (하드코딩된 샘플 데이터, 실제 추천 아님) */}
        <div className="relative">
          <div className="absolute -inset-3 bg-gradient-to-br from-sky-200 to-sky-100 rounded-[2rem] -z-10 blur-xl opacity-70" />
          <div className="bg-white rounded-3xl shadow-xl border border-slate-100 p-6">
            <div className="flex items-center gap-2.5 pb-4 mb-4 border-b border-slate-100">
              <div className="w-8 h-8 rounded-xl bg-sky-500 flex items-center justify-center shrink-0">
                <svg className="w-4 h-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
              </div>
              <div>
                <p className="font-bold text-slate-800 text-sm">제주도 2박 3일 · 힐링·휴양</p>
                <p className="text-xs text-slate-400">2명 · 1인당 30~50만원</p>
              </div>
            </div>

            <ul className="space-y-3">
              {[
                { day: "1일차", plan: "성산일출봉 트레킹 → 우도 자전거 투어" },
                { day: "2일차", plan: "한라산 영실 코스 → 천지연 폭포" },
                { day: "3일차", plan: "협재 해수욕장 → 오설록 티뮤지엄" },
              ].map((item) => (
                <li key={item.day} className="flex gap-3 text-sm">
                  <span className="shrink-0 px-2 py-0.5 h-fit bg-sky-50 text-sky-600 text-xs font-bold rounded-full">
                    {item.day}
                  </span>
                  <span className="text-slate-600 leading-relaxed">{item.plan}</span>
                </li>
              ))}
            </ul>

            <div className="mt-5 pt-4 border-t border-slate-100 flex items-center gap-2 text-xs text-slate-400">
              <svg className="w-3.5 h-3.5 text-sky-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
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
  )
}
