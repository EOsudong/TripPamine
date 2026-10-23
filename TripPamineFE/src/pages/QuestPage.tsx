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
import TitleSelectModal from "../components/TitleSelectModal";

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
  // [칭호 변경] 모달 열림 상태
  const [titleModalOpen, setTitleModalOpen] = useState(false);
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

  // 현재 공개 퀘스트 기준 완료율을 계산해 비활성/삭제 퀘스트 로그가 분모를 넘지 않게 한다.
  const completedCount = quests.filter((item) =>
      logByQuestId.get(item.questId)?.status === "SUCCESS",
  ).length;

  // 챕터 진행률은 별도 상태를 저장하지 않고 서버 퀘스트/성공 로그에서 계산한다.
  // 공개 퀘스트가 하나도 없는 챕터는 완주 처리하지 않는다.
  const chapterProgress = useMemo(() => {
    const result = new Map<number, { total: number; completed: number }>();
    quests.forEach((quest) => {
      if (quest.chapterId == null) return;
      const current = result.get(quest.chapterId) ?? { total: 0, completed: 0 };
      current.total += 1;
      if (logByQuestId.get(quest.questId)?.status === "SUCCESS") {
        current.completed += 1;
      }
      result.set(quest.chapterId, current);
    });
    return result;
  }, [quests, logByQuestId]);

  const visibleQuests = useMemo(() => {
    if (selectedChapterId == null) return quests;
    return quests.filter((quest) => quest.chapterId === selectedChapterId);
  }, [quests, selectedChapterId]);

  // 독립 퀘스트 간 선행 관계를 표시한다. 기존 CHAIN 내부 단계와는 별개다.
  const visibleCompletedCount = visibleQuests.filter((item) =>
      logByQuestId.get(item.questId)?.status === "SUCCESS",
  ).length;
  const visiblePercent = visibleQuests.length > 0
      ? Math.round((visibleCompletedCount / visibleQuests.length) * 100)
      : 0;

  // 최근 완료 내역은 이미 지급된 보상 스냅샷으로만 표시한다.
  const recentClears = useMemo(() => myLogs
      .filter((log) => log.status === "SUCCESS" && quests.some((quest) => quest.questId === log.questId))
      .sort((a, b) => (b.clearDate ?? "").localeCompare(a.clearDate ?? ""))
      .slice(0, 4), [myLogs, quests]);

  const questById = useMemo(
      () => new Map(quests.map((item) => [item.questId, item])),
      [quests],
  );
  const storyQuests = useMemo(() => {
    return visibleQuests
        .filter((item) => (item.prerequisiteQuestIds?.length ?? 0) > 0 ||
            quests.some((next) => next.prerequisiteQuestIds?.includes(item.questId)))
        .sort((left, right) => left.questId - right.questId);
  }, [quests, visibleQuests]);
  const nextQuest = useMemo(() => {
    const pending = visibleQuests.filter((item) => logByQuestId.get(item.questId)?.status !== "SUCCESS");
    return pending.find((item) => logByQuestId.get(item.questId)?.status === "PROGRESS") ??
        pending.find((item) => !item.locked && (item.questType === "LOCATION" || item.questType === "CHAIN")) ?? null;
  }, [logByQuestId, visibleQuests]);

  const handleQuestSelect = useCallback(
      (questId: number) => navigate(`/quests/${questId}`),
      [navigate],
  );

  return (
      <AppLayout
          pageClassName="bg-night-950 text-white"
      >
        <div className="mx-auto max-w-6xl px-4 py-10">
          <section className="relative isolate overflow-hidden rounded-[2rem] border border-white/10 p-6 shadow-[0_40px_80px_-40px_rgba(34,211,238,0.45)] md:p-8">
            <div className="absolute inset-0 -z-10 tp-aurora" />
            <div className="absolute inset-0 -z-10 tp-dot-grid opacity-40" />
            <p className="text-xs font-black tracking-[0.25em] text-cyan-400">
              TRIPPAMINE ADVENTURE
            </p>
            <div className="mt-3 flex flex-wrap items-end justify-between gap-6">
              <div>
                <h1 className="flex items-center gap-2 font-display text-3xl font-bold tracking-tight md:text-5xl">
                  <span className="text-2xl md:text-3xl select-none">🎖️</span>
                  <span>{profile?.userName ?? "여행자"} 님</span>
                </h1>
                <p className="mt-2 text-sm text-slate-400">
                  오늘도 짜릿한 도파민 어드벤처를 떠날 준비가 되셨나요?
                </p>
              </div>
              <div className="flex flex-wrap gap-3">
                <div className="flex flex-col gap-1.5">
                  <RpgStatsBar stats={stats} />
                  <button
                      type="button"
                      onClick={() => setTitleModalOpen(true)}
                      className="self-end text-xs font-bold text-amber-300 hover:underline"
                  >
                    🎖️ 칭호 변경
                  </button>
                </div>
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
                    const progress = chapterProgress.get(chapter.chapterId) ?? { total: 0, completed: 0 };
                    const percent = progress.total > 0
                        ? Math.round((progress.completed / progress.total) * 100)
                        : 0;
                    const cleared = progress.total > 0 && progress.completed === progress.total;
                    return (
                        <button
                            key={chapter.chapterId}
                            type="button"
                            disabled={chapter.locked}
                            onClick={() => setSelectedChapterId(chapter.chapterId)}
                            className={`shrink-0 min-w-[208px] rounded-2xl border px-4 py-3 text-left transition disabled:cursor-not-allowed disabled:opacity-60 ${
                                selected
                                    ? "border-cyan-400 bg-cyan-500/10"
                                    : "border-slate-800 bg-slate-900 hover:border-slate-700"
                            }`}
                        >
                          <p className="flex items-center gap-1.5 text-xs font-black text-white">
                            {cleared ? "🏆" : chapter.locked ? "🔒" : "🗺️"} {chapter.chapterName}
                          </p>
                          <p className="mt-1 text-[11px] text-slate-500">
                            {chapter.region ?? "미정"}
                            {chapter.locked ? ` · Lv.${chapter.requiredLevel} 필요` : ""}
                          </p>
                          <div className="mt-3 flex items-center justify-between text-[11px] font-bold text-slate-300">
                            <span>{progress.completed} / {progress.total}개 완료</span>
                            <span className={cleared ? "text-emerald-300" : "text-cyan-300"}>
                              {cleared ? "챕터 완주" : `${percent}%`}
                            </span>
                          </div>
                          <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-slate-700"
                               role="progressbar" aria-label={`${chapter.chapterName} 완료율`}
                               aria-valuenow={progress.completed} aria-valuemin={0}
                               aria-valuemax={progress.total}>
                            <div className={`h-full rounded-full transition-all ${cleared ? "bg-emerald-400" : "bg-cyan-400"}`}
                                 style={{ width: `${percent}%` }} />
                          </div>
                        </button>
                    );
                  })}
                </div>
              </section>
          )}

          {!loading && !error && (
              <section className="mt-6 rounded-3xl border border-cyan-500/20 bg-slate-900 p-5 md:p-6" aria-label="퀘스트 모험 진행도">
                <div className="flex flex-wrap items-center justify-between gap-4">
                  <div>
                    <p className="text-[11px] font-black tracking-[0.18em] text-cyan-400">ADVENTURE PROGRESS</p>
                    <h2 className="mt-1 text-lg font-black">나의 여행 모험</h2>
                    <p className="mt-2 text-xs text-slate-400">
                      {selectedChapterId == null ? "전체 모험" : "선택한 챕터"} {visibleQuests.length}개 중 {visibleCompletedCount}개 완료 ({visiblePercent}%) · 선행 미션을 완료하면 다음 도전이 열립니다.
                    </p>
                  </div>
                  {nextQuest && (
                      <Link to={`/quests/${nextQuest.questId}`}
                            className="rounded-xl bg-cyan-500 px-4 py-3 text-xs font-black text-slate-950 hover:bg-cyan-400">
                        다음 목표 · {nextQuest.questName} →
                      </Link>
                  )}
                </div>
                <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-800"
                     role="progressbar" aria-valuemin={0} aria-valuemax={visibleQuests.length}
                     aria-valuenow={visibleCompletedCount} aria-label="선택한 모험 퀘스트 완료율">
                  <div className={`h-full rounded-full transition-all ${visiblePercent === 100 && visibleQuests.length > 0 ? "bg-emerald-400" : "bg-cyan-400"}`}
                       style={{ width: `${visiblePercent}%` }} />
                </div>
                {visibleQuests.length > 0 && visibleCompletedCount === visibleQuests.length && (
                    <p className="mt-3 rounded-xl bg-emerald-500/10 px-4 py-3 text-sm font-bold text-emerald-300"
                       role="status">
                      {selectedChapterId == null ? "공개된 모든 퀘스트를 완주했습니다." : "선택한 챕터를 완주했습니다."}
                      보상은 각 퀘스트 완료 시 지급된 기록을 기준으로 합니다.
                    </p>
                )}
                {storyQuests.length > 0 && (
                    <div className="mt-5 flex gap-3 overflow-x-auto pb-2" aria-label="선행 퀘스트 모험 경로">
                      {storyQuests.map((item, index) => {
                        const complete = logByQuestId.get(item.questId)?.status === "SUCCESS";
                        const missing = item.missingPrerequisiteQuestIds ?? [];
                        return (
                            <div key={item.questId} className="flex shrink-0 items-center gap-3">
                              {index > 0 && <span aria-hidden="true" className="text-slate-500">
                                {item.prerequisiteQuestIds?.includes(storyQuests[index - 1].questId) ? "→" : "·"}
                              </span>}
                              <Link to={`/quests/${item.questId}`}
                                    className={`w-48 rounded-2xl border border-white/10 p-3 shadow-[0_14px_28px_-16px_rgba(34,211,238,0.6)] transition duration-300 hover:-translate-y-1 hover:bg-white/10 ${complete ? "bg-emerald-500/10" : item.locked ? "bg-slate-950/90 opacity-80" : "bg-cyan-500/10"}`}>
                                <span className="text-[10px] font-bold text-slate-400">
                                  {complete ? "완료" : missing.length ? `선행 ${missing.length}개 남음` : item.locked ? `Lv.${item.requiredLevel} 필요` : "도전 가능"}
                                </span>
                                <strong className="mt-1 block truncate text-sm text-white">{item.questName}</strong>
                                {missing.length > 0 && (
                                    <span className="mt-1 block truncate text-[11px] text-slate-400">
                                      선행: {missing.map((id) => questById.get(id)?.questName ?? `#${id}`).join(", ")}
                                    </span>
                                )}
                              </Link>
                            </div>
                        );
                      })}
                    </div>
                )}
              </section>
          )}

          {!loading && !error && (
              <QuestWorldMap
                  quests={visibleQuests}
                  logs={myLogs}
                  onQuestSelect={handleQuestSelect}
              />
          )}

          {!loading && !error && recentClears.length > 0 && (
              <section className="mt-7 rounded-3xl border border-emerald-400/20 bg-gradient-to-br from-slate-900 to-emerald-950/30 p-5 md:p-6"
                       aria-label="최근 완료한 퀘스트 기록">
                <div className="mb-4 flex flex-wrap items-end justify-between gap-2">
                  <div>
                    <p className="text-[11px] font-black tracking-[0.18em] text-emerald-300">QUEST JOURNAL</p>
                    <h2 className="mt-1 text-lg font-black">최근 완료한 모험</h2>
                  </div>
                  <span className="text-xs text-slate-400">실제 지급 기록 기준</span>
                </div>
                <div className="grid gap-3 md:grid-cols-2">
                  {recentClears.map((log) => (
                      <Link key={log.logId} to={`/quests/${log.questId}`}
                            className="rounded-2xl border border-white/10 bg-slate-950/60 p-4 transition hover:border-emerald-300/40 hover:bg-slate-900">
                        <p className="truncate text-sm font-black text-white">✓ {log.questName}</p>
                        <div className="mt-2 flex flex-wrap justify-between gap-2 text-xs text-emerald-300">
                          <span>{log.earnedPoint == null ? "포인트 기록 없음" : `+${log.earnedPoint.toLocaleString()} P`}</span>
                          <span>{log.earnedExp == null ? "경험치 기록 없음" : `+${log.earnedExp.toLocaleString()} EXP`}</span>
                        </div>
                        {log.clearDate && (
                            <time dateTime={log.clearDate} className="mt-2 block text-[11px] text-slate-500">
                              {log.clearDate.replace("T", " ").slice(0, 16)} 완료
                            </time>
                        )}
                      </Link>
                  ))}
                </div>
              </section>
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
                            className={`rounded-3xl border p-5 shadow-[0_18px_45px_-25px_rgba(34,211,238,0.28)] transition duration-300 hover:-translate-y-1 hover:shadow-[0_25px_60px_-22px_rgba(34,211,238,0.36)] ${
                                quest.locked
                                    ? "border-slate-700/50 bg-gradient-to-br from-slate-900 to-slate-950/90 opacity-80"
                                    : "border-slate-700/70 bg-gradient-to-br from-slate-900 via-slate-900 to-indigo-950/60 hover:border-cyan-400/60"
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
                            🔒 {quest.missingPrerequisiteQuestIds?.length
                                ? `선행 ${quest.missingPrerequisiteQuestIds.length}개`
                                : `Lv.${quest.requiredLevel}`}
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
                                ? (quest.missingPrerequisiteQuestIds?.length ? "선행 퀘스트 확인" : "레벨 확인하기")
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
        {titleModalOpen && (
            <TitleSelectModal onClose={() => setTitleModalOpen(false)} onChanged={setStats} />
        )}
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