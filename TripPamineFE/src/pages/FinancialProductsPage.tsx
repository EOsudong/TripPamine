// 사이드바 "정보" 그룹의 "금융상품" 메뉴가 이동하는 페이지.
// 라우트: /finance (로그인 여부와 무관하게 접근 가능 — Router.tsx에서 ProtectedRoute로 감싸지 않음)
// 금융감독원 오픈API(금융상품 한눈에, FINLIFE)로 받아오는 정기예금/적금 상품을 보여줍니다.
import { useEffect, useState } from "react"
import Header from "../components/Header"
import Sidebar from "../components/Sidebar"
import Footer from "../components/Footer"
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
        <div className="min-h-screen bg-slate-50">
            <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
            <Header sidebarOpen={sidebarOpen} onToggleSidebar={() => setSidebarOpen((v) => !v)} />

            <main className={`transition-all duration-300 pt-16 ${sidebarOpen ? "lg:pl-64" : "lg:pl-16"}`}>
                <section className="py-14 px-4">
                    <div className="max-w-4xl mx-auto">
                        {/* Section header */}
                        <div className="mb-8">
                            <p className="text-sky-500 text-xs font-semibold tracking-widest uppercase mb-1">
                                금융감독원 오픈API · 금융상품 한눈에
                            </p>
                            <h2 className="text-2xl md:text-3xl font-bold text-slate-900 flex items-center gap-2">
                                <span>🏦</span>
                                금융상품
                            </h2>
                            <p className="text-slate-500 text-sm mt-1">
                                로그인 없이도 볼 수 있는 정기예금·적금 금리 비교 정보예요. 금융감독원이 공시하는 자료를
                                하루 한 번 받아와서 보여드려요.
                            </p>
                        </div>

                        {/* 상품 종류 탭 (정기예금 / 적금) */}
                        <div className="flex gap-2 mb-4">
                            {PRODUCT_TABS.map((tab) => (
                                <button
                                    key={tab.key}
                                    onClick={() => setProductTab(tab.key)}
                                    className={`px-4 py-1.5 rounded-full text-sm font-medium transition-colors ${
                                        productTab === tab.key
                                            ? "bg-slate-900 text-white shadow-sm"
                                            : "bg-white text-slate-600 border border-slate-200 hover:border-slate-400"
                                    }`}
                                >
                                    {tab.label}
                                </button>
                            ))}
                        </div>

                        {/* 업권 필터 (시중은행 / 저축은행) */}
                        <div className="flex gap-2 flex-wrap mb-7">
                            {BANK_TYPE_TABS.map((tab) => (
                                <button
                                    key={tab.key}
                                    onClick={() => setBankType(tab.key)}
                                    className={`px-4 py-1.5 rounded-full text-sm font-medium transition-colors ${
                                        bankType === tab.key
                                            ? "bg-sky-500 text-white shadow-sm"
                                            : "bg-white text-slate-600 border border-slate-200 hover:border-sky-300 hover:text-sky-500"
                                    }`}
                                >
                                    {tab.label}
                                </button>
                            ))}
                        </div>

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
                <div key={i} className="h-20 rounded-2xl bg-white border border-slate-100 animate-pulse" />
            ))}
        </div>
    )
}

function StateMessage({ label }: { label: string }) {
    return (
        <div className="text-center py-16 text-slate-400 text-sm bg-white rounded-2xl border border-dashed border-slate-200">
            {label}
        </div>
    )
}
