// 금융상품 북마크 저장소 - api/bookmark.ts(관광정보 북마크)와 동일하게 백엔드/DB 없이
// 브라우저 localStorage에만 저장합니다.
//
// 로그인 계정(userId)별로 다른 localStorage 키를 써서, 같은 브라우저를 여러 계정이
// 돌아가며 로그인해도 북마크가 서로 섞이지 않게 함. 단, 다른 기기/브라우저와는 동기화되지
// 않고 브라우저 저장공간을 지우면 함께 사라짐.
import type { FinancialProduct, FinancialProductBookmark } from "../types"

// AuthContext.tsx의 login()이 로그인 성공 시 localStorage에 "userId"를 저장해두는 걸 그대로 재사용.
// 없으면(비정상 접근 등) "guest" 버킷에 담아서 최소한 에러는 안 나게 함.
function storageKey(): string {
    const userId = localStorage.getItem("userId")
    return `tripPamine_finance_bookmarks_${userId ?? "guest"}`
}

/** 금융회사코드+상품코드로 상품 하나를 고유하게 식별하는 키. 카드 컴포넌트가 북마크 상태 확인에도 그대로 씀 */
export function getFinanceBookmarkId(product: Pick<FinancialProduct, "finCoNo" | "finPrdtCd">): string {
    return `${product.finCoNo ?? "co"}_${product.finPrdtCd ?? "prd"}`
}

function readAll(): FinancialProductBookmark[] {
    try {
        const raw = localStorage.getItem(storageKey())
        if (!raw) return []
        const parsed = JSON.parse(raw)
        return Array.isArray(parsed) ? parsed : []
    } catch (error) {
        console.error("금융상품 북마크 목록을 읽지 못했습니다:", error)
        return []
    }
}

function writeAll(list: FinancialProductBookmark[]): void {
    try {
        localStorage.setItem(storageKey(), JSON.stringify(list))
    } catch (error) {
        console.error("금융상품 북마크 저장에 실패했습니다 (저장공간 문제일 수 있음):", error)
    }
}

/** 북마크 추가. 이미 저장된 상품이면 새로 만들지 않고 기존 것을 그대로 반환 */
export const addFinanceBookmarkApi = async (product: FinancialProduct): Promise<FinancialProductBookmark> => {
    const bookmarkId = getFinanceBookmarkId(product)
    const list = readAll()
    const existing = list.find((b) => b.bookmarkId === bookmarkId)
    if (existing) return existing

    const bookmark: FinancialProductBookmark = {
        bookmarkId,
        finCoNo: product.finCoNo,
        finPrdtCd: product.finPrdtCd,
        korCoNm: product.korCoNm,
        finPrdtNm: product.finPrdtNm,
        productType: product.productType,
        maxRate: product.maxRate,
        joinWay: product.joinWay,
        createdAt: new Date().toISOString(),
    }
    writeAll([bookmark, ...list])
    return bookmark
}

/** 북마크 해제. 없는 항목을 지워도 에러 없이 조용히 넘어감 */
export const removeFinanceBookmarkApi = async (bookmarkId: string): Promise<void> => {
    writeAll(readAll().filter((b) => b.bookmarkId !== bookmarkId))
}

/** 내 금융상품 북마크 목록 조회. productType을 생략하면 전체(예금+적금), 최신순 정렬 */
export const getFinanceBookmarksApi = async (
    productType?: "deposit" | "saving",
): Promise<FinancialProductBookmark[]> => {
    const sorted = readAll().sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
    return productType ? sorted.filter((b) => b.productType === productType) : sorted
}

/** 특정 상품이 이미 북마크돼 있는지 확인 (카드 렌더링 시 버튼 초기 상태 세팅용) */
export const getFinanceBookmarkStatusApi = async (bookmarkId: string): Promise<boolean> => {
    return readAll().some((b) => b.bookmarkId === bookmarkId)
}
