import {
    useEffect,
    useRef,
    useState,
} from "react";

import {
    useNavigate,
} from "react-router-dom";

import {
    animate,
    createAnimatable,
    createScope,
    createTimeline,
    createTimer,
    onScroll,
    stagger,
    utils,
    type Scope,
} from "animejs";

import {
    ArrowRight,
    Bot,
    ChartNoAxesCombined,
    Gift,
    MessagesSquare,
    Sparkles,
    Trophy,
} from "lucide-react";

import BrandMark from "../../components/BrandMark";

import {
    POINT_COLORS,
    POINT_COUNT,
    SHAPES,
} from "./shapes";


/**
 * =========================================================
 * TripPamine Landing (비로그인 메인)
 * =========================================================
 *
 * 컨셉: "AI 기반 초개인화 맞춤 솔루션 플랫폼"
 *
 * animejs.com 랜딩처럼 화면 중앙의 3D 형상이
 * 마우스 휠(스크롤)에 따라 회전 · 변신하고,
 * 단계마다 플랫폼 설명과 기능이 하나씩 등장한다.
 *
 * - 3D 형상 : 216개 파티클을 직접 원근 투영해서 canvas에 그린다.
 * - 스크롤  : onScroll + createTimeline(sync) 으로
 *            형상 morph 값 / 회전 / 텍스트 패널을 스크롤에 동기화.
 * - 렌더    : createTimer 의 onUpdate 로 매 프레임 그리기.
 * - 마우스  : createAnimatable 로 부드러운 패럴랙스 기울기.
 */

interface Step {
    kicker: string;
    title: string[];
    desc: string;
    tags?: string[];
    label: string;
}

const STEPS: Step[] = [
    {
        label: "Intro",
        kicker: "AI HYPER-PERSONALIZATION PLATFORM",
        title: ["오직 당신을 위해", "설계되는 AI 솔루션"],
        desc: "TripPamine은 여행 · 자산 · 일상의 데이터를 하나로 연결해, 지금 당신에게 가장 필요한 선택을 AI가 먼저 제안하는 초개인화 맞춤 솔루션 플랫폼입니다.",
    },
    {
        label: "Personal AI",
        kicker: "01 · PERSONAL AI",
        title: ["AI가 당신의", "패턴을 이해합니다"],
        desc: "취향, 소비 습관, 이동 기록, 그날의 감정까지. 흩어져 있던 데이터를 학습해 나만의 라이프 DNA를 만들어요.",
        tags: ["취향 분석", "소비 패턴", "행동 로그", "감정 기록"],
    },
    {
        label: "Travel",
        kicker: "02 · TRAVEL",
        title: ["여행은", "AI가 설계하고"],
        desc: "예산 · 동행 · 날씨를 반영한 AI 코스 추천, 목적지를 숨겨 둔 미스터리 투어, 전국 축제와 관광 정보까지.",
        tags: ["AI 코스 추천", "미스터리 투어", "축제 · 관광지"],
    },
    {
        label: "Asset",
        kicker: "03 · ASSET",
        title: ["자산은", "AI가 관리하고"],
        desc: "계좌 연동 가계부와 영수증 OCR, 자산 흐름 리포트, 예산 · 목표 관리, 맞춤 금융상품 추천을 한 화면에서.",
        tags: ["자산 대시보드", "가계부 · OCR", "금융상품 추천"],
    },
    {
        label: "Play",
        kicker: "04 · PLAY & COMMUNITY",
        title: ["일상은", "게임처럼 즐겁게"],
        desc: "실제 장소에서 퀘스트를 인증하고 경험치를 쌓아요. 커뮤니티에서 경험을 나누면 목표 달성이 놀이가 됩니다.",
        tags: ["위치 퀘스트", "레벨 · 포인트", "커뮤니티"],
    },
    {
        label: "Start",
        kicker: "05 · ONE PLATFORM",
        title: ["하나의 AI,", "당신만의 솔루션"],
        desc: "가입 1분이면 AI 매니저가 당신의 여행과 자산, 일상을 함께 관리하기 시작해요.",
    },
];

