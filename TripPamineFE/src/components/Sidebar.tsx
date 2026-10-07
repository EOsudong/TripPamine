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


// =====================================================
// Sidebar Menu
// =====================================================

const navItems: NavGroup[] = [

  {
    group: "탐색",

    items: [

      {
        icon: "🏠",
        label: "홈",
        to: "/",
      },

      {
        icon: "🤖",
        label: "AI 여행 추천",
        to: "/#ai-planner",
      },

      {
        icon: "🎯",
        label: "여행 퀘스트",
        to: "/quests",
      },

    ],
  },


  {
    group: "마이",

    items: [

      {
        icon: "👤",
        label: "마이페이지",
        to: "/mypage",
      },

      {
        icon: "🔖",
        label: "북마크",
        to: "/mypage",
      },

      {
        icon: "📅",
        label: "내 여행 일정",
        to: "/mypage",
      },

      {
        icon: "💰",
        label: "자산관리",
        to: "/assets",
      },

    ],
  },


  {
    group: "정보",

    items: [

      ...tourMainCategories.map(
        (category) => ({

          icon: category.icon,

          label: category.label,

          to: category.path,

        }),
      ),


      {
        icon: "🏦",
        label: "금융상품",
        to: "/finance",
      },


      {
        icon: "💬",
        label: "자유게시판",
        to: null,
      },


      {
        icon: "⚙️",
        label: "설정",
        to: null,
      },

    ],
  },

];


// =====================================================
// Sidebar
// =====================================================

