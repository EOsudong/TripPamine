// 게시판 목록 페이지. 라우트: /boards (비로그인도 접근 가능)
// 카테고리/검색어/페이지를 URL 쿼리(?category=REVIEW&keyword=제주&page=2)에 담아서,
// 상세 페이지에 갔다가 뒤로가기를 눌러도 보던 목록 그대로 돌아오게 한다.
import { useEffect, useState, type FormEvent } from "react"
import { useLocation, useNavigate, useSearchParams } from "react-router-dom"
import BoardLayout from "../components/board/BoardLayout"
import CategoryBadge from "../components/board/CategoryBadge"
import Pagination from "../components/board/Pagination"
import { useAuth } from "../context/AuthContext"
import {
    BOARD_CATEGORY_LABEL,
    BOARD_SEARCH_TYPE_LABEL,
    boardApi,
    boardImageSrc,
    extractBoardErrorMessage,
    formatBoardDate,
    type BoardCategory,
    type BoardPage,
    type BoardPostSummary,
    type BoardSearchType,
} from "../api/board"

const CATEGORY_TABS: { key: BoardCategory | ""; label: string }[] = [
    { key: "", label: "전체" },
    { key: "NOTICE", label: BOARD_CATEGORY_LABEL.NOTICE },
    { key: "REVIEW", label: BOARD_CATEGORY_LABEL.REVIEW },
    { key: "QNA", label: BOARD_CATEGORY_LABEL.QNA },
    { key: "FREE", label: BOARD_CATEGORY_LABEL.FREE },
]

const SEARCH_TYPES = Object.keys(BOARD_SEARCH_TYPE_LABEL) as BoardSearchType[]
const VALID_CATEGORIES = ["NOTICE", "REVIEW", "QNA", "FREE"]

