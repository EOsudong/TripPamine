import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  ArrowUpRight,
  ChevronDown,
  HeartPulse,
  Map,
  Pause,
  Play,
  RefreshCw,
  Sparkles,
  Target,
  Wallet,
  BookOpen,
  Landmark,
  Compass,
} from "lucide-react";
import {
  getAiManagerHomeApi,
  type AiManagerActivityEvent,
  type AiManagerFocusTravel,
  type AiManagerHomeResponse,
} from "../../api/aiManagerHome";
import { useAuth } from "../../context/AuthContext";
import AiManagerCore, { type ManagerPhase } from "./AiManagerCore";
import EmotionChatPanel, { type ManagerChatState } from "./EmotionChatPanel";
import FinanceConsentCard from "./FinanceConsentCard";
import TravelOverviewCard from "./TravelOverviewCard";
import "./ai-manager.css";

type ManagerDomain = AiManagerActivityEvent["domain"];
interface ManagerAttentionItem {
  id: string;
  domain: ManagerDomain;
  title: string;
  description: string;
  actionLabel: string;
  to: string;
  priority: "high" | "normal";
}
const PHASE_LABEL: Record<ManagerPhase, string> = {
  idle: "편하게 이야기해 주세요",
  loading: "오늘의 정보를 불러오고 있어요",
  typing: "이야기를 적고 있어요",
  thinking: "답변을 준비하고 있어요",
  ready: "답변이 도착했어요",
  error: "대화 안내를 확인해 주세요",
};
const DOMAIN_LABEL: Record<ManagerDomain, string> = {
  travel: "여행",
  quest: "퀘스트",
  emotion: "마음",
  finance: "금융",
  diary: "일기",
  system: "소식",
};
const WORKSPACES = [
  {
    label: "여행 관리",
    description: "계획과 일정",
    path: "/travel",
    icon: Map,
  },
  {
    label: "AI 여행 추천",
    description: "새로운 여행 발견",
    path: "/ai-recommend",
    icon: Compass,
  },
  {
    label: "마음 메이트",
    description: "대화에 집중하기",
    path: "/ai/emotion",
    icon: HeartPulse,
  },
  {
    label: "퀘스트",
    description: "일상 속 새로운 도전",
    path: "/quests",
    icon: Target,
  },
  {
    label: "자산 리포트",
    description: "나의 자산 현황",
    path: "/assets",
    icon: Wallet,
  },
  {
    label: "가계부 · 계좌",
    description: "수입·지출 기록",
    path: "/mypage",
    icon: BookOpen,
  },
  {
    label: "금융상품",
    description: "예금 · 적금 추천",
    path: "/finance",
    icon: Landmark,
  },
];

