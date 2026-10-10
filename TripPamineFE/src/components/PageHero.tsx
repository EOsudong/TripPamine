import type { ReactNode } from "react";


/**
 * 페이지 상단 공통 히어로 배너.
 *
 * 다크 오로라 배경 + 점 그리드 + 그라데이션 타이틀로
 * "AI 여행 · 엔터테인먼트 플랫폼" 톤을 모든 페이지에서 통일한다.
 *
 * 페이지별 로직과는 무관한 순수 UI 컴포넌트.
 */
type Tone =
    | "violet"
    | "cyan"
    | "pink"
    | "amber"
    | "emerald";

const TONE_GLOW: Record<Tone, string> = {
    violet: "from-violet-500/40 via-indigo-500/10",
    cyan: "from-cyan-400/35 via-sky-500/10",
    pink: "from-fuchsia-500/35 via-pink-500/10",
    amber: "from-amber-400/35 via-orange-500/10",
    emerald: "from-emerald-400/35 via-teal-500/10",
};

const TONE_EYEBROW: Record<Tone, string> = {
    violet: "text-violet-200",
    cyan: "text-cyan-200",
    pink: "text-pink-200",
    amber: "text-amber-200",
    emerald: "text-emerald-200",
};

interface PageHeroProps {
    eyebrow?: ReactNode;
    title: ReactNode;
    description?: ReactNode;
    icon?: ReactNode;
    tone?: Tone;
    /** 타이틀 아래(또는 우측)에 붙일 액션/필터 영역 */
    children?: ReactNode;
    /** 우측 영역 (데스크톱에서 타이틀 옆에 배치) */
    aside?: ReactNode;
    className?: string;
}

export default function PageHero({
                                     eyebrow,
                                     title,
                                     description,
                                     icon,
                                     tone = "violet",
                                     children,
                                     aside,
                                     className = "",
                                 }: PageHeroProps) {
    return (
        <section
            className={`
        relative isolate overflow-hidden
        rounded-[2rem] bg-night-950
        px-6 py-10 text-white
        shadow-[0_40px_80px_-50px_rgba(60,40,180,0.8)]
        md:px-10 md:py-12
        ${className}
      `}
        >
            {/* 배경 레이어 */}
            <div className="absolute inset-0 -z-10 tp-aurora opacity-90" />
            <div className={`absolute -right-20 -top-20 -z-10 h-80 w-80 rounded-full bg-gradient-to-br ${TONE_GLOW[tone]} to-transparent blur-3xl`} />
            <div className="absolute inset-0 -z-10 tp-dot-grid opacity-40 [mask-image:linear-gradient(90deg,transparent,black_40%,black)]" />

            <div className="flex flex-col gap-8 lg:flex-row lg:items-end lg:justify-between">
                <div className="min-w-0">
                    {eyebrow && (
                        <span
                            className={`
                inline-flex items-center gap-2
                rounded-full border border-white/15 bg-white/5
                px-3 py-1 text-[10px] font-black uppercase tracking-[0.2em]
                ${TONE_EYEBROW[tone]}
              `}
                        >
                            <span className="h-1.5 w-1.5 rounded-full bg-cyan-300 shadow-[0_0_8px_rgba(103,232,249,0.9)]" />
                            {eyebrow}
                        </span>
                    )}

                    <h1 className="mt-4 flex items-center gap-3 font-display text-3xl font-bold leading-tight tracking-tight md:text-5xl">
                        {icon && (
                            <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl border border-white/10 bg-white/10 text-2xl md:h-14 md:w-14 md:text-3xl">
                                {icon}
                            </span>
                        )}
                        <span className="min-w-0">{title}</span>
                    </h1>

                    {description && (
                        <p className="mt-4 max-w-2xl text-sm leading-relaxed text-white/60 md:text-base">
                            {description}
                        </p>
                    )}
                </div>

                {aside && (
                    <div className="shrink-0">{aside}</div>
                )}
            </div>

            {children && (
                <div className="mt-8">{children}</div>
            )}
        </section>
    );
}
