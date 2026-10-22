import { useState, type MouseEvent, type ReactNode } from "react";
import { ArrowUpRight, Loader2 } from "lucide-react";
import { myRealTripMylinkApi } from "../../api/myrealtripMylink";

/**
 * 제휴사 상품의 검증된 링크 문법만 클릭 가능한 UI로 변경한다.
 * 일반 AI 답변을 HTML로 해석하지 않아 스크립트 삽입을 방지한다.
 */
const PRODUCT_LINK = /\[상품 보기\]\((https:\/\/[^\s()]+)\)/g;

function verifiedProductUrl(raw: string): { id: string; url: string } | null {
  try {
    const url = new URL(raw);
    if (url.protocol !== "https:" || url.port || url.username || url.password || url.hash) return null;
    if (url.hostname === "experiences.myrealtrip.com") {
      const match = /^\/products\/(\d{1,20})$/.exec(url.pathname);
      if (match && !url.search) return { id: match[1], url: url.href };
    }
    if (url.hostname === "accommodation.myrealtrip.com") {
      const match = /^\/union\/products\/(\d{1,20})$/.exec(url.pathname);
      const query = url.search.slice(1);
      if (match && /^checkIn=\d{4}-\d{2}-\d{2}&checkOut=\d{4}-\d{2}-\d{2}&adultCount=(?:[1-9]|1\d|20)&childCount=0&childAges=$/.test(query)) {
        return { id: match[1], url: url.href };
      }
    }
    return null;
  } catch {
    return null;
  }
}

function verifiedMylink(raw: string): string | null {
  try {
    const url = new URL(raw);
    return url.protocol === "https:" && url.hostname === "myrealt.rip"
      && !url.port && !url.username && !url.password && !url.hash ? url.href : null;
  } catch {
    return null;
  }
}

function OfferLink({ id, url }: { id: string; url: string }) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [readyUrl, setReadyUrl] = useState<string | null>(null);

  const openPartner = async (event: MouseEvent<HTMLButtonElement>) => {
    event.preventDefault();
    if (pending) return;
    setPending(true);
    setError("");
    // 클릭 순간 새 창을 확보해 비동기 제휴 링크 발급 중 팝업 차단을 피한다.
    const popup = window.open("about:blank", "_blank");
    if (popup) {
      popup.opener = null;
      popup.document.title = "상품 링크 준비 중";
      popup.document.body.textContent = "안전한 상품 연결 주소를 확인하고 있어요.";
    }
    try {
      const link = verifiedMylink(await myRealTripMylinkApi.create(id, url));
      if (!link) throw new Error("잘못된 제휴 링크");
      if (popup && !popup.closed) popup.location.replace(link);
      else {
        setReadyUrl(link);
        setError("팝업이 차단됐어요. 아래 링크를 이용해 주세요.");
      }
    } catch {
      if (popup && !popup.closed) popup.close();
      setError("상품 연결에 실패했어요. 제휴 링크를 확인한 후 다시 시도해 주세요.");
    } finally {
      setPending(false);
    }
  };

  return (
    <span className="inline-flex flex-wrap items-center gap-2 align-middle">
      <button type="button" onClick={(event) => void openPartner(event)} disabled={pending}
        className="inline-flex items-center gap-1 rounded-lg bg-sky-50 px-2 py-1 font-semibold text-sky-700 hover:bg-sky-100 disabled:opacity-60">
        {pending ? <Loader2 size={13} className="animate-spin" aria-hidden="true" />
          : <ArrowUpRight size={13} aria-hidden="true" />}
        {pending ? "연결 중…" : "상품 보기"}
      </button>
      {readyUrl && <a href={readyUrl} target="_blank" rel="noopener noreferrer"
        className="font-semibold text-sky-700 underline">제휴 링크 열기</a>}
      {error && <span role="alert" className="text-xs text-rose-700">{error}</span>}
    </span>
  );
}

/** 백엔드가 발급하지 않은 임의 URL은 링크화하지 않고 원문 그대로 보여준다. */
export default function PartnerOfferMessage({ text }: { text: string }) {
  const pieces: ReactNode[] = [];
  let last = 0;
  for (const match of text.matchAll(PRODUCT_LINK)) {
    const index = match.index ?? last;
    const candidate = verifiedProductUrl(match[1]);
    pieces.push(text.slice(last, index));
    if (candidate) pieces.push(<OfferLink key={index} id={candidate.id} url={candidate.url} />);
    else pieces.push(match[0]);
    last = index + match[0].length;
  }
  pieces.push(text.slice(last));
  return <>{pieces}</>;
}
