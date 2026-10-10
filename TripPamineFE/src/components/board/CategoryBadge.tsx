import type { BoardCategory } from "../../api/board"
import { BOARD_CATEGORY_LABEL } from "../../api/board"

const STYLE: Record<BoardCategory, string> = {
    NOTICE: "bg-rose-500 text-white",
    REVIEW: "bg-sky-100 text-sky-700",
    QNA: "bg-amber-100 text-amber-700",
    FREE: "bg-slate-100 text-slate-600",
}

export default function CategoryBadge({ category }: { category: BoardCategory }) {
    return (
        <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold tracking-wide shrink-0 ${STYLE[category]}`}>
            {BOARD_CATEGORY_LABEL[category]}
        </span>
    )
}
