// 0부터 시작하는 페이지 번호(Spring Page 기준)를 받는 페이지네이션. 화면에는 1부터 표시.
interface PaginationProps {
    page: number
    totalPages: number
    onChange: (page: number) => void
}

export default function Pagination({ page, totalPages, onChange }: PaginationProps) {
    if (totalPages <= 1) return null

    const blockSize = 5
    const start = Math.floor(page / blockSize) * blockSize
    const end = Math.min(start + blockSize, totalPages)
    const pages = Array.from({ length: end - start }, (_, i) => start + i)

    const base = "min-w-10 h-10 px-2 rounded-full text-sm font-semibold transition"

    return (
        <nav className="flex items-center justify-center gap-1 mt-8" aria-label="페이지 이동">
            <button
                onClick={() => onChange(Math.max(start - 1, 0))}
                disabled={start === 0}
                className={`${base} text-slate-500 hover:bg-white disabled:text-slate-300 disabled:hover:bg-transparent`}
                aria-label="이전 페이지 묶음"
            >
                ‹
            </button>
            {pages.map((p) => (
                <button
                    key={p}
                    onClick={() => onChange(p)}
                    aria-current={p === page ? "page" : undefined}
                    className={`${base} ${
                        p === page
                            ? "tp-gradient-bg text-white shadow-[0_8px_20px_-10px_rgba(124,108,255,0.9)]"
                            : "text-slate-600 hover:bg-white hover:text-sky-600"
                    }`}
                >
                    {p + 1}
                </button>
            ))}
            <button
                onClick={() => onChange(Math.min(end, totalPages - 1))}
                disabled={end >= totalPages}
                className={`${base} text-slate-500 hover:bg-white disabled:text-slate-300 disabled:hover:bg-transparent`}
                aria-label="다음 페이지 묶음"
            >
                ›
            </button>
        </nav>
    )
}
