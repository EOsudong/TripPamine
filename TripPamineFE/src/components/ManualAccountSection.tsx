// [Mock 오픈뱅킹 연동] 사용자가 계좌정보(은행명/계좌번호)를 입력해 연동을 요청하면,
// 서버가 Mock 은행 서버에 계좌 실명확인을 요청해서 핀테크이용번호와 최초 잔액(계좌번호 기반
// 결정론적 값)을 발급받아 저장한다. 그 이후 잔액 변화는 전부 가계부(AccountBook)에서
// 이 계좌를 지정해 수입/지출을 입력할 때 즉시 반영된다 - 이 화면에서 폴링하거나 별도로
// "동기화" 버튼을 눌러줄 필요가 없다 (GET /accounts 응답에 이미 최신 balance가 담겨 있음).
//
// - 연동: POST /accounts (linkAccountApi) — 서버가 Mock 은행 실명확인까지 처리
// - 조회: GET /accounts (getMyAccountsApi) — 최신 balance가 함께 내려옴
// - 삭제: DELETE /accounts/{id} (unlinkAccountApi)
//
// 마운트 시 한 번 목록을 불러오고, 연동/삭제가 성공할 때마다 다시 불러온다. 또한 부모
// (MyPage)가 refreshSignal을 바꿔주면(예: 가계부에서 이 계좌로 수입/지출을 입력해 잔액이
// 바뀌었을 때) 그 시점에도 다시 불러와서 잔액을 최신으로 보여준다.
//
// [주의] 백엔드의 DELETE /accounts/{id}는 실제로 DB row를 지우는 게 아니라 linkStatus를
// ACTIVE -> INACTIVE로 바꾸는 소프트 삭제(AccountService.unlinkAccount)이고, GET /accounts는
// 상태와 무관하게 그 유저의 계좌를 전부 돌려준다(AccountService.getMyAccounts). 그래서 서버
// 응답만 그대로 보여주면 삭제한 계좌가 화면에서 안 사라진다. 백엔드는 건드리지 않고(이력을
// 남겨두는 편이 안전) 프론트에서 ACTIVE 상태인 계좌만 걸러서 보여주는 방식으로 처리한다.
import { useEffect, useState } from "react";
import {
  type AccountSummary,
  getMyAccountsApi,
  linkAccountApi,
  unlinkAccountApi,
} from "../api/account";

const emptyForm = {
  bankName: "",
  accountNumber: "",
  accountAlias: "",
};

interface ManualAccountSectionProps {
  // 부모가 이 값을 바꾸면(예: 가계부에서 이 계좌로 수입/지출을 입력해 잔액이 바뀌었을 때)
  // 계좌 목록을 다시 불러온다.
  refreshSignal?: number;
  // 계좌를 새로 연동하거나 삭제해서 목록 자체가 바뀌었을 때 부모에게 알려주는 콜백.
  // 부모는 이걸 받아서 AccountBook 쪽 계좌 드롭다운을 다시 불러오게 하면 된다.
  onAccountsChanged?: () => void;
}

