import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  HeartPulse,
  Loader2,
  Send,
  MessageCircle,
  RefreshCw,
} from "lucide-react";
import axios from "axios";
import { Link } from "react-router-dom";

/**
 * 백엔드는 기능 ID만 반환한다.
 * 개발용 화면 주소는 모델 프롬프트나 대화 말풍선에 넣지 않고
 * 프론트엔드 라우터가 사용 가능한 이동 버튼으로만 표현한다.
 */
const FEATURE_ACTIONS: Record<string, { label: string; to: string }> = {
  TRAVEL_PLAN: { label: "여행 플래너", to: "/travel" },
  TRAVEL_SOS: { label: "여행 플래너에서 SOS 확인", to: "/travel" },
  WEATHER: { label: "여행 날씨 확인", to: "/travel" },
  TRAVEL_RECOMMENDATION: { label: "AI 여행 추천", to: "/ai-recommend" },
  ASSET_DASHBOARD: { label: "자산관리", to: "/assets" },
  ASSET_GOAL: { label: "자산 목표", to: "/assets" },
  ACCOUNT_BOOK: { label: "가계부·계좌", to: "/mypage" },
  FINANCIAL_PRODUCTS: { label: "금융상품", to: "/finance" },
  QUEST: { label: "여행 퀘스트", to: "/quests" },
  RPG: { label: "퀘스트·포인트", to: "/quests" },
  MASCOT: { label: "마스코트 탐험", to: "/mascots" },
  MASCOT_DEX: { label: "마스코트 도감", to: "/mascots/dex" },
  MYSTERY_TOUR: { label: "여행 플래너", to: "/travel" },
  TOUR_INFORMATION: { label: "관광 여행지", to: "/tour/destinations" },
  AI_PHOTO: { label: "AI 사진 편집", to: "/ai-photo-editor" },
  COMMUNITY: { label: "게시판", to: "/boards" },
  EMOTION: { label: "마음 메이트", to: "/ai/emotion" },
};

import {
  getEmotionChatMessagesApi,
  getLatestEmotionChatSessionApi,
  sendEmotionChatApi,
  type EmotionChatTurn,
} from "../../api/emotionChat";
import {
  changeEmotionConsentApi,
  getAiManagerSettingsApi,
} from "../../api/aiManagerSettings";
import { useAuth } from "../../context/AuthContext";

interface EmotionChatPanelProps {
  compact?: boolean;
}

