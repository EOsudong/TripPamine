import {
  useEffect,
  useState,
  type FormEvent,
} from "react"

import {
  createTravelGoal,
  getAssetAdvice,
  type AssetAdviceResponse,
  type FinancialStatus,
  type TravelGoalRequest,
} from "../../api/assetAdvice"


// =====================================================
// 상태 이름============

const STATUS_LABEL: Record<
  FinancialStatus,
  string
> = {

  ACHIEVABLE:
    "목표 달성 가능",

  TIGHT:
    "조금 빠듯함",

  SHORTAGE:
    "계획 조정 필요",

  COMPLETED:
    "목표 달성",

  NO_GOAL:
    "여행 목표 없음",
}


// =====================================================
// 상태 설명
// =====================================================

const STATUS_DESCRIPTION: Record<
  FinancialStatus,
  string
> = {

  ACHIEVABLE:
    "현재의 수입과 지출 흐름을 유지하면 목표 달성이 가능합니다.",

  TIGHT:
    "조금만 지출을 조정하면 여행 목표를 달성할 수 있습니다.",

  SHORTAGE:
    "현재 자금 흐름으로는 목표 달성이 어려워 계획 조정이 필요합니다.",

  COMPLETED:
    "축하합니다! 설정한 여행자금 목표를 달성했습니다.",

  NO_GOAL:
    "여행 목표를 등록하면 필요한 저축액을 계산해드릴게요.",
}

// =====================================================
// 추천 문구의 숫자 강조
// =====================================================

const highlightNumbers = (
    text: string,
) => {

  const parts =
      text.split(
          /(\d[\d,]*(?:\.\d+)?(?:%|원|개월)?)/g,
      )


  return parts.map(
      (
          part,
          index,
      ) => {

        const isNumber =
            /^\d[\d,]*(?:\.\d+)?(?:%|원|개월)?$/
                .test(
                    part,
                )


        if (isNumber) {

          return (
              <strong
                  key={index}
                  className="font-extrabold text-violet-700"
              >
                {part}
              </strong>
          )
        }


        return (
            <span key={index}>
              {part}
            </span>
        )
      },
  )
}

// =====================================================
// Component
// =====================================================

