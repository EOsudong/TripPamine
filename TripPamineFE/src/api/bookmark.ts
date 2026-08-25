// 북마크 저장소 - 백엔드/DB 없이 브라우저 localStorage에만 저장.
//
// 로그인 계정(userId)별로 다른 localStorage 키를 써서, 같은 브라우저를 여러 계정이
// 돌아가며 로그인해도 북마크가 서로 섞이지 않게 함. 단, 다른 기기/브라우저와는 동기화되지
// 않고 브라우저 저장공간을 지우면 함께 사라짐
import type { TourBookmark, TourMainCategoryKey } from "../types"

// TourDetailPage.tsx의 북마크 버튼이 보내는 데이터.
// TourDetail(상세 조회 응답)에 categoryKey만 얹으면 그대로 맞아떨어지는 구조.
export interface TourBookmarkRequest {
    categoryKey: TourMainCategoryKey
    contentId: string
    contentTypeId: string | null
    title: string
    category: string | null
    address: string | null
    imageUrl: string | null
    eventStartDate: string | null
    eventEndDate: string | null
    status: "ongoing" | "upcoming" | null
}

// AuthContext.tsx의 login()이 로그인 성공 시 localStorage에 "userId"를 저장해두는 걸 그대로 재사용.
// 없으면(비정상 접근 등) "guest" 버킷에 담아서 최소한 에러는 안 나게 함.
function storageKey(): string {
    const userId = localStorage.getItem("userId")
    return `tripPamine_bookmarks_${userId ?? "guest"}`
}

function readAll(): TourBookmark[] {
    try {
        const raw = localStorage.getItem(storageKey())
        if (!raw) return []
        const parsed = JSON.parse(raw)
        return Array.isArray(parsed) ? parsed : []
    } catch (error) {
        console.error("북마크 목록을 읽지 못했습니다:", error)
        return []
    }
}

function writeAll(list: TourBookmark[]): void {
    try {
        localStorage.setItem(storageKey(), JSON.stringify(list))
    } catch (error) {
        console.error("북마크 저장에 실패했습니다 (저장공간 문제일 수 있음):", error)
    }
}

/** 북마크 추가. 이미 저장된 항목이면 새로 만들지 않고 기존 것을 그대로 반환 */
export const addBookmarkApi = async (payload: TourBookmarkRequest): Promise<TourBookmark> => {
    const list = readAll()
    const existing = list.find((b) => b.contentId === payload.contentId)
    if (existing) return existing

    const bookmark: TourBookmark = { ...payload, createdAt: new Date().toISOString() }
    writeAll([bookmark, ...list])
    return bookmark
}

/** 북마크 해제. 없는 항목을 지워도 에러 없이 조용히 넘어감 */
export const removeBookmarkApi = async (contentId: string): Promise<void> => {
    writeAll(readAll().filter((b) => b.contentId !== contentId))
}

/** 내 북마크 목록 조회. categoryKey를 생략하면 전체(festivals+destinations+industry), 최신순 정렬 */
export const getBookmarksApi = async (categoryKey?: TourMainCategoryKey): Promise<TourBookmark[]> => {
    const sorted = readAll().sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
    return categoryKey ? sorted.filter((b) => b.categoryKey === categoryKey) : sorted
}

/** 특정 항목이 이미 북마크돼 있는지 확인 (상세페이지 진입 시 버튼 초기 상태 세팅용) */
export const getBookmarkStatusApi = async (contentId: string): Promise<boolean> => {
    return readAll().some((b) => b.contentId === contentId)
}
