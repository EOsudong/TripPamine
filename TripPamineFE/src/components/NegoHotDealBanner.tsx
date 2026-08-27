import { useEffect, useState } from "react";
import { useNego } from "../context/NegoContext";
import { extractNegoErrorMessage } from "../api/nego";
import { getMyAccountsApi, type AccountSummary } from "../api/account";

type Phase = "idle" | "submitting" | "done";

export default function NegoHotDealBanner() {
  const { activeOffer, dismiss, accept } = useNego();
  const [secondsLeft, setSecondsLeft] = useState(0);
  const [accounts, setAccounts] = useState<AccountSummary[]>([]);
  const [selectedAccountId, setSelectedAccountId] = useState<number | null>(null);
  const [phase, setPhase] = useState<Phase>("idle");
  const [error, setError] = useState<string | null>(null);
  // 수락 성공 시 activeOffer가 비워지므로, 결제 완료 패널에 보여줄 금액은 따로 잡아둔다.
  const [paidAmount, setPaidAmount] = useState<number | null>(null);

  const negoId = activeOffer?.negoId ?? null;

  // 카운트다운
  useEffect(() => {
    if (!activeOffer) return;
    setSecondsLeft(activeOffer.remainingSeconds);

    const timer = window.setInterval(() => {
      setSecondsLeft((prev) => {
        if (prev <= 1) {
          window.clearInterval(timer);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => window.clearInterval(timer);
  }, [activeOffer]);

  // 오퍼가 뜨면 결제 가능한 내 계좌 목록을 불러온다(ACTIVE만).
  useEffect(() => {
    if (!activeOffer) return;
    const price = activeOffer.offeredPrice;
    let cancelled = false;

    getMyAccountsApi()
      .then((list) => {
        if (cancelled) return;
        const active = list.filter((a) => a.linkStatus === "ACTIVE");
        setAccounts(active);
        // 잔액이 충분한 첫 계좌를 기본 선택, 없으면 첫 ACTIVE 계좌.
        const affordable = active.find((a) => a.balance >= price);
        setSelectedAccountId((affordable ?? active[0])?.accountId ?? null);
      })
      .catch(() => {
        if (!cancelled) {
          setAccounts([]);
          setSelectedAccountId(null);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [negoId]); // eslint-disable-line react-hooks/exhaustive-deps

  // 결제 완료 패널은 잠깐 보여준 뒤 스스로 닫힌다.
  useEffect(() => {
    if (phase !== "done") return;
    const t = window.setTimeout(() => {
      setPhase("idle");
      setPaidAmount(null);
    }, 3000);
    return () => window.clearTimeout(t);
  }, [phase]);

  if (phase === "done") {
    return (
      <div className="fixed top-20 right-4 z-[60] w-80 rounded-2xl bg-white shadow-xl border border-emerald-100 p-4 animate-in fade-in slide-in-from-top-2">
        <p className="text-sm font-bold text-emerald-600">✅ 결제 완료</p>
        <p className="text-lg font-bold text-slate-800 mt-1">
          {paidAmount?.toLocaleString()}원
        </p>
        <p className="text-xs text-slate-500 mt-1">
          최근 입출금 내역과 가계부에 지출로 반영되었습니다.
        </p>
      </div>
    );
  }

  if (!activeOffer || secondsLeft <= 0) return null;

  const selected =
    accounts.find((a) => a.accountId === selectedAccountId) ?? null;
  const noAccount = accounts.length === 0;
  const insufficient =
    selected != null && selected.balance < activeOffer.offeredPrice;
  const acceptDisabled =
    phase === "submitting" || noAccount || selected == null || insufficient;

  const handleAccept = async () => {
    if (selectedAccountId == null) return;
    setError(null);
    setPhase("submitting");
    const amount = activeOffer.offeredPrice;
    try {
      await accept(selectedAccountId);
      setPaidAmount(amount);
      setPhase("done");
    } catch (e) {
      setPhase("idle");
      setError(
        extractNegoErrorMessage(e, "결제에 실패했습니다. 다시 시도해주세요."),
      );
    }
  };

  const acceptLabel =
    phase === "submitting"
      ? "결제 중..."
      : noAccount
        ? "계좌 연동 필요"
        : insufficient
          ? "잔액 부족"
          : "수락하기";

  return (
    <div className="fixed top-20 right-4 z-[60] w-80 rounded-2xl bg-white shadow-xl border border-sky-100 p-4 animate-in fade-in slide-in-from-top-2">
      <div className="flex items-center justify-between mb-2">
        <span className="text-xs font-bold text-sky-500">🔥 AI 타임 네고</span>
        <button
          onClick={dismiss}
          className="text-slate-400 hover:text-slate-600 text-sm"
          aria-label="닫기"
        >
          ✕
        </button>
      </div>
      <p className="text-sm font-semibold text-slate-800 mb-1">
        {activeOffer.itemName}
      </p>
      <p className="text-lg font-bold text-sky-600 mb-2">
        {activeOffer.offeredPrice.toLocaleString()}원
      </p>

      {noAccount ? (
        <p className="text-xs text-slate-500 mb-2">
          결제하려면 먼저 계좌를 연동해주세요.
        </p>
      ) : (
        <select
          value={selectedAccountId ?? ""}
          onChange={(e) => setSelectedAccountId(Number(e.target.value))}
          disabled={phase === "submitting"}
          className="w-full mb-2 rounded-lg border border-slate-200 px-2 py-1.5 text-xs text-slate-700"
        >
          {accounts.map((a) => (
            <option key={a.accountId} value={a.accountId}>
              {a.bankName} {a.maskedAccountNumber} (
              {a.balance.toLocaleString()}원)
            </option>
          ))}
        </select>
      )}

      {error && <p className="text-xs text-red-500 mb-2">{error}</p>}

      <div className="flex items-center justify-between">
        <span className="text-xs text-red-500 font-bold">
          ⏳ {secondsLeft}초 남음
        </span>
        <button
          onClick={handleAccept}
          disabled={acceptDisabled}
          className={
            acceptDisabled
              ? "px-3 py-1.5 rounded-lg bg-slate-200 text-slate-500 text-xs font-bold cursor-not-allowed"
              : "px-3 py-1.5 rounded-lg bg-sky-500 hover:bg-sky-600 text-white text-xs font-bold"
          }
        >
          {acceptLabel}
        </button>
      </div>
    </div>
  );
}
