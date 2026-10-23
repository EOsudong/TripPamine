// 사이드바 "정보" → "금융상품" 페이지 상단의 "맞춤 추천받기" 패널.
// 예금 일시 예치금액/적금 매월 납입액과 기간을 입력하면 규칙 기반으로 상품을 골라
// 순위 + 추천 이유 + (금액을 입력했다면) 세전 예상이자와 함께 보여줍니다.
import { useEffect, useRef, useState } from "react"
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
    const [months, setMonths] = useState<number>(12)
    const [results, setResults] = useState<FinancialRecommendation[] | null>(null)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const requestVersion = useRef(0)

    // 탭 변경 중 이전 요청의 응답이 도착해도 다른 금융상품 결과가 섞이지 않도록 한다.
    useEffect(() => {
        requestVersion.current += 1
        setResults(null)
        setError(null)
        setLoading(false)
    }, [productType, bankType])

    // 예금(일시금)과 적금(월 납입액)은 입력 금액의 의미가 다르므로 탭 변경 시 초기화한다.
    useEffect(() => {
        setAmount("")
    }, [productType])

    const handleRecommend = async () => {
        const normalizedAmount = amount.replace(/,/g, "").trim()
        if (normalizedAmount && !/^[1-9]\d*$/.test(normalizedAmount)) {
            setError(productType === "saving" ? "월 납입액은 1원 이상의 숫자로 입력해주세요." : "예치금액은 1원 이상의 숫자로 입력해주세요.")
            setResults(null)
            return
        }
        const parsedAmount = normalizedAmount ? Number(normalizedAmount) : undefined
        if (parsedAmount !== undefined && !Number.isSafeInteger(parsedAmount)) {
            setError("금액이 너무 큽니다. 원 단위 숫자를 다시 확인해주세요.")
            setResults(null)
            return
        }

        const currentRequest = ++requestVersion.current
        setLoading(true)
        setError(null)
        try {
            const data = await getFinancialRecommendationsApi({
                productType,
                bankType,
                months,
                amount: parsedAmount,
                limit: 5,
            })
            if (requestVersion.current === currentRequest) setResults(data)
        } catch (e) {
            if (requestVersion.current === currentRequest) {
                console.error("금융상품 추천 조회 실패:", e)
                setError("추천 결과를 불러오지 못했어요. 잠시 후 다시 시도해주세요.")
            }
        } finally {
            if (requestVersion.current === currentRequest) setLoading(false)
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
                요청한 가입기간과 정확히 일치하는 상품을 기본금리 순서로 비교해요.
                {productType === "saving" ? " 적금 금액은 매월 1회 납입하는 금액이에요." : " 예금 금액은 가입할 때 한 번 예치하는 금액이에요."}
                {" "}예상 이자는 세전 참고치이며 실제 우대조건 충족 여부나 은행별 이자 지급 규칙은 확인되지 않았어요.
            </p>

            <div className="relative mb-5 flex flex-wrap items-end gap-3 rounded-2xl bg-slate-50/80 p-3">
                <div>
                    <label className="tp-label">{productType === "saving" ? "월 납입액(원)" : "일시 예치금액(원)"}</label>
                    <input
                        type="text"
                        inputMode="numeric"
                        value={amount}
                        onChange={(e) => setAmount(e.target.value)}
                        placeholder={productType === "saving" ? "예: 300,000" : "예: 10,000,000"}
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
                                <div className="mb-2 flex flex-wrap gap-x-4 gap-y-1 px-1 text-xs text-slate-700">
                                    <span>해당 {rec.matchedTerm}개월 기본금리: <strong>{rec.matchedIntrRate != null ? `${rec.matchedIntrRate.toFixed(2)}%` : "미공시"}</strong></span>
                                    <span>최고우대금리: <strong>{rec.matchedIntrRate2 != null ? `${rec.matchedIntrRate2.toFixed(2)}%` : "미공시"}</strong></span>
                                </div>
                                <FinancialProductCard product={rec.product} />
                                <div className="mt-2 space-y-1 px-1 text-[11px] text-slate-500">
                                    {rec.estimatedInterest != null && (
                                        <p>기본금리 기준 세전 예상이자: <strong className="text-slate-700">{formatCurrency(rec.estimatedInterest)}</strong></p>
                                    )}
                                    {rec.estimatedMaxInterest != null && (
                                        <p>최고우대금리 충족 가정 세전 예상이자: <strong className="text-slate-700">{formatCurrency(rec.estimatedMaxInterest)}</strong></p>
                                    )}
                                    <p>{rec.estimateBasis}</p>
                                </div>
                            </div>
                        ))}
                    </div>
                )
            )}
        </div>
    )
}
