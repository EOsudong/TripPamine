// 사이드바 "정보" → "금융상품" 페이지 상단의 "맞춤 추천받기" 패널.
// 예치금액/기간을 입력하면 백엔드가 규칙 기반(외부 AI 미사용)으로 상위 5개 상품을 골라
// 순위 + 추천 이유 + (금액을 입력했다면) 세전 예상이자와 함께 보여줍니다.
import { useState } from "react"
import FinancialProductCard from "./FinancialProductCard"
import { getFinancialRecommendationsApi } from "../api/finance"
import type { FinancialBankType, FinancialRecommendation } from "../types"

const TERM_OPTIONS = [1, 3, 6, 12, 24, 36]

function formatCurrency(value: number): string {
    return `${Math.round(value).toLocaleString()}원`
}

interface FinancialRecommendationPanelProps {
    productType: "deposit" | "saving"
    bankType: FinancialBankType
}

export default function FinancialRecommendationPanel({ productType, bankType }: FinancialRecommendationPanelProps) {
    const [amount, setAmount] = useState<string>("")
    const [months, setMonths] = useState<number>()
    const [results, setResults] = useState<FinancialRecommendation[] | null>(null)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const handleRecommend = async () => {
        setLoading(true)
        setError(null)
        try {
            const parsedAmount = amount.trim() === "" ? undefined : Number(amount.replace(/[^0-9]/g, ""))
            const data = await getFinancialRecommendationsApi({
                productType,
                bankType,
                months,
                amount: parsedAmount,
                limit: 5,
            })
            setResults(data)
        } catch (e) {
            console.error("금융상품 추천 조회 실패:", e)
            setError("추천 결과를 불러오지 못했어요. 잠시 후 다시 시도해주세요.")
        } finally {
            setLoading(false)
        }
    }

    return (
        <div className="relative mb-8 overflow-hidden rounded-[2rem] border border-white bg-white/80 p-6 shadow-[0_30px_70px_-40px_rgba(60,40,180,0.5)] backdrop-blur-xl md:p-7">
            <div className="pointer-events-none absolute -right-20 -top-24 h-64 w-64 rounded-full bg-gradient-to-br from-emerald-200/50 via-sky-200/40 to-transparent blur-3xl" />
            <div className="relative mb-3 flex items-center gap-3">
                <span className="flex h-10 w-10 items-center justify-center rounded-2xl tp-gradient-bg text-sm font-black text-white shadow-[0_8px_20px_-8px_rgba(124,108,255,0.9)]">AI</span>
                <div>
                    <p className="tp-kicker">Personal Match</p>
                    <h3 className="text-base font-bold text-slate-900">나에게 맞는 {productType === "saving" ? "적금" : "예금"} 추천받기</h3>
                </div>
            </div>
            <p className="relative mb-5 max-w-2xl text-xs leading-relaxed text-slate-500">
                예치금액과 기간을 넣으면, 그 조건에서 최고우대금리가 가장 높은 상품 5개를 골라드려요.
                (외부 AI 없이 공시 데이터만으로 계산하는 단순 비교이며, 예상이자는 세금·우대조건 충족 여부를 반영하지 않은 참고용 수치예요.)
            </p>

            <div className="relative mb-5 flex flex-wrap items-end gap-3 rounded-2xl bg-slate-50/80 p-3">
                <div>
                    <label className="tp-label">예치금액(원)</label>
                    <input
                        type="text"
                        inputMode="numeric"
                        value={amount}
                        onChange={(e) => setAmount(e.target.value)}
                        placeholder="예: 10,000,000원"
                        className="tp-input w-44"
                    />
                </div>
                <div>
                    <label className="tp-label">기간</label>
                    <select
                        value={months}
                        onChange={(e) => setMonths(Number(e.target.value))}
                        className="tp-input w-auto cursor-pointer pr-8"
                    >
                        {TERM_OPTIONS.map((m) => (
                            <option key={m} value={m}>
                                {m}개월
                            </option>
                        ))}
                    </select>
                </div>
                <button
                    onClick={handleRecommend}
                    disabled={loading}
                    className="tp-btn-primary"
                >
                    {loading ? "찾는 중..." : "추천받기"}
                </button>
            </div>

            {error && <p className="text-xs text-rose-500 mb-2">{error}</p>}

            {results && (
                results.length === 0 ? (
                    <p className="text-xs text-slate-400 py-4 text-center">조건에 맞는 추천 상품이 없어요</p>
                ) : (
                    <div className="space-y-3">
                        {results.map((rec, idx) => (
                            <div key={`${rec.product.finCoNo ?? "co"}-${rec.product.finPrdtCd ?? idx}`}>
                                <div className="flex items-center gap-2 mb-1 px-1">
                                    <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-lg bg-night-950 font-display text-[11px] font-bold text-white">
                                        {idx + 1}
                                    </span>
                                    <p className="text-[11px] text-slate-500">{rec.reason}</p>
                                </div>
                                <FinancialProductCard product={rec.product} />
                                {rec.estimatedInterest !== null && (
                                    <p className="text-[11px] text-slate-400 mt-1 px-1">
                                        {rec.matchedTerm}개월 · {formatCurrency(Number(amount) || 0)} 예치 기준 세전 예상이자 약{" "}
                                        <span className="font-semibold text-slate-600">{formatCurrency(rec.estimatedInterest)}</span>
                                    </p>
                                )}
                            </div>
                        ))}
                    </div>
                )
            )}
        </div>
    )
}
