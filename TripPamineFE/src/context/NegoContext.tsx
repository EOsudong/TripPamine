import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { negoApi, type AiNegoLogResponse } from "../api/nego";
import { useAuth } from "./AuthContext";

interface NegoContextType {
  activeOffer: AiNegoLogResponse | null;
  dismiss: () => void;
}



const NegoContext = createContext<NegoContextType | undefined>(undefined);

const POLL_INTERVAL_MS = 15_000; // TTL이 60초라 너무 길게 잡으면 놓치기 쉬움. WebSocket 붙이면 이 폴링은 제거 예정.

export function NegoProvider({ children }: { children: ReactNode }) {
  const { isLoggedIn } = useAuth();
  const [activeOffer, setActiveOffer] = useState<AiNegoLogResponse | null>(null);
  const [dismissedNegoId, setDismissedNegoId] = useState<number | null>(null);

  useEffect(() => {
    if (!isLoggedIn) {
      setActiveOffer(null);
      return;
    }

    let cancelled = false;

    const poll = async () => {
      try {
        const offers = await negoApi.getActiveOffers();
        if (cancelled) return;
        // remainingSeconds가 가장 큰(=방금 발송된) 오퍼를 우선 노출
        const latest = offers
          .filter((o) => o.conversionYn === "N" && o.remainingSeconds > 0)
          .sort((a, b) => b.remainingSeconds - a.remainingSeconds)[0];

        if (latest && latest.negoId !== dismissedNegoId) {
          setActiveOffer(latest);
        } else if (!latest) {
          setActiveOffer(null);
        }
      } catch (err) {
        console.error("네고 오퍼 폴링 실패:", err);
      }
    };

    poll();
    const timer = window.setInterval(poll, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [isLoggedIn, dismissedNegoId]);

  const dismiss = () => {
    if (activeOffer) setDismissedNegoId(activeOffer.negoId);
    setActiveOffer(null);
  };

  return (
    <NegoContext.Provider value={{ activeOffer, dismiss }}>
      {children}
    </NegoContext.Provider>
  );
}

export function useNego() {
  const context = useContext(NegoContext);
  if (!context) {
    throw new Error("useNego는 NegoProvider 안에서 사용해야 합니다.");
  }
  return context;
}