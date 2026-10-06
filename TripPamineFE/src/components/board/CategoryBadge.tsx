import type { BoardCategory } from "../../api/board"
import { BOARD_CATEGORY_LABEL } from "../../api/board"

const STYLE: Record<BoardCategory, string> = {
    NOTICE: "bg-rose-50 text-rose-600",
    REVIEW: "bg-sky-50 text-sky-600",
    QNA: "bg-amber-50 text-amber-600",
    FREE: "bg-slate-100 text-slate-600",
}

export default function CategoryBadge({ category }: { category: BoardCategory }) {
    return (
        <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-semibold shrink-0 ${STYLE[category]}`}>
            {BOARD_CATEGORY_LABEL[category]}
        </span>
    )
}
