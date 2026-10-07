import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { getMyInfoApi, type MyInfoResponse } from "../api/auth";
import {
  extractQuestErrorMessage,
  questApi,
  type QuestChapterResponse,
  type QuestResponse,
  type RpgStatsResponse,
  type UserQuestLogResponse,
} from "../api/quest";
import AppLayout from "../layout/AppLayout";
import QuestWorldMap from "../components/QuestWorldMap";
import RpgStatsBar from "../components/RpgStatsBar";

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

// 난이도(1~3 권장)를 ★☆ 문자열로 변환. 범위를 벗어나도 깨지지 않게 1~5로 클램프.
function difficultyStars(difficulty: number) {
  const level = Math.min(5, Math.max(1, difficulty || 1));
  return "★".repeat(level) + "☆".repeat(Math.max(0, 5 - level));
}

export default function QuestPage() {
  const navigate = useNavigate();
  const [profile, setProfile] = useState<MyInfoResponse | null>(null);
  const [quests, setQuests] = useState<QuestResponse[]>([]);
  const [myLogs, setMyLogs] = useState<UserQuestLogResponse[]>([]);
  const [stats, setStats] = useState<RpgStatsResponse | null>(null);
  const [chapters, setChapters] = useState<QuestChapterResponse[]>([]);
  const [selectedChapterId, setSelectedChapterId] = useState<number | null>(
      null,
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryToken, setRetryToken] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    Promise.all([
      getMyInfoApi(),
      questApi.getQuests(),
      questApi.getMyLogs(),
      questApi.getMyStats(),
      questApi.getChapters(),
    ])
        .then(([myInfo, questList, logList, statsResponse, chapterList]) => {
          if (cancelled) return;
          setProfile(myInfo);
          setQuests(questList);
          setMyLogs(logList);
          setStats(statsResponse);
          setChapters(chapterList);
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

  const visibleQuests = useMemo(() => {
    if (selectedChapterId == null) return quests;
    return quests.filter((quest) => quest.chapterId === selectedChapterId);
  }, [quests, selectedChapterId]);

  const handleQuestSelect = useCallback(
      (questId: number) => navigate(`/quests/${questId}`),
      [navigate],
  );

  return (
      <AppLayout
          pageClassName="bg-slate-950 text-white"
      >
        <div className="mx-auto max-w-6xl px-4 py-10">
          <section className="overflow-hidden rounded-3xl border border-cyan-500/20 bg-gradient-to-br from-slate-900 via-slate-900 to-cyan-950 p-6 shadow-2xl md:p-8">
            <p className="text-xs font-black tracking-[0.25em] text-cyan-400">
              TRIPPAMINE ADVENTURE
            </p>
            <div className="mt-3 flex flex-wrap items-end justify-between gap-6">
              <div>
                <h1 className="text-3xl font-black md:text-4xl flex items-center gap-2">
                  <span className="text-2xl md:text-3xl select-none">🎖️</span>
                  <span>{profile?.userName ?? "여행자"} 님</span>
                </h1>
                <p className="mt-2 text-sm text-slate-400">
                  오늘도 짜릿한 도파민 어드벤처를 떠날 준비가 되셨나요?
                </p>
              </div>
              <div className="flex flex-wrap gap-3">
                <RpgStatsBar stats={stats} />
                <Stat
                    label="보유 포인트"
                    value={`${(profile?.totalPoints ?? 0).toLocaleString()} P`}
                />
                <Stat label="완료 퀘스트" value={`${completedCount}개`} />
              </div>
            </div>
          </section>

          {!loading && !error && chapters.length > 0 && (
              <section className="mt-6">
                <div className="mb-3 flex items-center justify-between">
                  <h2 className="text-xs font-black tracking-[0.2em] text-cyan-400">
                    CHAPTERS
                  </h2>
                  <span className="text-xs text-slate-500">
                  챕터를 선택해 퀘스트를 걸러볼 수 있어요
                </span>
                </div>
                <div className="flex gap-3 overflow-x-auto pb-2">
                  <button
                      type="button"
                      onClick={() => setSelectedChapterId(null)}
                      className={`shrink-0 rounded-2xl border px-4 py-3 text-left text-xs font-black transition ${
                          selectedChapterId == null
                              ? "border-cyan-400 bg-cyan-500/10 text-cyan-300"
                              : "border-slate-800 bg-slate-900 text-slate-400 hover:border-slate-700"
                      }`}
                  >
                    전체 보기
                  </button>
                  {chapters.map((chapter) => {
                    const selected = selectedChapterId === chapter.chapterId;
                    return (
                        <button
                            key={chapter.chapterId}
                            type="button"
                            disabled={chapter.locked}
                            onClick={() => setSelectedChapterId(chapter.chapterId)}
                            className={`shrink-0 min-w-[160px] rounded-2xl border px-4 py-3 text-left transition disabled:cursor-not-allowed disabled:opacity-50 ${
                                selected
                                    ? "border-cyan-400 bg-cyan-500/10"
                                    : "border-slate-800 bg-slate-900 hover:border-slate-700"
                            }`}
                        >
                          <p className="flex items-center gap-1.5 text-xs font-black text-white">
                            {chapter.locked ? "🔒" : "🗺️"} {chapter.chapterName}
                          </p>
                          <p className="mt-1 text-[11px] text-slate-500">
                            {chapter.region ?? "미정"}
                            {chapter.locked ? ` · Lv.${chapter.requiredLevel} 필요` : ""}
                          </p>
                        </button>
                    );
                  })}
                </div>
              </section>
          )}

          {!loading && !error && (
              <QuestWorldMap
                  quests={visibleQuests}
                  logs={myLogs}
                  onQuestSelect={handleQuestSelect}
              />
          )}

          <section className="mt-8">
            <div className="mb-4 flex items-center justify-between gap-3">
              <div>
                <p className="text-xs font-bold text-cyan-400">
                  FIELD MISSIONS
                </p>
                <h2 className="mt-1 text-xl font-black">발견된 퀘스트</h2>
              </div>
              <span className="text-xs text-slate-500">
                총 {visibleQuests.length}개의 미션
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
            ) : visibleQuests.length === 0 ? (
                <div className="rounded-3xl border border-dashed border-slate-700 bg-slate-900 px-6 py-16 text-center text-sm text-slate-400">
                  현재 도전 가능한 퀘스트가 없습니다.
                </div>
            ) : (
                <div className="grid gap-4 md:grid-cols-2">
                  {visibleQuests.map((quest) => {
                    const log = logByQuestId.get(quest.questId);
                    const status: QuestViewStatus = log?.status ?? "NOT_STARTED";
                    const statusConfig = STATUS_CONFIG[status];

                    return (
                        <article
                            key={quest.questId}
                            className={`rounded-3xl border p-5 shadow-lg transition hover:-translate-y-0.5 ${
                                quest.locked
                                    ? "border-slate-800 bg-slate-900/60 opacity-60"
                                    : "border-slate-800 bg-slate-900 hover:border-cyan-500/50"
                            }`}
                        >
                          <div className="flex items-start justify-between gap-4">
                            <div>
                              <p className="flex items-center gap-2 text-xs font-bold text-slate-500">
                                <span>{quest.questType} QUEST</span>
                                <span className="text-amber-400">
                              {difficultyStars(quest.difficulty)}
                            </span>
                              </p>
                              <h3 className="mt-2 text-lg font-black">
                                {statusConfig.icon} {quest.questName}
                              </h3>
                            </div>
                            {quest.locked ? (
                                <span className="shrink-0 rounded-full bg-slate-800 px-3 py-1 text-xs font-black text-slate-400">
                            🔒 Lv.{quest.requiredLevel}
                          </span>
                            ) : (
                                <span
                                    className={`shrink-0 rounded-full px-3 py-1 text-xs font-black ${statusConfig.badgeClass}`}
                                >
                            {statusConfig.label}
                          </span>
                            )}
                          </div>

                          <div className="mt-5 grid grid-cols-3 gap-3 text-xs">
                            <QuestMeta
                                label="인증 범위"
                                value={`반경 ${quest.clearRadius}m`}
                            />
                            <QuestMeta
                                label="보상"
                                value={`+${quest.rewardPoint.toLocaleString()} P`}
                            />
                            <QuestMeta
                                label="경험치"
                                value={`+${quest.rewardExp.toLocaleString()} EXP`}
                            />
                          </div>

                          <Link
                              to={`/quests/${quest.questId}`}
                              className={`mt-5 block w-full rounded-2xl py-3 text-center text-sm font-black transition ${
                                  quest.locked
                                      ? "bg-slate-800 text-slate-400 hover:bg-slate-700"
                                      : "bg-cyan-500 text-slate-950 hover:bg-cyan-400"
                              }`}
                          >
                            {quest.locked
                                ? "레벨 확인하기"
                                : status === "SUCCESS"
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
      </AppLayout>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
      <div className="min-w-24 rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-center">
      <span className="block text-[10px] font-bold uppercase text-slate-500">
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
