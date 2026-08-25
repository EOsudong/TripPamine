import { api } from "./axios"

// 백엔드 AccountResponse.java와 동일한 필드 구성
// [Mock 은행 연동 추가] balance 필드가 새로 추가됨.
// balance 자체가 연동 시 최초 발급값 + 그동안 가계부에 입력한 수입/지출이 누적 반영된 "현재" 값
export interface AccountSummary {
    accountId: number
    bankCode: string | null
    bankName: string
    maskedAccountNumber: string
    accountAlias: string | null
    linkStatus: "ACTIVE" | "INACTIVE"
    linkDate: string
    balance: number
}

/** 내 계좌 목록 조회 (응답에 이미 최신 balance가 포함되어 있음) */
export const getMyAccountsApi = async (): Promise<AccountSummary[]> => {
    const response = await api.get<AccountSummary[]>("/accounts")
    return response.data
}

// 백엔드 AccountLinkRequest.java와 동일한 필드 구성
export interface AccountLinkRequest {
    bankName: string
    bankCode?: string
    accountNumber: string
    accountAlias?: string
}

/**
 * 계좌 연동 — 계좌정보를 입력하면 서버가 Mock 오픈뱅킹 서버에 계좌 실명확인을 요청해
 * 핀테크이용번호와 최초 잔액(계좌번호 기반으로 결정론적으로 계산된 값)을 발급받은 뒤
 * USER_ACCOUNTS에 저장한다. 백엔드의 AccountController.linkAccount()가 이 요청을 처리한다.
 */
export const linkAccountApi = async (request: AccountLinkRequest): Promise<AccountSummary> => {
    const response = await api.post<AccountSummary>("/accounts", request)
    return response.data
}

/** 계좌 별칭 수정 */
export const updateAccountAliasApi = async (accountId: number, accountAlias: string) => {
    const response = await api.patch(`/accounts/${accountId}`, { accountAlias })
    return response.data
}

/** 계좌 연동 해지 */
export const unlinkAccountApi = async (accountId: number) => {
    const response = await api.delete(`/accounts/${accountId}`)
    return response.data
}

// 백엔드 AccountHistoryResponse.java와 동일한 필드 구성
export interface AccountHistoryItem {
    historyId: number
    transactionType: "DEPOSIT" | "WITHDRAW"
    amount: number
    description: string | null
    transactionDate: string
    balanceAfter: number
}

interface PageResponse<T> {
    content: T[]
    totalElements: number
    totalPages: number
    number: number
    size: number
    last: boolean
}

/**
 * 계좌 거래내역 조회 (페이징) — 가계부에서 이 계좌를 선택해 입력했던 수입/지출 내역이
 * 최신순으로 쌓여있다. (Mock 은행과 별도로 동기화하는 절차가 없으므로, 여기 보이는 내역은
 * 전부 우리 가계부에서 발생한 것.)
 */
export const getAccountHistoryApi = async (
    accountId: number,
    page = 0,
    size = 20,
): Promise<PageResponse<AccountHistoryItem>> => {
    const response = await api.get<PageResponse<AccountHistoryItem>>(`/accounts/${accountId}/history`, {
        params: { page, size },
    })
    return response.data
}
