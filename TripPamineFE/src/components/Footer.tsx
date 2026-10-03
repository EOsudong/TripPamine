import { useState } from "react";
import { useNavigate } from "react-router-dom";
// 하단 푸터. 로고/소개 문구/SNS 버튼 + 링크 3개 그룹으로 구성된 정적 컴포넌트입니다.
// (실제 클릭 가능한 페이지로 연결된 링크는 없고 전부 "#" — 필요할 때 라우트를 붙이면 됩니다)
const travelFilterMap: Record<string, string> = {
  문화관광: "문화 관광",
  체험관광: "체험 관광",
  역사관광: "역사 관광",
  자연관광: "자연 관광",
};
export default function Footer() {
  const [modalType, setModalType] = useState<string | null>(null);
  const navigate = useNavigate();
  const modalContents: Record<
    string,
    {
      icon: string;
      title: string;
      description: string;
      content: React.ReactNode;
    }
  > = {
    이용가이드: {
      icon: "🧭",
      title: "TripPamine 이용가이드",
      description: "AI와 함께 나만의 여행을 시작해보세요.",
      content: (
        <div className="space-y-4">
          {[
            [
              "01",
              "여행 조건 입력",
              "날짜, 인원, 예산과 여행 스타일을 입력해주세요.",
            ],
            [
              "02",
              "AI 여행 추천",
              "입력한 조건과 소비 패턴을 바탕으로 AI가 여행을 추천합니다.",
            ],
            [
              "03",
              "여행 계획 확인",
              "추천 장소와 일정, 예상 비용을 한눈에 확인하세요.",
            ],
            [
              "04",
              "미스터리 투어",
              "목적지를 모른 채 출발해 GPS와 사진 인증 퀘스트를 수행할 수도 있어요.",
            ],
          ].map(([number, title, text]) => (
            <div
              key={number}
              className="flex gap-4 rounded-2xl bg-slate-800/70 p-4"
            >
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-sky-500/20 text-xs font-black text-sky-300">
                {number}
              </div>

              <div>
                <p className="font-bold text-white">{title}</p>
                <p className="mt-1 text-sm leading-relaxed text-slate-400">
                  {text}
                </p>
              </div>
            </div>
          ))}
        </div>
      ),
    },

    "자주 묻는 질문": {
      icon: "💬",
      title: "자주 묻는 질문",
      description: "TripPamine 이용 중 자주 묻는 내용을 모았습니다.",
      content: (
        <div className="space-y-3">
          {[
            [
              "AI 추천은 어떻게 만들어지나요?",
              "입력한 여행 조건과 예산, 여행 스타일 등을 기반으로 맞춤 여행 정보를 제공합니다.",
            ],
            [
              "미스터리 투어는 무엇인가요?",
              "AI가 목적지와 퀘스트를 생성하고 GPS·사진·간편 인증을 수행하며 즐기는 여행 기능입니다.",
            ],
            [
              "사진 인증은 어떻게 하나요?",
              "퀘스트에서 사진을 등록하면 OCR 기술로 사진 속 정보를 분석하여 인증합니다.",
            ],
          ].map(([q, a]) => (
            <div key={q} className="rounded-2xl bg-slate-800/70 p-4">
              <p className="font-bold text-sky-300">Q. {q}</p>
              <p className="mt-2 text-sm leading-relaxed text-slate-400">{a}</p>
            </div>
          ))}
        </div>
      ),
    },

    공지사항: {
      icon: "📢",
      title: "공지사항",
      description: "TripPamine의 새로운 소식을 확인하세요.",
      content: (
        <div className="divide-y divide-slate-800">
          {[
            ["TripPamine 서비스 오픈 안내", "2026.10.03"],
            ["미스터리 투어 PHOTO 인증 기능 추가", "2026.10.03"],
            ["AI 여행 추천 서비스 이용 안내", "2026.09.25"],
          ].map(([title, date]) => (
            <div key={title} className="flex items-center justify-between py-4">
              <span className="text-sm font-medium text-slate-200">
                {title}
              </span>
              <span className="text-xs text-slate-500">{date}</span>
            </div>
          ))}
        </div>
      ),
    },

    "이용 약관": {
      icon: "📄",
      title: "서비스 이용 약관",
      description: "TripPamine 서비스 이용에 관한 기본 사항입니다.",
      content: (
        <div className="space-y-4 text-sm leading-7 text-slate-400">
          <p>
            본 약관은 TripPamine이 제공하는 AI 기반 여행 서비스의 이용과
            관련하여 필요한 기본 사항을 규정합니다.
          </p>
          <p>
            AI가 제공하는 여행지, 일정, 예상 비용 등의 정보는 사용자의 여행
            계획을 돕기 위한 참고 정보이며 실제 상황에 따라 달라질 수 있습니다.
          </p>
          <p>
            사용자는 서비스를 관련 법령과 본 약관에 따라 정상적인 목적으로
            이용해야 합니다.
          </p>
        </div>
      ),
    },

    개인정보처리방침: {
      icon: "🔐",
      title: "개인정보처리방침",
      description: "소중한 개인정보를 안전하게 관리합니다.",
      content: (
        <div className="space-y-4 text-sm leading-7 text-slate-400">
          <p>
            TripPamine은 서비스 제공에 필요한 범위에서 개인정보를 처리하며, 이용
            목적이 달성된 정보는 관련 기준에 따라 관리합니다.
          </p>
          <p>
            여행 추천, 가계부 및 개인화 서비스에 사용되는 정보는 서비스 제공과
            사용자 경험 개선을 목적으로 활용됩니다.
          </p>
          <p>
            개인정보의 처리 목적과 보유 기간 등 세부 사항은 서비스 정책에 따라
            제공됩니다.
          </p>
        </div>
      ),
    },
  };
  return (
    <footer className="bg-slate-900 text-slate-400 py-12 px-4">
      <div className="max-w-5xl mx-auto grid grid-cols-1 md:grid-cols-3 gap-10 mb-8">
        {/* 왼쪽: 로고 + 소개 문구 + SNS 버튼 */}
        <div className="col-span-2 md:col-span-1">
          <div className="text-xl font-bold mb-3">
            <span className="text-sky-400">Trip</span>
            <span className="text-white">Pamine</span>
          </div>
          <p className="text-sm leading-relaxed mb-4">
            대한민국 구석구석, AI와 함께 더 특별한 국내 여행을 경험하세요.
          </p>
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
          {
            title: "여행 서비스",
            items: ["문화관광", "체험관광", "역사관광", "자연관광"],
          },
          {
            title: "고객 지원",
            items: ["이용가이드", "자주 묻는 질문", "공지사항"],
          },
          {
            title: "서비스 안내",
            items: ["이용 약관", "개인정보처리방침"],
          },
        ].map((col) => (
          <div key={col.title}>
            <p className="text-white font-semibold text-sm mb-3">{col.title}</p>
            <ul className="space-y-2">
              {col.items.map((item) => (
                <li key={item}>
                  <button
                    type="button"
                    onClick={() => {
                      // 여행 서비스 → 실제 국내 관광 여행지 페이지

                      const filter = travelFilterMap[item];

                      if (filter) {
                        navigate(
                          `/tour/destinations?filter=${encodeURIComponent(filter)}`,
                        );

                        window.scrollTo({
                          top: 0,
                          behavior: "smooth",
                        });

                        return;
                      }

                      // 고객지원 / 서비스안내 → 모달
                      if (modalContents[item]) {
                        setModalType(item);
                      }
                    }}
                    className="text-sm hover:text-sky-400 transition-colors text-left"
                  >
                    {item}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="border-t border-slate-800 pt-6 text-center text-xs text-slate-600">
        © 2026 TripPamine. All rights reserved.
      </div>
      {modalType && modalContents[modalType] && (
        <div
          className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/70 px-4 backdrop-blur-sm"
          onClick={() => setModalType(null)}
        >
          <div
            className="relative w-full max-w-xl overflow-hidden rounded-3xl border border-white/10 bg-slate-900 shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="border-b border-white/10 px-6 py-5">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <div className="mb-2 text-3xl">
                    {modalContents[modalType].icon}
                  </div>

                  <h2 className="text-xl font-black text-white">
                    {modalContents[modalType].title}
                  </h2>

                  <p className="mt-1 text-sm text-slate-400">
                    {modalContents[modalType].description}
                  </p>
                </div>

                <button
                  type="button"
                  onClick={() => setModalType(null)}
                  className="flex h-9 w-9 items-center justify-center rounded-full bg-white/5 text-slate-400 transition hover:bg-white/10 hover:text-white"
                >
                  ✕
                </button>
              </div>
            </div>

            <div className="max-h-[60vh] overflow-y-auto p-6">
              {modalContents[modalType].content}
            </div>

            <div className="border-t border-white/10 px-6 py-4 text-right">
              <button
                type="button"
                onClick={() => setModalType(null)}
                className="rounded-xl bg-sky-500 px-5 py-2.5 text-xs font-bold text-white transition hover:bg-sky-400"
              >
                확인
              </button>
            </div>
          </div>
        </div>
      )}
    </footer>
  );
}