export default function AiManagerDashboard() {
  const navigate = useNavigate();
  const { isLoggedIn, userId } = useAuth();
  const [homeContext, setHomeContext] = useState<AiManagerHomeResponse | null>(
    null,
  );
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadCount, setReloadCount] = useState(0);
  const [chat, setChat] = useState<ManagerChatState>({
    phase: "idle",
    hasMessages: false,
  });
  const [focusRequest, setFocusRequest] = useState(0);
  const [paused, setPaused] = useState(false);
  const [detailsOpen, setDetailsOpen] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setHomeContext(null);
    setLoadError(null);
    if (!isLoggedIn) {
      setLoading(false);
      return;
    }
    setLoading(true);
    void getAiManagerHomeApi()
      .then((context) => {
        if (!cancelled) setHomeContext(context);
      })
      .catch(() => {
        if (!cancelled)
          setLoadError(
            "홈 정보를 불러오지 못했어요. 대화와 바로가기는 계속 사용할 수 있어요.",
          );
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [isLoggedIn, userId, reloadCount]);

  const focusTravel = homeContext?.travel.focusPlan ?? null;
  const activeQuestCount = homeContext?.quests.activeCount ?? 0;
  const attentionItems = useMemo(
    () =>
      homeContext ? buildAttentionItems(focusTravel, activeQuestCount) : [],
    [homeContext, focusTravel, activeQuestCount],
  );
  const phase = chat.phase === "idle" && loading ? "loading" : chat.phase;
  const profile = homeContext?.user;

  return (
    <div className="am-dashboard">
      <section
        className={`am-hero ${chat.hasMessages ? "am-hero--conversation" : ""}`}
        aria-labelledby="am-heading"
      >
        <div className="am-hero-topline">
          <span className="am-eyebrow">
            <Sparkles size={14} aria-hidden="true" />
            YOUR AI MANAGER
          </span>
          <button
            type="button"
            className="am-motion-toggle"
            onClick={() => setPaused((value) => !value)}
            aria-pressed={paused}
            aria-label={paused ? "코어 모션 재생" : "코어 모션 일시정지"}
          >
            {paused ? (
              <Play size={13} aria-hidden="true" />
            ) : (
              <Pause size={13} aria-hidden="true" />
            )}
            <span>{paused ? "모션 재생" : "모션 멈춤"}</span>
          </button>
        </div>
        <div className="am-intro">
          <p className="am-greeting">
            {getTimeGreeting()}
            {profile ? `, ${profile.userName}님.` : "."}
          </p>
          <h1 id="am-heading">
            오늘은 어떤 이야기를
            <br className="am-mobile-break" /> 나눌까요?
          </h1>
          {!chat.hasMessages && (
            <p className="am-intro-description">
              마음을 나누고, 다음 여행을 찾아보세요.
            </p>
          )}
        </div>
        <AiManagerCore
          phase={phase}
          compact={chat.hasMessages}
          paused={paused}
        />
        <p className="am-core-status" role="status">
          <span
            className={`am-status-dot ${phase === "error" ? "am-status-dot--error" : ""}`}
          />
          {PHASE_LABEL[phase]}
        </p>
        <div className="am-chat-wrap">
          <EmotionChatPanel
            embedded
            focusRequest={focusRequest}
            onStateChange={setChat}
          />
        </div>
        {!chat.hasMessages && (
          <div className="am-shortcuts" aria-label="빠른 시작">
            <button
              type="button"
              onClick={() => setFocusRequest((value) => value + 1)}
            >
              <HeartPulse size={15} aria-hidden="true" />
              마음 이야기
            </button>
            <button type="button" onClick={() => navigate("/ai-recommend")}>
              <Compass size={15} aria-hidden="true" />
              여행 추천
              <ArrowUpRight size={13} aria-hidden="true" />
            </button>
            <button type="button" onClick={() => navigate("/quests")}>
              <Target size={15} aria-hidden="true" />
              퀘스트 확인
              <ArrowUpRight size={13} aria-hidden="true" />
            </button>
          </div>
        )}
      </section>

      <div className="am-overview">
        <section
          className="am-attention"
          aria-labelledby="am-attention-heading"
          aria-busy={loading}
        >
          <div className="am-section-heading">
            <h2 id="am-attention-heading">지금 확인할 것</h2>
            <span>오늘의 체크인</span>
          </div>
          {loading ? (
            <p className="am-empty" role="status">
              여행과 퀘스트를 확인하고 있어요…
            </p>
          ) : loadError ? (
            <div className="am-load-error">
              <p role="alert">{loadError}</p>
              <button
                type="button"
                onClick={() => setReloadCount((value) => value + 1)}
                className="am-secondary-button"
              >
                <RefreshCw size={14} aria-hidden="true" />
                다시 불러오기
              </button>
            </div>
          ) : attentionItems.length ? (
            <div className="am-attention-grid">
              {attentionItems.map((item) => (
                <button
                  className="am-attention-item"
                  key={item.id}
                  type="button"
                  onClick={() => navigate(item.to)}
                >
                  <span className="am-attention-icon">
                    {item.domain === "quest" ? (
                      <Target size={19} aria-hidden="true" />
                    ) : (
                      <Map size={19} aria-hidden="true" />
                    )}
                  </span>
                  <span className="am-attention-content">
                    <span className="am-attention-label">
                      {DOMAIN_LABEL[item.domain]}
                      {item.priority === "high" && (
                        <span className="am-priority">먼저 확인</span>
                      )}
                    </span>
                    <strong>{item.title}</strong>
                    <span className="am-attention-description">
                      {item.description}
                    </span>
                    <span className="am-attention-action">
                      {item.actionLabel}
                      <ArrowUpRight size={13} aria-hidden="true" />
                    </span>
                  </span>
                </button>
              ))}
            </div>
          ) : (
            <p className="am-empty">지금은 확인할 항목이 없어요.</p>
          )}
        </section>

        <details
          className="am-detail-disclosure"
          onToggle={(event) => setDetailsOpen(event.currentTarget.open)}
        >
          <summary>
            <span>
              <strong>내 현황 자세히 보기</strong>
              <span>여행 · 금융 · 최근 활동</span>
            </span>
            <ChevronDown size={19} aria-hidden="true" />
          </summary>
          {detailsOpen && (
            <div className="am-detail-content">
              <TravelOverviewCard key={userId ?? "guest"} />
              <FinanceConsentCard
                finance={homeContext?.finance ?? null}
                onSettingsChanged={async () => {
                  const updated = await getAiManagerHomeApi();
                  setHomeContext(updated);
                  setLoadError(null);
                }}
              />
              <div className="am-context-grid">
                <section className="am-surface-card am-context">
                  <h2>최근 활동</h2>
                  {loading ? (
                    <p className="am-empty">불러오는 중…</p>
                  ) : loadError ? (
                    <p className="am-empty">
                      홈 정보를 다시 불러오면 활동을 확인할 수 있어요.
                    </p>
                  ) : homeContext?.activities.length ? (
                    <ul className="am-activity-list">
                      {homeContext.activities.map((activity) => (
                        <li key={activity.eventId}>
                          <div>
                            <span className="am-domain">
                              {DOMAIN_LABEL[activity.domain]}
                            </span>
                            <time dateTime={activity.occurredAt ?? undefined}>
                              {activity.occurredAt
                                ? formatRelativeTime(activity.occurredAt)
                                : "시간 정보 없음"}
                            </time>
                          </div>
                          <h3>{activity.title}</h3>
                          <p>{activity.description}</p>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="am-empty">아직 기록된 활동이 없어요.</p>
                  )}
                </section>
                <section className="am-surface-card am-context">
                  <h2>나의 현재 상태</h2>
                  <dl className="am-context-list">
                    <div>
                      <dt>여행 계획</dt>
                      <dd>
                        {homeContext
                          ? `${homeContext.travel.totalPlanCount}개`
                          : "—"}
                      </dd>
                    </div>
                    <div>
                      <dt>진행 중인 퀘스트</dt>
                      <dd>{homeContext ? `${activeQuestCount}개` : "—"}</dd>
                    </div>
                    <div>
                      <dt>완료 / 미완료 퀘스트</dt>
                      <dd>
                        {homeContext
                          ? `${homeContext.quests.completedCount} / ${homeContext.quests.failedCount}개`
                          : "—"}
                      </dd>
                    </div>
                    <div>
                      <dt>최근 감정 기록</dt>
                      <dd>
                        {homeContext
                          ? (homeContext.emotion?.fineLabelName ?? "기록 없음")
                          : "—"}
                      </dd>
                    </div>
                    <div>
                      <dt>보유 포인트</dt>
                      <dd>
                        {profile
                          ? `${profile.totalPoints.toLocaleString("ko-KR")} P`
                          : "—"}
                      </dd>
                    </div>
                  </dl>
                  <p className="am-muted text-xs leading-5">
                    감정 기록은 분석 당시의 추정 결과예요.
                  </p>
                </section>
              </div>
            </div>
          )}
        </details>

        <section
          className="am-workspaces"
          aria-labelledby="am-workspace-heading"
        >
          <div className="am-section-heading">
            <h2 id="am-workspace-heading">나의 공간</h2>
          </div>
          <div className="am-workspace-grid">
            {WORKSPACES.map(({ label, description, path, icon: Icon }) => (
              <button key={path} type="button" onClick={() => navigate(path)}>
                <Icon size={18} aria-hidden="true" />
                <span>
                  <strong>{label}</strong>
                  <span>{description}</span>
                </span>
                <ArrowUpRight size={14} aria-hidden="true" />
              </button>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}

function buildAttentionItems(
  travel: AiManagerFocusTravel | null,

  activeQuestCount: number,
): ManagerAttentionItem[] {
  const items: ManagerAttentionItem[] = [];

  if (travel) {
    if (travel.state === "ACTIVE") {
      items.push({
        id: `travel-${travel.planId}`,

        domain: "travel",

        title: `${travel.planName} 여행이 진행 중이에요`,

        description: "현재 여행 일정과 필요한 정보를 확인해보세요.",

        actionLabel: "여행 확인",

        to: "/travel",

        priority: "high",
      });
    }

    if (
      travel.state === "UPCOMING" &&
      travel.daysUntilStart !== null &&
      travel.daysUntilStart <= 7
    ) {
      const days = travel.daysUntilStart;

      items.push({
        id: `travel-${travel.planId}`,

        domain: "travel",

        title:
          days === 0
            ? `${travel.planName}이 오늘 시작돼요`
            : `${travel.planName}까지 ${days}일 남았어요`,

        description: "출발 전에 등록된 일정과 여행 정보를 다시 확인해보세요.",

        actionLabel: "여행 확인",

        to: "/travel",

        priority: days <= 1 ? "high" : "normal",
      });
    }
  }

  if (activeQuestCount > 0) {
    items.push({
      id: "active-quests",

      domain: "quest",

      title: `진행 중인 퀘스트가 ${activeQuestCount}개 있어요`,

      description: "아직 완료되지 않은 미션을 이어서 진행할 수 있습니다.",

      actionLabel: "퀘스트 보기",

      to: "/quests",

      priority: "normal",
    });
  }

  if (!travel && activeQuestCount === 0) {
    items.push({
      id: "start-travel",

      domain: "travel",

      title: "아직 예정된 여행이 없어요",

      description:
        "여행 계획을 하나 만들어 두면 앞으로의 일정을 한눈에 볼 수 있어요.",

      actionLabel: "여행 만들기",

      to: "/travel",

      priority: "normal",
    });
  }

  return items;
}

/* =========================================================
 * Time Helpers
 * ========================================================= */

/**
 * 인사말은 Browser의 현재 시간을 사용한다.
 *
 * D-Day처럼 중요한 Business 시간 계산은
 * Backend가 담당한다.
 */
function getTimeGreeting(): string {
  const hour = new Date().getHours();

  if (hour < 5) {
    return "늦은 밤이에요";
  }

  if (hour < 12) {
    return "좋은 아침이에요";
  }

  if (hour < 18) {
    return "좋은 오후예요";
  }

  return "좋은 저녁이에요";
}

/**
 * Event 발생 시각을 상대 시간으로 표현한다.
 *
 * 예:
 *
 * 방금
 * 10분 전
 * 3시간 전
 * 2일 전
 */
function formatRelativeTime(value: string): string {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) return "시간 정보 없음";
  const diffMs = Date.now() - date.getTime();

  const minutes = Math.max(0, Math.floor(diffMs / (1000 * 60)));

  if (minutes < 1) {
    return "방금";
  }

  if (minutes < 60) {
    return `${minutes}분 전`;
  }

  const hours = Math.floor(minutes / 60);

  if (hours < 24) {
    return `${hours}시간 전`;
  }

  const days = Math.floor(hours / 24);

  return `${days}일 전`;
}
