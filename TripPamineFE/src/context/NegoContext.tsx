import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import {
  negoApi,
  connectNegoSocket,
  type AiNegoLogResponse,
} from "../api/nego";
import { useAuth } from "./AuthContext";

interface NegoContextType {
  activeOffer: AiNegoLogResponse | null;
  dismiss: () => void;
  /** 현재 오퍼를 지정한 계좌로 결제 수락한다. 실패 시 예외를 그대로 던진다. */
  accept: (accountId: number) => Promise<AiNegoLogResponse>;
}

const NegoContext = createContext<NegoContextType | undefined>(undefined);

// WebSocket(/user/queue/nego) 푸시가 주 경로.
// 소켓이 끊긴 사이 발송된 오퍼를 놓치지 않도록 폴링(/nego/active)을 백업으로 함께 돌린다.
const POLL_INTERVAL_MS = 15_000;

export function NegoProvider({ children }: { children: ReactNode }) {
  const { isLoggedIn } = useAuth();
  const [activeOffer, setActiveOffer] = useState<AiNegoLogResponse | null>(null);
  const [dismissedNegoId, setDismissedNegoId] = useState<number | null>(null);

  // 소켓 콜백은 최초 1회만 등록되므로, 최신 dismiss 상태는 ref로 참조한다(재연결 방지).
  const dismissedRef = useRef<number | null>(null);
  useEffect(() => {
    dismissedRef.current = dismissedNegoId;
  }, [dismissedNegoId]);

  // 유효(만료 전·미전환)하고 아직 닫지 않은 오퍼면 노출한다.
  const applyOffer = (offer: AiNegoLogResponse | null) => {
    if (!offer) {
      setActiveOffer(null);
      return;
    }
    if (
      offer.conversionYn === "N" &&
      offer.remainingSeconds > 0 &&
      offer.negoId !== dismissedRef.current
    ) {
      setActiveOffer(offer);
    }
  };

  // WebSocket 실시간 수신
  useEffect(() => {
    if (!isLoggedIn) {
      setActiveOffer(null);
      return;
    }

    const client = connectNegoSocket((offer) => applyOffer(offer));
    return () => {
      void client.deactivate();
    };
  }, [isLoggedIn]);

  // 폴링 백업
  useEffect(() => {
    if (!isLoggedIn) return;

    let cancelled = false;

    const poll = async () => {
      try {
        const offers = await negoApi.getActiveOffers();
        if (cancelled) return;
        // remainingSeconds가 가장 큰(=방금 발송된) 오퍼를 우선 노출
        const latest = offers
          .filter((o) => o.conversionYn === "N" && o.remainingSeconds > 0)
          .sort((a, b) => b.remainingSeconds - a.remainingSeconds)[0];

        if (latest && latest.negoId !== dismissedRef.current) {
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
  }, [isLoggedIn]);

  const dismiss = () => {
    if (activeOffer) setDismissedNegoId(activeOffer.negoId);
    setActiveOffer(null);
  };

  const accept = async (accountId: number) => {
    if (!activeOffer) throw new Error("수락할 제안이 없습니다.");
    const result = await negoApi.accept(activeOffer.negoId, accountId);
    // 전환 완료된 오퍼가 폴링/소켓으로 다시 뜨지 않도록 닫힘 처리한다.
    setDismissedNegoId(activeOffer.negoId);
    setActiveOffer(null);
    // 방금 생긴 결제 내역을 가계부("최근 입출금 내역")·계좌 잔액 화면이 바로 반영하도록 알린다.
    window.dispatchEvent(new CustomEvent("trippamine:ledger-changed"));
    return result;
  };

  return (
    <NegoContext.Provider value={{ activeOffer, dismiss, accept }}>
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
