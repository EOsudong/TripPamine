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
            setError("한 줄 이상 적어주세요. 내용이 길다면 여러 줄로 나누어 작성해 주세요.");
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
                setError("지금은 연결이 원활하지 않아요. 잠시 후 다시 시도해 주세요.");
            } else {
                setError("마음을 살펴보는 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요.");
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
                        나의 마음 기록
                    </p>
                    <h1 className="mt-2 text-3xl font-black text-slate-950">
                        내 마음 살펴보기
                    </h1>
                    <p className="mt-3 text-sm leading-6 text-slate-600">
                        떠오르는 생각이나 기분을 편하게 적어주세요. 지금 어떤 감정에 가까운지 살펴볼게요.
                    </p>
                </header>

                <form
                    onSubmit={(event) => { void handleSubmit(event); }}
                    className="rounded-3xl bg-white/90 p-6 shadow-[0_20px_50px_-35px_rgba(60,40,180,0.45)] backdrop-blur-xl"
                >
                    <label htmlFor="emotion-human-turns" className="text-sm font-bold text-slate-800">
                        지금 어떤 마음인가요?
                    </label>
                    <p className="mt-1 text-xs text-slate-500">
                        생각이 여러 가지라면 줄을 바꿔 적어주세요. 짧게 적어도 좋아요.
                    </p>
                    <textarea
                        id="emotion-human-turns"
                        value={input}
                        onChange={(event) => setInput(event.target.value)}
                        rows={6}
                        placeholder={"내일 여행을 떠나서 설레요.\n친구들과 좋은 추억을 만들고 싶어요."}
                        className="mt-4 w-full resize-y rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-6 text-slate-900 outline-none focus:border-sky-400 focus:ring-2 focus:ring-sky-100"
                    />
                    <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
                        <p className="text-xs text-slate-500">작성한 이야기 {humanTurns.length}줄</p>
                        <button
                            type="submit"
                            disabled={analyzing || invalidTurns}
                            className="rounded-2xl bg-sky-600 px-6 py-3 text-sm font-bold text-white shadow-lg shadow-sky-600/20 transition hover:bg-sky-700 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            {analyzing ? "지금 마음을 살펴보고 있어요..." : "내 마음 살펴보기"}
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
                        <p className="text-xs font-bold tracking-widest text-sky-600">내 마음 기록에 남겼어요</p>
                        <h2 className="mt-3 text-2xl font-black text-slate-900">
                            {result.fineLabelName}
                        </h2>
                        <p className="mt-2 text-sm text-slate-600">
                            적어주신 이야기에서 이런 마음이 느껴져요.
                        </p>
                        <p className="mt-4 text-xs leading-5 text-slate-500">
                            마음은 상황에 따라 달라질 수 있어요. 이 결과는 참고로 살펴봐 주세요.
                        </p>
                    </section>
                )}

                {latest && (
                    <section className="mt-6 rounded-3xl bg-white/80 p-6 shadow-[0_15px_40px_-35px_rgba(60,40,180,0.4)]">
                        <p className="text-xs font-bold tracking-widest text-slate-500">최근에 살펴본 마음</p>
                        <h2 className="mt-2 text-xl font-bold text-slate-900">
                            {latest.fineLabelName}
                        </h2>
                        <p className="mt-2 text-sm text-slate-600">
                            기록한 시간: {latest.observedAt.replace("T", " ").slice(0, 16)}
                        </p>
                    </section>
                )}
            </div>
        </AppLayout>
    );
}
