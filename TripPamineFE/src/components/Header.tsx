import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { getUserAccessToken } from "../auth/session";


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


                const accessToken =
                    getUserAccessToken();


                if (!accessToken) {

                    setCurrentUser(null);

                    return;
                }


                const response =
                    await fetch(
                        "http://localhost:8080/users/auth/me",
                        {
                            method: "GET",

                            headers: {
                                Accept: "application/json",

                                Authorization:
                                    `Bearer ${accessToken}`,
                            },
                        }
                    );


                if (!response.ok) {

                    console.error(
                        "회원정보 조회 실패 =",
                        response.status
                    );

                    setCurrentUser(null);

                    return;
                }


                const data =
                    await response.json();


                console.log(
                    "현재 로그인 사용자 정보 =",
                    data
                );


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
                fixed
                top-0
                right-0
                left-0
                z-50
                h-16

                bg-white/90
                backdrop-blur-md

                border-b
                border-slate-100

                flex
                items-center

                px-4
                gap-3
            "
        >

            {/* ========================================= */}
            {/* 사이드바 버튼 */}
            {/* ========================================= */}

            <button
                type="button"
                onClick={onToggleSidebar}
                className="
                    p-2
                    rounded-xl

                    hover:bg-slate-100

                    text-slate-600

                    transition-colors
                    shrink-0
                "
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

                        d={
                            sidebarOpen
                                ? "M6 18L18 6M6 6l12 12"
                                : "M4 6h16M4 12h16M4 18h16"
                        }
                    />

                </svg>

            </button>


            {/* ========================================= */}
            {/* TripPamine Logo */}
            {/* ========================================= */}

            <button
                type="button"
                onClick={() =>
                    navigate("/")
                }
                className="
                    text-lg
                    font-bold
                    tracking-tight
                    shrink-0
                "
            >

                <span className="text-sky-500">
                    Trip
                </span>

                <span className="text-slate-800">
                    Pamine
                </span>

            </button>


            {/* ========================================= */}
            {/* 슬로건 */}
            {/* ========================================= */}

            <div
                className="
                    hidden
                    md:block

                    text-xs
                    text-gray-500

                    border-l
                    border-gray-300

                    pl-3
                    shrink-0
                "
            >

                "계획은 AI가, 짜릿함은 당신이"

            </div>


            {/* ========================================= */}
            {/* 검색창 */}
            {/* ========================================= */}

            <div
                className="
                    hidden
                    sm:flex

                    flex-1
                    max-w-sm
                    mx-auto
                "
            >

                <div
                    className="
                        flex
                        items-center
                        gap-2

                        w-full

                        bg-slate-100

                        rounded-xl

                        px-4
                        py-2
                    "
                >

                    <svg
                        className="
                            w-4
                            h-4
                            text-slate-400
                            shrink-0
                        "
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                    >

                        <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}

                            d="
                                M21 21l-4.35-4.35
                                M17 11A6 6 0 1 1 5 11
                                a6 6 0 0 1 12 0z
                            "
                        />

                    </svg>


                    <input
                        className="
                            flex-1
                            bg-transparent

                            text-sm
                            text-slate-700

                            placeholder-slate-400

                            outline-none
                        "
                        placeholder="여행지, 축제, 코스 검색..."
                    />

                </div>

            </div>


            {/* ========================================= */}
            {/* 오른쪽 영역 */}
            {/* ========================================= */}

            <div
                className="
                    ml-auto

                    flex
                    items-center
                    gap-2

                    shrink-0
                "
            >

                {/* ===================================== */}
                {/* 알림 */}
                {/* ===================================== */}

                <button
                    type="button"
                    className="
                        p-2
                        rounded-xl

                        hover:bg-slate-100

                        text-slate-500

                        transition-colors

                        relative
                    "
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

                            d="
                                M15 17h5
                                l-1.405-1.405
                                A2.032 2.032 0 0118 14.158
                                V11
                                a6 6 0 00-9.33-5.003
                                A6 6 0 006 11
                                v3.159
                                c0 .538-.214 1.055-.595 1.436
                                L4 17h5
                                m6 0v1
                                a3 3 0 11-6 0
                                v-1
                                m6 0H9
                            "
                        />

                    </svg>


                    <span
                        className="
                            absolute
                            top-1.5
                            right-1.5

                            w-2
                            h-2

                            rounded-full

                            bg-sky-500
                        "
                    />

                </button>


                {/* ===================================== */}
                {/* 로그인 사용자 */}
                {/* ===================================== */}

                {isLoggedIn ? (

                    <>

                        {/* ============================= */}
                        {/* 사용자 프로필 */}
                        {/* ============================= */}

                        <button
                            type="button"
                            onClick={() =>
                                navigate("/mypage")
                            }
                            className="
                                hidden
                                sm:flex

                                items-center
                                gap-2

                                px-2
                                py-1

                                rounded-xl

                                hover:bg-slate-50

                                transition-colors
                            "
                        >

                            {/* ========================= */}
                            {/* 프로필 이미지 */}
                            {/* ========================= */}

                            <div
                                className="
                                    w-9
                                    h-9

                                    rounded-full

                                    overflow-hidden

                                    bg-sky-100

                                    flex
                                    items-center
                                    justify-center

                                    border
                                    border-sky-200

                                    shrink-0
                                "
                            >

                                {currentUser?.profileImageUrl ? (

                                    <img
                                        src={
                                            currentUser
                                                .profileImageUrl
                                        }
                                        alt="프로필"
                                        className="
                                            w-full
                                            h-full
                                            object-cover
                                        "
                                    />

                                ) : (

                                    <svg
                                        className="
                                            w-5
                                            h-5
                                            text-sky-600
                                        "
                                        fill="none"
                                        stroke="currentColor"
                                        viewBox="0 0 24 24"
                                    >

                                        <path
                                            strokeLinecap="round"
                                            strokeLinejoin="round"
                                            strokeWidth={2}
                                            d="
                                                M16 7
                                                a4 4 0 11-8 0
                                                4 4 0 018 0z

                                                M12 14
                                                a7 7 0 00-7 7
                                                h14
                                                a7 7 0 00-7-7z
                                            "
                                        />

                                    </svg>

                                )}

                            </div>


                            {/* ========================= */}
                            {/* 이름 + 회원등급 */}
                            {/* ========================= */}

                            <div
                                className="
                                    flex
                                    flex-col
                                    items-start

                                    leading-tight
                                "
                            >

                                <span
                                    className="
                                        text-sm
                                        font-semibold
                                        text-slate-800

                                        max-w-[100px]
                                        truncate
                                    "
                                >

                                    {
                                        userLoading
                                            ? "불러오는 중..."
                                            : displayUserName
                                    }

                                </span>


                                {!userLoading && (

                                    <span
                                        className={`
                                            mt-1

                                            text-[10px]
                                            font-bold

                                            px-2
                                            py-0.5

                                            rounded-full

                                            border

                                            ${getGradeClass(
                                                displayGrade
                                            )}
                                        `}
                                    >

                                        {displayGrade}

                                    </span>

                                )}

                            </div>

                        </button>


                        {/* ============================= */}
                        {/* 로그아웃 */}
                        {/* ============================= */}

                        <button
                            type="button"
                            onClick={handleLogout}
                            className="
                                flex
                                items-center
                                gap-2

                                px-4
                                py-2

                                bg-sky-500

                                hover:bg-sky-600

                                text-white
                                text-sm
                                font-semibold

                                rounded-xl

                                transition-colors

                                shadow-sm
                                shadow-sky-200
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
                            flex
                            items-center
                            gap-2

                            px-4
                            py-2

                            bg-sky-500

                            hover:bg-sky-600

                            text-white
                            text-sm
                            font-semibold

                            rounded-xl

                            transition-colors

                            shadow-sm
                            shadow-sky-200
                        "
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
                                d="
                                    M16 7
                                    a4 4 0 11-8 0
                                    4 4 0 018 0z

                                    M12 14
                                    a7 7 0 00-7 7
                                    h14
                                    a7 7 0 00-7-7z
                                "
                            />

                        </svg>

                        로그인 / 회원가입

                    </button>

                )}

            </div>

        </header>
    );
}