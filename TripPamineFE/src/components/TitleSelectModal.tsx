import { useEffect, useState } from "react";
import { notifyUserUpdated } from "../api/auth";
import {
  describeTitleCondition,
  extractQuestErrorMessage,
  questApi,
  type RpgStatsResponse,
  type TitleResponse,
} from "../api/quest";

// [칭호 변경] 모달.
// 전체 칭호를 보여주고, 획득한 칭호는 눌러서 장착 / 장착 중인 칭호는 해제할 수 있다.
// 아직 못 얻은 칭호는 잠금 상태로 획득 조건만 보여준다.
// 바뀐 RPG 스탯을 onChanged로 넘겨서 부모 화면(레벨바 등)이 바로 갱신되게 한다.

interface TitleSelectModalProps {
  onClose: () => void;
  onChanged: (stats: RpgStatsResponse) => void;
}

function formatDate(value: string | null) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")} 획득`;
}

export default function TitleSelectModal({ onClose, onChanged }: TitleSelectModalProps) {
  const [titles, setTitles] = useState<TitleResponse[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | "unequip" | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    questApi
      .getMyTitles()
      .then((list) => {
        if (!cancelled) setTitles(list);
      })
      .catch((err) => {
        if (!cancelled) setLoadError(extractQuestErrorMessage(err, "칭호 목록을 불러오지 못했어요."));
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const applyResult = (stats: RpgStatsResponse) => {
    setTitles((prev) => prev?.map((t) => ({ ...t, equipped: t.titleId === stats.currentTitleId })) ?? prev);
    onChanged(stats);
    notifyUserUpdated();
  };

  const handleEquip = async (title: TitleResponse) => {
    if (!title.earned || title.equipped || busyId !== null) return;
    setBusyId(title.titleId);
    setActionError(null);
    try {
      applyResult(await questApi.equipTitle(title.titleId));
    } catch (err) {
      setActionError(extractQuestErrorMessage(err, "칭호를 장착하지 못했어요."));
    } finally {
      setBusyId(null);
    }
  };

  const handleUnequip = async () => {
    if (busyId !== null) return;
    setBusyId("unequip");
    setActionError(null);
    try {
      applyResult(await questApi.unequipTitle());
    } catch (err) {
      setActionError(extractQuestErrorMessage(err, "칭호를 해제하지 못했어요."));
    } finally {
      setBusyId(null);
    }
  };

  // 획득한 칭호를 먼저, 그 안에서는 원래 순서 유지
  const sorted = titles ? [...titles].sort((a, b) => Number(b.earned) - Number(a.earned)) : null;
  const earnedCount = titles?.filter((t) => t.earned).length ?? 0;
  const equipped = titles?.find((t) => t.equipped) ?? null;

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/70 p-4" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="title-modal-title"
        className="flex max-h-[85vh] w-full max-w-lg flex-col overflow-hidden rounded-[2rem] border border-white/10 bg-night-950 text-white shadow-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="border-b border-white/10 p-6 pb-4">
          <p className="text-[10px] font-black tracking-[0.22em] text-amber-300">TITLES</p>
          <div className="mt-1 flex items-end justify-between gap-3">
            <h2 id="title-modal-title" className="font-display text-2xl font-bold">칭호 변경</h2>
            {titles && (
              <span className="text-sm font-bold text-white/50">
                {earnedCount} / {titles.length} 획득
              </span>
            )}
          </div>
          <p className="mt-1 text-sm text-white/50">
            {equipped ? (
              <>
                지금 장착: <span className="font-bold text-amber-300">{equipped.titleName}</span>
              </>
            ) : (
              "장착한 칭호가 없어요."
            )}
          </p>
        </div>

        <div className="flex-1 overflow-y-auto p-4">
          {!titles && !loadError &&
            Array.from({ length: 4 }).map((_, i) => <div key={i} className="mb-2 h-16 animate-pulse rounded-2xl bg-white/5" />)}

          {loadError && (
            <div className="rounded-2xl border border-rose-400/30 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">{loadError}</div>
          )}

          {sorted && sorted.length === 0 && (
            <p className="py-10 text-center text-sm text-white/50">아직 등록된 칭호가 없어요.</p>
          )}

          <ul className="space-y-2">
            {sorted?.map((title) => {
              const busy = busyId === title.titleId;
              return (
                <li key={title.titleId}>
                  <button
                    type="button"
                    disabled={!title.earned || title.equipped || busyId !== null}
                    onClick={() => void handleEquip(title)}
                    className={`flex w-full items-center gap-3 rounded-2xl border px-4 py-3 text-left transition ${
                      title.equipped
                        ? "border-amber-300/60 bg-amber-300/10"
                        : title.earned
                          ? "border-white/10 bg-white/5 hover:border-cyan-400/50 hover:bg-white/10"
                          : "cursor-not-allowed border-white/5 bg-white/[0.02] opacity-60"
                    }`}
                  >
                    <span
                      className={`flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-xl text-lg ${
                        title.earned ? "bg-amber-300/15" : "bg-white/5"
                      }`}
                    >
                      {title.earned ? (
                        title.iconUrl ? (
                          <img src={title.iconUrl} alt="" className="h-full w-full object-cover" />
                        ) : (
                          "🎖️"
                        )
                      ) : (
                        "🔒"
                      )}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className={`block truncate text-sm font-black ${title.earned ? "" : "text-white/50"}`}>
                        {title.titleName}
                      </span>
                      <span className="block truncate text-xs text-white/45">
                        {title.earned
                          ? title.description || formatDate(title.earnedAt)
                          : describeTitleCondition(title)}
                      </span>
                    </span>
                    <span className="shrink-0 text-xs font-black">
                      {title.equipped ? (
                        <span className="rounded-full bg-amber-300 px-2.5 py-1 text-night-950">장착 중</span>
                      ) : busy ? (
                        <span className="text-cyan-300">장착 중…</span>
                      ) : title.earned ? (
                        <span className="text-cyan-300">장착</span>
                      ) : (
                        <span className="text-white/30">미획득</span>
                      )}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        </div>

        <div className="border-t border-white/10 p-4">
          {actionError && (
            <div className="mb-3 rounded-xl border border-rose-400/30 bg-rose-500/10 px-4 py-2.5 text-sm text-rose-200">{actionError}</div>
          )}
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => void handleUnequip()}
              disabled={!equipped || busyId !== null}
              className="flex-1 rounded-2xl border border-white/15 py-3 text-sm font-bold hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-40"
            >
              {busyId === "unequip" ? "해제 중…" : "칭호 해제"}
            </button>
            <button type="button" onClick={onClose} className="flex-1 rounded-2xl bg-white/10 py-3 text-sm font-bold hover:bg-white/15">
              닫기
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
