// 금융감독원 오픈API(금융상품 한눈에) 데이터 카드 한 장을 그리는 공용 컴포넌트.
// 정기예금/적금 두 탭, 그리고 맞춤 추천 결과가 전부 이 카드를 재사용합니다.
// 클릭하면 아코디언처럼 펼쳐지면서 기간(개월)별 금리 표와 우대조건 등 상세 설명을 보여줍니다.
//
// [북마크 추가] 우측 상단 🔖 버튼으로 저장 — 관광정보 상세페이지(TourDetailPage.tsx)와 동일하게
// 로그인 안 한 상태에서 누르면 로그인 페이지로 보내고, 로그인 성공 후 이 페이지로 돌아오게 함.
// 실제 저장은 백엔드 없이 브라우저 localStorage에 하며(api/financeBookmark.ts), 마이페이지의
// "저장된 금융상품" 탭에서 모아 볼 수 있습니다.
import { useEffect, useState } from "react"
import { useLocation, useNavigate } from "react-router-dom"
import { useAuth } from "../context/AuthContext"
import {
    addFinanceBookmarkApi,
    getFinanceBookmarkId,
    getFinanceBookmarkStatusApi,
    removeFinanceBookmarkApi,
} from "../api/financeBookmark"
import type { FinancialProduct } from "../types"

function formatRate(rate: number | null): string {
    if (rate === null || rate === undefined) return "-"
    return `${rate.toFixed(2)}%`
}

interface FinancialProductCardProps {
    product: FinancialProduct
}

