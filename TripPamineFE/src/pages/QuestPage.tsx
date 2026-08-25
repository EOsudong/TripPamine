import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { getMyInfoApi, type MyInfoResponse } from "../api/auth";
import {
  extractQuestErrorMessage,
  questApi,
  type QuestResponse,
  type UserQuestLogResponse,
} from "../api/quest";
import Footer from "../components/Footer";
import Header from "../components/Header";
import Sidebar from "../components/Sidebar";

type QuestViewStatus = "NOT_STARTED" | UserQuestLogResponse["status"];

const STATUS_CONFIG: Record<
  QuestViewStatus,
  { label: string; badgeClass: string; icon: string }
> = {
  NOT_STARTED: {
    label: "도전 가능",
    badgeClass: "bg-sky-50 text-sky-600",
    icon: "📌",
  },
  PROGRESS: {
    label: "진행 중",
    badgeClass: "bg-amber-50 text-amber-600",
    icon: "🔥",
  },
  FAILED: {
    label: "재도전 가능",
    badgeClass: "bg-rose-50 text-rose-500",
    icon: "🔁",
  },
  SUCCESS: {
    label: "완료",
    badgeClass: "bg-emerald-100 text-emerald-700",
    icon: "✅",
  },
};

export default function QuestPage() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [profile, setProfile] = useState<MyInfoResponse | null>(null);
  const [quests, setQuests] = useState<QuestResponse[]>([]);
  const [myLogs, setMyLogs] = useState<UserQuestLogResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryToken, setRetryToken] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    Promise.all([getMyInfoApi(), questApi.getQuests(), questApi.getMyLogs()])
      .then(([myInfo, questList, logList]) => {
        if (cancelled) return;
        setProfile(myInfo);
        setQuests(questList);
        setMyLogs(logList);
      })
      .catch((err) => {
        console.error("퀘스트 허브 조회 실패:", err);
        if (!cancelled) {
          setError(
            extractQuestErrorMessage(
              err,
              "퀘스트 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.",
            ),
          );
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [retryToken]);

  const logByQuestId = useMemo(() => {
    const map = new Map<number, UserQuestLogResponse>();
    myLogs.forEach((log) => map.set(log.questId, log));
    return map;
  }, [myLogs]);

  const completedCount = myLogs.filter(
    (log) => log.status === "SUCCESS",
  ).length;

  return (
    <div className="min-h-screen bg-slate-950 text-white">
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <Header
        sidebarOpen={sidebarOpen}
        onToggleSidebar={() => setSidebarOpen((value) => !value)}
      />

      <main
        className={`transition-all duration-300 pt-16 ${
          sidebarOpen ? "lg:pl-64" : "lg:pl-16"
        }`}
      >
        <div className="mx-auto max-w-6xl px-4 py-10">
          <section className="overflow-hidden rounded-3xl border border-cyan-500/20 bg-gradient-to-br from-slate-900 via-slate-900 to-cyan-950 p-6 shadow-2xl md:p-8">
            <p className="text-xs font-black tracking-[0.25em] text-cyan-400">
              TRIPPAMINE ADVENTURE
            </p>
            <div className="mt-3 flex flex-wrap items-end justify-between gap-6">
              <div>
                <h1 className="text-3xl font-black md:text-3xl">
                  {profile?.userName || "여행자"}님, 안녕하세요 👋
                </h1>
                <p className="mt-2 text-sm text-slate-400">
                  오늘도 짜릿한 도파민 어드벤처를 떠날 준비가 되셨나요?
                </p>
              </div>
              <div className="flex gap-3">
                <Stat label="등급" value={profile?.grade ?? "-"} />
                <Stat
                  label="보유 포인트"
                  value={`${(profile?.totalPoints ?? 0).toLocaleString()} P`}
                />
                <Stat label="완료 퀘스트" value={`${completedCount}개`} />
              </div>
            </div>
          </section>

          <section className="mt-8">
            <div className="mb-4 flex items-center justify-between gap-3">
              <div>
                <p className="text-xs font-bold text-cyan-400">
                  FIELD MISSIONS
                </p>
                <h2 className="mt-1 text-xl font-black">발견된 퀘스트</h2>
              </div>
              <span className="text-xs text-slate-500">
                총 {quests.length}개의 미션
              </span>
            </div>

            {loading ? (
              <QuestGridSkeleton />
            ) : error ? (
              <div className="rounded-3xl border border-dashed border-slate-700 bg-slate-900 px-6 py-16 text-center">
                <p className="text-sm text-slate-400">{error}</p>
                <button
                  type="button"
                  onClick={() => setRetryToken((value) => value + 1)}
                  className="mt-4 rounded-xl bg-cyan-500 px-4 py-2 text-xs font-black text-slate-950 transition hover:bg-cyan-400"
                >
                  다시 시도
                </button>
              </div>
            ) : quests.length === 0 ? (
              <div className="rounded-3xl border border-dashed border-slate-700 bg-slate-900 px-6 py-16 text-center text-sm text-slate-400">
                현재 도전 가능한 퀘스트가 없습니다.
              </div>
            ) : (
              <div className="grid gap-4 md:grid-cols-2">
                {quests.map((quest) => {
                  const log = logByQuestId.get(quest.questId);
                  const status: QuestViewStatus = log?.status ?? "NOT_STARTED";
                  const statusConfig = STATUS_CONFIG[status];

                  return (
                    <article
                      key={quest.questId}
                      className="rounded-3xl border border-slate-800 bg-slate-900 p-5 shadow-lg transition hover:-translate-y-0.5 hover:border-cyan-500/50"
                    >
                      <div className="flex items-start justify-between gap-4">
                        <div>
                          <p className="text-xs font-bold text-slate-500">
                            LOCATION QUEST
                          </p>
                          <h3 className="mt-2 text-lg font-black">
                            {statusConfig.icon} {quest.questName}
                          </h3>
                        </div>
                        <span
                          className={`shrink-0 rounded-full px-3 py-1 text-xs font-black ${statusConfig.badgeClass}`}
                        >
                          {statusConfig.label}
                        </span>
                      </div>

                      <div className="mt-5 grid grid-cols-2 gap-3 text-xs">
                        <QuestMeta
                          label="인증 범위"
                          value={`반경 ${quest.clearRadius}m`}
                        />
                        <QuestMeta
                          label="보상"
                          value={`+${quest.rewardPoint.toLocaleString()} P`}
                        />
                      </div>

                      <Link
                        to={`/quests/${quest.questId}`}
                        className="mt-5 block w-full rounded-2xl bg-cyan-500 py-3 text-center text-sm font-black text-slate-950 transition hover:bg-cyan-400"
                      >
                        {status === "SUCCESS"
                          ? "완료 기록 보기"
                          : status === "NOT_STARTED"
                            ? "퀘스트 상세 보기"
                            : "퀘스트 이어가기"}
                      </Link>
                    </article>
                  );
                })}
              </div>
            )}
          </section>
        </div>
        <Footer />
      </main>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-24 rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-center">
      <span className="block text-[10px] font-bold uppercase text-slate-400">
        {label}
      </span>
      <span className="mt-1 block text-sm font-black text-cyan-300">
        {value}
      </span>
    </div>
  );
}

function QuestMeta({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl bg-slate-950/70 p-3">
      <span className="block text-slate-500">{label}</span>
      <strong className="mt-1 block text-slate-200">{value}</strong>
    </div>
  );
}

function QuestGridSkeleton() {
  return (
    <div className="grid gap-4 md:grid-cols-2">
      {[0, 1, 2, 3].map((item) => (
        <div
          key={item}
          className="h-56 animate-pulse rounded-3xl border border-slate-800 bg-slate-900"
        />
      ))}
    </div>
  );
}
