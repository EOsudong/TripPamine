import { useState } from "react";
import { MessageCircle, X, Maximize2 } from "lucide-react";
import { Link } from "react-router-dom";
import EmotionChatPanel from "./EmotionChatPanel";

/** Home 전용 플로팅 런처. 닫을 때도 채팅 컴포넌트를 유지해 임시 이력을 보존한다. */
export default function EmotionChatLauncher() {
  const [open, setOpen] = useState(false);

  return (
    <div className="fixed bottom-5 right-4 z-[80] flex flex-col items-end gap-3 sm:bottom-7 sm:right-7">
      <section
        id="trip-emotion-chat-window"
        aria-label="TripPamine 마음 메이트 메신저"
        className={`${open ? "flex" : "hidden"} h-[min(640px,calc(100dvh-115px))] w-[min(400px,calc(100vw-24px))] flex-col overflow-hidden rounded-3xl border border-slate-100 bg-white shadow-[0_24px_80px_-20px_rgba(15,23,42,0.4)]`}
      >
        <div className="flex justify-end gap-2 border-b border-slate-100 bg-white px-3 py-2">
          <Link
            to="/ai/emotion"
            onClick={() => setOpen(false)}
            title="전체 화면에서 보기"
            aria-label="감정 챗봇 전체 화면에서 보기"
            className="rounded-full p-1.5 text-slate-500 hover:bg-slate-100"
          >
            <Maximize2 size={16} />
          </Link>
          <button
            type="button"
            title="메신저 닫기"
            aria-label="메신저 닫기"
            onClick={() => setOpen(false)}
            className="rounded-full p-1.5 text-slate-500 hover:bg-slate-100"
          >
            <X size={18} />
          </button>
        </div>
        <div className="min-h-0 flex-1">
          <EmotionChatPanel compact />
        </div>
      </section>
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        aria-controls="trip-emotion-chat-window"
        aria-label={open ? "마음 메이트 닫기" : "마음 메이트 열기"}
        className="flex h-15 w-15 items-center justify-center rounded-full bg-gradient-to-br from-sky-500 to-indigo-600 text-white shadow-[0_12px_32px_rgba(14,165,233,0.45)] transition duration-200 hover:scale-105 hover:shadow-xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-500"
      >
        {open ? <X size={26} /> : <MessageCircle size={27} />}
      </button>
    </div>
  );
}
