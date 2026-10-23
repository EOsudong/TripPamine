import {
    useLocation,
    useNavigate,
} from "react-router-dom";

import {
    useAuth,
} from "../context/AuthContext";

import {
    tourMainCategories,
} from "../data/tourCategories";


import "./Sidebar.css";


interface SidebarProps {
    open: boolean;
    onClose: () => void;
}


/**
 * 사이드바에서 사용하는 아이콘 종류.
 *
 * 기존처럼 Emoji 문자열을 직접 넣지 않고
 * 하나의 SVG 스타일로 통일한다.
 *
 * 이렇게 하면:
 *
 * - Windows / macOS Emoji 모양 차이 제거
 * - 아이콘 크기 통일
 * - active color 적용 용이
 * - 전체 UI 완성도 향상
 */
type NavIconName =
    | "manager"
    | "travel"
    | "ai"
    | "music"
    | "quest"
    | "user"
    | "finance"
    | "calendar"
    | "map"
    | "building"
    | "compass"
    | "board"
    | "wallet"
    | "photoEditor"
    | "mascot"
    | "diary";


interface NavItem {
    icon: NavIconName;
    label: string;
    to: string;

    /**
     * 같은 Workspace로 취급할 추가 URL.
     *
     * 예:
     *
     * 여행 플래너
     * /travel
     * /mystery-tour/123
     *
     * 둘 모두 여행 메뉴를 active 처리한다.
     */
    matches?: string[];
}

interface NavGroup {
    group: string;
    items: NavItem[];
}


/**
 * 관광정보 메뉴는 기존 tourMainCategories 데이터를 그대로 사용한다.
 *
 * 기존 데이터 구조를 없애지 않고
 * 아이콘 표현만 현재 Sidebar 디자인 시스템에 맞춰 변환한다.
 */
function getTourIcon(
    path: string,
): NavIconName {
    if (path.includes("festival")) {
        return "calendar";
    }

    if (path.includes("destination")) {
        return "map";
    }

    if (path.includes("industry")) {
        return "building";
    }

    return "compass";
}


/*
 * [메뉴 재구성] "AI 기반 초개인화 맞춤 솔루션 플랫폼" 기준으로
 * AI 매니저 → 여행 → 자산 → 커뮤니티 → 탐색 순서로 묶는다.
 */
const navItems: NavGroup[] = [
    {
        group: "AI Workspace",
        items: [
            {
                icon: "manager",
                label: "AI Manager",
                to: "/",
            },

            {
                icon: "user",
                label: "마이페이지",
                to: "/mypage",
            },
        ],
    },

    {
        group: "Travel",
        items: [
            {
                icon: "travel",
                label: "여행 플래너",
                to: "/travel",

                /*
                 * 미스터리 투어 실행 화면도
                 * Travel Workspace에 속하는 것으로 본다.
                 */
                matches: [
                    "/travel",
                    "/mystery-tour",
                ],
            },

            {
                icon: "ai",
                label: "AI 여행 추천",
                to: "/ai-recommend",
            },


            {
                icon: "photoEditor",
                label: "AI 사진 편집",
                to: "/ai-photo-editor",
            },

          {
              icon: "diary",
              label: "AI 다이어리",
              to: "/diaries",
          },

            {
                icon: "quest",
                label: "여행 퀘스트",
                to: "/quests",
            },

            // [마스코트 추가] /mascots(탐험 지도), /mascots/dex(도감) 모두 이 메뉴를 active 처리
            {
                icon: "mascot",
                label: "마스코트 탐험",
                to: "/mascots",
            },
        ],
    },

    {
        group: "Asset",
        items: [
            // [수정] 병합 과정에서 빠졌던 "자산관리" 메뉴 복구 (/assets → AssetDashboardPage)
            {
                icon: "wallet",
                label: "자산관리",
                to: "/assets",
            },

            {
                icon: "finance",
                label: "금융상품",
                to: "/finance",
            },

        
        ],
    },

    {
        group: "Community",
        items: [
            // [게시판 추가] /boards/123, /boards/write 같은 하위 경로도 isActive()가 자동으로 active 처리
            {
                icon: "board",
                label: "게시판",
                to: "/boards",
            },
            {
                icon: "music",
                label: "여행 플레이리스트",
                to: "/music",
            },
        ],
    },

    {
        group: "Explore",
        items: tourMainCategories.map(
            (category) => ({
                icon: getTourIcon(
                    category.path,
                ),

                label: category.label,

                to: category.path,
            }),
        ),
    },
];


