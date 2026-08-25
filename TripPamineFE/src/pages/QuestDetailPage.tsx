import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getMyInfoApi } from "../api/auth";
import {
  extractQuestErrorMessage,
  questApi,
  type QuestResponse,
  type UserQuestLogResponse,
} from "../api/quest";
import Footer from "../components/Footer";
import Header from "../components/Header";
import { QuestMapView } from "../components/QuestMapView";
import { RealtimeQuestVerifier } from "../components/RealtimeQuestVerifier";
import Sidebar from "../components/Sidebar";

export default function QuestDetailPage() {
  const { questId: questIdParam } = useParams<{ questId: string }>();
  const questId = Number(questIdParam);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [quest, setQuest] = useState<QuestResponse | null>(null);
  const [questLog, setQuestLog] = useState<UserQuestLogResponse | null>(null);
  const [totalPoints, setTotalPoints] = useState(0);
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!Number.isInteger(questId) || questId <= 0) {
      setError("올바르지 않은 퀘스트 번호입니다.");
      setLoading(false);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);

    Promise.all([
      questApi.getQuest(questId),
      questApi.getMyLogs(),
      getMyInfoApi(),
    ])
      .then(([questResponse, logs, myInfo]) => {
        if (cancelled) return;
        setQuest(questResponse);
        setQuestLog(
          logs.find((log) => log.questId === questResponse.questId) ?? null,
        );
        setTotalPoints(myInfo.totalPoints);
      })
      .catch((err) => {
        console.error("퀘스트 상세 조회 실패:", err);
        if (!cancelled) {
          setError(
            extractQuestErrorMessage(
              err,
              "퀘스트 상세 정보를 불러오지 못했습니다.",
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
  }, [questId]);

  const handleStartQuest = async () => {
    if (!quest) return;

    try {
      setStarting(true);
      setError(null);
      const log = await questApi.startQuest(quest.questId);
      setQuestLog(log);
    } catch (err) {
      setError(extractQuestErrorMessage(err, "퀘스트를 시작하지 못했습니다."));
    } finally {
      setStarting(false);
    }
  };

  const handleLogUpdate = async (log: UserQuestLogResponse) => {
    setQuestLog(log);

    if (log.status === "SUCCESS") {
      try {
        const myInfo = await getMyInfoApi();
        setTotalPoints(myInfo.totalPoints);
      } catch (err) {
        console.error("퀘스트 완료 후 포인트 갱신 실패:", err);
      }
    }
  };

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
        <div className="mx-auto max-w-6xl px-4 py-8">
          <Link
            to="/quests"
            className="inline-flex items-center gap-2 text-sm font-bold text-slate-400 transition hover:text-cyan-400"
          >
            ← 퀘스트 목록
          </Link>

          {loading ? (
            <div className="mt-6 h-[520px] animate-pulse rounded-3xl border border-slate-800 bg-slate-900" />
          ) : error && !quest ? (
            <div className="mt-6 rounded-3xl border border-rose-500/30 bg-rose-500/10 p-10 text-center text-sm text-rose-300">
              {error}
            </div>
          ) : quest ? (
            <div className="mt-6 space-y-6">
              <section className="rounded-3xl border border-cyan-500/20 bg-gradient-to-br from-slate-900 to-cyan-950 p-6 md:p-8">
                <div className="flex flex-wrap items-start justify-between gap-5">
                  <div>
                    <span className="rounded-full border border-cyan-500/30 bg-cyan-500/10 px-3 py-1 text-xs font-black text-cyan-300">
                      LOCATION QUEST
                    </span>
                    <h1 className="mt-4 text-3xl font-black">
                      {quest.questName}
                    </h1>
                    <p className="mt-2 max-w-2xl text-sm leading-relaxed text-slate-400">
                      목표 지점 반경 {quest.clearRadius}m 이내에서 GPS 위치
                      인증을 완료하세요.
                    </p>
                  </div>
                  <div className="rounded-2xl border border-amber-400/20 bg-amber-400/10 px-5 py-4 text-right">
                    <span className="block text-xs font-bold text-amber-300">
                      QUEST REWARD
                    </span>
                    <strong className="mt-1 block text-2xl font-black text-amber-400">
                      +{quest.rewardPoint.toLocaleString()} P
                    </strong>
                    <span className="mt-1 block text-xs text-slate-400">
                      현재 {totalPoints.toLocaleString()} P
                    </span>
                  </div>
                </div>
              </section>

              {error && (
                <div className="rounded-2xl border border-rose-500/30 bg-rose-500/10 p-4 text-sm text-rose-300">
                  {error}
                </div>
              )}

              {!questLog ? (
                <section className="rounded-3xl border border-slate-800 bg-slate-900 p-6 text-center md:p-10">
                  <p className="text-xs font-black tracking-[0.2em] text-cyan-400">
                    MISSION READY
                  </p>
                  <h2 className="mt-3 text-2xl font-black">
                    이 퀘스트를 수락하시겠습니까?
                  </h2>
                  <p className="mx-auto mt-3 max-w-xl text-sm leading-relaxed text-slate-400">
                    수락한 뒤부터 지도와 실시간 GPS 인증 화면이 활성화됩니다.
                  </p>
                  <button
                    type="button"
                    onClick={handleStartQuest}
                    disabled={starting}
                    className="mt-6 w-full max-w-md rounded-2xl bg-gradient-to-r from-cyan-500 to-blue-600 py-4 text-sm font-black transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {starting ? "퀘스트 수락 처리 중..." : "퀘스트 수락"}
                  </button>
                </section>
              ) : (
                <section className="grid gap-6 lg:grid-cols-3">
                  <div className="h-[480px] overflow-hidden rounded-3xl border border-slate-800 bg-slate-900 shadow-xl lg:col-span-2">
                    <QuestMapView quest={quest} />
                  </div>
                  <div className="flex flex-col gap-4">
                    <div className="rounded-2xl border border-slate-800 bg-slate-900 p-4 text-xs text-slate-400">
                      <p>
                        상태:{" "}
                        <strong className="text-cyan-300">
                          {questLog.status}
                        </strong>
                      </p>
                      <p className="mt-2">
                        목표 좌표: {quest.targetLat.toFixed(5)},{" "}
                        {quest.targetLng.toFixed(5)}
                      </p>
                    </div>
                    <RealtimeQuestVerifier
                      quest={quest}
                      initialStatus={questLog.status}
                      onLogUpdate={handleLogUpdate}
                    />
                  </div>
                </section>
              )}
            </div>
          ) : null}
        </div>
        <Footer />
      </main>
    </div>
  );
}