export default function Sidebar({
  open,
  onClose,
}: SidebarProps) {

  const navigate =
    useNavigate();


  const currentPath =

    typeof window !== "undefined"

      ? window.location.pathname

      : "/";


  // ===================================================
  // Menu Click
  // ===================================================

  function handleClick(
    to: string | null,
  ) {

    if (!to) {
      return;
    }


    // 홈 내부 특정 위치
    if (
      to.startsWith("/#")
    ) {

      navigate("/");


      window.setTimeout(
        () => {

          document
            .getElementById(
              to.split("#")[1],
            )
            ?.scrollIntoView({
              behavior: "smooth",
            });

        },
        50,
      );

    } else {

      navigate(to);

    }


    onClose();
  }


  // ===================================================
  // Active Menu
  // ===================================================

  function isActive(
    to: string | null,
    label: string,
  ) {

    if (!to) {
      return false;
    }


    // 홈
    if (to === "/") {

      return (
        currentPath === "/"
        && label === "홈"
      );
    }


    // AI 추천은 홈의 anchor이므로
    // 홈에서 항상 active 처리하지 않음
    if (to.startsWith("/#")) {
      return false;
    }


    // 여행 퀘스트
    if (to === "/quests") {

      return currentPath.startsWith(
        "/quests",
      );
    }


    // 자산관리
    if (to === "/assets") {

      return currentPath.startsWith(
        "/assets",
      );
    }


    // 금융상품
    if (to === "/finance") {

      return currentPath.startsWith(
        "/finance",
      );
    }


    // =================================================
    // 중요
    //
    // 현재 마이페이지 / 북마크 / 내 여행 일정이
    // 모두 /mypage로 연결되어 있음.
    //
    // 따라서 /mypage에서는
    // 마이페이지만 Active 처리한다.
    // =================================================

    if (to === "/mypage") {

      return (
        currentPath === "/mypage"
        && label === "마이페이지"
      );
    }


    return currentPath === to;
  }


  return (

    <>

      {/* ================================================= */}
      {/* Mobile Overlay */}
      {/* ================================================= */}

      {open && (

        <div

          className="
            fixed
            inset-0
            top-16
            bg-black/40
            z-30
            lg:hidden
          "

          onClick={onClose}

        />

      )}


      {/* ================================================= */}
      {/* Sidebar */}
      {/* ================================================= */}

      <aside

        className={`
          fixed
          top-16
          left-0

          h-[calc(100%-4rem)]

          z-40

          bg-white

          border-r
          border-slate-100

          flex
          flex-col

          transition-all
          duration-300
          ease-in-out

          ${
            open

              ? "w-64 shadow-2xl"

              : "w-0 lg:w-16 overflow-hidden"
          }
        `}

      >


        {/* ================================================= */}
        {/* Mobile Close */}
        {/* ================================================= */}

        {open && (

          <div

            className="
              flex
              items-center
              justify-end

              px-3
              py-3

              border-b
              border-slate-100

              shrink-0

              lg:hidden
            "

          >

            <button

              type="button"

              onClick={onClose}

              className="
                p-1.5

                rounded-lg

                hover:bg-slate-100

                text-slate-500
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
                    M6 18L18 6
                    M6 6l12 12
                  "

                />

              </svg>

            </button>

          </div>

        )}


        {/* ================================================= */}
        {/* Navigation */}
        {/* ================================================= */}

        <nav

          className="
            flex-1

            overflow-y-auto

            py-4
            px-2

            space-y-5
          "

        >

          {navItems.map(
            (group) => (

              <div
                key={group.group}
              >


                {/* Group Title */}

                {open && (

                  <p

                    className="
                      text-[10px]

                      font-bold

                      text-slate-400

                      uppercase

                      tracking-widest

                      px-3

                      mb-1.5
                    "

                  >

                    {group.group}

                  </p>

                )}


                <ul className="space-y-0.5">

                  {group.items.map(
                    (item) => {


                      const active =
                        isActive(
                          item.to,
                          item.label,
                        );


                      return (

                        <li
                          key={item.label}
                        >

                          <button

                            type="button"

                            onClick={() =>
                              handleClick(
                                item.to,
                              )
                            }

                            className={`
                              w-full

                              flex
                              items-center

                              gap-3

                              px-3
                              py-2.5

                              rounded-xl

                              text-sm

                              font-medium

                              transition-all

                              group

                              ${
                                active

                                  ? `
                                    bg-sky-50
                                    text-sky-600
                                  `

                                  : `
                                    text-slate-600

                                    hover:bg-slate-50

                                    hover:text-slate-900
                                  `
                              }

                              ${
                                !open

                                  ? "justify-center"

                                  : ""
                              }
                            `}

                          >


                            {/* Icon */}

                            <span

                              className="
                                text-base
                                shrink-0
                              "

                            >

                              {item.icon}

                            </span>


                            {/* Label */}

                            {open && (

                              <span>
                                {item.label}
                              </span>

                            )}


                            {/* Active Point */}

                            {active && open && (

                              <span

                                className="
                                  ml-auto

                                  w-1.5
                                  h-1.5

                                  rounded-full

                                  bg-sky-500
                                "

                              />

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

        </nav>


        {/* ================================================= */}
        {/* Login */}
        {/* ================================================= */}

        {open && (

          <div

            className="
              p-3

              border-t
              border-slate-100
            "

          >

            <button

              type="button"

              onClick={() =>
                handleClick(
                  "/login",
                )
              }

              className="
                w-full

                flex
                items-center

                gap-3

                px-3
                py-2.5

                rounded-xl

                text-sm

                text-slate-500

                hover:bg-slate-50

                transition-colors
              "

            >

              <div

                className="
                  w-7
                  h-7

                  rounded-full

                  bg-slate-200

                  flex

                  items-center
                  justify-center

                  shrink-0
                "

              >

                <svg

                  className="
                    w-4
                    h-4

                    text-slate-500
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
                      M16 7a4 4 0 11-8 0
                      4 4 0 018 0z

                      M12 14a7 7 0 00-7 7h14
                      a7 7 0 00-7-7z
                    "

                  />

                </svg>

              </div>


              <div className="text-left">

                <p

                  className="
                    text-xs
                    font-semibold
                    text-slate-700
                  "

                >

                  로그인이 필요해요

                </p>


                <p

                  className="
                    text-[10px]
                    text-slate-400
                  "

                >

                  클릭해서 시작하기

                </p>

              </div>

            </button>

          </div>

        )}

      </aside>

    </>

  );
}