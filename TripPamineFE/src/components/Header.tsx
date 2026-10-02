import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

interface HeaderProps {
    sidebarOpen: boolean;
    onToggleSidebar: () => void;
}

/**
 * 전체 서비스 공통 Header.
 *
 * AI Manager 중심 구조에서는 Header에 많은 기능을 넣지 않는다.
 *
 * - Navigation은 Sidebar
 * - 개인화 정보는 Main
 * - Header는 Brand + 전역 사용자 Action만 담당
 */
export default function Header({
                                   sidebarOpen,
                                   onToggleSidebar,
                               }: HeaderProps) {
    const navigate = useNavigate();

    const {
        isLoggedIn,
        logout,
    } = useAuth();

    const handleLogout =
        async () => {
            await logout();
            navigate("/login");
        };

    return (
        <header
            className="
        fixed inset-x-0 top-0 z-50
        flex h-16 items-center
        border-b border-slate-200/80
        bg-white/90 px-4
        backdrop-blur-xl
      "
        >
            <button
                type="button"
                onClick={onToggleSidebar}
                aria-label="사이드바 토글"
                className="
          flex h-9 w-9 shrink-0
          items-center justify-center
          rounded-xl text-slate-500
          transition
          hover:bg-slate-100
          hover:text-slate-900
        "
            >
                <svg
                    className="h-5 w-5"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                >
                    <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={1.8}
                        d={
                            sidebarOpen
                                ? "M6 18L18 6M6 6l12 12"
                                : "M4 7h16M4 12h16M4 17h16"
                        }
                    />
                </svg>
            </button>

            <button
                type="button"
                onClick={() => navigate("/")}
                className="ml-2 flex items-center gap-3"
            >
        <span className="text-lg font-black tracking-tight">
          <span className="text-sky-500">
            Trip
          </span>

          <span className="text-slate-950">
            Pamine
          </span>
        </span>

                <span className="hidden rounded-md bg-slate-100 px-2 py-1 text-[10px] font-bold tracking-wide text-slate-500 sm:inline">
          AI MANAGER
        </span>
            </button>

            <div className="ml-auto flex items-center gap-2">
                {isLoggedIn ? (
                    <>
                        <button
                            type="button"
                            onClick={() =>
                                navigate("/mypage")
                            }
                            className="
                hidden rounded-xl px-3 py-2
                text-sm font-semibold text-slate-600
                transition
                hover:bg-slate-100
                hover:text-slate-950
                sm:block
              "
                        >
                            마이페이지
                        </button>

                        <button
                            type="button"
                            onClick={handleLogout}
                            className="
                rounded-xl border border-slate-200
                bg-white px-3.5 py-2
                text-xs font-bold text-slate-600
                transition
                hover:border-slate-300
                hover:bg-slate-50
              "
                        >
                            로그아웃
                        </button>
                    </>
                ) : (
                    <button
                        type="button"
                        onClick={() =>
                            navigate("/login")
                        }
                        className="
              rounded-xl bg-slate-950
              px-4 py-2
              text-sm font-bold text-white
              transition
              hover:bg-sky-600
            "
                    >
                        시작하기
                    </button>
                )}
            </div>
        </header>
    );
}