export default function ManualAccountSection({ refreshSignal, onAccountsChanged }: ManualAccountSectionProps) {
  const [accounts, setAccounts] = useState<AccountSummary[]>([]);
  const [loadingList, setLoadingList] = useState(true);
  const [listError, setListError] = useState<string | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const [deletingId, setDeletingId] = useState<number | null>(null);

  const loadAccounts = async (options?: { silent?: boolean }) => {
    if (!options?.silent) {
      setLoadingList(true);
      setListError(null);
    }
    try {
      const data = await getMyAccountsApi();
      // 서버는 삭제(해지)된 계좌도 linkStatus: "INACTIVE"로 같이 내려주므로,
      // 화면에서는 그중 ACTIVE인 것만 남겨서 "삭제하면 목록에서 사라지는" 것처럼 보이게 한다.
      setAccounts(data.filter((account) => account.linkStatus === "ACTIVE"));
    } catch (e) {
      console.error(e);
      if (!options?.silent) {
        setListError(
            "계좌 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.",
        );
      }
    } finally {
      if (!options?.silent) {
        setLoadingList(false);
      }
    }
  };

  useEffect(() => {
    loadAccounts();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 가계부에서 이 계좌를 지정해 수입/지출을 입력해 잔액이 바뀌었을 수 있는 시점 - 조용히 재조회
  useEffect(() => {
    if (refreshSignal !== undefined) {
      loadAccounts({ silent: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshSignal]);

  // AI 타임 네고 결제 등으로 계좌 잔액이 바뀌었을 때 조용히 재조회
  useEffect(() => {
    const refresh = () => loadAccounts({ silent: true });
    window.addEventListener("trippamine:ledger-changed", refresh);
    return () => window.removeEventListener("trippamine:ledger-changed", refresh);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    if (!form.bankName.trim() || !form.accountNumber.trim()) {
      setFormError("은행명과 계좌번호는 필수로 입력해주세요.");
      return;
    }

    setSubmitting(true);
    try {
      await linkAccountApi({
        bankName: form.bankName.trim(),
        accountNumber: form.accountNumber.trim(),
        accountAlias: form.accountAlias.trim() || undefined,
      });
      setForm(emptyForm);
      setFormOpen(false);
      await loadAccounts();
      onAccountsChanged?.();
    } catch (e) {
      console.error(e);
      setFormError(
          "계좌 연동에 실패했습니다. 입력값을 확인하고 다시 시도해주세요.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (accountId: number) => {
    setDeletingId(accountId);
    try {
      await unlinkAccountApi(accountId);
      await loadAccounts();
      onAccountsChanged?.();
    } catch (e) {
      console.error(e);
      setListError("계좌 삭제에 실패했습니다. 잠시 후 다시 시도해주세요.");
    } finally {
      setDeletingId(null);
    }
  };

  return (
      <div>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <p className="text-xs text-slate-500">
            연동 계좌 <b className="text-slate-900">{accounts.length}</b>개 · 총{" "}
            <b className="text-slate-900">
              {accounts.reduce((sum, a) => sum + a.balance, 0).toLocaleString()}원
            </b>
          </p>
          <button
              onClick={() => {
                setFormOpen((v) => !v);
                setFormError(null);
              }}
              className="tp-btn-dark rounded-full px-4 py-2 text-xs"
          >
            {formOpen ? "닫기" : "+ 계좌 연동하기"}
          </button>
        </div>

        {formOpen && (
            <form
                onSubmit={handleSubmit}
                className="mb-6 grid grid-cols-1 gap-3 rounded-3xl border border-sky-100 bg-gradient-to-br from-sky-50/80 to-white p-5 sm:grid-cols-2"
            >
              <div>
                <label className="tp-label">
                  은행명 <span className="text-red-500">*</span>
                </label>
                <input
                    type="text"
                    value={form.bankName}
                    onChange={(e) =>
                        setForm((f) => ({ ...f, bankName: e.target.value }))
                    }
                    placeholder="예: 국민은행"
                    className="tp-input"
                />
              </div>
              <div>
                <label className="tp-label">
                  계좌번호 <span className="text-red-500">*</span>
                </label>
                <input
                    type="text"
                    value={form.accountNumber}
                    onChange={(e) =>
                        setForm((f) => ({ ...f, accountNumber: e.target.value }))
                    }
                    placeholder="'-' 없이 숫자만 입력"
                    className="tp-input"
                />
              </div>
              <div className="sm:col-span-2">
                <label className="tp-label">
                  별칭 (선택)
                </label>
                <input
                    type="text"
                    value={form.accountAlias}
                    onChange={(e) =>
                        setForm((f) => ({ ...f, accountAlias: e.target.value }))
                    }
                    placeholder="예: 여행 경비 통장"
                    className="tp-input"
                />
              </div>

              {formError && (
                  <p className="sm:col-span-2 text-xs text-red-500">{formError}</p>
              )}

              <div className="sm:col-span-2 flex justify-end">
                <button
                    type="submit"
                    disabled={submitting}
                    className="tp-btn-primary rounded-full px-5 py-2"
                >
                  {submitting ? "연동 중..." : "연동하기"}
                </button>
              </div>
            </form>
        )}

        {listError && <p className="text-sm text-red-500 mb-3">{listError}</p>}

        {loadingList ? (
            <p className="text-sm text-slate-400 py-6 text-center">
              불러오는 중...
            </p>
        ) : accounts.length === 0 ? (
            <div className="tp-empty py-10">
              연동된 계좌가 없어요. 위 버튼으로 계좌를 연동해보세요.
            </div>
        ) : (
            <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {accounts.map((a) => (
                  <li
                      key={a.accountId}
                      className="group relative flex min-h-[132px] flex-col justify-between overflow-hidden rounded-3xl bg-night-950 p-5 text-white shadow-[0_20px_40px_-24px_rgba(30,20,90,0.9)]"
                  >
                    <div className="pointer-events-none absolute -right-10 -top-12 h-36 w-36 rounded-full bg-gradient-to-br from-violet-500/60 to-cyan-400/30 blur-2xl" />
                    <div className="relative min-w-0">
                      {/* 별칭(등록한 계좌 이름)이 있으면 별칭을 메인 강조 제목으로, 없으면 은행명을 제목으로 표시 */}
                      <p className="truncate text-sm font-bold">
                        {a.accountAlias ? a.accountAlias : a.bankName}
                      </p>

                      {/* 하단 서브 텍스트: 별칭이 있을 때는 (은행명 · 계좌번호), 없을 때는 (계좌번호)만 표시 */}
                      <p className="mt-0.5 text-[11px] tracking-wider text-white/45">
                        {a.accountAlias
                            ? `${a.bankName} · ${a.maskedAccountNumber}`
                            : a.maskedAccountNumber}
                      </p>
                    </div>

                    <div className="relative flex items-end justify-between gap-3">
                <span className="font-display text-xl font-bold tracking-tight">
                  {a.balance.toLocaleString()}원
                </span>
                      <button
                          onClick={() => handleDelete(a.accountId)}
                          disabled={deletingId === a.accountId}
                          className="rounded-full px-2.5 py-1 text-[11px] font-semibold text-white/40 transition hover:bg-white/10 hover:text-rose-300 disabled:opacity-60"
                      >
                        {deletingId === a.accountId ? "삭제 중..." : "삭제"}
                      </button>
                    </div>
                  </li>
              ))}
            </ul>
        )}
      </div>
  );
}
