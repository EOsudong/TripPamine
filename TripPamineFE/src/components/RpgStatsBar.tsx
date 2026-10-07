import type { RpgStatsResponse } from "../api/quest";

// 레벨 + EXP 진행바 + 현재 칭호를 보여주는 재사용 컴포넌트.
// QuestPage 헤더(큰 버전)와 QuestDetailPage 헤더(컴팩트 버전)에서 함께 쓴다.
interface RpgStatsBarProps {
  stats: RpgStatsResponse | null;
  compact?: boolean;
}

export default function RpgStatsBar({ stats, compact = false }: RpgStatsBarProps) {
  const level = stats?.level ?? 1;
  const exp = stats?.exp ?? 0;
  const expToNextLevel = stats?.expToNextLevel ?? 100;
  const progressPercent = Math.min(
    100,
    Math.round((exp / Math.max(expToNextLevel, 1)) * 100),
  );

  if (compact) {
    return (
      <div className="flex items-center gap-3 rounded-xl border border-white/10 bg-white/5 px-4 py-2.5">
        <span className="shrink-0 rounded-lg bg-cyan-500/20 px-2 py-1 text-xs font-black text-cyan-300">
          Lv.{level}
        </span>
        <div className="min-w-0 flex-1">
          <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-800">
            <div
              className="h-full rounded-full bg-gradient-to-r from-cyan-400 to-emerald-400 transition-all"
              style={{ width: `${progressPercent}%` }}
            />
          </div>
        </div>
        <span className="shrink-0 text-[10px] font-bold text-slate-400">
          {exp}/{expToNextLevel}
        </span>
      </div>
    );
  }

  return (
    <div className="min-w-[220px] rounded-2xl border border-white/10 bg-white/5 px-5 py-4">
      <div className="flex items-center justify-between gap-3">
        <span className="flex items-center gap-2 text-sm font-black text-white">
          <span className="rounded-lg bg-cyan-500/20 px-2 py-1 text-xs text-cyan-300">
            Lv.{level}
          </span>
          {stats?.currentTitleName && (
            <span className="truncate text-xs font-bold text-amber-300">
              {stats.currentTitleName}
            </span>
          )}
        </span>
        <span className="shrink-0 text-[10px] font-bold text-slate-400">
          {exp} / {expToNextLevel} XP
        </span>
      </div>
      <div className="mt-2 h-2 w-full overflow-hidden rounded-full bg-slate-800">
        <div
          className="h-full rounded-full bg-gradient-to-r from-cyan-400 to-emerald-400 transition-all"
          style={{ width: `${progressPercent}%` }}
        />
      </div>
    </div>
  );
}
