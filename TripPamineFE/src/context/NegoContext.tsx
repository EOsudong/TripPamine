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
import {
  negoSnoozeStorageKey,
  readNegoSnoozeUntil,
  saveNegoSnoozeOneHour,
} from "../utils/negoSnooze";

interface NegoContextType {
  activeOffer: AiNegoLogResponse | null;
  /** 현재 오퍼 닫기. true를 전달하면 1시간 동안 모든 오퍼 팝업을 숨긴다. */
  dismiss: (snoozeOneHour?: boolean) => void;
  /** 현재 오퍼를 지정한 계좌로 모의 거래 수락한다. 실패 시 예외를 그대로 던진다. */
  accept: (accountId: number) => Promise<AiNegoLogResponse>;
}

const NegoContext = createContext<NegoContextType | undefined>(undefined);

// WebSocket이 주 경로이고, 소켓이 끊긴 동안의 누락은 REST 폴링으로 보충한다.
const POLL_INTERVAL_MS = 15_000;

export function NegoProvider({ children }: { children: ReactNode }) {
  const { isLoggedIn, userId } = useAuth();
  const [activeOffer, setActiveOffer] = useState<AiNegoLogResponse | null>(null);

  // ref로 갱신하면 소켓 재연결 없이도 닫기·숨김 상태를 최신으로 판단할 수 있다.
  const dismissedRef = useRef<Set<number>>(new Set());
  const snoozeUntilRef = useRef<number>(0);

  useEffect(() => {
    // 사용자 변경 시 이전 계정에서 닫은 오퍼 ID를 다른 계정에 적용하지 않는다.
    dismissedRef.current = new Set();
    snoozeUntilRef.current = isLoggedIn ? readNegoSnoozeUntil(userId) : 0;
    setActiveOffer(null);
  }, [isLoggedIn, userId]);

  useEffect(() => {
    if (!isLoggedIn || userId == null) return;

    // 다른 브라우저 탭에서 1시간 숨김을 선택한 경우도 즉시 반영한다.
    const onStorage = (event: StorageEvent) => {
      if (event.key !== negoSnoozeStorageKey(userId) && event.key !== null) return;
      snoozeUntilRef.current = readNegoSnoozeUntil(userId);
      if (snoozeUntilRef.current > Date.now()) setActiveOffer(null);
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, [isLoggedIn, userId]);

  const isSnoozed = () => snoozeUntilRef.current > Date.now();

  const applyOffer = (offer: AiNegoLogResponse | null) => {
    // 숨김 상태에서는 소켓 이벤트와 폴링 결과 어느 쪽도 배너를 열지 않는다.
    if (isSnoozed()) {
      setActiveOffer(null);
      return;
    }
    if (!offer) {
      setActiveOffer(null);
      return;
    }
    if (
      offer.conversionYn === "N" &&
      offer.remainingSeconds > 0 &&
      !dismissedRef.current.has(offer.negoId)
    ) {
      setActiveOffer(offer);
    }
  };

  // WebSocket 실시간 수신
  useEffect(() => {
    if (!isLoggedIn || userId == null) {
      setActiveOffer(null);
      return;
    }

    let disconnected = false;
    const client = connectNegoSocket((offer) => {
      if (!disconnected) applyOffer(offer);
    });
    return () => {
      disconnected = true;
      void client.deactivate();
    };
  }, [isLoggedIn, userId]);

  // 폴링 백업: 아직 전환되지 않은 유효 제안 중 하나만 표시한다.
  useEffect(() => {
    if (!isLoggedIn || userId == null) return;
    let cancelled = false;

    const poll = async () => {
      try {
        if (isSnoozed()) {
          setActiveOffer(null);
          return;
        }
        const offers = await negoApi.getActiveOffers();
        if (cancelled || snoozeUntilRef.current > Date.now()) return;

        const latest = offers
          .filter(
            (o) =>
              o.conversionYn === "N" &&
              o.remainingSeconds > 0 &&
              !dismissedRef.current.has(o.negoId),
          )
          .sort((a, b) => b.remainingSeconds - a.remainingSeconds)[0];

        setActiveOffer(latest ?? null);
      } catch (err) {
        if (!cancelled) console.error("네고 오퍼 폴링 실패:", err);
      }
    };

    void poll();
    const timer = window.setInterval(() => void poll(), POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [isLoggedIn, userId]);

  const dismiss = (snoozeOneHour = false) => {
    if (activeOffer) dismissedRef.current.add(activeOffer.negoId);
    if (snoozeOneHour && userId != null) {
      snoozeUntilRef.current = saveNegoSnoozeOneHour(userId);
    }
    setActiveOffer(null);
  };

  const accept = async (accountId: number) => {
    if (!activeOffer) throw new Error("수락할 제안이 없습니다.");
    // await 전에 확정한 ID를 사용해야 소켓에서 다른 오퍼가 도착해도 섞이지 않는다.
    const acceptedNegoId = activeOffer.negoId;
    const result = await negoApi.accept(acceptedNegoId, accountId);
    if (result.conversionYn !== "Y") {
      throw new Error("거래 완료 상태를 확인하지 못했습니다.");
    }

    dismissedRef.current.add(acceptedNegoId);
    setActiveOffer(null);
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
