// "AI 플랫폼의 장점 및 특징 소개" 섹션.
// 카드 4개짜리 정적 목록으로, 데이터도 컴포넌트 안에 배열로 직접 넣어뒀습니다
// (항목이 많아지거나 재사용이 필요해지면 data 폴더로 분리하면 됩니다).
export default function WhyUs() {
  return (
    <section className="py-14 px-4 bg-white">
      <div className="max-w-6xl mx-auto">
        <div className="text-center mb-10">
          <p className="text-sky-500 text-xs font-semibold tracking-widest uppercase mb-2">Why TripPamine</p>
          <h2 className="text-2xl md:text-3xl font-bold text-slate-900">트립파민을 선택하는 이유</h2>
        </div>
        {/* 카드 4개를 배열.map()으로 반복 렌더링 */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
          {[
            { icon: "🛡️", title: "안전한 예약", desc: "100% 환불 보장 정책으로 안심하고 예약하세요" },
            { icon: "💰", title: "최저가 보장", desc: "다른 곳보다 비싸면 차액의 110%를 돌려드립니다" },
            { icon: "🤖", title: "AI 맞춤 추천", desc: "AI가 취향과 예산에 꼭 맞는 국내 여행 코스를 설계해드립니다" },
            { icon: "📞", title: "24시간 지원", desc: "여행 중 언제든 전문 상담사가 도움을 드립니다" },
          ].map((item) => (
            <div
              key={item.title}
              className="text-center p-6 rounded-2xl bg-slate-50 border border-slate-100 hover:shadow-md transition-shadow group"
            >
              <div className="text-4xl mb-4 group-hover:scale-110 transition-transform inline-block">{item.icon}</div>
              <h3 className="font-bold text-slate-800 mb-2">{item.title}</h3>
              <p className="text-sm text-slate-500 leading-relaxed">{item.desc}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
