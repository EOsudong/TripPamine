// 사이드바 "정보" 그룹의 "금융상품" 메뉴가 이동하는 페이지.
// 라우트: /finance (로그인 여부와 무관하게 접근 가능 — Router.tsx에서 ProtectedRoute로 감싸지 않음)
// 금융감독원 오픈API(금융상품 한눈에, FINLIFE)로 받아오는 정기예금/적금 상품을 보여줍니다.
import { useEffect, useState } from "react"
import Header from "../components/Header"
import Sidebar from "../components/Sidebar"
import Footer from "../components/Footer"
import PageHero from "../components/PageHero"
import FinancialProductCard from "../components/FinancialProductCard"
import FinancialRecommendationPanel from "../components/FinancialRecommendationPanel"
import { getDepositProductsApi, getSavingProductsApi } from "../api/finance"
import type { FinancialBankType, FinancialProduct } from "../types"

type ProductTab = "deposit" | "saving"

const PRODUCT_TABS: { key: ProductTab; label: string }[] = [
    { key: "deposit", label: "정기예금" },
    { key: "saving", label: "적금" },
]

const BANK_TYPE_TABS: { key: FinancialBankType; label: string }[] = [
    { key: "bank", label: "시중은행" },
    { key: "savings", label: "저축은행" },
]

export default function FinancialProductsPage() {
    const [sidebarOpen, setSidebarOpen] = useState(false)
    const [productTab, setProductTab] = useState<ProductTab>("deposit")
    const [bankType, setBankType] = useState<FinancialBankType>("bank")
    const [products, setProducts] = useState<FinancialProduct[]>([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        let cancelled = false
        setLoading(true)
        setError(null)

        const fetcher = productTab === "deposit" ? getDepositProductsApi : getSavingProductsApi

        fetcher(bankType)
            .then((data) => {
                if (!cancelled) setProducts(data)
            })
            .catch(() => {
                if (!cancelled) setError("금융상품 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.")
            })
            .finally(() => {
                if (!cancelled) setLoading(false)
            })

        return () => {
            cancelled = true
        }
    }, [productTab, bankType])

    return (
        <div className="min-h-screen tp-aurora-light">
            <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
            <Header sidebarOpen={sidebarOpen} onToggleSidebar={() => setSidebarOpen((v) => !v)} />

            <main className={`transition-all duration-300 pt-16 ${sidebarOpen ? "lg:pl-64" : "lg:pl-16"}`}>
                <section className="px-4 py-8 md:py-10">
                    <div className="max-w-4xl mx-auto">
                        {/* Section header */}
                        <PageHero
                            className="mb-8"
                            tone="emerald"
                            eyebrow="금융감독원 오픈API · AI ASSET"
                            icon="🏦"
                            title={<>나에게 맞는 <span className="tp-gradient-text">금융상품</span></>}
                            description="로그인 없이도 볼 수 있는 정기예금·적금 금리 비교 정보예요. 금융감독원이 공시하는 자료를 하루 한 번 받아와서 보여드려요."
                        >
                            <div className="flex flex-col gap-3">
                                {/* 상품 종류 탭 (정기예금 / 적금) */}
                                <div className="flex w-fit gap-1 rounded-2xl border border-white/10 bg-white/5 p-1">
                                    {PRODUCT_TABS.map((tab) => (
                                        <button
                                            key={tab.key}
                                            onClick={() => setProductTab(tab.key)}
                                            className={`rounded-xl px-5 py-2 text-sm font-bold transition ${
                                                productTab === tab.key
                                                    ? "bg-white text-night-950 shadow-[0_0_24px_rgba(52,211,153,0.45)]"
                                                    : "text-white/65 hover:text-white"
                                            }`}
                                        >
                                            {tab.label}
                                        </button>
                                    ))}
                                </div>

                                {/* 업권 필터 (시중은행 / 저축은행) */}
                                <div className="flex flex-wrap gap-2">
                                    {BANK_TYPE_TABS.map((tab) => (
                                        <button
                                            key={tab.key}
                                            onClick={() => setBankType(tab.key)}
                                            className={`rounded-full px-4 py-1.5 text-xs font-semibold transition ${
                                                bankType === tab.key
                                                    ? "bg-emerald-300 text-night-950"
                                                    : "border border-white/15 bg-white/5 text-white/70 hover:bg-white/10 hover:text-white"
                                            }`}
                                        >
                                            {tab.label}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        </PageHero>

                        {/* 맞춤 추천 (규칙 기반 — 예치금액/기간을 넣으면 최고우대금리 상위 5개를 골라줌) */}
                        <FinancialRecommendationPanel productType={productTab} bankType={bankType} />

                        <h3 className="font-bold text-slate-800 text-sm mb-3">전체 목록</h3>

                        {/* 목록 상태별 렌더링 */}
                        {loading ? (
                            <LoadingList />
                        ) : error ? (
                            <StateMessage label={error} />
                        ) : products.length === 0 ? (
                            <StateMessage label="조건에 맞는 상품이 없어요" />
                        ) : (
                            <div className="space-y-3">
                                {products.map((product, idx) => (
                                    <FinancialProductCard
                                        key={`${product.finCoNo ?? "co"}-${product.finPrdtCd ?? idx}`}
                                        product={product}
                                    />
                                ))}
                            </div>
                        )}
                    </div>
                </section>

                <Footer />
            </main>
        </div>
    )
}

function LoadingList() {
    return (
        <div className="space-y-3">
            {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="h-20 animate-pulse rounded-2xl border border-white bg-gradient-to-r from-white to-emerald-50" />
            ))}
        </div>
    )
}

function StateMessage({ label }: { label: string }) {
    return (
        <div className="rounded-3xl border border-dashed border-sky-200 bg-white/70 py-16 text-center text-sm text-slate-400 backdrop-blur">
            {label}
        </div>
    )
}