export default function AssetAdvicePanel() {

  // ===================================================
  // Advice
  // ===================================================

  const [
    advice,
    setAdvice,
  ] =
    useState<AssetAdviceResponse | null>(
      null,
    )


  const [
    loading,
    setLoading,
  ] =
    useState(true)


  const [
    error,
    setError,
  ] =
    useState<string | null>(
      null,
    )


  // ===================================================
  // 여행 목표 등록 Form
  // ===================================================

  const [
    form,
    setForm,
  ] =
    useState<TravelGoalRequest>({

      goalName: "",

      targetAmount: 0,

      currentAmount: 0,

      targetDate: "",
    })


  const [
    submitting,
    setSubmitting,
  ] =
    useState(false)


  const [
    formError,
    setFormError,
  ] =
    useState<string | null>(
      null,
    )


  // ===================================================
  // 금액 출력
  // ===================================================

  const formatMoney = (
    value?: number | null,
  ) => {

    return `${Number(
      value ?? 0,
    ).toLocaleString(
      "ko-KR",
    )}원`
  }


  // ===================================================
  // Advice 조회
  // ===================================================

  const loadAdvice =
    async () => {

      try {

        setLoading(true)

        setError(null)


        const data =
          await getAssetAdvice()


        setAdvice(
          data,
        )

      } catch (error) {

        console.error(
          "Asset Advice Error =",
          error,
        )


        setError(

          error instanceof Error

            ? error.message

            : "자산관리 정보를 불러오지 못했습니다.",
        )

      } finally {

        setLoading(false)
      }
    }


  // ===================================================
  // 최초 조회
  // ===================================================

  useEffect(
    () => {

      loadAdvice()

    },
    [],
  )


  // ===================================================
  // Input 변경
  // ===================================================

  const handleChange = (
    field: keyof TravelGoalRequest,
    value: string,
  ) => {

    setForm(
      previous => ({

        ...previous,

        [field]:
          field === "goalName"
          || field === "targetDate"

            ? value

            : Number(value),
      }),
    )
  }


  // ===================================================
  // 여행 목표 등록
  // ===================================================

  const handleSubmit =
    async (
      event: FormEvent<HTMLFormElement>,
    ) => {

      event.preventDefault()


      try {

        setSubmitting(true)

        setFormError(null)


        // =========================================
        // 기본 검증
        // =========================================

        if (
          !form.goalName.trim()
        ) {

          throw new Error(
            "여행 목표 이름을 입력해주세요.",
          )
        }


        if (
          form.targetAmount <= 0
        ) {

          throw new Error(
            "목표 금액을 입력해주세요.",
          )
        }


        if (
          form.currentAmount < 0
        ) {

          throw new Error(
            "현재 모은 금액을 확인해주세요.",
          )
        }


        if (
          !form.targetDate
        ) {

          throw new Error(
            "목표 날짜를 선택해주세요.",
          )
        }


        // =========================================
        // API 호출
        // =========================================

        const goalId =
          await createTravelGoal(
            form,
          )


        console.log(
          "생성된 여행 목표 ID =",
          goalId,
        )


        // =========================================
        // 입력값 초기화
        // =========================================

        setForm({

          goalName: "",

          targetAmount: 0,

          currentAmount: 0,

          targetDate: "",
        })


        // =========================================
        // Advice 다시 조회
        // =========================================

        await loadAdvice()


        alert(
          "여행 목표가 등록되었습니다.",
        )


        // 기존 Dashboard의 목표 목록까지
        // 다시 조회시키기 위한 가장 단순한 방법
        window.location.reload()

      } catch (error) {

        console.error(
          "Travel Goal Create Error =",
          error,
        )


        setFormError(

          error instanceof Error

            ? error.message

            : "여행 목표 등록 중 문제가 발생했습니다.",
        )

      } finally {

        setSubmitting(false)
      }
    }


  // ===================================================
  // Loading
  // ===================================================

  if (loading) {

    return (

      <section className="mb-6 rounded-[2rem] border border-slate-100 bg-white p-6 shadow-sm">

        <p className="text-center text-sm text-slate-400">

          자산관리 가이드를 분석하고 있습니다.

        </p>

      </section>
    )
  }


  // ===================================================
  // Error
  // ===================================================

  if (error) {

    return (

      <section className="mb-6 rounded-[2rem] border border-rose-100 bg-rose-50 p-6">

        <strong className="text-sm text-rose-600">

          자산관리 가이드를 불러오지 못했습니다.

        </strong>

        <p className="mt-2 text-xs text-rose-500">

          {error}

        </p>

      </section>
    )
  }


  // ===================================================
  // 데이터 없음
  // ===================================================

  if (!advice) {

    return null
  }


  // ===================================================
  // Render
  // ===================================================

  return (

    <section className="mb-6 grid grid-cols-1 gap-5 xl:grid-cols-2">

      {/* ================================================= */}
      {/* 왼쪽 : 자산관리 분석 */}
      {/* ================================================= */}

      <article className="tp-card min-w-0 p-5 md:p-7">


        {/* Header */}

        <div className="mb-6">

          <span className="tp-kicker mb-1.5 inline-block">

            TRAVEL ASSET GUIDE

          </span>

          <h2 className="tp-heading text-xl">

            여행자금 자산관리

          </h2>

          <p className="mt-1 text-[11px] text-slate-400">

            최근 3개월의 수입과 지출을 분석해
            여행 목표 달성 가능성을 계산합니다.

          </p>

        </div>


        {/* 월평균 수입 / 지출 / 여유자금 */}

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">

          <div className="rounded-2xl bg-emerald-50 p-4">

            <span className="text-[10px] font-bold text-emerald-600">

              월평균 수입

            </span>

            <strong className="mt-2 block text-lg text-slate-900">

              {formatMoney(
                advice.monthlyAverageIncome,
              )}

            </strong>

          </div>


          <div className="rounded-2xl bg-rose-50 p-4">

            <span className="text-[10px] font-bold text-rose-500">

              월평균 지출

            </span>

            <strong className="mt-2 block text-lg text-slate-900">

              {formatMoney(
                advice.monthlyAverageExpense,
              )}

            </strong>

          </div>


          <div className="rounded-2xl bg-sky-50 p-4">

            <span className="text-[10px] font-bold text-sky-600">

              월평균 여유자금

            </span>

            <strong className="mt-2 block text-lg text-slate-900">

              {formatMoney(
                advice.monthlySurplus,
              )}

            </strong>

          </div>

        </div>


        {/* 자산관리 상태 */}

        <div className="mt-5 rounded-2xl border border-violet-100 bg-violet-50/60 p-5">

          <div className="flex items-center justify-between gap-4">

            <div>

              <span className="text-[10px] font-bold text-violet-500">

                현재 자산관리 상태

              </span>

              <strong className="mt-1 block text-lg text-slate-900">

                {
                  STATUS_LABEL[
                    advice.financialStatus
                  ]
                }

              </strong>

            </div>


            <span className="rounded-full bg-violet-600 px-3 py-1 text-[10px] font-bold text-white">

              {advice.financialStatus}

            </span>

          </div>


          <p className="mt-3 text-xs leading-relaxed text-slate-500">

            {
              STATUS_DESCRIPTION[
                advice.financialStatus
              ]
            }

          </p>

        </div>


        {/* 여행 목표 */}

        {advice.travelGoal && (

          <div className="mt-5 rounded-2xl border border-slate-100 p-5">

            <div className="flex items-start justify-between gap-4">

              <div>

                <span className="text-[10px] text-slate-400">

                  현재 여행 목표

                </span>

                <strong className="mt-1 block text-base text-slate-900">

                  {
                    advice.travelGoal
                      .goalName
                  }

                </strong>

              </div>


              <strong className="text-2xl font-bold text-violet-600">

                {
                  advice.travelGoal
                    .progressRate
                }%

              </strong>

            </div>


            {/* Progress */}

            <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-100">

              <div
                className="h-full rounded-full bg-violet-600 transition-[width] duration-700"
                style={{

                  width:
                    `${
                      Math.min(
                        100,
                        advice.travelGoal
                          .progressRate,
                      )
                    }%`,
                }}
              />

            </div>


            <div className="mt-4 grid grid-cols-2 gap-3 text-xs">

              <div>

                <span className="block text-slate-400">

                  목표 금액

                </span>

                <strong className="mt-1 block">

                  {formatMoney(
                    advice.travelGoal
                      .targetAmount,
                  )}

                </strong>

              </div>


              <div>

                <span className="block text-slate-400">

                  현재 금액

                </span>

                <strong className="mt-1 block">

                  {formatMoney(
                    advice.travelGoal
                      .currentAmount,
                  )}

                </strong>

              </div>


              <div>

                <span className="block text-slate-400">

                  남은 금액

                </span>

                <strong className="mt-1 block">

                  {formatMoney(
                    advice.travelGoal
                      .remainingAmount,
                  )}

                </strong>

              </div>


              <div>

                <span className="block text-slate-400">

                  남은 기간

                </span>

                <strong className="mt-1 block">

                  {
                    advice.travelGoal
                      .remainingMonths
                  }개월

                </strong>

              </div>

            </div>


            <div className="mt-4 rounded-xl bg-sky-50 px-4 py-3">

              <span className="text-[10px] text-sky-500">

                매월 필요한 저축액

              </span>

              <strong className="mt-1 block text-lg text-sky-700">

                {formatMoney(
                  advice.travelGoal
                    .requiredMonthlySaving,
                )}

              </strong>

            </div>

          </div>

        )}


        {/* 추천 */}

        <div className="mt-6">

          <h3 className="text-sm font-bold text-slate-900">

            맞춤 추천

          </h3>


          <div className="mt-3 space-y-3">

            {
              advice.recommendations.length
              === 0

                ? (

                  <div className="rounded-xl bg-slate-50 p-4 text-xs text-slate-400">

                    현재 추천 내용이 없습니다.

                  </div>

                )

                : (

                  advice.recommendations.map(
                    (
                      recommendation,
                      index,
                    ) => (

                      <div
                        key={`${recommendation.category}-${index}`}
                        className="rounded-2xl border border-slate-100 p-4"
                      >

                        <div className="flex items-center justify-between gap-4">

                          <span className="text-[10px] font-bold text-violet-600">

                            {
                              recommendation
                                .category
                            }

                          </span>


                          <span className="rounded-full bg-slate-100 px-2 py-1 text-[9px] font-bold text-slate-500">

                            {
                              recommendation
                                .priority
                            }

                          </span>

                        </div>


                        <strong className="mt-2 block text-sm text-slate-900">

                          {
                            recommendation
                              .title
                          }

                        </strong>


                        <p className="mt-1 text-xs leading-relaxed text-slate-500">

                          {highlightNumbers(
                            recommendation.description,
                        )}

                        </p>

                      </div>

                    ),
                  )
                )
            }

          </div>

        </div>

      </article>


      {/* ================================================= */}
      {/* 오른쪽 : 여행 목표 등록 */}
      {/* ================================================= */}

      <article className="tp-card min-w-0 p-5 md:p-7">


        <div className="mb-6">

          <span className="tp-kicker mb-1.5 inline-block">

            NEW GOAL

          </span>

          <h2 className="tp-heading text-xl">

            여행 목표 등록

          </h2>

          <p className="mt-1 text-[11px] text-slate-400">

            여행에 필요한 목표 금액과 날짜를
            입력해주세요.

          </p>

        </div>


        <form
          className="space-y-5"
          onSubmit={
            handleSubmit
          }
        >


          {/* 목표 이름 */}

          <div>

            <label
              className="mb-2 block text-xs font-bold text-slate-600"
              htmlFor="goalName"
            >

              여행 목표 이름

            </label>


            <input
              id="goalName"
              type="text"
              value={
                form.goalName
              }
              onChange={
                event =>
                  handleChange(
                    "goalName",
                    event.target.value,
                  )
              }
              placeholder="예: 일본 도쿄 여행"
              className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none transition focus:border-violet-400 focus:ring-2 focus:ring-violet-100"
            />

          </div>


          {/* 목표 금액 */}

          <div>

            <label
              className="mb-2 block text-xs font-bold text-slate-600"
              htmlFor="targetAmount"
            >

              목표 금액

            </label>


            <input
              id="targetAmount"
              type="number"
              min="1"
              value={
                form.targetAmount
                || ""
              }
              onChange={
                event =>
                  handleChange(
                    "targetAmount",
                    event.target.value,
                  )
              }
              placeholder="3000000"
              className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none transition focus:border-violet-400 focus:ring-2 focus:ring-violet-100"
            />

          </div>


          {/* 현재 금액 */}

          <div>

            <label
              className="mb-2 block text-xs font-bold text-slate-600"
              htmlFor="currentAmount"
            >

              현재 모은 금액

            </label>


            <input
              id="currentAmount"
              type="number"
              min="0"
              value={
                form.currentAmount
                || ""
              }
              onChange={
                event =>
                  handleChange(
                    "currentAmount",
                    event.target.value,
                  )
              }
              placeholder="1000000"
              className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none transition focus:border-violet-400 focus:ring-2 focus:ring-violet-100"
            />

          </div>


          {/* 목표 날짜 */}

          <div>

            <label
              className="mb-2 block text-xs font-bold text-slate-600"
              htmlFor="targetDate"
            >

              여행 목표 날짜

            </label>


            <input
              id="targetDate"
              type="date"
              value={
                form.targetDate
              }
              onChange={
                event =>
                  handleChange(
                    "targetDate",
                    event.target.value,
                  )
              }
              className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none transition focus:border-violet-400 focus:ring-2 focus:ring-violet-100"
            />

          </div>


          {/* Error */}

          {formError && (

            <div className="rounded-xl bg-rose-50 px-4 py-3 text-xs text-rose-600">

              {formError}

            </div>

          )}


          {/* Submit */}

          <button
            type="submit"
            disabled={
              submitting
            }
            className="w-full rounded-xl bg-violet-600 px-4 py-3 text-sm font-bold text-white transition hover:bg-violet-700 disabled:cursor-wait disabled:opacity-60"
          >

            {
              submitting

                ? "등록 중..."

                : "여행 목표 등록하기"
            }

          </button>

        </form>

      </article>

    </section>
  )
}