/** 단계별 카메라 기울기 (rad) */
const STEP_TILT = [-0.35, -0.12, -0.95, -0.55, -0.5, -1.05];

/** 단계별 확대율 */
const STEP_ZOOM = [1, 0.92, 1.02, 0.95, 0.98, 1.08];

const SEGMENT = 1000;

const FEATURES = [
    { icon: Bot, title: "AI 매니저", desc: "오늘 확인할 일을 먼저 정리해 주는 개인 비서", path: "/login" },
    { icon: Sparkles, title: "AI 코스 추천", desc: "조건만 고르면 일자별 여행 코스 완성", path: "/ai-recommend" },
    { icon: ChartNoAxesCombined, title: "자산 대시보드", desc: "총자산 · 소비 패턴 · 예산 · 목표를 한눈에", path: "/assets" },
    { icon: Gift, title: "미스터리 투어", desc: "도착해야 공개되는 블라인드 여행", path: "/travel" },
    { icon: Trophy, title: "퀘스트", desc: "위치 인증으로 쌓는 경험치와 포인트", path: "/quests" },
    { icon: MessagesSquare, title: "커뮤니티", desc: "후기 · 동행 · 꿀팁을 나누는 라운지", path: "/boards" },
] as const;


export default function LandingPage() {

    const navigate = useNavigate();

    const rootRef =
        useRef<HTMLDivElement>(null);

    const canvasRef =
        useRef<HTMLCanvasElement>(null);

    const storyRef =
        useRef<HTMLElement>(null);

    const scopeRef =
        useRef<Scope | null>(null);

    const activeRef =
        useRef(0);

    const [activeStep, setActiveStep] =
        useState(0);


    useEffect(() => {

        const canvas = canvasRef.current;
        const story = storyRef.current;
        const ctx = canvas?.getContext("2d");

        if (!canvas || !story || !ctx) {
            return;
        }

        const reduceMotion =
            window.matchMedia("(prefers-reduced-motion: reduce)").matches;

        /* 형상 상태 — anime.js 가 이 객체의 값을 애니메이션한다 */
        const view = {
            morph: 0,
            tilt: STEP_TILT[0],
            zoom: STEP_ZOOM[0],
            intro: reduceMotion ? 1 : 0,
        };

        /* 마우스 패럴랙스 값 */
        const pointer = { x: 0, y: 0 };

        let spin = 0;
        let width = 0;
        let height = 0;
        let dpr = 1;

        const projected = Array.from({ length: POINT_COUNT }, () => ({
            x: 0,
            y: 0,
            z: 0,
            f: 1,
            i: 0,
        }));

        const resize = () => {
            dpr = Math.min(window.devicePixelRatio || 1, 2);
            width = canvas.clientWidth;
            height = canvas.clientHeight;
            canvas.width = Math.round(width * dpr);
            canvas.height = Math.round(height * dpr);
        };

        resize();

        const resizeObserver = new ResizeObserver(resize);
        resizeObserver.observe(canvas);

        const easeInOut = (t: number) =>
            t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;

        const render = (delta: number) => {

            if (!width || !height) return;

            const desktop = width >= 1024;

            ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
            ctx.clearRect(0, 0, width, height);

            const cx = desktop ? width * 0.68 : width * 0.5;
            const cy = desktop ? height * 0.5 : height * 0.36;
            const base = desktop
                ? Math.min(width * 0.2, height * 0.34)
                : Math.min(width * 0.36, height * 0.24);
            const radius = base * view.zoom * (0.35 + 0.65 * view.intro);

            if (!reduceMotion) {
                spin += delta * 0.00016;
            }

            const rotY = spin + view.morph * 0.75 + pointer.x * 0.4;
            const rotX = view.tilt + pointer.y * 0.25;

            const cosY = Math.cos(rotY);
            const sinY = Math.sin(rotY);
            const cosX = Math.cos(rotX);
            const sinX = Math.sin(rotX);

            const k = Math.min(Math.floor(view.morph), SHAPES.length - 2);
            const t = Math.min(Math.max(view.morph - k, 0), 1);
            const from = SHAPES[k];
            const to = SHAPES[k + 1];

            /* 은은한 중심 글로우 */
            const glow = ctx.createRadialGradient(cx, cy, 0, cx, cy, radius * 1.9);
            glow.addColorStop(0, "rgba(124, 108, 255, 0.20)");
            glow.addColorStop(0.55, "rgba(34, 211, 238, 0.05)");
            glow.addColorStop(1, "rgba(0, 0, 0, 0)");
            ctx.fillStyle = glow;
            ctx.fillRect(0, 0, width, height);

            for (let i = 0; i < POINT_COUNT; i++) {

                /* 점마다 살짝 다른 타이밍으로 변신 → 물결치듯 morph */
                const local = easeInOut(
                    Math.min(Math.max(t * 1.4 - (i / POINT_COUNT) * 0.4, 0), 1),
                );

                const a = from[i];
                const b = to[i];

                const px = a.x + (b.x - a.x) * local;
                const py = a.y + (b.y - a.y) * local;
                const pz = a.z + (b.z - a.z) * local;

                /* Y축 회전 → X축 회전 */
                const x1 = px * cosY - pz * sinY;
                const z1 = px * sinY + pz * cosY;
                const y2 = py * cosX - z1 * sinX;
                const z2 = py * sinX + z1 * cosX;

                const f = 3 / (3 - z2);

                const p = projected[i];
                p.x = cx + x1 * radius * f;
                p.y = cy + y2 * radius * f;
                p.z = z2;
                p.f = f;
                p.i = i;
            }

            /* 뒤쪽 점부터 그린다 */
            projected.sort((m, n) => m.z - n.z);

            for (let j = 0; j < POINT_COUNT; j++) {
                const p = projected[j];
                const depth = (p.z + 1.2) / 2.4;
                const [r, g, b] = POINT_COLORS[p.i];
                const size = (1.1 + depth * 2.6) * p.f;
                const alpha = (0.18 + depth * 0.82) * view.intro;

                ctx.beginPath();
                ctx.fillStyle = `rgba(${r}, ${g}, ${b}, ${alpha.toFixed(3)})`;
                ctx.arc(p.x, p.y, size, 0, Math.PI * 2);
                ctx.fill();
            }
        };


        scopeRef.current = createScope({ root: rootRef }).add(() => {

            /* ---------- 매 프레임 렌더 ---------- */
            createTimer({
                onUpdate: (self) => render(self.deltaTime),
            });


            /* ---------- 첫 진입 ---------- */
            if (!reduceMotion) {
                utils.set(".lp-nav, .lp-panel-0 .lp-line", { opacity: 0 });

                animate(view, {
                    intro: 1,
                    duration: 2200,
                    ease: "out(4)",
                });

                animate(".lp-nav", {
                    opacity: [0, 1],
                    y: [-12, 0],
                    duration: 800,
                    delay: 200,
                    ease: "out(3)",
                });

                animate(".lp-panel-0 .lp-line", {
                    opacity: [0, 1],
                    y: [36, 0],
                    duration: 1000,
                    delay: stagger(110, { start: 300 }),
                    ease: "out(4)",
                });
            }


            /* ---------- 마우스 패럴랙스 ---------- */
            const pointerAnim = createAnimatable(pointer, {
                x: 1200,
                y: 1200,
                ease: "out(3)",
            });

            const onPointerMove = (e: PointerEvent) => {
                pointerAnim.x((e.clientX / window.innerWidth - 0.5) * 2);
                pointerAnim.y((e.clientY / window.innerHeight - 0.5) * 2);
            };

            window.addEventListener("pointermove", onPointerMove);


            /* ---------- 스크롤 스토리 ---------- */
            utils.set(".lp-panel", { opacity: 0, y: 40 });
            utils.set(".lp-panel-0", { opacity: 1, y: 0 });

            const story = createTimeline({
                defaults: { ease: "linear" },
                autoplay: onScroll({
                    target: storyRef.current!,
                    enter: "top top",
                    leave: "bottom bottom",
                    sync: 0.3,
                }),
                onUpdate: (self) => {
                    const index = Math.round(self.progress * (STEPS.length - 1));
                    if (index !== activeRef.current) {
                        activeRef.current = index;
                        setActiveStep(index);
                    }
                },
            });

            for (let i = 0; i < STEPS.length - 1; i++) {
                const start = i * SEGMENT;

                story
                    .add(view, {
                        morph: i + 1,
                        tilt: STEP_TILT[i + 1],
                        zoom: STEP_ZOOM[i + 1],
                        duration: SEGMENT,
                        ease: "inOut(2)",
                    }, start)
                    .add(`.lp-panel-${i}`, {
                        opacity: [1, 0],
                        y: [0, -40],
                        duration: SEGMENT * 0.22,
                        ease: "in(2)",
                    }, start + SEGMENT * 0.18)
                    .add(`.lp-panel-${i + 1}`, {
                        opacity: [0, 1],
                        y: [40, 0],
                        duration: SEGMENT * 0.25,
                        ease: "out(2)",
                    }, start + SEGMENT * 0.6);
            }

            story.add(".lp-scroll-hint", {
                opacity: [1, 0],
                duration: SEGMENT * 0.15,
            }, 0);


            /* ---------- 기능 그리드 등장 ---------- */
            animate(".lp-feature", {
                opacity: [0, 1],
                y: [40, 0],
                duration: 900,
                delay: stagger(80),
                ease: "out(4)",
                autoplay: onScroll({
                    target: ".lp-features",
                    enter: "bottom-=120 top",
                }),
            });


            return () => {
                window.removeEventListener("pointermove", onPointerMove);
            };
        });

        return () => {
            resizeObserver.disconnect();
            scopeRef.current?.revert();
        };

    }, []);


    /** 진행 표시 점을 누르면 해당 단계 위치로 스크롤 */
    const goToStep = (index: number) => {
        const story = storyRef.current;
        if (!story) return;
        const top = story.getBoundingClientRect().top + window.scrollY;
        const distance = story.offsetHeight - window.innerHeight;
        window.scrollTo({
            top: top + (distance * index) / (STEPS.length - 1),
            behavior: "smooth",
        });
    };


    return (
        <div
            ref={rootRef}
            className="min-h-screen bg-night-950 text-white"
        >

            {/* =================================================
                NAV
               ================================================= */}

            <nav className="lp-nav fixed inset-x-0 top-0 z-50 bg-gradient-to-b from-night-950/90 to-night-950/0 backdrop-blur-[2px]">
                <div className="mx-auto flex h-20 max-w-7xl items-center gap-8 px-6">
                    <button
                        type="button"
                        onClick={() => navigate("/")}
                        className="flex items-center gap-2.5"
                    >
                        <BrandMark />
                        <span className="font-display text-lg font-bold tracking-tight">
                            TripPamine
                        </span>
                    </button>

                    <div className="hidden items-center gap-7 text-[13px] text-white/50 md:flex">
                        <button type="button" onClick={() => goToStep(2)} className="transition hover:text-white">여행</button>
                        <button type="button" onClick={() => goToStep(3)} className="transition hover:text-white">자산</button>
                        <button type="button" onClick={() => goToStep(4)} className="transition hover:text-white">플레이</button>
                        <button type="button" onClick={() => navigate("/boards")} className="transition hover:text-white">커뮤니티</button>
                    </div>

                    <div className="ml-auto flex items-center gap-1">
                        <button
                            type="button"
                            onClick={() => navigate("/login")}
                            className="rounded-full px-4 py-2 text-[13px] font-semibold text-white/70 transition hover:text-white"
                        >
                            로그인
                        </button>
                        <button
                            type="button"
                            onClick={() => navigate("/join")}
                            className="rounded-full bg-white px-4 py-2 text-[13px] font-bold text-night-950 transition hover:bg-violet-100"
                        >
                            시작하기
                        </button>
                    </div>
                </div>
            </nav>


            {/* =================================================
                SCROLL STORY (3D)
               ================================================= */}

            <section
                ref={storyRef}
                className="relative"
                style={{ height: `${STEPS.length * 100}vh` }}
            >
                <div className="sticky top-0 h-screen overflow-hidden">

                    {/* 배경 */}
                    <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(60%_50%_at_70%_50%,rgba(76,60,190,0.22),transparent_70%)]" />

                    {/* 3D 파티클 */}
                    <canvas
                        ref={canvasRef}
                        className="absolute inset-0 h-full w-full"
                        aria-hidden="true"
                    />

                    {/* 단계별 텍스트 */}
                    <div className="relative mx-auto flex h-full max-w-7xl items-end px-6 pb-16 lg:items-center lg:pb-0">
                        <div className="relative h-[340px] w-full max-w-xl lg:h-[420px]">
                            {STEPS.map((step, index) => (
                                <div
                                    key={step.label}
                                    className={`lp-panel lp-panel-${index} absolute inset-x-0 bottom-0 lg:bottom-auto lg:top-1/2 lg:-translate-y-1/2`}
                                    style={{ pointerEvents: activeStep === index ? "auto" : "none" }}
                                >
                                    <p className="lp-line text-[11px] font-semibold tracking-[0.24em] text-violet-300/80">
                                        {step.kicker}
                                    </p>

                                    <h2 className="lp-line mt-5 font-display text-[clamp(2.2rem,5.2vw,4.4rem)] font-bold leading-[1.08] tracking-[-0.035em]">
                                        {step.title[0]}
                                        <br />
                                        <span className="tp-gradient-text">{step.title[1]}</span>
                                    </h2>

                                    <p className="lp-line mt-6 max-w-md text-[15px] leading-relaxed text-white/55">
                                        {step.desc}
                                    </p>

                                    {step.tags && (
                                        <div className="lp-line mt-7 flex flex-wrap gap-2">
                                            {step.tags.map((tag) => (
                                                <span
                                                    key={tag}
                                                    className="rounded-full border border-white/10 px-3 py-1.5 text-xs text-white/60"
                                                >
                                                    {tag}
                                                </span>
                                            ))}
                                        </div>
                                    )}

                                    {(index === 0 || index === STEPS.length - 1) && (
                                        <div className="lp-line mt-9 flex flex-wrap items-center gap-3">
                                            <button
                                                type="button"
                                                onClick={() => navigate("/join")}
                                                className="group inline-flex items-center gap-2 rounded-full bg-white px-6 py-3.5 text-sm font-bold text-night-950 transition hover:bg-violet-100"
                                            >
                                                무료로 시작하기
                                                <ArrowRight className="h-4 w-4 transition group-hover:translate-x-0.5" />
                                            </button>

                                            <button
                                                type="button"
                                                onClick={() => navigate(index === 0 ? "/ai-recommend" : "/login")}
                                                className="rounded-full px-5 py-3.5 text-sm font-semibold text-white/70 transition hover:text-white"
                                            >
                                                {index === 0 ? "AI 코스 체험하기" : "이미 계정이 있어요"}
                                            </button>
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* 진행 표시 */}
                    <div className="absolute right-6 top-1/2 hidden -translate-y-1/2 flex-col gap-4 md:flex">
                        {STEPS.map((step, index) => (
                            <button
                                key={step.label}
                                type="button"
                                onClick={() => goToStep(index)}
                                className="group flex items-center justify-end gap-3"
                                aria-label={`${step.label} 단계로 이동`}
                            >
                                <span
                                    className={`text-[10px] font-semibold tracking-[0.18em] transition ${
                                        activeStep === index
                                            ? "text-white/80"
                                            : "text-white/0 group-hover:text-white/40"
                                    }`}
                                >
                                    {step.label.toUpperCase()}
                                </span>
                                <span
                                    className={`block rounded-full transition-all duration-300 ${
                                        activeStep === index
                                            ? "h-6 w-1.5 bg-white"
                                            : "h-1.5 w-1.5 bg-white/25 group-hover:bg-white/60"
                                    }`}
                                />
                            </button>
                        ))}
                    </div>

                    {/* 스크롤 안내 */}
                    <div className="lp-scroll-hint pointer-events-none absolute bottom-8 left-1/2 hidden -translate-x-1/2 flex-col items-center gap-2 text-[10px] tracking-[0.25em] text-white/35 lg:flex">
                        SCROLL
                        <span className="relative h-10 w-px overflow-hidden bg-white/10">
                            <span className="absolute inset-x-0 top-0 h-4 animate-[tp-scroll-line_1.8s_ease-in-out_infinite] bg-white/70" />
                        </span>
                    </div>
                </div>
            </section>


            {/* =================================================
                FEATURES
               ================================================= */}

            <section className="lp-features mx-auto max-w-7xl px-6 py-32">
                <div className="flex flex-col justify-between gap-6 md:flex-row md:items-end">
                    <div>
                        <p className="text-[11px] font-semibold tracking-[0.24em] text-violet-300/80">
                            EVERYTHING IN ONE PLACE
                        </p>
                        <h2 className="mt-4 font-display text-4xl font-bold tracking-[-0.03em] md:text-5xl">
                            필요한 모든 기능,
                            <br />
                            <span className="text-white/40">하나의 AI로.</span>
                        </h2>
                    </div>

                    <p className="max-w-sm text-sm leading-relaxed text-white/45">
                        각 기능은 따로 놀지 않아요. AI 매니저가 여행 · 자산 · 퀘스트 기록을 함께 보고 다음 행동을 제안합니다.
                    </p>
                </div>

                <div className="mt-16 grid grid-cols-1 gap-px overflow-hidden rounded-3xl border border-white/10 bg-white/10 sm:grid-cols-2 lg:grid-cols-3">
                    {FEATURES.map((feature) => {
                        const Icon = feature.icon;
                        return (
                            <button
                                key={feature.title}
                                type="button"
                                onClick={() => navigate(feature.path)}
                                className="lp-feature group flex flex-col items-start bg-night-950 p-8 text-left transition hover:bg-night-900"
                            >
                                <Icon className="h-6 w-6 text-violet-300 transition group-hover:text-cyan-300" strokeWidth={1.6} />
                                <h3 className="mt-10 text-lg font-bold">{feature.title}</h3>
                                <p className="mt-2 text-sm text-white/45">{feature.desc}</p>
                                <span className="mt-6 inline-flex items-center gap-1 text-xs font-semibold text-white/35 transition group-hover:gap-2 group-hover:text-white">
                                    바로 가기 <ArrowRight className="h-3.5 w-3.5" />
                                </span>
                            </button>
                        );
                    })}
                </div>
            </section>


            {/* =================================================
                FOOTER
               ================================================= */}

            <footer className="border-t border-white/5">
                <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-4 px-6 py-10 text-xs text-white/35 md:flex-row">
                    <div className="flex items-center gap-2.5">
                        <BrandMark small />
                        <span className="font-display font-semibold text-white/70">TripPamine</span>
                        <span className="hidden sm:inline">· AI 기반 초개인화 맞춤 솔루션 플랫폼</span>
                    </div>

                    <div className="flex gap-6">
                        <button type="button" onClick={() => navigate("/tour/festivals")} className="hover:text-white">축제 · 관광</button>
                        <button type="button" onClick={() => navigate("/finance")} className="hover:text-white">금융상품</button>
                        <button type="button" onClick={() => navigate("/boards")} className="hover:text-white">커뮤니티</button>
                    </div>

                    <p>© {new Date().getFullYear()} TripPamine</p>
                </div>
            </footer>
        </div>
    );
}