/** 플로팅 창과 /ai/emotion에서 동일한 채팅 및 동의 기능을 사용한다. */
export default function EmotionChatPanel({
  compact = false,
}: EmotionChatPanelProps) {
  const { userId } = useAuth();
  const [messages, setMessages] = useState<EmotionChatTurn[]>([]);
  const [input, setInput] = useState("");
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [saveHistory, setSaveHistory] = useState(false);
  const [emotionEnabled, setEmotionEnabled] = useState(false);
  const [settingsLoaded, setSettingsLoaded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [changing, setChanging] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [emotionName, setEmotionName] = useState<string | null>(null);
  const [relatedFeatureIds, setRelatedFeatureIds] = useState<string[]>([]);
  const bottomRef = useRef<HTMLDivElement>(null);
  const sendingRef = useRef(false);

  // 화면 진입 시 기존 동의 설정만 읽는다. 임시 채팅 내용은 서버에서 자동으로 가져오지 않는다.
  useEffect(() => {
    let cancelled = false;
    setEmotionEnabled(false);
    setSettingsLoaded(false);
    void getAiManagerSettingsApi()
      .then((settings) => {
        if (!cancelled) setEmotionEnabled(settings.emotionEnabled);
      })
      .catch(() => {
        if (!cancelled)
          setError(
            "감정 분석 설정을 불러오지 못했어요. 새로고침 후 다시 시도해 주세요.",
          );
      })
      .finally(() => {
        if (!cancelled) setSettingsLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, [userId]);

  useEffect(() => {
    setMessages([]);
    setConversationId(null);
    setSaveHistory(false);
    setEmotionName(null);
    setRelatedFeatureIds([]);
    setInput("");
  }, [userId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, loading]);

  const toggleEmotion = async () => {
    if (changing || !settingsLoaded) return;
    setChanging(true);
    setError(null);
    try {
      const settings = await changeEmotionConsentApi(!emotionEnabled);
      setEmotionEnabled(settings.emotionEnabled);
      if (!settings.emotionEnabled) setEmotionName(null);
    } catch {
      setError("감정 분석 동의를 변경하지 못했어요.");
    } finally {
      setChanging(false);
    }
  };

  const toggleSaving = () => {
    if (loading || changing) return;
    // 기존 임시 메시지를 동의 없이 서버에 업로드하지 않는다.
    setSaveHistory((current) => !current);
    setConversationId(null);
    setMessages([]);
    setEmotionName(null);
    setRelatedFeatureIds([]);
    setError(null);
  };

  const loadPrevious = async () => {
    if (!saveHistory || loading || changing) return;
    setChanging(true);
    setError(null);
    try {
      const id = await getLatestEmotionChatSessionApi();
      if (id === null) {
        setError("저장된 대화가 아직 없어요.");
        return;
      }
      const saved = await getEmotionChatMessagesApi(id);
      setMessages(
        saved.map((item) => ({
          role: item.role === "USER" ? "user" : "assistant",
          content: item.content,
        })),
      );
      setConversationId(id);
      setEmotionName(null);
      setRelatedFeatureIds([]);
    } catch {
      setError("이전 대화를 불러오지 못했어요.");
    } finally {
      setChanging(false);
    }
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const message = input.trim();
    if (!message || message.length > 2000 || sendingRef.current || changing)
      return;
    sendingRef.current = true;
    setLoading(true);
    setError(null);
    setInput("");
    const previous = [...messages];
    setMessages([...previous, { role: "user", content: message }]);
    try {
      const result = await sendEmotionChatApi({
        message,
        conversationId: saveHistory ? conversationId : null,
        saveHistory,
        analyzeEmotion: emotionEnabled,
        humanTurnCount:
          previous.filter((item) => item.role === "user").length + 1,
        history: saveHistory ? [] : previous.slice(-11),
      });
      setConversationId(result.conversationId);
      setMessages((current) => [
        ...current,
        { role: "assistant", content: result.answer },
      ]);
      // 감정 모델이 실행되지 않은 중립 대화에서는 오래된 감정 라벨을 유지하지 않는다.
      setEmotionName(result.emotionName);
      setRelatedFeatureIds(result.relatedFeatureIds ?? []);
    } catch (cause) {
      // 사용자 메시지는 다시 전송할 수 있도록 입력칸에 복구한다.
      setMessages(previous);
      setInput(message);
      setError(
        axios.isAxiosError(cause) && cause.response?.status === 503
          ? "지금은 AI 서버와 연결되지 않아요. 다시 시도해 주세요."
          : "답변을 가져오지 못했어요. 입력한 내용을 확인하고 다시 보내주세요.",
      );
    } finally {
      sendingRef.current = false;
      setLoading(false);
    }
  };

  return (
    <div className="flex h-full min-h-0 flex-col bg-white text-slate-900">
      <div className="flex items-center justify-between gap-3 border-b border-slate-100 px-4 py-3">
        <div className="flex items-center gap-2">
          <span className="rounded-full bg-sky-100 p-2 text-sky-600">
            <HeartPulse size={19} />
          </span>
          <div>
            <p className="text-sm font-extrabold">마음 메이트</p>
            <p className="text-[11px] text-slate-500">편하게 이야기해 주세요</p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => {
            setMessages([]);
            setConversationId(null);
            setEmotionName(null);
            setRelatedFeatureIds([]);
          }}
          disabled={loading}
          title="새로운 대화"
          aria-label="새로운 대화"
          className="rounded-full p-2 text-slate-500 transition hover:bg-slate-100 disabled:opacity-40"
        >
          <RefreshCw size={18} />
        </button>
      </div>

      <div className="flex flex-wrap items-center gap-2 border-b border-slate-100 px-4 py-2 text-[11px] text-slate-600">
        <label className="inline-flex cursor-pointer items-center gap-1.5">
          <input
            type="checkbox"
            checked={saveHistory}
            disabled={loading || changing}
            onChange={toggleSaving}
            className="accent-sky-600"
          />
          대화 서버 저장
        </label>
        <label className="inline-flex cursor-pointer items-center gap-1.5">
          <input
            type="checkbox"
            checked={emotionEnabled}
            disabled={!settingsLoaded || changing || loading}
            onChange={() => {
              void toggleEmotion();
            }}
            className="accent-sky-600"
          />
          감정 분석 동의
        </label>
        {saveHistory && (
          <button
            type="button"
            disabled={changing || loading}
            onClick={() => {
              void loadPrevious();
            }}
            className="ml-auto font-bold text-sky-600 hover:underline disabled:opacity-40"
          >
            이전 기록 불러오기
          </button>
        )}
        <p className="w-full text-[10px] leading-4 text-slate-400">
          저장을 켜거나 끄면 새 대화가 시작됩니다. 기존 저장 기록은 자동
          삭제되지 않습니다. 감정 분석은 동의 시에만 진행합니다.
        </p>
      </div>

      <div
        className="min-h-0 flex-1 space-y-4 overflow-y-auto bg-slate-50/70 px-4 py-5"
        role="log"
        aria-label="마음 메이트 대화"
        aria-live="polite"
      >
        {messages.length === 0 && (
          <div className="flex items-start gap-2">
            <span className="mt-1 rounded-full bg-sky-100 p-2 text-sky-700">
              <MessageCircle size={16} />
            </span>
            <p className="max-w-[85%] rounded-2xl rounded-tl-sm bg-white px-4 py-3 text-sm leading-6 shadow-sm">
              안녕하세요! 오늘은 어떤 하루를 보내셨나요? 마음에 떠오르는
              이야기를 편하게 해주세요.
            </p>
          </div>
        )}
        {messages.map((item, index) => (
          <div
            key={index}
            className={`flex ${item.role === "user" ? "justify-end" : "justify-start"}`}
          >
            <p
              className={`max-w-[88%] whitespace-pre-wrap break-words rounded-2xl px-4 py-3 text-sm leading-6 shadow-sm ${
                item.role === "user"
                  ? "rounded-tr-sm bg-sky-600 text-white"
                  : "rounded-tl-sm bg-white text-slate-800"
              }`}
            >
              {item.content}
            </p>
          </div>
        ))}
        {relatedFeatureIds.length > 0 && !loading && (
          <nav className="flex flex-wrap gap-2" aria-label="관련 서비스 바로가기">
            {relatedFeatureIds
              .map((id) => FEATURE_ACTIONS[id])
              .filter((item): item is { label: string; to: string } => !!item)
              .filter(
                (item, index, items) =>
                  items.findIndex((candidate) => candidate.to === item.to) === index,
              )
              .slice(0, 3)
              .map((item) => (
                <Link
                  key={item.to}
                  to={item.to}
                  className="rounded-full border border-sky-200 bg-white px-3 py-1.5 text-xs font-semibold text-sky-700 transition hover:bg-sky-50"
                >
                  {item.label} 바로가기
                </Link>
              ))}
          </nav>
        )}
        {loading && (
          <p className="flex items-center gap-2 text-xs text-slate-500">
            <Loader2 size={14} className="animate-spin" /> 마음 메이트가 답변을
            준비하고 있어요...
          </p>
        )}
        <div ref={bottomRef} />
      </div>

      {emotionName && emotionEnabled && (
        <p className="bg-sky-50 px-4 py-2 text-xs text-sky-800">
          최근 분석: <strong>{emotionName}</strong> · 추정 결과이며 감정을
          단정하지 않습니다.
        </p>
      )}
      {error && (
        <p role="alert" className="bg-rose-50 px-4 py-2 text-xs text-rose-700">
          {error}
        </p>
      )}
      <form
        onSubmit={(event) => {
          void submit(event);
        }}
        className="flex items-end gap-2 border-t border-slate-100 bg-white p-3"
      >
        <label
          htmlFor={compact ? "emotion-chat-compact" : "emotion-chat-full"}
          className="sr-only"
        >
          메시지
        </label>
        <textarea
          id={compact ? "emotion-chat-compact" : "emotion-chat-full"}
          value={input}
          onChange={(event) => setInput(event.target.value)}
          maxLength={2000}
          rows={compact ? 2 : 3}
          onKeyDown={(event) => {
            if (
              event.key === "Enter" &&
              !event.shiftKey &&
              !event.nativeEvent.isComposing
            ) {
              event.preventDefault();
              event.currentTarget.form?.requestSubmit();
            }
          }}
          placeholder="메시지를 입력하세요... (Shift+Enter 줄바꿈)"
          className="min-h-11 max-h-28 flex-1 resize-none rounded-2xl bg-slate-100 px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-sky-300"
        />
        <button
          type="submit"
          disabled={loading || changing || !input.trim()}
          aria-label="메시지 보내기"
          className="rounded-2xl bg-sky-600 p-3 text-white shadow-md shadow-sky-500/20 transition hover:bg-sky-700 disabled:opacity-40"
        >
          {loading ? (
            <Loader2 size={20} className="animate-spin" />
          ) : (
            <Send size={20} />
          )}
        </button>
      </form>
    </div>
  );
}
