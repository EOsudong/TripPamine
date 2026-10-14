import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import AppLayout from "../layout/AppLayout";
import {
  MASCOT_RARITY_COLOR,
  extractMascotErrorMessage,
  mascotApi,
  resolveMascotImageUrl,
  type MascotDexEntry,
  type MascotDexResponse,
} from "../api/mascot";

// 마스코트 도감 (/mascots/dex).
// GET /mascots/dex 응답을 지역별로 보여준다. 아직 못 잡은 마스코트는 실루엣 + "???"로 표시하고,
// 잡은 마스코트는 포획 횟수와 첫 포획일을 보여준다.

function formatDate(value: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")}`;
}

export default function MascotDexPage() {
  const [dex, setDex] = useState<MascotDexResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<{ entry: MascotDexEntry; regionName: string } | null>(null);

  useEffect(() => {
    let cancelled = false;
    mascotApi
      .getDex()
      .then((response) => {
        if (!cancelled) setDex(response);
      })
      .catch((err) => {
        if (!cancelled) setError(extractMascotErrorMessage(err, "도감을 불러오지 못했어요."));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <AppLayout pageClassName="bg-night-950 text-white">
      <div className="mx-auto max-w-6xl px-4 py-10">
        <section className="relative isolate overflow-hidden rounded-[2rem] border border-white/10 p-6 md:p-8">
          <div className="absolute inset-0 -z-10 tp-aurora" />
          <div className="absolute inset-0 -z-10 tp-dot-grid opacity-40" />
          <Link to="/mascots" className="text-xs font-bold text-cyan-300 hover:underline">
            ← 마스코트 탐험으로
          </Link>
          <h1 className="mt-3 font-display text-3xl font-bold tracking-tight md:text-5xl">마스코트 도감</h1>

          {dex && (
            <div className="mt-6 max-w-xl">
              <div className="flex items-end justify-between">
                <p className="text-sm font-semibold text-white/60">전체 수집률</p>
                <p className="text-2xl font-black">
                  {dex.collectedCount}
                  <span className="text-base text-white/40"> / {dex.totalCount}</span>
                  <span className="ml-2 text-base text-cyan-300">{dex.completionRate}%</span>
                </p>
              </div>
              <div className="mt-2 h-3 overflow-hidden rounded-full bg-white/10">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-cyan-400 to-fuchsia-400 transition-all"
                  style={{ width: `${dex.completionRate}%` }}
                />
              </div>
            </div>
          )}
        </section>

        {loading && (
          <div className="mt-8 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
            {Array.from({ length: 10 }).map((_, index) => (
              <div key={index} className="h-48 animate-pulse rounded-3xl bg-white/5" />
            ))}
          </div>
        )}

        {error && (
          <div className="mt-6 rounded-2xl border border-rose-400/30 bg-rose-500/10 px-5 py-4 text-sm text-rose-200">{error}</div>
        )}

        {dex && dex.regions.length === 0 && (
          <div className="mt-8 rounded-3xl border border-dashed border-white/15 px-6 py-16 text-center text-sm text-white/50">
            아직 등록된 마스코트가 없어요.
          </div>
        )}

        {dex?.regions.map((region) => {
          const rate = region.totalCount === 0 ? 0 : Math.round((region.collectedCount / region.totalCount) * 100);
          const complete = region.totalCount > 0 && region.collectedCount === region.totalCount;
          return (
            <section key={region.regionId} className="mt-10">
              <div className="flex flex-wrap items-end justify-between gap-3">
                <h2 className="text-xl font-black">
                  {region.regionName}
                  {complete && <span className="ml-2 rounded-full bg-yellow-300/20 px-2 py-0.5 text-xs text-yellow-200">COMPLETE</span>}
                </h2>
                <p className="text-sm font-bold text-white/60">
                  {region.collectedCount} / {region.totalCount} · {rate}%
                </p>
              </div>
              <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-white/10">
                <div className="h-full rounded-full bg-cyan-400" style={{ width: `${rate}%` }} />
              </div>

              <div className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
                {region.mascots.map((entry) => (
                  <DexCard
                    key={entry.mascotId}
                    entry={entry}
                    onClick={() => setSelected({ entry, regionName: region.regionName })}
                  />
                ))}
              </div>
            </section>
          );
        })}
      </div>

      {selected && (
        <DexDetailModal entry={selected.entry} regionName={selected.regionName} onClose={() => setSelected(null)} />
      )}
    </AppLayout>
  );
}

function MascotImage({ entry, className }: { entry: MascotDexEntry; className: string }) {
  const [broken, setBroken] = useState(false);
  const url = resolveMascotImageUrl(entry.imageUrl);
  const color = MASCOT_RARITY_COLOR[entry.rarity];

  if (!url || broken) {
    return (
      <div className={`${className} flex items-center justify-center text-5xl`} style={{ color: entry.collected ? color : "#334155" }}>
        {entry.collected ? "✦" : "?"}
      </div>
    );
  }

  return (
    <img
      src={url}
      alt={entry.collected ? entry.mascotName : "아직 만나지 못한 마스코트"}
      onError={() => setBroken(true)}
      draggable={false}
      className={`${className} object-contain`}
      // 미수집 마스코트는 실루엣으로
      style={entry.collected ? undefined : { filter: "brightness(0)", opacity: 0.35 }}
    />
  );
}

function DexCard({ entry, onClick }: { entry: MascotDexEntry; onClick: () => void }) {
  const color = MASCOT_RARITY_COLOR[entry.rarity];
  return (
    <button
      type="button"
      onClick={onClick}
      className="group relative flex flex-col items-center rounded-3xl border bg-white/5 p-4 text-center transition hover:-translate-y-0.5 hover:bg-white/10"
      style={{ borderColor: entry.collected ? `${color}66` : "rgba(255,255,255,0.08)" }}
    >
      <span className="absolute left-3 top-3 font-mono text-[10px] text-white/30">No.{String(entry.mascotId).padStart(3, "0")}</span>
      {entry.collected && entry.catchCount > 1 && (
        <span className="absolute right-3 top-3 rounded-full bg-white/10 px-1.5 text-[10px] font-bold text-white/70">×{entry.catchCount}</span>
      )}
      <MascotImage entry={entry} className="mt-3 h-24 w-24" />
      <p className={`mt-3 w-full truncate text-sm font-black ${entry.collected ? "" : "text-white/30"}`}>
        {entry.collected ? entry.mascotName : "???"}
      </p>
      <span
        className="mt-1 rounded-full px-2 py-0.5 text-[10px] font-black"
        style={{ background: `${color}22`, color: entry.collected ? color : "rgba(255,255,255,0.3)" }}
      >
        {entry.rarityLabel}
      </span>
    </button>
  );
}

function DexDetailModal({ entry, regionName, onClose }: { entry: MascotDexEntry; regionName: string; onClose: () => void }) {
  const color = MASCOT_RARITY_COLOR[entry.rarity];
  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/70 p-4" onClick={onClose}>
      <div
        className="w-full max-w-sm overflow-hidden rounded-[2rem] border border-white/10 bg-night-950 text-white shadow-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div
          className="flex h-56 items-center justify-center"
          style={{ background: `radial-gradient(circle at 50% 45%, ${color}40, transparent 65%), #0b0b16` }}
        >
          <MascotImage entry={entry} className="h-40 w-40" />
        </div>
        <div className="p-6">
          <span className="rounded-full px-2 py-0.5 text-[10px] font-black" style={{ background: `${color}33`, color }}>
            {entry.rarityLabel}
          </span>
          <h3 className="mt-2 text-2xl font-black">{entry.collected ? entry.mascotName : "아직 만나지 못했어요"}</h3>
          <p className="text-sm text-white/50">{regionName}</p>

          <dl className="mt-5 grid grid-cols-2 gap-3 text-sm">
            <div className="rounded-2xl bg-white/5 p-3">
              <dt className="text-xs text-white/40">포획 횟수</dt>
              <dd className="mt-0.5 font-black">{entry.catchCount}회</dd>
            </div>
            <div className="rounded-2xl bg-white/5 p-3">
              <dt className="text-xs text-white/40">첫 포획 보상</dt>
              <dd className="mt-0.5 font-black">{entry.rewardPoint.toLocaleString()}P</dd>
            </div>
            <div className="rounded-2xl bg-white/5 p-3">
              <dt className="text-xs text-white/40">첫 포획일</dt>
              <dd className="mt-0.5 font-black">{formatDate(entry.firstCaughtAt)}</dd>
            </div>
            <div className="rounded-2xl bg-white/5 p-3">
              <dt className="text-xs text-white/40">최근 포획일</dt>
              <dd className="mt-0.5 font-black">{formatDate(entry.lastCaughtAt)}</dd>
            </div>
          </dl>

          {!entry.collected && (
            <p className="mt-4 text-xs text-white/50">{regionName} 근처의 스폰 지점에서 만날 수 있어요.</p>
          )}

          <div className="mt-5 flex gap-2">
            {!entry.collected && (
              <Link
                to="/mascots"
                className="flex-1 rounded-2xl bg-cyan-400 py-3 text-center text-sm font-black text-night-950 hover:bg-cyan-300"
              >
                찾으러 가기
              </Link>
            )}
            <button type="button" onClick={onClose} className="flex-1 rounded-2xl bg-white/10 py-3 text-sm font-bold hover:bg-white/15">
              닫기
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
