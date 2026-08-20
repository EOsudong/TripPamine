// 상단 고정 헤더.
// - 사이드바 열기/닫기 햄버거 버튼
// - 로고
// - 검색창(UI만 있고 실제 검색 기능은 아직 연결 안 됨)
// - 로그인/회원가입 통합 버튼 (클릭 시 /login 페이지로 이동)
//
// props
// - sidebarOpen     : 사이드바가 열려있는지 여부 (햄버거 아이콘 모양을 X로 바꾸는 데 사용)
// - onToggleSidebar : 햄버거 버튼 클릭 시 실행할 함수 (Home.jsx 등 상위 페이지에서 내려줌)
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

interface HeaderProps {
  sidebarOpen: boolean;
  onToggleSidebar: () => void;
}

export default function Header({ sidebarOpen, onToggleSidebar }: HeaderProps) {
  const navigate = useNavigate();
  const { isLoggedIn } = useAuth();

  const handleLogout = async () => {
    const token = localStorage.getItem("accessToken");

    try {
      const response = await fetch("http://localhost:8080/users/auth/logout", {
        // 백엔드 Full URL 확인
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });

      console.log("HTTP 응답 상태 코드:", response.status);

      if (!response.ok) {
        const errorText = await response.text();
        console.error("백엔드 에러 응답 내용:", errorText);
      } else {
        console.log("로그아웃 성공!");
        // 로그아웃 시 스토리지 전체 초기화
        localStorage.clear();
        sessionStorage.clear();
        window.location.href = "/login";
      }
    } catch (error) {
      console.error("실제 발생한 에러:", error);
      // } finally {
      //   // 에러 발생 여부와 무관하게 무조건 클라이언트 토큰 삭제 및 페이지 이동
      //   localStorage.removeItem("accessToken");
      //   window.location.href = "/login";
    }
  };

  return (
    <header className="fixed top-0 right-0 left-0 z-50 h-16 bg-white/90 backdrop-blur-md border-b border-slate-100 flex items-center px-4 gap-3">
      {/* 햄버거 버튼: 클릭할 때마다 sidebarOpen 상태가 true/false로 토글되고,
          열림 상태에 따라 아이콘 모양(≡ ↔ ✕)도 함께 바뀝니다 */}
      <button
        onClick={onToggleSidebar}
        className="p-2 rounded-xl hover:bg-slate-100 text-slate-600 transition-colors shrink-0"
        aria-label="사이드바 토글"
      >
        <svg
          className="w-5 h-5"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d={sidebarOpen ? "M6 18L18 6M6 6l12 12" : "M4 6h16M4 12h16M4 18h16"}
          />
        </svg>
      </button>

      {/* 로고 → 클릭하면 홈으로 이동 */}
      <a href="/" className="text-lg font-bold tracking-tight shrink-0">
        <span className="text-sky-500">Trip</span>
        <span className="text-slate-800">Pamin</span>
      </a>

      {/* 검색창: 작은 화면(sm 미만)에서는 숨김. 지금은 입력만 되고 실제 검색 로직은 미구현 */}
      <div className="hidden sm:flex flex-1 max-w-sm mx-auto">
        <div className="flex items-center gap-2 w-full bg-slate-100 rounded-xl px-4 py-2">
          <svg
            className="w-4 h-4 text-slate-400 shrink-0"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M21 21l-4.35-4.35M17 11A6 6 0 1 1 5 11a6 6 0 0 1 12 0z"
            />
          </svg>
          <input
            className="flex-1 bg-transparent text-sm text-slate-700 placeholder-slate-400 outline-none"
            placeholder="여행지, 축제, 코스 검색..."
          />
        </div>
      </div>

      <div className="ml-auto flex items-center gap-2 shrink-0">
        {/* 알림 아이콘 (UI만 있고 실제 알림 기능은 미구현. 오른쪽 위 점은 "새 알림 있음" 표시) */}
        <button className="p-2 rounded-xl hover:bg-slate-100 text-slate-500 transition-colors relative">
          <svg
            className="w-5 h-5"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6 6 0 00-9.33-5.003A6 6 0 006 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
            />
          </svg>
          <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-sky-500" />
        </button>

        {/* 로그아웃 버튼 => 로그인 시 활성화 */}
        {isLoggedIn ? (
          <button
            onClick={handleLogout}
            className="flex items-center gap-2 px-4 py-2 bg-sky-500 hover:bg-sky-600 text-white text-sm font-semibold rounded-xl transition-colors shadow-sm shadow-sky-200"
          >
            로그아웃
          </button>
        ) : (
          <>
            {/* 단일 로그인/회원가입 버튼 -> /login 페이지로 이동 */}
            <button
              onClick={() => navigate("/login")}
              className="flex items-center gap-2 px-4 py-2 bg-sky-500 hover:bg-sky-600 text-white text-sm font-semibold rounded-xl transition-colors shadow-sm shadow-sky-200"
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
                  d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
                />
              </svg>
              로그인 / 회원가입
            </button>
          </>
        )}
      </div>
    </header>
  );
}
