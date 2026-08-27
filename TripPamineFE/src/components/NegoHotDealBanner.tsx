import { useEffect, useState } from "react";
import { useNego } from "../context/NegoContext";

export default function NegoHotDealBanner() {
  const { activeOffer, dismiss } = useNego();
  const [secondsLeft, setSecondsLeft] = useState(0);

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

  if (!activeOffer || secondsLeft <= 0) return null;

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
      <div className="flex items-center justify-between">
        <span className="text-xs text-red-500 font-bold">
          ⏳ {secondsLeft}초 남음
        </span>
        <button
          disabled
          className="px-3 py-1.5 rounded-lg bg-slate-200 text-slate-500 text-xs font-bold cursor-not-allowed"
          title="계좌 연동 완료 후 이용 가능합니다"
        >
          수락하기 (준비중)
        </button>
      </div>
    </div>
  );
}