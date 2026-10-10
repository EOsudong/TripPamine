import {
    useEffect,
    useRef,
    type ReactNode,
} from "react";

import { Link } from "react-router-dom";

import {
    animate,
    createScope,
    createTimeline,
    stagger,
    type Scope,
} from "animejs";

import BrandMark from "./BrandMark";


/**
 * 로그인 / 회원가입 / 계정 찾기 화면 공통 레이아웃.
 *
 * - 좌측(데스크톱): 오로라 배경 + AI 여행 무드 + Anime.js 플로팅 카드
 * - 우측: 실제 폼(children)
 *
 * 폼 로직은 각 페이지가 그대로 가지고 있고,
 * 이 컴포넌트는 화면 껍데기만 담당한다.
 */
const FLOATING_CHIPS = [
    { icon: "🤖", title: "AI 매니저", desc: "오늘 확인할 일 3건을 정리했어요", pos: "left-[8%] top-[18%]" },
    { icon: "📊", title: "자산 리포트", desc: "이번 달 식비가 예산의 80%예요", pos: "right-[6%] top-[38%]" },
    { icon: "🏆", title: "퀘스트 클리어", desc: "+120 XP · 레벨 업!", pos: "left-[22%] top-[48%]" },
];

export default function AuthShell({
                                      children,
                                      title,
                                      subtitle,
                                  }: {
    children: ReactNode;
    title?: ReactNode;
    subtitle?: ReactNode;
}) {

    const rootRef =
        useRef<HTMLDivElement>(null);

    const scope =
        useRef<Scope | null>(null);

    useEffect(() => {

        if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
            return;
        }

        scope.current = createScope({ root: rootRef }).add(() => {

            createTimeline({ defaults: { ease: "out(4)" } })
                .add(".auth-copy", {
                    opacity: [0, 1],
                    y: [24, 0],
                    duration: 800,
                    delay: stagger(110),
                })
                .add(".auth-chip", {
                    opacity: [0, 1],
                    scale: [0.85, 1],
                    duration: 700,
                    delay: stagger(160),
                }, "-=500")
                .add(".auth-card", {
                    opacity: [0, 1],
                    y: [30, 0],
                    duration: 800,
                }, 0);

            animate(".auth-chip", {
                y: [-8, 8],
                duration: 2600,
                delay: stagger(400),
                ease: "inOutSine",
                loop: true,
                alternate: true,
            });

            animate(".auth-ring", {
                rotate: 360,
                duration: 40000,
                ease: "linear",
                loop: true,
            });
        });

        return () => scope.current?.revert();

    }, []);

    return (
        <div
            ref={rootRef}
            className="grid min-h-screen grid-cols-1 bg-night-950 lg:grid-cols-[1.05fr_1fr]"
        >

            {/* =========== Visual Panel =========== */}
            <aside className="relative hidden overflow-hidden text-white lg:block">
                <div className="absolute inset-0 tp-aurora" />
                <div className="absolute inset-0 tp-dot-grid opacity-40" />

                <div className="auth-ring absolute left-1/2 top-1/2 h-[560px] w-[560px] -translate-x-1/2 -translate-y-1/2 rounded-full border border-dashed border-white/10" />
                <div className="absolute left-1/2 top-1/2 h-[360px] w-[360px] -translate-x-1/2 -translate-y-1/2 rounded-full border border-white/5" />

                {FLOATING_CHIPS.map((chip) => (
                    <div
                        key={chip.title}
                        className={`auth-chip absolute ${chip.pos} flex items-center gap-3 rounded-2xl tp-glass-dark px-4 py-3 shadow-2xl`}
                    >
                        <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/10 text-xl">
                            {chip.icon}
                        </span>
                        <div>
                            <p className="text-sm font-bold">{chip.title}</p>
                            <p className="text-xs text-white/55">{chip.desc}</p>
                        </div>
                    </div>
                ))}

                <div className="relative flex h-full flex-col justify-between p-12">
                    <Link to="/" className="auth-copy flex items-center gap-2.5">
                        <BrandMark />
                        <span className="font-display text-xl font-bold">TripPamine</span>
                    </Link>

                    <div>
                        <p className="auth-copy text-[11px] font-black tracking-[0.22em] text-cyan-300">
                            AI HYPER-PERSONALIZATION PLATFORM
                        </p>
                        <h1 className="auth-copy mt-4 font-display text-5xl font-bold leading-[1.05] tracking-tight">
                            오직 나에게 맞춘<br />
                            <span className="tp-gradient-text">AI 맞춤 솔루션</span>
                        </h1>
                        <p className="auth-copy mt-5 max-w-sm text-sm leading-relaxed text-white/60">
                            여행 코스부터 자산 관리, 일상의 퀘스트까지. AI가 당신의 패턴을 이해하고 다음 선택을 먼저 제안해요.
                        </p>
                    </div>
                </div>
            </aside>


            {/* =========== Form Panel =========== */}
            <main className="relative flex items-center justify-center bg-[#f6f6fb] px-4 py-10 lg:rounded-l-[2.5rem]">
                <div className="pointer-events-none absolute inset-0 tp-aurora-light lg:rounded-l-[2.5rem]" />

                <div className="auth-card relative w-full max-w-sm">
                    {/* 모바일 브랜드 */}
                    <Link to="/" className="mb-6 flex items-center gap-2.5 lg:hidden">
                        <BrandMark />
                        <span className="font-display text-xl font-bold text-slate-950">TripPamine</span>
                    </Link>

                    {(title || subtitle) && (
                        <div className="mb-6">
                            {title && (
                                <h2 className="font-display text-3xl font-bold tracking-tight text-slate-950">
                                    {title}
                                </h2>
                            )}
                            {subtitle && (
                                <p className="mt-1.5 text-sm text-slate-500">{subtitle}</p>
                            )}
                        </div>
                    )}

                    <div className="rounded-3xl border border-white bg-white/80 p-6 shadow-[0_30px_80px_-40px_rgba(60,40,180,0.45)] backdrop-blur-xl">
                        {children}
                    </div>
                </div>
            </main>
        </div>
    );
}