export default function FinancialProductCard({ product }: FinancialProductCardProps) {
    const [open, setOpen] = useState(false)
    const [bookmarked, setBookmarked] = useState(false)
    const [bookmarkBusy, setBookmarkBusy] = useState(false)

    const { isLoggedIn } = useAuth()
    const navigate = useNavigate()
    const location = useLocation()

    const bookmarkId = getFinanceBookmarkId(product)
    const baseRate = product.options[0]?.intrRate ?? null

    // 로그인한 상태에서만 "이미 북마크했는지" 확인해서 버튼 초기 상태를 맞춤 (TourDetailPage.tsx와 동일한 패턴)
    useEffect(() => {
        if (!isLoggedIn) {
            setBookmarked(false)
            return
        }

        let cancelled = false
        getFinanceBookmarkStatusApi(bookmarkId)
            .then((isBookmarked) => {
                if (!cancelled) setBookmarked(isBookmarked)
            })
            .catch(() => {
                // 상태 확인 실패는 조용히 무시 — 버튼은 "저장 안 함" 상태로 남아있고, 눌러보면 다시 시도됨
            })

        return () => {
            cancelled = true
        }
    }, [bookmarkId, isLoggedIn])

    const handleToggleBookmark = async (e: React.MouseEvent) => {
        e.stopPropagation() // 카드 아코디언 토글과 겹치지 않도록

        // 로그인 안 한 상태로 북마크를 누르면 로그인 페이지로 보내고, 로그인 성공 후 이 페이지로 돌아오게 함
        if (!isLoggedIn) {
            navigate("/login", { state: { from: location } })
            return
        }

        setBookmarkBusy(true)
        try {
            if (bookmarked) {
                await removeFinanceBookmarkApi(bookmarkId)
                setBookmarked(false)
            } else {
                await addFinanceBookmarkApi(product)
                setBookmarked(true)
            }
        } catch (err) {
            console.error("금융상품 북마크 처리 실패:", err)
            alert("북마크 처리 중 오류가 발생했어요. 잠시 후 다시 시도해주세요.")
        } finally {
            setBookmarkBusy(false)
        }
    }

    return (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-sm hover:shadow-md transition-all duration-300 overflow-hidden">
            <div className="w-full flex items-center gap-2 p-4">
                <button
                    type="button"
                    onClick={() => setOpen((v) => !v)}
                    className="flex-1 min-w-0 flex items-center justify-between gap-3 text-left"
                >
                    <div className="min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                            <span className="shrink-0 px-2 py-0.5 bg-sky-50 text-sky-600 text-[11px] font-bold rounded-full">
                                {product.korCoNm}
                            </span>
                            {product.joinDenyLabel && product.joinDenyLabel !== "제한없음" && (
                                <span className="shrink-0 px-2 py-0.5 bg-amber-50 text-amber-600 text-[10px] font-semibold rounded-full">
                                    {product.joinDenyLabel}
                                </span>
                            )}
                        </div>
                        <h3 className="font-bold text-slate-800 text-sm leading-snug line-clamp-1">
                            {product.finPrdtNm}
                        </h3>
                        {product.dclsMonth && (
                            <p className="text-[11px] text-slate-400 mt-0.5">공시월 {product.dclsMonth}</p>
                        )}
                    </div>

                    <div className="shrink-0 flex items-center gap-3">
                        <div className="text-right">
                            <p className="text-[11px] text-slate-400">최고우대금리</p>
                            <p className="text-lg font-extrabold text-sky-600 leading-tight">
                                {formatRate(product.maxRate)}
                            </p>
                            {baseRate !== null && (
                                <p className="text-[11px] text-slate-400">기본 {formatRate(baseRate)}</p>
                            )}
                        </div>
                        <svg
                            className={`w-4 h-4 text-slate-400 transition-transform duration-300 ${open ? "rotate-180" : ""}`}
                            fill="none"
                            stroke="currentColor"
                            viewBox="0 0 24 24"
                        >
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                        </svg>
                    </div>
                </button>

                <button
                    type="button"
                    onClick={handleToggleBookmark}
                    disabled={bookmarkBusy}
                    title={bookmarked ? "북마크 해제" : "북마크 저장"}
                    className={`shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-sm transition-colors disabled:opacity-60 ${
                        bookmarked ? "bg-amber-100 text-amber-600" : "bg-slate-50 text-slate-400 hover:bg-slate-100"
                    }`}
                >
                    🔖
                </button>
            </div>

            {open && (
                <div className="px-4 pb-4 border-t border-slate-50 pt-3 space-y-3">
                    {product.options.length > 0 && (
                        <div className="overflow-x-auto">
                            <table className="w-full text-xs">
                                <thead>
                                <tr className="text-slate-400 border-b border-slate-100">
                                    <th className="text-left font-medium py-1.5 pr-3">기간</th>
                                    <th className="text-left font-medium py-1.5 pr-3">이자 방식</th>
                                    {product.productType === "saving" && (
                                        <th className="text-left font-medium py-1.5 pr-3">적립유형</th>
                                    )}
                                    <th className="text-right font-medium py-1.5 pr-3">기본금리</th>
                                    <th className="text-right font-medium py-1.5">최고우대금리</th>
                                </tr>
                                </thead>
                                <tbody>
                                {product.options.map((option, idx) => (
                                    <tr key={idx} className="border-b border-slate-50 last:border-0">
                                        <td className="py-1.5 pr-3 text-slate-700">
                                            {option.saveTrm ? `${option.saveTrm}개월` : "-"}
                                        </td>
                                        <td className="py-1.5 pr-3 text-slate-500">{option.intrRateTypeNm ?? "-"}</td>
                                        {product.productType === "saving" && (
                                            <td className="py-1.5 pr-3 text-slate-500">{option.rsrvTypeNm ?? "-"}</td>
                                        )}
                                        <td className="py-1.5 pr-3 text-right text-slate-600">{formatRate(option.intrRate)}</td>
                                        <td className="py-1.5 text-right font-semibold text-sky-600">
                                            {formatRate(option.intrRate2)}
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    )}

                    <dl className="space-y-2 text-xs text-slate-500">
                        {product.joinWay && (
                            <div className="flex gap-2">
                                <dt className="shrink-0 w-16 font-semibold text-slate-400">가입방법</dt>
                                <dd className="flex-1">{product.joinWay}</dd>
                            </div>
                        )}
                        {product.joinMember && (
                            <div className="flex gap-2">
                                <dt className="shrink-0 w-16 font-semibold text-slate-400">가입대상</dt>
                                <dd className="flex-1">{product.joinMember}</dd>
                            </div>
                        )}
                        {product.spclCnd && (
                            <div className="flex gap-2">
                                <dt className="shrink-0 w-16 font-semibold text-slate-400">우대조건</dt>
                                <dd className="flex-1 whitespace-pre-line">{product.spclCnd}</dd>
                            </div>
                        )}
                        {product.mtrtInt && (
                            <div className="flex gap-2">
                                <dt className="shrink-0 w-16 font-semibold text-slate-400">만기 후</dt>
                                <dd className="flex-1 whitespace-pre-line">{product.mtrtInt}</dd>
                            </div>
                        )}
                        {product.etcNote && (
                            <div className="flex gap-2">
                                <dt className="shrink-0 w-16 font-semibold text-slate-400">유의사항</dt>
                                <dd className="flex-1 whitespace-pre-line">{product.etcNote}</dd>
                            </div>
                        )}
                    </dl>
                </div>
            )}
        </div>
    )
}
