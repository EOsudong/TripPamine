// 게시판 페이지 공용 뼈대 (헤더 + 사이드바 + 본문 + 푸터).
// 다른 페이지(FinancialProductsPage 등)와 같은 레이아웃 규칙을 그대로 따른다.
import { useState, type ReactNode } from "react"
import Header from "../Header"
import Sidebar from "../Sidebar"
import Footer from "../Footer"

export default function BoardLayout({ children }: { children: ReactNode }) {
    const [sidebarOpen, setSidebarOpen] = useState(false)

    return (
        <div className="min-h-screen tp-aurora-light">
            <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
            <Header sidebarOpen={sidebarOpen} onToggleSidebar={() => setSidebarOpen((v) => !v)} />

            <main className={`transition-all duration-300 pt-16 ${sidebarOpen ? "lg:pl-64" : "lg:pl-16"}`}>
                <section className="px-4 py-8 md:py-10">
                    <div className="mx-auto max-w-4xl">
                        {children}
                    </div>
                </section>
                <Footer />
            </main>
        </div>
    )
}
