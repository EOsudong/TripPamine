import { useEffect, useState, type FormEvent } from "react";
import axios from "axios";
import {
    analyzeEmotionApi,
    getLatestEmotionApi,
    type EmotionAnalysisResponse,
    type LatestEmotionResponse,
} from "../api/aiManager";
import AppLayout from "../layout/AppLayout";


/**
 * Spring Boot를 통해 AWS 감정 분석 모델을 호출한다.
 * 사용자 식별 및 Oracle 저장은 Backend에서 처리한다.
 */
export default function EmotionAnalysisPage() {
    const [input, setInput] = useState("");
    const [analyzing, setAnalyzing] = useState(false);
    const [result, setResult] = useState<EmotionAnalysisResponse | null>(null);
    const [latest, setLatest] = useState<LatestEmotionResponse | null>(null);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;

        void getLatestEmotionApi()
            .then((value) => {
                if (!cancelled) {
                    setLatest(value);
                }
            })
            .catch(() => {
                if (!cancelled) {
                    setLatest(null);
                }
            });

        return () => {
            cancelled = true;
        };
    }, []);

    const humanTurns = input
        .split(/\r?\n/)
        .map((turn) => turn.trim())
        .filter((turn) => turn.length > 0);

    const invalidTurns = humanTurns.length === 0 ||
        humanTurns.length > 50 ||
        humanTurns.some((turn) => turn.length > 4000);

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();

        if (invalidTurns || analyzing) {
            setError("1~50개의 대화를 입력해 주세요. 각 대화는 최대 4,000자입니다.");
            return;
        }

        setAnalyzing(true);
        setError(null);
        setResult(null);

        try {
            const prediction = await analyzeEmotionApi({
                planId: null,
                humanTurns,
            });

            setResult(prediction);

            // 최신 감정은 API의 실제 저장 결과를 다시 조회한다.
            try {
                setLatest(await getLatestEmotionApi());
            } catch {
                // 분석 결과는 성공했으므로 조회 실패와 구분한다.
                setLatest(null);
            }
        } catch (cause) {
            if (axios.isAxiosError(cause) && cause.response?.status === 503) {
                setError("감정 분석 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");
            } else {
                setError("감정 분석 중 오류가 발생했습니다. 로그인 및 서버 상태를 확인해 주세요.");
            }
        } finally {
            setAnalyzing(false);
        }
    };

    return (
        <AppLayout pageClassName="tp-aurora-light">
            <div className="mx-auto max-w-4xl px-4 py-10 md:px-8">
                <header className="mb-7">
                    <p className="text-xs font-bold tracking-widest text-sky-600">
                        AI MANAGER · EMOTION
                    </p>
                    <h1 className="mt-2 text-3xl font-black text-slate-950">
                        감정 분석
                    </h1>
                    <p className="mt-3 text-sm leading-6 text-slate-600">
                        대화를 입력하면 학습된 감정 모델이 분석하고 내 감정 기록에 저장합니다.
                    </p>
                </header>

                <form
                    onSubmit={(event) => { void handleSubmit(event); }}
                    className="rounded-3xl bg-white/90 p-6 shadow-[0_20px_50px_-35px_rgba(60,40,180,0.45)] backdrop-blur-xl"
                >
                    <label htmlFor="emotion-human-turns" className="text-sm font-bold text-slate-800">
                        대화 내용
                    </label>
                    <p className="mt-1 text-xs text-slate-500">
                        사용자 발화마다 줄을 바꿔 입력하세요. 최대 50개까지 분석할 수 있습니다.
                    </p>
                    <textarea
                        id="emotion-human-turns"
                        value={input}
                        onChange={(event) => setInput(event.target.value)}
                        rows={6}
                        placeholder={"내일 여행을 떠나서 설레요.\n친구들과 새로운 곳에 가는 게 기대돼요."}
                        className="mt-4 w-full resize-y rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-6 text-slate-900 outline-none focus:border-sky-400 focus:ring-2 focus:ring-sky-100"
                    />
                    <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
                        <p className="text-xs text-slate-500">현재 {humanTurns.length}개 발화</p>
                        <button
                            type="submit"
                            disabled={analyzing || invalidTurns}
                            className="rounded-2xl bg-sky-600 px-6 py-3 text-sm font-bold text-white shadow-lg shadow-sky-600/20 transition hover:bg-sky-700 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            {analyzing ? "AI가 분석하고 있어요..." : "감정 분석하기"}
                        </button>
                    </div>
                    {error && (
                        <p role="alert" className="mt-4 rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">
                            {error}
                        </p>
                    )}
                </form>

                {result && (
                    <section aria-live="polite" className="mt-6 rounded-3xl bg-white/90 p-6 shadow-[0_20px_50px_-35px_rgba(60,40,180,0.45)]">
                        <p className="text-xs font-bold tracking-widest text-sky-600">분석 완료 · 저장됨</p>
                        <h2 className="mt-3 text-2xl font-black text-slate-900">
                            {result.fineLabelName}
                        </h2>
                        <p className="mt-2 text-sm text-slate-600">
                            {result.coarseLabel} · {result.fineLabel}
                        </p>
                        <div className="mt-5 grid gap-3 text-sm text-slate-700 sm:grid-cols-2">
                            <p>모델 점수: {(result.confidence * 100).toFixed(1)}%</p>
                            <p>분석 문맥: {result.contextMode === "FULL_FALLBACK" ? "전체 대화" : "첫 2개 발화"}</p>
                            <p>사용된 발화: {result.turnsUsed}개</p>
                            <p>추론 시간: {result.latencyMs.toFixed(0)}ms</p>
                        </div>
                        <p className="mt-4 text-xs text-slate-500">
                            모델 점수는 정답률이 아닙니다. 분석 결과는 로그인 사용자의 감정 기록에 저장됩니다.
                        </p>
                    </section>
                )}

                {latest && (
                    <section className="mt-6 rounded-3xl bg-white/80 p-6 shadow-[0_15px_40px_-35px_rgba(60,40,180,0.4)]">
                        <p className="text-xs font-bold tracking-widest text-slate-500">최근 감정 기록</p>
                        <h2 className="mt-2 text-xl font-bold text-slate-900">
                            {latest.fineLabelName}
                        </h2>
                        <p className="mt-2 text-sm text-slate-600">
                            {latest.coarseLabel} · {latest.fineLabel} · {latest.observedAt.replace("T", " ").slice(0, 19)}
                        </p>
                    </section>
                )}
            </div>
        </AppLayout>
    );
}
