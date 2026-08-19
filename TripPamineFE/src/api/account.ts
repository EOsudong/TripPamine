import { api } from "./axios"

// 백엔드 AccountResponse.java와 동일한 필드 구성
export interface AccountSummary {
    accountId: number
    bankCode: string | null
    bankName: string
    maskedAccountNumber: string
    accountAlias: string | null
    linkStatus: "ACTIVE" | "INACTIVE"
    linkDate: string
}

/** 내 계좌 목록 조회 */
export const getMyAccountsApi = async (): Promise<AccountSummary[]> => {
    const response = await api.get<AccountSummary[]>("/accounts")
    return response.data
}

// 백엔드 AccountLinkRequest.java와 동일한 필드 구성
export interface AccountLinkRequest {
    bankName: string
    bankCode?: string
    accountNumber: string
    fintechUseNum?: string
    accountAlias?: string
}

/**
 * 계좌 등록 — 사용자가 직접 입력한 계좌정보를 그대로 DB(USER_ACCOUNTS)에 저장한다.
 * 백엔드의 AccountController.linkAccount()가 이 요청을 처리한다.
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