export default function Sidebar({
                                    open,
                                    onClose,
                                }: SidebarProps) {
    const navigate =
        useNavigate();

    /*
     * window.location.pathname을 직접 읽지 않고
     * React Router의 useLocation()을 사용한다.
     *
     * SPA route 이동 시 React 상태 변경에 맞춰
     * Sidebar active 상태도 자동으로 갱신된다.
     */
    const location =
        useLocation();

    const currentPath =
        location.pathname;

    const {
        isLoggedIn,
    } = useAuth();


    /**
     * 메뉴 이동.
     *
     * 현재 Sidebar에서는 더 이상
     * "/#section" 구조를 사용하지 않는다.
     *
     * 각 주요 기능이 독립 Workspace로 분리됐기 때문에
     * 일반적인 React Router navigation만 처리하면 된다.
     */
    const handleClick = (
        to: string,
    ) => {
        navigate(to);

        /*
         * 모바일에서는 메뉴를 누른 후
         * Sidebar를 자동으로 닫는다.
         *
         * Desktop에서는 main layout이
         * open 값을 그대로 유지한다.
         */
        if (
            window.innerWidth < 1024
        ) {
            onClose();
        }
    };


    /**
     * 현재 메뉴가 active인지 계산한다.
     */
    const isActive = (
        item: NavItem,
    ): boolean => {
        /*
         * 홈은 정확히 "/"일 때만 active.
         *
         * 그렇지 않으면 모든 페이지가
         * "/" prefix와 일치하기 때문이다.
         */
        if (item.to === "/") {
            return currentPath === "/";
        }

        const targets =
            item.matches ??
            [item.to];

        return targets.some(
            (path) =>
                currentPath === path ||
                currentPath.startsWith(
                    `${path}/`,
                ),
        );
    };


    return (
        <>
            {/* ==================================================
          Mobile Backdrop
         ================================================== */}

            {open && (
                <button
                    type="button"
                    aria-label="사이드바 닫기"
                    onClick={onClose}
                    className="
            fixed
            inset-0
            top-16
            z-30
            bg-slate-950/30
            backdrop-blur-[2px]
            lg:hidden
          "
                />
            )}


            {/* ==================================================
          Sidebar
         ================================================== */}

            <aside
                id="tp-sidebar"
                aria-label="주 메뉴"
                data-open={open}
                className={`
          tp-sidebar
          fixed
          bottom-0
          left-0
          top-16
          z-40

          flex
          flex-col

          border-r
          border-white/5

          bg-night-900/95
          backdrop-blur-xl

          transition-all
          duration-300
          ease-out

          ${
                    open
                        ? `
                w-[280px]
                translate-x-0
                shadow-[0_20px_50px_rgba(15,23,42,0.12)]

                lg:w-64
                lg:shadow-none
              `
                        : `
                w-[280px]
                -translate-x-full

                lg:w-16
                lg:translate-x-0
                lg:shadow-none
              `
                }
        `}
            >
                {/* ==================================================
            Sidebar Header
           ================================================== */}

                {open && (
                    <div
                        className="
              shrink-0
              border-b
              border-white/5
              px-3
              py-4
            "
                    >
                        <div
                            className="
                flex
                items-center
                justify-between
                gap-3
              "
                        >
                            {/* AI Workspace Identity */}
                            <div
                                className="
                  flex
                  min-w-0
                  items-center
                  gap-3
                "
                            >
                                <div
                                    className="
                    relative
                    flex
                    h-9
                    w-9
                    shrink-0
                    items-center
                    justify-center
                    rounded-xl

                    bg-gradient-to-br
                    from-sky-400
                    to-indigo-500

                    shadow-[0_0_18px_rgba(124,108,255,0.65)]
                    shadow-violet-500/40
                  "
                                >
                  <span
                      className="
                      h-2.5
                      w-2.5
                      rounded-full
                      bg-white
                      shadow-[0_0_12px_rgba(255,255,255,0.9)]
                    "
                  />
                                </div>

                                <div className="min-w-0">
                                    <p
                                        className="
                      truncate
                      text-xs
                      font-black
                      tracking-[0.13em]
                      text-white
                    "
                                    >
                                        AI WORKSPACE
                                    </p>

                                    <p
                                        className="
                      mt-0.5
                      truncate
                      text-[10px]
                      text-[#aaa5bf]
                    "
                                    >
                                        Hyper-Personal AI
                                    </p>
                                </div>
                            </div>

                            {/* 모바일에서만 X 버튼 표시 */}
                            <button
                                type="button"
                                onClick={onClose}
                                aria-label="사이드바 닫기"
                                className="
                  flex
                  h-11
                  w-11
                  shrink-0
                  items-center
                  justify-center
                  rounded-lg

                  text-[#aaa5bf]

                  transition
                  hover:bg-white/10
                  hover:text-white

                  lg:hidden
                "
                            >
                                <svg
                                    className="h-4 w-4"
                                    fill="none"
                                    stroke="currentColor"
                                    viewBox="0 0 24 24"
                                >
                                    <path
                                        strokeLinecap="round"
                                        strokeLinejoin="round"
                                        strokeWidth={1.8}
                                        d="M6 18L18 6M6 6l12 12"
                                    />
                                </svg>
                            </button>
                        </div>
                    </div>
                )}


                {/* ==================================================
            Navigation
           ================================================== */}

                <nav
                    className={`
            flex-1
            overflow-y-auto
            [scrollbar-width:none]
            [&::-webkit-scrollbar]:hidden
            overflow-x-hidden

            py-4

            ${
                        open
                            ? "px-3"
                            : "px-2"
                    }
          `}
                >
                    <div className="space-y-5">
                        {navItems.map(
                            (group, groupIndex) => (
                                <div
                                    key={group.group}
                                >
                                    {/* 그룹 제목 */}
                                    {open ? (
                                        <p
                                            className="
                        mb-2
                        px-3

                        text-[10px]
                        font-black
                        uppercase
                        tracking-[0.16em]

                        text-[#aaa5bf]
                      "
                                        >
                                            {group.group}
                                        </p>
                                    ) : (
                                        /*
                                         * 접힌 Sidebar에서는
                                         * 그룹 제목 대신 얇은 separator 사용.
                                         */
                                        groupIndex > 0 && (
                                            <div
                                                className="
                          mx-auto
                          mb-3
                          h-px
                          w-7
                          bg-white/10
                        "
                                            />
                                        )
                                    )}


                                    <ul className="space-y-1">
                                        {group.items.map(
                                            (item) => {
                                                const active =
                                                    isActive(item);

                                                return (
                                                    <li
                                                        key={
                                                            item.label
                                                        }
                                                    >
                                                        <button
                                                            type="button"

                                                            onClick={() =>
                                                                handleClick(
                                                                    item.to,
                                                                )
                                                            }

                                                            /*
                                                             * 접힌 모드에서는
                                                             * 브라우저 tooltip으로 메뉴명을 확인할 수 있다.
                                                             */
                                                            title={
                                                                open
                                                                    ? undefined
                                                                    : item.label
                                                            }

                                                            aria-current={
                                                                active
                                                                    ? "page"
                                                                    : undefined
                                                            }

                                                            aria-label={
                                                                item.label
                                                            }

                                                            className={`
                                group
                                relative

                                flex
                                w-full
                                items-center

                                rounded-xl

                                transition-all
                                duration-200

                                ${
                                                                open
                                                                    ? `
                                      gap-3
                                      px-3
                                      py-2.5
                                    `
                                                                    : `
                                      h-11
                                      justify-center
                                      px-0
                                    `
                                                            }

                                ${
                                                                active
                                                                    ? `
                                      bg-gradient-to-r from-violet-500/25 via-violet-500/10 to-transparent
                                      text-white
                                    `
                                                                    : `
                                      text-[#c1b9d6]
                                      hover:bg-white/5
                                      hover:text-white
                                    `
                                                            }
                              `}
                                                        >
                                                            {/* Active Rail */}
                                                            {active && (
                                                                <span
                                                                    className="
                                    absolute
                                    -left-3
                                    top-1/2

                                    h-6
                                    w-1

                                    -translate-y-1/2

                                    rounded-r-full
                                    bg-cyan-300 shadow-[0_0_10px_rgba(103,232,249,0.9)]
                                  "
                                                                />
                                                            )}


                                                            {/* Icon */}
                                                            <span
                                                                className={`
                                  flex
                                  h-8
                                  w-8
                                  shrink-0
                                  items-center
                                  justify-center

                                  rounded-lg

                                  transition-all
                                  duration-200

                                  ${
                                                                    active
                                                                        ? `
                                        tp-gradient-bg
                                        text-white
                                        shadow-[0_0_18px_rgba(124,108,255,0.65)]
                                      `
                                                                        : `
                                        text-[#aaa5bf]
                                        group-hover:text-white
                                      `
                                                                }
                                `}
                                                            >
                                <NavIcon
                                    name={
                                        item.icon
                                    }
                                />
                              </span>


                                                            {/* Label */}
                                                            {open && (
                                                                <>
                                  <span
                                      className={`
                                      min-w-0
                                      flex-1
                                      truncate
                                      text-left
                                      text-sm

                                      ${
                                          active
                                              ? "font-bold"
                                              : "font-medium"
                                      }
                                    `}
                                  >
                                    {
                                        item.label
                                    }
                                  </span>

                                                                    {/* Active Dot */}
                                                                    {active && (
                                                                        <span
                                                                            className="
                                        h-1.5
                                        w-1.5
                                        shrink-0
                                        rounded-full
                                        bg-cyan-300 shadow-[0_0_10px_rgba(103,232,249,0.9)]
                                      "
                                                                        />
                                                                    )}
                                                                </>
                                                            )}
                                                        </button>
                                                    </li>
                                                );
                                            },
                                        )}
                                    </ul>
                                </div>
                            ),
                        )}
                    </div>
                </nav>


                {/* ==================================================
            Account Footer
           ================================================== */}

                <div
                    className={`
            shrink-0
            border-t
            border-white/5

            ${
                        open
                            ? "p-3"
                            : "p-2"
                    }
          `}
                >
                    {isLoggedIn ? (
                        <button
                            type="button"
                            onClick={() =>
                                handleClick("/mypage")
                            }
                            aria-label={isLoggedIn ? "마이페이지" : "로그인"}
                            title={
                                open
                                    ? undefined
                                    : "마이페이지"
                            }
                            className={`
                group
                flex
                w-full
                items-center

                rounded-xl

                transition
                hover:bg-white/5

                ${
                                open
                                    ? `
                      gap-3
                      px-3
                      py-2.5
                    `
                                    : `
                      h-11
                      justify-center
                    `
                            }
              `}
                        >
                            <div
                                className="
                  flex
                  h-8
                  w-8
                  shrink-0
                  items-center
                  justify-center

                  rounded-full

                  bg-gradient-to-br
                  from-violet-500
                  to-cyan-400

                  text-[#c1b9d6]
                "
                            >
                                <NavIcon
                                    name="user"
                                />
                            </div>

                            {open && (
                                <>
                                    <div
                                        className="
                      min-w-0
                      flex-1
                      text-left
                    "
                                    >
                                        <p
                                            className="
                        truncate
                        text-xs
                        font-bold
                        text-white/90
                      "
                                        >
                                            내 계정
                                        </p>

                                        <p
                                            className="
                        mt-0.5
                        text-[10px]
                        text-[#aaa5bf]
                      "
                                        >
                                            프로필 및 개인 설정
                                        </p>
                                    </div>

                                    <span
                                        className="
                      text-sm
                      text-[#aaa5bf]
                      transition
                      group-hover:text-cyan-300
                    "
                                    >
                    →
                  </span>
                                </>
                            )}
                        </button>
                    ) : (
                        <button
                            type="button"
                            onClick={() =>
                                handleClick("/login")
                            }
                            aria-label={isLoggedIn ? "마이페이지" : "로그인"}
                            title={
                                open
                                    ? undefined
                                    : "로그인"
                            }
                            className={`
                flex
                w-full
                items-center

                rounded-xl

                transition

                ${
                                open
                                    ? `
                      gap-3
                      tp-gradient-bg
                      px-3
                      py-3
                      text-white
                      hover:brightness-110
                    `
                                    : `
                      h-11
                      justify-center
                      tp-gradient-bg
                      text-white
                      hover:brightness-110
                    `
                            }
              `}
                        >
              <span
                  className="
                  flex
                  h-7
                  w-7
                  shrink-0
                  items-center
                  justify-center
                "
              >
                <NavIcon
                    name="user"
                />
              </span>

                            {open && (
                                <div className="text-left">
                                    <p
                                        className="
                      text-xs
                      font-bold
                    "
                                    >
                                        로그인
                                    </p>

                                    <p
                                        className="
                      mt-0.5
                      text-[10px]
                      text-white/60
                    "
                                    >
                                        AI Manager 시작하기
                                    </p>
                                </div>
                            )}
                        </button>
                    )}
                </div>
            </aside>
        </>
    );
}


/* =====================================================
 * Sidebar SVG Icons
 * ===================================================== */

/**
 * 모든 주요 메뉴 아이콘을 같은 stroke 스타일로 통일한다.
 */
function NavIcon({
                     name,
                 }: {
    name: NavIconName;
}) {
    const commonProps = {
        className: "h-[18px] w-[18px]",
        fill: "none",
        stroke: "currentColor",
        viewBox: "0 0 24 24",
        strokeWidth: 1.8,
        strokeLinecap:
            "round" as const,
        strokeLinejoin:
            "round" as const,
    };


    switch (name) {
        /* AI Manager */
        case "manager":
            return (
                <svg {...commonProps}>
                    <path d="M12 3v3" />
                    <path d="M12 18v3" />
                    <path d="M3 12h3" />
                    <path d="M18 12h3" />

                    <path d="M5.64 5.64l2.12 2.12" />
                    <path d="M16.24 16.24l2.12 2.12" />
                    <path d="M18.36 5.64l-2.12 2.12" />
                    <path d="M7.76 16.24l-2.12 2.12" />

                    <circle
                        cx="12"
                        cy="12"
                        r="3.5"
                    />
                </svg>
            );

            /* 여행 플레이리스트 */
            case "music":
                return (
                    <svg {...commonProps}>
                        <path d="M9 18V5l10-2v13" />
                        <circle cx="6" cy="18" r="3" />
                        <circle cx="16" cy="16" r="3" />
                    </svg>
                );


        /* 여행 */
        case "travel":
            return (
                <svg {...commonProps}>
                    <path d="M3 11l18-7-7 18-3-8-8-3z" />
                </svg>
            );


        /* AI 추천 */
        case "ai":
            return (
                <svg {...commonProps}>
                    <rect
                        x="5"
                        y="6"
                        width="14"
                        height="12"
                        rx="3"
                    />

                    <path d="M9 10h.01" />
                    <path d="M15 10h.01" />
                    <path d="M9 14h6" />
                    <path d="M12 3v3" />
                </svg>
            );


        /* AI 사진 편집 */
        case "photoEditor":
            return (
                <svg {...commonProps}>
                    <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z" />
                    <circle cx="12" cy="13" r="4" />
                </svg>
            );

      /* AI 다이어리 */
      case "diary":
        return (
          <svg {...commonProps}>
            <rect x="5" y="3" width="14" height="18" rx="2" />
            <path d="M9 3v18" />
            <path d="M12.5 8h3" />
            <path d="M12.5 12h3" />
          </svg>
        );


        /* 마스코트 */
        case "mascot":
            return (
                <svg {...commonProps}>
                    <circle cx="12" cy="13" r="7" />
                    <path d="M5 13h14" />
                    <circle cx="12" cy="13" r="2.2" />
                    <path d="M8 4.5l1.5 2.5" />
                    <path d="M16 4.5L14.5 7" />
                </svg>
            );


        /* Quest */
        case "quest":
            return (
                <svg {...commonProps}>
                    <path d="M12 3l3 6 6 1-4.5 4.5L18 21l-6-3-6 3 1.5-6.5L3 10l6-1 3-6z" />
                </svg>
            );


        /* User */
        case "user":
            return (
                <svg {...commonProps}>
                    <circle
                        cx="12"
                        cy="8"
                        r="3"
                    />

                    <path d="M5 21c.8-4 3.1-6 7-6s6.2 2 7 6" />
                </svg>
            );


        /* 게시판 */
        case "board":
            return (
                <svg {...commonProps}>
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                    <path d="M8 9h8" />
                    <path d="M8 13h5" />
                </svg>
            );


        /* 자산관리 */
        case "wallet":
            return (
                <svg {...commonProps}>
                    <rect x="3" y="6" width="18" height="14" rx="2" />
                    <path d="M3 10h18" />
                    <path d="M16 15h2" />
                    <path d="M6 6l9-3 1 3" />
                </svg>
            );


        /* 금융 */
        case "finance":
            return (
                <svg {...commonProps}>
                    <path d="M3 9l9-5 9 5" />
                    <path d="M5 10v7" />
                    <path d="M9 10v7" />
                    <path d="M15 10v7" />
                    <path d="M19 10v7" />
                    <path d="M3 20h18" />
                </svg>
            );


        /* 축제 */
        case "calendar":
            return (
                <svg {...commonProps}>
                    <rect
                        x="4"
                        y="5"
                        width="16"
                        height="15"
                        rx="2"
                    />

                    <path d="M8 3v4" />
                    <path d="M16 3v4" />
                    <path d="M4 10h16" />
                </svg>
            );


        /* 여행지 */
        case "map":
            return (
                <svg {...commonProps}>
                    <path d="M9 18l-6 3V6l6-3 6 3 6-3v15l-6 3-6-3z" />
                    <path d="M9 3v15" />
                    <path d="M15 6v15" />
                </svg>
            );


        /* 관광산업 */
        case "building":
            return (
                <svg {...commonProps}>
                    <path d="M5 21V5h10v16" />
                    <path d="M15 9h4v12" />
                    <path d="M8 9h4" />
                    <path d="M8 13h4" />
                    <path d="M8 17h4" />
                    <path d="M3 21h18" />
                </svg>
            );


        /* fallback */
        case "compass":
        default:
            return (
                <svg {...commonProps}>
                    <circle
                        cx="12"
                        cy="12"
                        r="9"
                    />

                    <path d="M15.5 8.5l-2 5-5 2 2-5 5-2z" />
                </svg>
            );
    }
}