export default function BoardListPage() {
    const navigate = useNavigate()
    const location = useLocation()
    const { isLoggedIn } = useAuth()
    const [searchParams, setSearchParams] = useSearchParams()

    const categoryParam = searchParams.get("category") ?? ""
    const category = (VALID_CATEGORIES.includes(categoryParam) ? categoryParam : "") as BoardCategory | ""
    const keyword = searchParams.get("keyword") ?? ""
    const searchTypeParam = searchParams.get("searchType") as BoardSearchType | null
    const searchType: BoardSearchType =
        searchTypeParam && SEARCH_TYPES.includes(searchTypeParam) ? searchTypeParam : "TITLE_CONTENT"
    const page = Math.max(Number(searchParams.get("page") ?? "1") - 1, 0) // URL은 1부터, API는 0부터

    const [result, setResult] = useState<BoardPage<BoardPostSummary> | null>(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    // 검색 입력창은 URL과 별개로 입력 중인 값을 들고 있다가 "검색"을 누를 때 URL에 반영
    const [keywordInput, setKeywordInput] = useState(keyword)
    const [searchTypeInput, setSearchTypeInput] = useState<BoardSearchType>(searchType)

    useEffect(() => {
        setKeywordInput(keyword)
        setSearchTypeInput(searchType)
    }, [keyword, searchType])

    useEffect(() => {
        let cancelled = false
        setLoading(true)
        setError(null)

        boardApi
            .getPosts({ category: category || undefined, searchType, keyword, page, size: 10 })
            .then((data) => {
                if (!cancelled) setResult(data)
            })
            .catch((err) => {
                if (!cancelled) setError(extractBoardErrorMessage(err, "게시글을 불러오지 못했어요. 잠시 후 다시 시도해주세요."))
            })
            .finally(() => {
                if (!cancelled) setLoading(false)
            })

        return () => {
            cancelled = true
        }
    }, [category, searchType, keyword, page])

    function updateParams(next: Record<string, string | null>) {
        const params = new URLSearchParams(searchParams)
        Object.entries(next).forEach(([key, value]) => {
            if (value) params.set(key, value)
            else params.delete(key)
        })
        setSearchParams(params)
    }

    function handleCategory(key: BoardCategory | "") {
        updateParams({ category: key || null, page: null })
    }

    function handleSearch(e: FormEvent) {
        e.preventDefault()
        const trimmed = keywordInput.trim()
        updateParams({
            keyword: trimmed || null,
            searchType: trimmed ? searchTypeInput : null,
            page: null,
        })
    }

    function handlePage(nextPage: number) {
        updateParams({ page: nextPage > 0 ? String(nextPage + 1) : null })
        window.scrollTo({ top: 0, behavior: "smooth" })
    }

    function handleWrite() {
        if (!isLoggedIn) {
            navigate("/login", { state: { from: { pathname: "/boards/write" } } })
            return
        }
        navigate("/boards/write", { state: { category: category && category !== "NOTICE" ? category : undefined } })
    }

    return (
        <BoardLayout>
            {/* 헤더 */}
            <div className="flex items-end justify-between gap-4 mb-7">
                <div>
                    <p className="text-sky-500 text-xs font-semibold tracking-widest uppercase mb-1">Community</p>
                    <h2 className="text-2xl md:text-3xl font-bold text-slate-900 flex items-center gap-2">
                        <span>💬</span>
                        게시판
                    </h2>
                    <p className="text-slate-500 text-sm mt-1">여행 후기와 궁금한 점을 함께 나눠요.</p>
                </div>
                <button
                    onClick={handleWrite}
                    className="shrink-0 px-4 py-2.5 rounded-xl bg-sky-500 text-white text-sm font-semibold hover:bg-sky-600 shadow-sm transition-colors"
                >
                    ✏️ 글쓰기
                </button>
            </div>

            {/* 카테고리 탭 */}
            <div className="flex gap-2 flex-wrap mb-4">
                {CATEGORY_TABS.map((tab) => (
                    <button
                        key={tab.key || "all"}
                        onClick={() => handleCategory(tab.key)}
                        className={`px-4 py-1.5 rounded-full text-sm font-medium transition-colors ${
                            category === tab.key
                                ? "bg-slate-900 text-white shadow-sm"
                                : "bg-white text-slate-600 border border-slate-200 hover:border-slate-400"
                        }`}
                    >
                        {tab.label}
                    </button>
                ))}
            </div>

            {/* 검색 */}
            <form onSubmit={handleSearch} className="flex gap-2 mb-5">
                <select
                    value={searchTypeInput}
                    onChange={(e) => setSearchTypeInput(e.target.value as BoardSearchType)}
                    className="h-10 px-3 rounded-xl border border-slate-200 bg-white text-sm text-slate-700 focus:outline-none focus:border-sky-400"
                    aria-label="검색 기준"
                >
                    {SEARCH_TYPES.map((type) => (
                        <option key={type} value={type}>
                            {BOARD_SEARCH_TYPE_LABEL[type]}
                        </option>
                    ))}
                </select>
                <input
                    value={keywordInput}
                    onChange={(e) => setKeywordInput(e.target.value)}
                    placeholder="검색어를 입력하세요"
                    className="flex-1 min-w-0 h-10 px-3 rounded-xl border border-slate-200 bg-white text-sm focus:outline-none focus:border-sky-400"
                />
                <button
                    type="submit"
                    className="h-10 px-4 rounded-xl bg-slate-900 text-white text-sm font-semibold hover:bg-slate-700 transition-colors"
                >
                    검색
                </button>
            </form>

            {keyword && (
                <p className="text-sm text-slate-500 mb-3">
                    <span className="font-semibold text-slate-800">"{keyword}"</span> 검색 결과{" "}
                    {result ? `${result.totalElements}건` : ""}
                    <button onClick={() => updateParams({ keyword: null, searchType: null, page: null })} className="ml-2 text-sky-500 hover:underline">
                        초기화
                    </button>
                </p>
            )}

            {/* 목록 */}
            {loading && !result ? (
                <LoadingList />
            ) : error ? (
                <StateMessage label={error} />
            ) : !result || result.content.length === 0 ? (
                <StateMessage label={keyword ? "검색 결과가 없어요" : "아직 작성된 글이 없어요. 첫 글을 남겨보세요!"} />
            ) : (
                <ul className={`bg-white rounded-2xl border border-slate-100 divide-y divide-slate-100 overflow-hidden ${loading ? "opacity-60" : ""}`}>
                    {result.content.map((post) => (
                        <li key={post.postId}>
                            <button
                                onClick={() => navigate(`/boards/${post.postId}`, { state: { backTo: location.pathname + location.search } })}
                                className={`w-full text-left px-4 md:px-5 py-4 flex gap-4 hover:bg-slate-50 transition-colors ${
                                    post.pinned ? "bg-rose-50/40" : ""
                                }`}
                            >
                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center gap-2 mb-1.5">
                                        {post.pinned && <span className="text-xs" title="상단 고정">📌</span>}
                                        <CategoryBadge category={post.category} />
                                        <h3 className="font-semibold text-slate-800 text-sm md:text-[15px] truncate">
                                            {post.title}
                                        </h3>
                                        {post.commentCount > 0 && (
                                            <span className="text-sky-500 text-xs font-semibold shrink-0">[{post.commentCount}]</span>
                                        )}
                                    </div>
                                    <div className="flex items-center gap-x-3 gap-y-1 flex-wrap text-xs text-slate-400">
                                        <span className={post.writerType === "ADMIN" ? "text-rose-500 font-semibold" : "text-slate-500"}>
                                            {post.writerName}
                                        </span>
                                        <span>{formatBoardDate(post.createDate)}</span>
                                        <span>조회 {post.viewCount.toLocaleString()}</span>
                                        <span>♥ {post.likeCount.toLocaleString()}</span>
                                    </div>
                                </div>
                                {post.thumbnailUrl && (
                                    <img
                                        src={boardImageSrc(post.thumbnailUrl)}
                                        alt=""
                                        loading="lazy"
                                        className="w-14 h-14 md:w-16 md:h-16 rounded-xl object-cover bg-slate-100 shrink-0"
                                    />
                                )}
                            </button>
                        </li>
                    ))}
                </ul>
            )}

            {result && <Pagination page={result.number} totalPages={result.totalPages} onChange={handlePage} />}
        </BoardLayout>
    )
}

function LoadingList() {
    return (
        <div className="bg-white rounded-2xl border border-slate-100 divide-y divide-slate-100">
            {Array.from({ length: 6 }).map((_, i) => (
                <div key={i} className="px-5 py-4">
                    <div className="h-4 w-2/3 rounded bg-slate-100 animate-pulse mb-2" />
                    <div className="h-3 w-1/3 rounded bg-slate-100 animate-pulse" />
                </div>
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
