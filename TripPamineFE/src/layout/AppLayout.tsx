import {
    useState,
    type ReactNode,
} from "react";

import Footer from "../components/Footer";
import Header from "../components/Header";
import Sidebar from "../components/Sidebar";


interface AppLayoutProps {
    children: ReactNode;

    /**
     * 페이지 최상위 배경/글자색.
     *
     * 예:
     *
     * 일반 화면:
     * bg-slate-50
     *
     * Quest:
     * bg-slate-950 text-white
     */
    pageClassName?: string;

    /**
     * main 요소에 추가할 class.
     *
     * 대부분 필요 없지만 페이지별
     * overflow 등의 특수 처리가 필요할 때 사용한다.
     */
    mainClassName?: string;

    /**
     * 기본값 true.
     *
     * 전체 화면형 기능 등 Footer가 필요 없는 페이지는
     * false로 지정할 수 있다.
     */
    showFooter?: boolean;
    appearance?: "light" | "dark";
}


/**
 * TripPamine 전체 애플리케이션 공통 Layout.
 *
 * 기존에는 Home / MyPage / QuestPage / QuestDetailPage /
 * TravelPage 등에서 아래 로직을 계속 반복했다.
 *
 * - sidebarOpen state
 * - Sidebar
 * - Header
 * - pt-16
 * - lg:pl-16 / lg:pl-64
 * - Footer
 *
 * 이 구조를 AppLayout이 한 번만 관리한다.
 *
 * 각 페이지는 이제 자신의 실제 콘텐츠만 책임진다.
 */
export default function AppLayout({
                                      children,
                                      pageClassName = "tp-aurora-light",
                                      mainClassName = "",
                                      showFooter = true,
                                      appearance = "light",
                                  }: AppLayoutProps) {
    const [
        sidebarOpen,
        setSidebarOpen,
    ] = useState(false);

    return (
        <div
            className={`
        min-h-screen
        ${pageClassName}
      `}
        >
            {/* 공통 왼쪽 Navigation */}
            <Sidebar
                open={sidebarOpen}
                onClose={() =>
                    setSidebarOpen(false)
                }
            />

            {/* 공통 상단 Header */}
            <Header
                appearance={appearance}
                sidebarOpen={sidebarOpen}
                onToggleSidebar={() =>
                    setSidebarOpen(
                        (value) => !value,
                    )
                }
            />

            {/*
       * Header 높이가 64px이므로 pt-16.
       *
       * Desktop Sidebar:
       *
       * 접힘 → 64px  → lg:pl-16
       * 펼침 → 256px → lg:pl-64
       *
       * 모바일에서는 Sidebar가 overlay이므로
       * 콘텐츠 자체의 left padding은 적용하지 않는다.
       */}
            <main
                className={`
          min-h-[calc(100vh-4rem)]

          pt-16

          transition-[padding]
          duration-300
          ease-out

          ${
                    sidebarOpen
                        ? "lg:pl-64"
                        : "lg:pl-16"
                }

          ${mainClassName}
        `}
            >
                {children}

                {showFooter && (
                    <Footer />
                )}
            </main>
        </div>
    );
}
