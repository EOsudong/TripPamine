import {
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react"

import { useNavigate } from "react-router-dom"

import {
  Chart as ChartJS,
  ArcElement,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Tooltip,
  Legend,
  Filler,
  type ChartOptions,
} from "chart.js"

import {
  Doughnut,
  Line,
} from "react-chartjs-2"

import AppLayout from "../layout/AppLayout"

import { useAuth } from "../context/AuthContext"
import { API_BASE_URL } from "../api/axios"

// =====================================================
// 공통 Tailwind 클래스 (예전 AssetDashboardPage.css를 대체)
// =====================================================

const CARD_BASE =
    "min-w-0 border border-slate-200 bg-white shadow-[0_4px_18px_rgba(15,23,42,0.035)]"

const CARD =
    `${CARD_BASE} rounded-[20px] p-[18px] md:p-6`

const CARD_HEADER =
    "mb-5 flex min-h-[58px] flex-col items-start gap-2 md:flex-row md:justify-between md:gap-5"

const CARD_KICKER =
    "mb-[5px] inline-block text-[9px] font-black tracking-[0.15em] text-sky-600"

const CARD_TITLE =
    "text-lg font-extrabold text-slate-900"

const CARD_DESC =
    "mt-[5px] text-[11px] leading-normal text-slate-400"

const EMPTY =
    "flex min-h-[220px] items-center justify-center text-center text-xs text-slate-400"

const STATE_BOX =
    "flex min-h-[calc(100vh-4rem)] flex-col items-center justify-center gap-2 text-center"


// =====================================================
// Chart.js 등록
// =====================================================

ChartJS.register(
    ArcElement,
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    Tooltip,
    Legend,
    Filler,
)


// =====================================================
// Type
// =====================================================

interface BudgetInfo {
  budgetId: number
  categoryId?: number | null
  categoryName: string
  budgetAmount: number
}


interface GoalInfo {
  goalId: number
  goalName: string
  targetAmount: number
  currentAmount: number
  targetDate?: string | null
  goalType?: string | null
  status?: string | null
  progressRate?: number
}


interface AssetHistoryInfo {
  snapshotDate: string
  totalAsset: number
  totalIncome: number
  totalExpense: number
}


interface CategoryExpenseInfo {
  category: string
  amount: number
}


interface DashboardData {
  userId: number
  year: number
  month: number

  totalAsset: number
  totalIncome: number
  totalExpense: number
  travelExpense: number

  snapshotDate?: string | null

  assetHistory: AssetHistoryInfo[]
  categoryExpenses: CategoryExpenseInfo[]
  budgets: BudgetInfo[]
  goals: GoalInfo[]
}


// =====================================================
// 카테고리 이름
// =====================================================

const CATEGORY_NAMES: Record<string, string> = {
  FOOD: "식비",
  TRANSPORT: "교통",
  MEDICAL: "의료",
  CULTURE: "문화",
  SHOPPING: "쇼핑",
  HOUSING: "주거",
  SALARY: "급여",
  TRAVEL: "여행",
  ETC: "기타",
}


// =====================================================
// 카테고리 색상
// =====================================================

const CATEGORY_COLORS: Record<string, string> = {
  FOOD: "#0ea5e9",
  TRANSPORT: "#22c55e",
  MEDICAL: "#ef4444",
  CULTURE: "#8b5cf6",
  SHOPPING: "#f59e0b",
  HOUSING: "#64748b",
  SALARY: "#14b8a6",
  TRAVEL: "#ec4899",
  ETC: "#94a3b8",
}


const CHART_STEP_SIZE = 500_000


// =====================================================
// Component
// =====================================================

export default function AssetDashboardPage() {

  // ===================================================
  // Router
  // ===================================================

  const navigate = useNavigate()


  // ===================================================
  // 로그인 사용자
  // ===================================================

  const {
    userId,
    isLoggedIn,
  } = useAuth()


  // ===================================================
  // Dashboard
  // ===================================================

  const [
    dashboard,
    setDashboard,
  ] = useState<DashboardData | null>(null)


  const [
    loading,
    setLoading,
  ] = useState(true)


  const [
    error,
    setError,
  ] = useState<string | null>(null)


  // ===================================================
  // 현재 년 / 월
  // ===================================================

  const now = new Date()

  const year =
      now.getFullYear()

  const month =
      now.getMonth() + 1


  // ===================================================
  // Dashboard API
  // ===================================================

  useEffect(() => {

    if (
        !isLoggedIn
        || userId == null
    ) {

      setDashboard(null)

      setError(
          "로그인 사용자 정보를 확인할 수 없습니다.",
      )

      setLoading(false)

      return
    }


    const fetchDashboard =
        async () => {

          try {

            setLoading(true)
            setError(null)


            const accessToken =
                localStorage.getItem(
                    "accessToken",
                )


            if (!accessToken) {

              throw new Error(
                  "로그인 토큰을 확인할 수 없습니다.",
              )
            }


            console.log(
                "Asset Dashboard 요청 userId =",
                userId,
            )


            console.log(
                "조회 년월 =",
                `${year}-${month}`,
            )


            const response =
                await fetch(
                    // [수정] localhost:8080 하드코딩 → 공용 API 주소(VITE_API_BASE_URL) 사용
                    `${API_BASE_URL}/api/assets/dashboard?year=${year}&month=${month}`,
                    {
                      method: "GET",

                      headers: {
                        Accept:
                            "application/json",

                        Authorization:
                            `Bearer ${accessToken}`,
                      },
                    },
                )


            console.log(
                "Dashboard HTTP Status =",
                response.status,
            )


            if (!response.ok) {

              const errorText =
                  await response.text()


              console.error(
                  "Dashboard API Error =",
                  errorText,
              )


              throw new Error(
                  "자산 정보를 불러오지 못했습니다.",
              )
            }


            const data =
                await response.json()


            console.log(
                "Dashboard API 응답 =",
                data,
            )


            if (
                data.userId !== undefined
                && Number(data.userId)
                !== Number(userId)
            ) {

              console.warn(
                  "로그인 사용자와 Dashboard 응답 사용자가 다릅니다.",
              )
            }


            setDashboard({

              ...data,

              assetHistory:
                  data.assetHistory ?? [],

              categoryExpenses:
                  data.categoryExpenses ?? [],

              budgets:
                  data.budgets ?? [],

              goals:
                  data.goals ?? [],

            })

          } catch (error) {

            console.error(
                "Asset Dashboard Error =",
                error,
            )


            setDashboard(null)


            setError(

                error instanceof Error

                    ? error.message

                    : "자산 정보를 불러오지 못했습니다.",

            )

          } finally {

            setLoading(false)
          }
        }


    fetchDashboard()

  }, [
    userId,
    isLoggedIn,
    year,
    month,
  ])


  // ===================================================
  // 금액 표시
  // ===================================================

  const formatMoney = (
      value?: number | null,
  ) => {

    const amount =
        Number(value ?? 0)

    return `${amount.toLocaleString("ko-KR")}원`
  }


  // ===================================================
  // 그래프 금액 표시
  // ===================================================

  const formatChartMoney = (
      value: number,
  ) => {

    const absolute =
        Math.abs(value)


    if (
        absolute >= 100_000_000
    ) {

      const result =
          value / 100_000_000

      return Number.isInteger(result)

          ? `${result}억`

          : `${result.toFixed(1)}억`
    }


    if (
        absolute >= 10_000
    ) {

      const result =
          value / 10_000

      return Number.isInteger(result)

          ? `${result}만`

          : `${result.toFixed(1)}만`
    }


    return value.toLocaleString(
        "ko-KR",
    )
  }


  // ===================================================
  // Category Expense
  // ===================================================

  const categoryExpenses =
      dashboard?.categoryExpenses ?? []


  // ===================================================
  // 가장 많이 사용한 Category
  // ===================================================

  const highestCategory =
      useMemo(() => {

        if (
            categoryExpenses.length === 0
        ) {

          return null
        }


        return categoryExpenses.reduce(

            (
                highest,
                current,
            ) =>

                current.amount
                > highest.amount

                    ? current

                    : highest,

        )

      }, [
        categoryExpenses,
      ])


  // ===================================================
  // Doughnut Chart
  // ===================================================

  const doughnutData =
      useMemo(() => {

        return {

          labels:
              categoryExpenses.map(

                  (item) =>

                      CATEGORY_NAMES[
                          item.category
                              ?.toUpperCase()
                          ]

                      ?? item.category

                      ?? "기타",

              ),


          datasets: [
            {
              data:
                  categoryExpenses.map(
                      (item) =>
                          item.amount ?? 0,
                  ),

              backgroundColor:
                  categoryExpenses.map(

                      (item) =>

                          CATEGORY_COLORS[
                              item.category
                                  ?.toUpperCase()
                              ]

                          ?? "#94a3b8",

                  ),

              borderWidth: 0,
              hoverOffset: 8,
            },
          ],
        }

      }, [
        categoryExpenses,
      ])


  // ===================================================
  // Doughnut Options
  // ===================================================

  const doughnutOptions:
      ChartOptions<"doughnut"> = {

    responsive: true,

    maintainAspectRatio: false,

    cutout: "68%",

    plugins: {

      legend: {

        position: "bottom",

        labels: {
          usePointStyle: true,
          padding: 18,
        },
      },


      tooltip: {

        callbacks: {

          label: (context) => {

            const value =
                Number(
                    context.raw ?? 0,
                )

            return `${context.label}: ${formatMoney(value)}`
          },
        },
      },
    },
  }


  // ===================================================
  // Asset History
  // ===================================================

  const assetHistory =
      dashboard?.assetHistory ?? []


  // ===================================================
  // Line Chart
  // ===================================================

  const assetChartData =
      useMemo(() => {

        return {

          labels:
              assetHistory.map(

                  (item) => {

                    if (
                        !item.snapshotDate
                    ) {

                      return "-"
                    }


                    const date =
                        new Date(
                            `${item.snapshotDate}T00:00:00`,
                        )


                    if (
                        Number.isNaN(
                            date.getTime(),
                        )
                    ) {

                      return item.snapshotDate
                    }


                    return `${date.getMonth() + 1}/${date.getDate()}`
                  },

              ),


          datasets: [

            // 총자산
            {
              label: "총자산",

              data:
                  assetHistory.map(
                      (item) =>
                          item.totalAsset ?? 0,
                  ),

              borderColor:
                  "#0ea5e9",

              backgroundColor:
                  "rgba(14, 165, 233, 0.10)",

              pointBackgroundColor:
                  "#0ea5e9",

              pointBorderColor:
                  "#ffffff",

              pointBorderWidth: 2,

              pointRadius: 4,

              pointHoverRadius: 6,

              borderWidth: 3,

              tension: 0.35,

              fill: true,

              yAxisID:
                  "yAsset",
            },


            // 수입
            {
              label: "수입",

              data:
                  assetHistory.map(
                      (item) =>
                          item.totalIncome ?? 0,
                  ),

              borderColor:
                  "#22c55e",

              backgroundColor:
                  "#22c55e",

              pointBackgroundColor:
                  "#22c55e",

              pointBorderColor:
                  "#ffffff",

              pointBorderWidth: 2,

              pointRadius: 4,

              pointHoverRadius: 6,

              borderWidth: 2,

              tension: 0.35,

              fill: false,

              yAxisID:
                  "yFlow",
            },


            // 지출
            {
              label: "지출",

              data:
                  assetHistory.map(
                      (item) =>
                          item.totalExpense ?? 0,
                  ),

              borderColor:
                  "#ef4444",

              backgroundColor:
                  "#ef4444",

              pointBackgroundColor:
                  "#ef4444",

              pointBorderColor:
                  "#ffffff",

              pointBorderWidth: 2,

              pointRadius: 4,

              pointHoverRadius: 6,

              borderWidth: 2,

              tension: 0.35,

              fill: false,

              yAxisID:
                  "yFlow",
            },
          ],
        }

      }, [
        assetHistory,
      ])


  // ===================================================
  // Line Chart Options
  // ===================================================

  const assetChartOptions:
      ChartOptions<"line"> = {

    responsive: true,

    maintainAspectRatio: false,

    interaction: {
      mode: "index",
      intersect: false,
    },


    plugins: {

      legend: {
        display: true,
        position: "top",
        align: "end",

        labels: {
          usePointStyle: true,
          padding: 18,
        },
      },


      tooltip: {

        callbacks: {

          label: (context) => {

            const value =
                Number(
                    context.raw ?? 0,
                )

            return `${context.dataset.label}: ${formatMoney(value)}`
          },
        },
      },
    },


    scales: {

      x: {

        grid: {
          display: false,
        },

        ticks: {
          color: "#64748b",
        },
      },


      yAsset: {

        type: "linear",

        position: "left",

        beginAtZero: true,

        ticks: {

          stepSize:
          CHART_STEP_SIZE,

          callback: (
              value,
          ) =>
              formatChartMoney(
                  Number(value),
              ),
        },

        grid: {
          color:
              "rgba(148, 163, 184, 0.15)",
        },
      },


      yFlow: {

        type: "linear",

        position: "right",

        beginAtZero: true,

        ticks: {

          stepSize:
          CHART_STEP_SIZE,

          callback: (
              value,
          ) =>
              formatChartMoney(
                  Number(value),
              ),
        },

        grid: {
          drawOnChartArea: false,
        },
      },
    },
  }


  // ===================================================
  // 공통 Layout
  //
  // [변경] 예전에는 이 페이지만 Header + Sidebar + <main>을 직접 조립하고
  // AssetDashboardPage.css로 꾸몄다. 다른 페이지들처럼 AppLayout을 쓰고
  // 스타일은 전부 Tailwind 클래스로 옮겨서 CSS 파일 없이 tsx 하나로 동작한다.
  // (AppLayout이 pt-16 / 사이드바 여백을 처리하므로 본문이 고정 헤더 밑으로 숨지 않는다)
  // ===================================================

  const renderLayout = (
      content: ReactNode,
  ) => (
      <AppLayout pageClassName="bg-[#f7f9fc] text-slate-900">
        {content}
      </AppLayout>
  )


  // ===================================================
  // Loading
  // ===================================================

  if (loading) {

    return renderLayout(

        <div className={STATE_BOX}>

          <div className="h-[34px] w-[34px] animate-spin rounded-full border-[3px] border-slate-200 border-t-sky-600" />


          <h2 className="mt-2.5 text-[17px] font-bold text-slate-700">
            자산 정보를 불러오는 중입니다.
          </h2>


          <p className="text-xs text-slate-400">
            잠시만 기다려주세요.
          </p>

        </div>,

    )
  }


  // ===================================================
  // Error
  // ===================================================

  if (
      error
      || !dashboard
  ) {

    return renderLayout(

        <div className={STATE_BOX}>

          <h2 className="mt-2.5 text-[17px] font-bold text-slate-700">
            자산 정보를 불러오지 못했습니다.
          </h2>


          <p className="text-xs text-slate-400">
            {error}
          </p>


          {!isLoggedIn && (

              <button
                  type="button"
                  className="mt-2.5 cursor-pointer rounded-[10px] bg-sky-600 px-[18px] py-2.5 text-xs font-bold text-white transition hover:-translate-y-px hover:bg-sky-700"
                  onClick={() =>
                      navigate("/login")
                  }
              >
                로그인하기
              </button>

          )}

        </div>,

    )
  }


  // ===================================================
  // Render
  // ===================================================

  return renderLayout(

      <div className="mx-auto w-full max-w-[1500px] px-4 pb-[50px] pt-7 md:px-6 md:pb-[70px] md:pt-10 xl:px-10">


        {/* ================================================= */}
        {/* 자산관리 페이지 제목 */}
        {/* ================================================= */}

        <section className="mb-[30px] flex flex-col items-start gap-[15px] md:flex-row md:items-end md:justify-between md:gap-[30px]">

          <div>

            <p className="mb-2 text-[10px] font-black tracking-[0.18em] text-sky-600">
              TRIPPAMINE ASSET
            </p>


            <h1 className="text-[22px] font-extrabold tracking-[-0.04em] text-slate-900 sm:text-[25px] md:text-[30px]">
              나의 여행 자산 관리
            </h1>


            <p className="mt-2.5 text-sm leading-relaxed text-slate-500">
              가계부와 연결된 자산과 지출을
              한눈에 확인해보세요.
            </p>

          </div>


          <div className="flex w-full flex-col items-start gap-[3px] rounded-[13px] border border-slate-200 bg-white px-[15px] py-3 shadow-[0_3px_12px_rgba(15,23,42,0.03)] md:w-auto md:min-w-[155px] md:items-end">

          <span className="text-[10px] text-slate-400">
            현재 조회
          </span>


            <strong className="text-[13px] text-slate-700">
              {dashboard.year}년{" "}
              {dashboard.month}월
            </strong>


            {dashboard.snapshotDate && (

                <small className="text-[9px] text-slate-400">
                  최근 자산 기준{" "}
                  {dashboard.snapshotDate}
                </small>

            )}

          </div>

        </section>


        {/* ================================================= */}
        {/* Summary */}
        {/* ================================================= */}

        <section className="mb-[22px] grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">

          {[
            {
              label: "총자산",
              value: dashboard.totalAsset,
              desc: "현재 연결된 계좌의 총자산",
              color: "text-slate-900",
            },
            {
              label: "이번 달 수입",
              value: dashboard.totalIncome,
              desc: "이번 달 가계부 기준 수입",
              color: "text-green-600",
            },
            {
              label: "이번 달 지출",
              value: dashboard.totalExpense,
              desc: "이번 달 가계부 기준 지출",
              color: "text-red-600",
            },
            {
              label: "여행 지출",
              value: dashboard.travelExpense,
              desc: "여행 관련 누적 지출",
              color: "text-fuchsia-600",
            },
          ].map((card) => (

              <article
                  key={card.label}
                  className={`${CARD_BASE} flex min-h-[100px] flex-col justify-center gap-[5px] rounded-[18px] p-5 transition duration-200 hover:-translate-y-0.5 hover:border-sky-200 hover:shadow-[0_10px_26px_rgba(15,23,42,0.07)] md:min-h-[125px]`}
              >

            <span className="text-[11px] font-bold text-slate-500">
              {card.label}
            </span>


                <strong className={`break-words text-lg font-extrabold tracking-[-0.035em] sm:text-[21px] ${card.color}`}>
                  {formatMoney(card.value)}
                </strong>


                <p className="text-[10px] text-slate-400">
                  {card.desc}
                </p>

              </article>

          ))}

        </section>


        {/* ================================================= */}
        {/* Charts */}
        {/* ================================================= */}

        <section className="mb-[22px] grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1.7fr)_minmax(320px,0.8fr)]">


          {/* ================================================= */}
          {/* 자산 흐름 */}
          {/* ================================================= */}

          <article className={CARD}>

            <div className={CARD_HEADER}>

              <div>

              <span className={CARD_KICKER}>
                ASSET FLOW
              </span>


                <h2 className={CARD_TITLE}>
                  자산 흐름
                </h2>


                <p className={CARD_DESC}>
                  총자산 · 수입 · 지출 변화를
                  확인해보세요.
                </p>

              </div>


              <div className="whitespace-nowrap rounded-full bg-sky-50 px-2.5 py-1.5 text-[10px] font-bold text-sky-600">
                1눈금 50만원
              </div>

            </div>


            <div className="relative h-[300px] w-full sm:h-[330px] md:h-[390px] xl:h-[420px]">

              {assetHistory.length > 0 ? (

                  <Line
                      data={
                        assetChartData
                      }
                      options={
                        assetChartOptions
                      }
                  />

              ) : (

                  <div className={EMPTY}>
                    아직 자산 흐름 데이터가 없습니다.
                  </div>

              )}

            </div>

          </article>


          {/* ================================================= */}
          {/* 소비 패턴 */}
          {/* ================================================= */}

          <article className={CARD}>

            <div className={CARD_HEADER}>

              <div>

              <span className={CARD_KICKER}>
                SPENDING
              </span>


                <h2 className={CARD_TITLE}>
                  소비 패턴
                </h2>


                <p className={CARD_DESC}>
                  이번 달 카테고리별 지출입니다.
                </p>

              </div>

            </div>


            {categoryExpenses.length > 0 ? (

                <>

                  <div className="relative mx-auto h-[240px] w-full max-w-[260px] md:h-[275px] md:max-w-[310px]">

                    <Doughnut
                        data={
                          doughnutData
                        }
                        options={
                          doughnutOptions
                        }
                    />

                  </div>


                  {highestCategory && (

                      <div className="mt-[18px] rounded-[14px] border border-sky-100 bg-[#f8fcff] p-4 text-center">

                  <span className="mb-1 block text-[10px] text-slate-400">
                    가장 많이 사용한 항목
                  </span>


                        <strong className="block text-[17px] font-extrabold text-sky-600">

                          {
                              CATEGORY_NAMES[
                                  highestCategory.category
                                      ?.toUpperCase()
                                  ]

                              ?? highestCategory.category
                          }

                        </strong>


                        <p className="mt-[5px] text-[13px] font-bold text-slate-700">

                          {formatMoney(
                              highestCategory.amount,
                          )}

                        </p>

                      </div>

                  )}

                </>

            ) : (

                <div className={EMPTY}>
                  이번 달 지출 데이터가 없습니다.
                </div>

            )}

          </article>

        </section>


        {/* ================================================= */}
        {/* Budget / Goal */}
        {/* ================================================= */}

        <section className="grid grid-cols-1 gap-5 xl:grid-cols-2">


          {/* ================================================= */}
          {/* Budget */}
          {/* ================================================= */}

          <article className={CARD}>

            <div className={CARD_HEADER}>

              <div>

              <span className={CARD_KICKER}>
                BUDGET
              </span>


                <h2 className={CARD_TITLE}>
                  월간 예산
                </h2>


                <p className={CARD_DESC}>
                  카테고리별 예산을 확인하세요.
                </p>

              </div>

            </div>


            {dashboard.budgets.length > 0 ? (

                <div className="flex flex-col">

                  {dashboard.budgets.map(

                      (budget) => (

                          <div
                              className="flex min-h-[65px] items-center justify-between gap-5 border-b border-slate-100 px-0.5 py-[13px] last:border-b-0"
                              key={
                                budget.budgetId
                              }
                          >

                            <div className="flex flex-col gap-1">

                      <span className="text-[9px] text-slate-400">
                        카테고리
                      </span>


                              <strong className="text-[13px] text-slate-700">

                                {
                                    CATEGORY_NAMES[
                                        budget.categoryName
                                            ?.toUpperCase()
                                        ]

                                    ?? budget.categoryName
                                }

                              </strong>

                            </div>


                            <strong className="text-sm text-sky-600">

                              {formatMoney(
                                  budget.budgetAmount,
                              )}

                            </strong>

                          </div>

                      ),

                  )}

                </div>

            ) : (

                <div className={EMPTY}>
                  등록된 월간 예산이 없습니다.
                </div>

            )}

          </article>


          {/* ================================================= */}
          {/* Goal */}
          {/* ================================================= */}

          <article className={CARD}>

            <div className={CARD_HEADER}>

              <div>

              <span className={CARD_KICKER}>
                GOAL
              </span>


                <h2 className={CARD_TITLE}>
                  자산 목표
                </h2>


                <p className={CARD_DESC}>
                  여행과 자산 목표를 관리하세요.
                </p>

              </div>

            </div>


            {dashboard.goals.length > 0 ? (

                <div className="flex flex-col gap-[22px]">

                  {dashboard.goals.map(

                      (goal) => {

                        const calculatedRate =

                            goal.targetAmount > 0

                                ? Math.min(

                                    100,

                                    Math.max(

                                        0,

                                        (
                                            goal.currentAmount
                                            / goal.targetAmount
                                        ) * 100,

                                    ),

                                )

                                : 0


                        const progressRate =

                            goal.progressRate
                            ?? calculatedRate


                        return (

                            <div
                                className="border-b border-slate-100 pb-5 last:border-b-0 last:pb-0"
                                key={
                                  goal.goalId
                                }
                            >

                              <div className="mb-3 flex items-start justify-between gap-5">

                                <div className="flex flex-col gap-[3px]">

                          <span className="text-[9px] text-slate-400">
                            목표
                          </span>


                                  <strong className="text-sm text-slate-900">
                                    {goal.goalName}
                                  </strong>

                                </div>


                                <strong className="text-sm text-slate-900">

                                  {Math.round(
                                      progressRate,
                                  )}%

                                </strong>

                              </div>


                              <div className="h-2 w-full overflow-hidden rounded-full bg-slate-200">

                                <div
                                    className="h-full rounded-full bg-gradient-to-r from-sky-600 to-sky-400 transition-[width] duration-500"
                                    style={{

                                      width:
                                          `${Math.min(
                                              100,
                                              progressRate,
                                          )}%`,

                                    }}
                                />

                              </div>


                              <div className="mt-[9px] flex flex-col items-start justify-between gap-1 text-[10px] text-slate-500 sm:flex-row sm:items-center sm:gap-[15px]">

                        <span>

                          현재{" "}

                          {formatMoney(
                              goal.currentAmount,
                          )}

                        </span>


                                <span>

                          목표{" "}

                                  {formatMoney(
                                      goal.targetAmount,
                                  )}

                        </span>

                              </div>


                              {goal.targetDate && (

                                  <small className="mt-2 block text-[9px] text-slate-400">

                                    목표일{" "}

                                    {goal.targetDate}

                                  </small>

                              )}

                            </div>

                        )
                      },

                  )}

                </div>

            ) : (

                <div className={EMPTY}>
                  등록된 자산 목표가 없습니다.
                </div>

            )}

          </article>

        </section>

      </div>,

  )
}
