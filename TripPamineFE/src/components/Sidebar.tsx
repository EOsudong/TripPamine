// 왼쪽 네비게이션 사이드바.
// - 데스크탑에서는 항상 보이되, open 값에 따라 "아이콘만(64px)" ↔ "전체 펼침(256px)" 너비만 바뀜
// - 모바일에서는 open이 true일 때만 화면 위로 슬라이드되어 나타나는 오버레이 형태
//
// props
// - open    : 사이드바가 펼쳐진 상태인지 여부 (Home.jsx 등 상위 페이지의 state)
// - onClose : 사이드바를 닫을 때 실행할 함수 (배경 클릭, 메뉴 클릭, 모바일 닫기 버튼에서 호출)
import { useNavigate } from "react-router-dom";
import { tourMainCategories } from "../data/tourCategories";

interface SidebarProps {
  open: boolean;
  onClose: () => void;
}

interface NavItem {
  icon: string;
  label: string;
  to: string | null;
}

interface NavGroup {
  group: string;
  items: NavItem[];
}

// 메뉴 목록을 그룹 단위로 정의.
// to가 "/#festivals"처럼 "#"을 포함하면 → 홈으로 이동 후 해당 id 위치로 스크롤
// to가 null이면 → 아직 연결된 페이지가 없는 메뉴 (클릭해도 아무 동작 안 함)
//
// "정보" 그룹의 대분류 3개(국내 축제 및 행사 / 국내 관광 여행지 / 국내 관광 산업)는
// data/tourCategories.ts를 그대로 불러와서 만듭니다. 소분류 탭 구성까지 그 파일 한 곳만
// 고치면 사이드바 메뉴와 /tour/:categoryKey 페이지가 함께 바뀝니다.
const navItems: NavGroup[] = [
  {
    group: "탐색",
    items: [
      { icon: "🏠", label: "홈", to: "/" },
      { icon: "🤖", label: "AI 여행 추천", to: "/#ai-planner" },
      { icon: "🎯", label: "나의 퀘스트", to: "/mypage?tab=quest" },
    ],
  },
  {
    group: "마이",
    items: [
      { icon: "👤", label: "마이페이지", to: "/mypage" },
      { icon: "🔖", label: "북마크", to: "/mypage" },
      { icon: "📅", label: "내 여행 일정", to: "/mypage" },
    ],
  },
  {
    group: "정보",
    items: [
      ...tourMainCategories.map((category) => ({
        icon: category.icon,
        label: category.label,
        to: category.path,
      })),
      { icon: "💬", label: "자유게시판", to: null },
      { icon: "⚙️", label: "설정", to: null },
    ],
  },
];

export default function Sidebar({ open, onClose }: SidebarProps) {
  const navigate = useNavigate();
  // 현재 URL 경로를 직접 읽어와서, 해당 메뉴가 "현재 페이지"인지(active 표시) 판단하는 데 사용
  const currentPath =
    typeof window !== "undefined" ? window.location.pathname : "/";

  // 메뉴 클릭 시 이동 처리
  // 1) to가 없으면(null) 아무 동작 안 함
  // 2) "/#festivals"처럼 해시가 포함되면 → 먼저 홈으로 이동한 뒤, 렌더링이 끝나길 잠깐(50ms) 기다렸다가
  //    해당 id를 가진 요소로 부드럽게 스크롤
  // 3) 그 외에는 일반적인 페이지 이동(navigate)
  function handleClick(to: string | null) {
    if (!to) return;
    if (to.startsWith("/#")) {
      navigate("/");
      window.setTimeout(() => {
        document
          .getElementById(to.split("#")[1])
          ?.scrollIntoView({ behavior: "smooth" });
      }, 50);
    } else {
      navigate(to);
    }
    onClose(); // 메뉴 선택 후에는 모바일에서 사이드바를 자동으로 닫아줌
  }

  return (
    <>
      {/* 모바일 전용 반투명 배경. 사이드바가 열려있을 때만 보이고, 클릭하면 닫힘 (lg 이상 화면에서는 숨김) */}
      {open && (
        <div
          className="fixed inset-0 top-16 bg-black/40 z-30 lg:hidden"
          onClick={onClose}
        />
      )}

      {/* Sidebar panel — 헤더(z-50) 아래에서 시작해서 겹치지 않도록 top-16 사용 */}
      <aside
        className={`fixed top-16 left-0 h-[calc(100%-4rem)] z-40 bg-white border-r border-slate-100 flex flex-col transition-all duration-300 ease-in-out
          ${open ? "w-64 shadow-2xl" : "w-0 lg:w-16 overflow-hidden"}
        `}
      >
        {/* 상단 영역: 모바일에서만 닫기 버튼 표시 (로고는 헤더에 이미 있음) */}
        {open && (
          <div className="flex items-center justify-end px-3 py-3 border-b border-slate-100 shrink-0 lg:hidden">
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500"
            >
              <svg
                className="w-4 h-4"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M6 18L18 6M6 6l12 12"
                />
              </svg>
            </button>
          </div>
        )}

        {/* 메뉴 그룹 목록.
            active(현재 페이지) 여부에 따라 배경색이 바뀌고, open이 false(아이콘 모드)일 때는
            그룹 제목/라벨 텍스트를 숨기고 아이콘만 중앙 정렬로 보여줍니다 */}
        <nav className="flex-1 overflow-y-auto py-4 px-2 space-y-5">
          {navItems.map((group) => (
            <div key={group.group}>
              {open && (
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest px-3 mb-1.5">
                  {group.group}
                </p>
              )}
              <ul className="space-y-0.5">
                {group.items.map((item) => {
                  const active =
                    item.to === "/"
                      ? currentPath === "/"
                      : item.to && currentPath === item.to;
                  return (
                    <li key={item.label}>
                      <button
                        onClick={() => handleClick(item.to)}
                        className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all group
                          ${active ? "bg-sky-50 text-sky-600" : "text-slate-600 hover:bg-slate-50 hover:text-slate-900"}
                          ${!open ? "justify-center" : ""}
                        `}
                      >
                        <span className="text-base shrink-0">{item.icon}</span>
                        {open && <span>{item.label}</span>}
                        {active && open && (
                          <span className="ml-auto w-1.5 h-1.5 rounded-full bg-sky-500" />
                        )}
                      </button>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </nav>

        {/* 사이드바 맨 아래: 로그인 안 한 사용자에게 로그인을 유도하는 영역 (펼쳐진 상태에서만 표시) */}
        {open && (
          <div className="p-3 border-t border-slate-100">
            <button
              onClick={() => handleClick("/login")}
              className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-slate-500 hover:bg-slate-50 transition-colors"
            >
              <div className="w-7 h-7 rounded-full bg-slate-200 flex items-center justify-center shrink-0">
                <svg
                  className="w-4 h-4 text-slate-500"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
                  />
                </svg>
              </div>
              <div className="text-left">
                <p className="text-xs font-semibold text-slate-700">
                  로그인이 필요해요
                </p>
                <p className="text-[10px] text-slate-400">클릭해서 시작하기</p>
              </div>
            </button>
          </div>
        )}
      </aside>
    </>
  );
}
