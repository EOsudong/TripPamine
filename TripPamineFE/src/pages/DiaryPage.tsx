import {useEffect, useState} from "react"
import AppLayout from "../layout/AppLayout"
import {getTravelPlansApi} from "../api/travel"
import {getDiariesApi, generateDiaryApi, toggleDiaryShareApi, deleteDiaryApi} from "../api/diary"
import type {TravelDiary} from "../api/diary"
import type {TravelPlan} from "../types"
import axios from "axios"

export default function DiaryPage() {
  const [plans, setPlans] = useState<TravelPlan[]>([])
  const [diaries, setDiaries] = useState<TravelDiary[]>([])
  const [selectedPlanId, setSelectedPlanId] = useState("")
  const [message, setMessage] = useState("")
  const [loading, setLoading] = useState(false)

  const handleGenerate = async () => {
    if (!selectedPlanId) {
      setMessage("여행을 먼저 선택해 주세요.")
      return
    }
    setLoading(true)
    setMessage("")
    try {
      await generateDiaryApi(Number(selectedPlanId))
      setSelectedPlanId("")
      await loadAll() // 생성 응답엔 날짜가 null이라, 목록을 다시 불러옵니다.
      setMessage("다이어리가 생성되었어요.")
    } catch (error) {
      // 서버가 보낸 안내 문구(400, 409 등)가 있으면 그대로 보여 줍니다
      const serverMessage = axios.isAxiosError(error) ? error.response?.data?.message : undefined
      setMessage(serverMessage ?? "다이어리 생성에 실패했습니다.")
    } finally {
      setLoading(false)
    }
  }

  const handleToggleShare = async (diaryId: number) => {
    try {
      const updated = await toggleDiaryShareApi(diaryId)
      setDiaries((prev) =>
        prev.map((d) =>
          (d.diaryId === diaryId ? updated : d)))
    } catch {
      setMessage("공유 설정을 바꾸지 못했습니다.")
    }
  }

  const handleDelete = async (diaryId: number) => {
    if (!window.confirm("이 다이어리를 삭제할까요?")) return
    try {
      await deleteDiaryApi(diaryId)
      setDiaries((prev) =>
        prev.filter((d) =>
          d.diaryId !== diaryId))
    } catch {
      setMessage("삭제하지 못했습니다.")
    }
  }

  const loadAll = async () => {
    try {
      const [planList, diaryList] = await Promise.all([getTravelPlansApi(), getDiariesApi()])
      setPlans(planList)
      setDiaries(diaryList)
    } catch {
      setMessage("목록을 불러오지 못했습니다.")
    }
  }

  useEffect(() => {
    loadAll()
  }, [])

  // 다이어리를 만들 수 있는 여행 = 이미 끝났고, 아직 다이어리가 없는 여행
  const diarizedPlanIds = new Set(diaries.map((d) => d.planId))
  const eligiblePlans = plans.filter(
    (p) => p.endDate !== null && new Date(p.endDate) <= new Date() && !diarizedPlanIds.has(p.planId)
  )

  return (
    <AppLayout pageClassName="bg-slate-950 text-white">
      <div className="mx-auto max-w-4xl px-4 py-10">
        <h1 className="text-2xl font-black">AI 다이어리</h1>
        <p>생성 가능한 여행 {eligiblePlans.length}개 · 내 다이어리 {diaries.length}개</p>

        <section className="mt-6 rounded-2xl border border-white/10 bg-white/5 p-5">
          <h2 className="font-bold">새 다이어리 만들기</h2>

          {eligiblePlans.length === 0 ? (
            <p className="mt-2 text-sm text-slate-400">
              종료된 여행 중 다이어리가 없는 여행이 없어요.
            </p>
          ) : (
            <div className="mt-3 flex flex-col gap-3 sm:flex-row">
              <select
                value={selectedPlanId}
                onChange={(e) => setSelectedPlanId(e.target.value)}
                disabled={loading}
                className="flex-1 rounded-xl border border-white/10 bg-slate-900 px-3 py-2"
              >
                <option value="">여행을 선택해 주세요</option>
                {eligiblePlans.map((p) => (
                  <option key={p.planId} value={p.planId}>
                    {p.planName}
                  </option>
                ))}
              </select>
              <button
                type="button"
                onClick={handleGenerate}
                disabled={loading}
                className="rounded-xl bg-cyan-400 px-5 py-2 font-bold text-slate-950 disabled:opacity-50"
              >
                {loading ? "작성 중..." : "다이어리 만들기"}
              </button>
            </div>
          )}

          {message && <p className="mt-3 text-sm text-cyan-300">{message}</p>}
        </section>

        <section className="mt-8 space-y-5">
          <h2 className="text-lg font-black">내 다이어리</h2>

          {diaries.length === 0 && (
            <p className="text-sm text-slate-400">아직 만든 다이어리가 없어요.</p>
          )}

          {diaries.map((diary) => {
            // 다이어리가 속한 여행의 이름을 찾아서 보여 줍니다
            const planName = plans.find((p) => p.planId === diary.planId)?.planName ?? "여행"

            return (
              <article
                key={diary.diaryId}
                className="rounded-2xl border border-white/10 bg-white/5 p-5"
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-xs text-cyan-300">
                      {planName}
                      {diary.createDate ? ` · ${diary.createDate.slice(0, 10)}` : ""}
                    </p>
                    <h3 className="mt-1 text-xl font-black">{diary.aiTitle}</h3>
                  </div>
                  <div className="text-right">
                    <p className="text-xs text-slate-400">도파민 점수</p>
                    <p className="text-2xl font-black text-cyan-300">{diary.dopamineScore}</p>
                  </div>
                </div>

                <p className="mt-4 whitespace-pre-line text-sm leading-7 text-slate-200">
                  {diary.aiContent}
                </p>

                <div className="mt-4 flex flex-wrap gap-2">
                  {diary.keywords.map((k) => (
                    <span
                      key={k.keywordId}
                      className="rounded-full bg-white/10 px-3 py-1 text-xs"
                    >
                      #{k.keywordName}
                    </span>
                  ))}
                </div>

                <div className="mt-5 flex items-center gap-2">
                  <span className="rounded-full bg-white/10 px-3 py-1 text-xs">
                    {diary.shareYn === "Y" ? "🌐 공개 중" : "🔒 나만 보기"}
                  </span>
                  <button
                    type="button"
                    onClick={() => handleToggleShare(diary.diaryId)}
                    className="rounded-xl border border-white/15 px-4 py-2 text-sm font-bold
                    cursor-pointer transition duration-200
                    hover:-translate-y-0.5 hover:border-cyan-300/60 hover:bg-white/10 hover:shadow-lg"
                  >
                    {diary.shareYn === "Y" ? "비공개로 전환" : "공개로 전환"}
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDelete(diary.diaryId)}
                    className="rounded-xl px-4 py-2 text-sm font-bold text-rose-300
                    cursor-pointer transition duration-200
                    hover:-translate-y-0.5 hover:bg-rose-500/10 hover:text-rose-200"
                  >
                    삭제
                  </button>
                </div>
              </article>
            )
          })}
        </section>
      </div>
    </AppLayout>
  )
}