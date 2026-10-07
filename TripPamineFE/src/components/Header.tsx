import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { getUserAccessToken } from "../auth/session";
import { getMyInfoApi } from "../api/auth";

interface HeaderProps {
    sidebarOpen: boolean;
    onToggleSidebar: () => void;
}

interface CurrentUser {
    id?: number;
    email?: string;
    name?: string;

    userName?: string;
    username?: string;

    grade?: string;
    userGrade?: string;

    profileImageUrl?: string | null;
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


    // =========================================
    // 현재 로그인 사용자
    // =========================================

    const [currentUser, setCurrentUser] =
        useState<CurrentUser | null>(null);


    // =========================================
    // 사용자 정보 로딩 여부
    // =========================================

    const [userLoading, setUserLoading] =
        useState(false);


    // =========================================
    // 로그인 사용자 정보 조회
    // =========================================

    useEffect(() => {

        if (!isLoggedIn) {

            setCurrentUser(null);

            return;
        }


        const fetchCurrentUser = async () => {

            try {

                setUserLoading(true);


                // [수정] 예전엔 fetch("http://localhost:8080/users/auth/me")로 주소를 하드코딩했다.
                // 공용 axios 인스턴스(getMyInfoApi)를 쓰면 VITE_API_BASE_URL(터널/배포 주소)과
                // 토큰 자동 첨부·만료 처리가 그대로 적용된다.
                if (!getUserAccessToken()) {

                    setCurrentUser(null);

                    return;
                }

                const data = await getMyInfoApi();

                setCurrentUser(data);

            } catch (error) {

                console.error(
                    "회원정보 조회 중 오류 =",
                    error
                );

                setCurrentUser(null);

            } finally {

                setUserLoading(false);
            }
        };


        fetchCurrentUser();

    }, [isLoggedIn]);


    // =========================================
    // 로그아웃
    // =========================================
    const handleLogout = async () => {

        await logout();

        setCurrentUser(null);

        navigate("/login");
    };

    // =========================================
    // 사용자명
    // =========================================

    const displayUserName =
        currentUser?.userName
        ?? currentUser?.username
        ?? currentUser?.name
        ?? "사용자";


    // =========================================
    // 회원등급
    // =========================================

    const displayGrade =
        currentUser?.grade
        ?? currentUser?.userGrade
        ?? "BRONZE";


    // =========================================
    // 회원등급 UI
    // =========================================

    const getGradeClass = (
        grade: string
    ) => {

        switch (
            grade.toUpperCase()
            ) {

            case "GOLD":

                return (
                    "bg-amber-50 "
                    + "text-amber-700 "
                    + "border-amber-200"
                );


            case "SILVER":

                return (
                    "bg-slate-100 "
                    + "text-slate-600 "
                    + "border-slate-300"
                );


            case "BRONZE":

                return (
                    "bg-orange-50 "
                    + "text-orange-700 "
                    + "border-orange-200"
                );


            default:

                return (
                    "bg-sky-50 "
                    + "text-sky-700 "
                    + "border-sky-200"
                );
        }
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
                        {/* [수정] 병합 과정에서 빠졌던 사용자명 + 회원등급 배지 표시를 복구 */}
                        <button
                            type="button"
                            onClick={() =>
                                navigate("/mypage")
                            }
                            title="마이페이지"
                            className="
                hidden items-center gap-2
                rounded-xl px-3 py-1.5
                text-sm font-semibold text-slate-600
                transition
                hover:bg-slate-100
                hover:text-slate-950
                sm:flex
              "
                        >
                            <span className="max-w-[120px] truncate">
                                {userLoading
                                    ? "불러오는 중..."
                                    : `${displayUserName}님`}
                            </span>

                            {!userLoading && (
                                <span
                                    className={`
                    rounded-full border
                    px-2 py-0.5
                    text-[10px] font-bold
                    ${getGradeClass(displayGrade)}
                  `}
                                >
                                    {displayGrade}
                                </span>
                            )}
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


