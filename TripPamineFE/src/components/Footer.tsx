// 하단 푸터. 로고/소개 문구/SNS 버튼 + 링크 3개 그룹으로 구성된 정적 컴포넌트입니다.
// (실제 클릭 가능한 페이지로 연결된 링크는 없고 전부 "#" — 필요할 때 라우트를 붙이면 됩니다)
export default function Footer() {
  return (
    <footer className="bg-slate-900 text-slate-400 py-12 px-4">
      <div className="max-w-6xl mx-auto grid grid-cols-2 md:grid-cols-4 gap-8 mb-8">
        {/* 왼쪽: 로고 + 소개 문구 + SNS 버튼 */}
        <div className="col-span-2 md:col-span-1">
          <div className="text-xl font-bold mb-3">
            <span className="text-sky-400">Trip</span>
            <span className="text-white">Pamin</span>
          </div>
          <p className="text-sm leading-relaxed mb-4">대한민국 구석구석, AI와 함께 더 특별한 국내 여행을 경험하세요.</p>
          <div className="flex gap-2">
            {[
              { l: "F", c: "#1877F2" }, // 페이스북
              { l: "I", c: "#E4405F" }, // 인스타그램
              { l: "Y", c: "#FF0000" }, // 유튜브
            ].map(({ l, c }) => (
              <button
                key={l}
                style={{ backgroundColor: c }}
                className="w-8 h-8 rounded-full flex items-center justify-center hover:opacity-80 transition-opacity"
              >
                <span className="text-xs font-bold text-white">{l}</span>
              </button>
            ))}
          </div>
        </div>
        {/* 오른쪽: 링크 3개 그룹(여행 서비스/고객 지원/회사 정보)을 배열.map()으로 반복 렌더링 */}
        {[
          { title: "여행 서비스", items: ["문화관광", "체험관광", "역사관광", "자연관광"] },
          { title: "고객 지원", items: ["이용가이드", "자주 묻는 질문", "1:1 문의", "공지사항"] },
          { title: "회사 정보", items: ["회사 소개", "회사 주소", "이용 약관", "개인정보처리방침"] },
        ].map((col) => (
          <div key={col.title}>
            <p className="text-white font-semibold text-sm mb-3">{col.title}</p>
            <ul className="space-y-2">
              {col.items.map((item) => (
                <li key={item}>
                  <a href="#" className="text-sm hover:text-sky-400 transition-colors">
                    {item}
                  </a>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="border-t border-slate-800 pt-6 text-center text-xs text-slate-600">
        © 2026 TripPamin. All rights reserved.
      </div>
    </footer>
  )
}
