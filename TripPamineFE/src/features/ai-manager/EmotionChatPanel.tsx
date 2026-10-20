import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import {
  HeartPulse,
  Loader2,
  Send,
  MessageCircle,
  RefreshCw,
  Settings2,
  ChevronDown,
  ShieldCheck,
} from "lucide-react";
import axios from "axios";
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
import type { ManagerPhase } from "./AiManagerCore";
import "./ai-manager.css";

export interface ManagerChatState {
  phase: ManagerPhase;
  hasMessages: boolean;
}
interface EmotionChatPanelProps {
  compact?: boolean;
  embedded?: boolean;
  focusRequest?: number;
  onStateChange?: (state: ManagerChatState) => void;
}

/** 계정이 바뀌면 대화·동의 상태와 진행 중인 응답을 이전 화면에서 분리한다. */
export default function EmotionChatPanel(props: EmotionChatPanelProps) {
  const { userId } = useAuth();
  return <EmotionChatSession key={userId ?? "guest"} {...props} />;
}

function EmotionChatSession({
  compact = false,
  embedded = false,
  focusRequest = 0,
  onStateChange,
}: EmotionChatPanelProps) {
  const [messages, setMessages] = useState<EmotionChatTurn[]>([]);
  const [input, setInput] = useState("");
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [saveHistory, setSaveHistory] = useState(false);
  const [emotionEnabled, setEmotionEnabled] = useState(false);
  const [settingsLoaded, setSettingsLoaded] = useState(false);
  const [settingsError, setSettingsError] = useState(false);
  const [settingsReload, setSettingsReload] = useState(0);
  const [loading, setLoading] = useState(false);
  const [changing, setChanging] = useState(false);
  const [focused, setFocused] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [emotionName, setEmotionName] = useState<string | null>(null);
  const logRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const sendingRef = useRef(false);
  const messageId = useId();

  // 임시 대화는 자동으로 서버에서 가져오거나 저장하지 않는다.
  useEffect(() => {
    let cancelled = false;
    setEmotionEnabled(false);
    setSettingsLoaded(false);
    setSettingsError(false);
    void getAiManagerSettingsApi()
      .then((settings) => {
        if (!cancelled) {
          setEmotionEnabled(settings.emotionEnabled);
          setSettingsLoaded(true);
        }
      })
      .catch(() => {
        if (!cancelled) setSettingsError(true);
      });
    return () => {
      cancelled = true;
    };
  }, [settingsReload]);

  useEffect(() => {
    // 페이지 전체를 움직이지 않고, 실제 대화가 있는 로그 안에서만 스크롤한다.
    const log = logRef.current;
    if (!log || (!messages.length && !loading)) return;
    log.scrollTo({
      top: log.scrollHeight,
      behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
        ? "auto"
        : "smooth",
    });
  }, [messages, loading]);

  useEffect(() => {
    if (focusRequest > 0) inputRef.current?.focus();
  }, [focusRequest]);
  const phase: ManagerPhase = loading
    ? "thinking"
    : error
      ? "error"
      : focused
        ? "typing"
        : messages.at(-1)?.role === "assistant"
          ? "ready"
          : "idle";
  const hasMessages = messages.length > 0;
  useEffect(() => {
    onStateChange?.({ phase, hasMessages });
  }, [phase, hasMessages, onStateChange]);

  const resetConversation = () => {
    if (loading || changing) return;
    setMessages([]);
    setConversationId(null);
    setEmotionName(null);
    setError(null);
  };
  const toggleEmotion = async () => {
    if (changing || loading || !settingsLoaded) return;
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
    setSaveHistory((current) => !current);
    resetConversation();
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
        analyzeEmotion: settingsLoaded && emotionEnabled,
        humanTurnCount:
          previous.filter((item) => item.role === "user").length + 1,
        history: saveHistory ? [] : previous.slice(-11),
      });
      setConversationId(result.conversationId);
      setMessages((current) => [
        ...current,
        { role: "assistant", content: result.answer },
      ]);
      if (result.emotionName) setEmotionName(result.emotionName);
    } catch (cause) {
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

  const settings = (
    <div
      className={
        embedded
          ? "am-settings-body"
          : "flex flex-wrap items-center gap-3 border-b border-slate-100 px-4 py-2 text-xs text-slate-600"
      }
    >
      <label
        className={
          embedded
            ? "am-settings-label"
            : "inline-flex min-h-11 cursor-pointer items-center gap-2 text-slate-700"
        }
      >
        <input
          type="checkbox"
          checked={saveHistory}
          disabled={loading || changing}
          onChange={toggleSaving}
          className="accent-sky-600"
        />
        <span>대화 서버 저장</span>
      </label>
      <label
        className={
          embedded
            ? "am-settings-label"
            : "inline-flex min-h-11 cursor-pointer items-center gap-2 text-slate-700"
        }
      >
        <input
          type="checkbox"
          checked={emotionEnabled}
          disabled={!settingsLoaded || changing || loading}
          onChange={() => void toggleEmotion()}
          className="accent-sky-600"
        />
        <span>감정 분석 동의</span>
      </label>
      {!settingsLoaded && !settingsError && (
        <p
          className={embedded ? "am-settings-help" : "text-xs text-slate-600"}
          role="status"
        >
          감정 분석 설정 확인 중…
        </p>
      )}
      {settingsError && (
        <div
          role="alert"
          className={embedded ? "am-settings-help" : "text-xs text-slate-600"}
        >
          감정 분석 설정을 확인하지 못했어요. 분석 없이 대화할 수 있어요.
          <button
            type="button"
            disabled={loading || changing}
            onClick={() => setSettingsReload((value) => value + 1)}
            className={
              embedded
                ? "am-text-button"
                : "ml-2 min-h-11 text-sky-700 underline"
            }
          >
            설정 다시 확인
          </button>
        </div>
      )}
      {saveHistory && (
        <button
          type="button"
          disabled={changing || loading}
          onClick={() => void loadPrevious()}
          className={
            embedded
              ? "am-text-button"
              : "min-h-11 font-bold text-sky-700 hover:underline disabled:opacity-50"
          }
        >
          이전 기록 불러오기
        </button>
      )}
      <p
        className={
          embedded
            ? "am-settings-help"
            : "w-full text-xs leading-5 text-slate-500"
        }
      >
        저장을 켜거나 끄면 새 대화가 시작됩니다. 기존 저장 기록은 자동 삭제되지
        않습니다. 감정 분석은 동의 시에만 진행합니다.
      </p>
    </div>
  );

  return (
    <div
      className={
        embedded
          ? "am-chat"
          : "flex h-full min-h-0 flex-col bg-white text-slate-900"
      }
    >
      {(!embedded || hasMessages) && (
        <div
          className={
            embedded
              ? "am-chat-header"
              : "flex items-center justify-between gap-3 border-b border-slate-100 px-4 py-3"
          }
        >
          <div className="flex items-center gap-2">
            <HeartPulse size={18} aria-hidden="true" />
            <p className="text-sm font-bold">AI 매니저와 대화</p>
          </div>
          <button
            type="button"
            onClick={resetConversation}
            disabled={loading || changing}
            aria-label="새로운 대화"
            className={
              embedded
                ? "am-text-button"
                : "flex min-h-11 items-center gap-2 rounded-xl px-3 text-xs text-slate-600 hover:bg-slate-100 disabled:opacity-50"
            }
          >
            <RefreshCw size={15} aria-hidden="true" />
            <span>새 대화</span>
          </button>
        </div>
      )}
      {!embedded && settings}
      {(!embedded || hasMessages) && (
        <div
          ref={logRef}
          className={
            embedded
              ? "am-chat-log"
              : "min-h-0 flex-1 space-y-4 overflow-y-auto bg-slate-50/70 px-4 py-5"
          }
          role="log"
          aria-label="AI 매니저 대화"
          aria-live="polite"
          aria-relevant="additions text"
        >
          {!hasMessages && (
            <div className="flex items-start gap-2">
              <MessageCircle size={18} aria-hidden="true" />
              <p className="rounded-2xl bg-white px-4 py-3 text-sm leading-6">
                오늘은 어떤 하루를 보내셨나요? 마음에 떠오르는 이야기를 편하게
                해주세요.
              </p>
            </div>
          )}
          {messages.map((item, index) => (
            <div
              key={index}
              className={`flex ${item.role === "user" ? "justify-end" : "justify-start"}`}
            >
              <p
                className={
                  embedded
                    ? `am-message am-message--${item.role}`
                    : `max-w-[88%] whitespace-pre-wrap break-words rounded-2xl px-4 py-3 text-sm leading-6 ${item.role === "user" ? "bg-sky-600 text-white" : "bg-white text-slate-800"}`
                }
              >
                <span className="sr-only">
                  {item.role === "user" ? "나: " : "AI 매니저: "}
                </span>
                {item.content}
              </p>
            </div>
          ))}
          {loading && (
            <p
              className={
                embedded
                  ? "am-chat-working"
                  : "flex items-center gap-2 text-xs text-slate-500"
              }
            >
              <Loader2 size={14} className="animate-spin" aria-hidden="true" />
              답변을 준비하고 있어요…
            </p>
          )}
        </div>
      )}
      {emotionName && emotionEnabled && (
        <p
          className={
            embedded
              ? "am-chat-notice"
              : "bg-sky-50 px-4 py-2 text-xs text-sky-800"
          }
        >
          최근 분석: <strong>{emotionName}</strong> · 추정 결과이며 감정을
          단정하지 않습니다.
        </p>
      )}
      {error && (
        <p
          role="alert"
          className={
            embedded
              ? "am-chat-error"
              : "bg-rose-50 px-4 py-2 text-xs text-rose-700"
          }
        >
          {error}
        </p>
      )}
      <form
        onSubmit={(event) => void submit(event)}
        className={
          embedded
            ? "am-composer"
            : "flex items-end gap-2 border-t border-slate-100 bg-white p-3"
        }
      >
        <label htmlFor={messageId} className="sr-only">
          AI 매니저에게 메시지 보내기
        </label>
        <textarea
          ref={inputRef}
          id={messageId}
          value={input}
          onChange={(event) => setInput(event.target.value)}
          maxLength={2000}
          rows={embedded || compact ? 2 : 3}
          disabled={loading || changing}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          onKeyDown={(event) => {
            if (
              event.key === "Enter" &&
              !event.shiftKey &&
              !event.nativeEvent.isComposing &&
              event.nativeEvent.keyCode !== 229
            ) {
              event.preventDefault();
              event.currentTarget.form?.requestSubmit();
            }
          }}
          aria-describedby={`${messageId}-help`}
          placeholder="오늘의 기분이나 고민을 이야기해 주세요…"
          className={
            embedded
              ? "am-message-input"
              : "min-h-11 max-h-28 min-w-0 flex-1 resize-none rounded-2xl bg-slate-100 px-4 py-3 text-base outline-none focus:ring-2 focus:ring-sky-300 disabled:opacity-60"
          }
        />
        <button
          type="submit"
          disabled={loading || changing || !input.trim()}
          aria-label="메시지 보내기"
          className={
            embedded
              ? "am-send"
              : "flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-sky-600 text-white transition hover:bg-sky-700 disabled:opacity-40"
          }
        >
          {loading ? (
            <Loader2 size={20} className="animate-spin" aria-hidden="true" />
          ) : (
            <Send size={20} aria-hidden="true" />
          )}
        </button>
      </form>
      <div
        className={
          embedded ? "am-chat-footnote" : "px-4 py-2 text-xs text-slate-500"
        }
        id={`${messageId}-help`}
      >
        <span>
          {input.length > 1800
            ? `${input.length.toLocaleString()} / 2,000자 · `
            : ""}

        </span>
        {embedded && (
          <span>
            <ShieldCheck size={13} aria-hidden="true" />
            {saveHistory ? "대화 저장 켜짐" : "대화 서버 저장 꺼짐"}
          </span>
        )}
      </div>
      {embedded && (
        <details className="am-settings">
          <summary>
            <Settings2 size={14} aria-hidden="true" />
            대화 설정
            <ChevronDown size={14} aria-hidden="true" />
          </summary>
          {settings}
        </details>
      )}
    </div>
  );
}
