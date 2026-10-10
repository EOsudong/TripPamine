// 게시글 신고 모달: 사유 선택 + 상세 내용(기타 사유는 필수)
import { useState, type FormEvent } from "react"
import {
    BOARD_REPORT_REASON_LABEL,
    boardApi,
    extractBoardErrorMessage,
    type BoardReportReason,
} from "../../api/board"

interface ReportModalProps {
    postId: number
    onClose: () => void
    onReported: () => void
}

const REASONS = Object.keys(BOARD_REPORT_REASON_LABEL) as BoardReportReason[]
const DETAIL_MAX = 500

export default function ReportModal({ postId, onClose, onReported }: ReportModalProps) {
    const [reason, setReason] = useState<BoardReportReason | null>(null)
    const [detail, setDetail] = useState("")
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const detailRequired = reason === "ETC"
    const canSubmit = reason !== null && (!detailRequired || detail.trim().length > 0) && !submitting

    async function handleSubmit(e: FormEvent) {
        e.preventDefault()
        if (!reason || !canSubmit) return
        setSubmitting(true)
        setError(null)
        try {
            await boardApi.reportPost(postId, { reason, detail: detail.trim() || undefined })
            onReported()
        } catch (err) {
            setError(extractBoardErrorMessage(err, "신고 접수에 실패했어요. 잠시 후 다시 시도해주세요."))
            setSubmitting(false)
        }
    }

    return (
        <div className="tp-modal-backdrop" onClick={onClose}>
            <form
                onSubmit={handleSubmit}
                onClick={(e) => e.stopPropagation()}
                className="tp-modal max-w-md"
                role="dialog"
                aria-labelledby="report-title"
            >
                <h2 id="report-title" className="flex items-center gap-2 text-lg font-bold text-slate-900"><span className="flex h-8 w-8 items-center justify-center rounded-xl bg-rose-50 text-sm">🚨</span>게시글 신고</h2>
                <p className="text-sm text-slate-500 mt-1 mb-5">
                    신고 사유를 선택해주세요. 운영자가 확인 후 조치하며, 허위 신고는 제재될 수 있어요.
                </p>

                <div className="grid grid-cols-2 gap-2 mb-4">
                    {REASONS.map((key) => (
                        <button
                            key={key}
                            type="button"
                            onClick={() => setReason(key)}
                            aria-pressed={reason === key}
                            className={`px-3 py-2.5 rounded-xl border text-sm font-medium text-left transition-colors ${
                                reason === key
                                    ? "border-rose-300 bg-rose-50 text-rose-600"
                                    : "border-slate-200 text-slate-600 hover:border-slate-400"
                            }`}
                        >
                            {BOARD_REPORT_REASON_LABEL[key]}
                        </button>
                    ))}
                </div>

                <label htmlFor="report-detail" className="block text-sm font-semibold text-slate-700 mb-1.5">
                    상세 내용 {detailRequired ? <span className="text-rose-500">(필수)</span> : <span className="font-normal text-slate-400">(선택)</span>}
                </label>
                <textarea
                    id="report-detail"
                    value={detail}
                    onChange={(e) => setDetail(e.target.value.slice(0, DETAIL_MAX))}
                    rows={3}
                    placeholder="어떤 점이 문제인지 알려주시면 확인에 도움이 돼요"
                    className="tp-input resize-none text-sm"
                />
                <p className="text-right text-xs text-slate-400 mt-1">{detail.length}/{DETAIL_MAX}</p>

                {error && <p className="text-sm text-rose-500 mt-2">{error}</p>}

                <div className="flex gap-2 mt-5">
                    <button
                        type="button"
                        onClick={onClose}
                        className="tp-btn-ghost flex-1"
                    >
                        취소
                    </button>
                    <button
                        type="submit"
                        disabled={!canSubmit}
                        className="tp-btn flex-1 bg-gradient-to-r from-rose-500 to-pink-500 text-white shadow-[0_10px_24px_-12px_rgba(244,63,94,0.9)] hover:brightness-110"
                    >
                        {submitting ? "접수 중..." : "신고하기"}
                    </button>
                </div>
            </form>
        </div>
    )
}
