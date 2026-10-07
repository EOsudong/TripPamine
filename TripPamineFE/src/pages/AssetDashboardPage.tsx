import {
  useEffect,
  useMemo,
  useState,
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

import Header from "../components/Header"
import Sidebar from "../components/Sidebar"

import { useAuth } from "../context/AuthContext"

import "./AssetDashboardPage.css"


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
  // Sidebar
  // ===================================================

  const [
    sidebarOpen,
    setSidebarOpen,
  ] = useState(false)


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
              `http://localhost:8080/api/assets/dashboard?year=${year}&month=${month}`,
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
  // 메인 Home과 동일하게
  // Header + Sidebar를 사용합니다.
  // ===================================================

  const renderLayout = (
    content: React.ReactNode,
  ) => {

    return (

      <div className="asset-page">

        <Sidebar
          open={sidebarOpen}
          onClose={() =>
            setSidebarOpen(false)
          }
        />


        <Header
          sidebarOpen={sidebarOpen}
          onToggleSidebar={() =>
            setSidebarOpen(
              (prev) => !prev,
            )
          }
        />


        <main
          className={
            sidebarOpen
              ? "asset-main asset-main-open"
              : "asset-main"
          }
        >

          {content}

        </main>

      </div>
    )
  }


  // ===================================================
  // Loading
  // ===================================================

  if (loading) {

    return renderLayout(

      <div className="asset-state-box">

        <div className="asset-loading-spinner" />


        <h2>
          자산 정보를 불러오는 중입니다.
        </h2>


        <p>
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

      <div className="asset-state-box">

        <h2>
          자산 정보를 불러오지 못했습니다.
        </h2>


        <p>
          {error}
        </p>


        {!isLoggedIn && (

          <button
            type="button"
            className="asset-login-button"
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

    <div className="asset-container">


      {/* ================================================= */}
      {/* 자산관리 페이지 제목 */}
      {/* ================================================= */}

      <section className="asset-header">

        <div>

          <p className="asset-eyebrow">
            TRIPPAMINE ASSET
          </p>


          <h1>
            나의 여행 자산 관리
          </h1>


          <p className="asset-description">
            가계부와 연결된 자산과 지출을
            한눈에 확인해보세요.
          </p>

        </div>


        <div className="asset-date-box">

          <span>
            현재 조회
          </span>


          <strong>
            {dashboard.year}년{" "}
            {dashboard.month}월
          </strong>


          {dashboard.snapshotDate && (

            <small>
              최근 자산 기준{" "}
              {dashboard.snapshotDate}
            </small>

          )}

        </div>

      </section>


      {/* ================================================= */}
      {/* Summary */}
      {/* ================================================= */}

      <section className="asset-summary-grid">


        {/* 총자산 */}

        <article className="asset-summary-card">

          <span className="asset-summary-label">
            총자산
          </span>


          <strong className="asset-summary-value">

            {formatMoney(
              dashboard.totalAsset,
            )}

          </strong>


          <p>
            현재 연결된 계좌의 총자산
          </p>

        </article>


        {/* 수입 */}

        <article className="asset-summary-card">

          <span className="asset-summary-label">
            이번 달 수입
          </span>


          <strong
            className="
              asset-summary-value
              asset-income
            "
          >

            {formatMoney(
              dashboard.totalIncome,
            )}

          </strong>


          <p>
            이번 달 가계부 기준 수입
          </p>

        </article>


        {/* 지출 */}

        <article className="asset-summary-card">

          <span className="asset-summary-label">
            이번 달 지출
          </span>


          <strong
            className="
              asset-summary-value
              asset-expense
            "
          >

            {formatMoney(
              dashboard.totalExpense,
            )}

          </strong>


          <p>
            이번 달 가계부 기준 지출
          </p>

        </article>


        {/* 여행 지출 */}

        <article className="asset-summary-card">

          <span className="asset-summary-label">
            여행 지출
          </span>


          <strong
            className="
              asset-summary-value
              asset-travel
            "
          >

            {formatMoney(
              dashboard.travelExpense,
            )}

          </strong>


          <p>
            여행 관련 누적 지출
          </p>

        </article>

      </section>


      {/* ================================================= */}
      {/* Charts */}
      {/* ================================================= */}

      <section className="asset-chart-grid">


        {/* ================================================= */}
        {/* 자산 흐름 */}
        {/* ================================================= */}

        <article
          className="
            asset-card
            asset-line-card
          "
        >

          <div className="asset-card-header">

            <div>

              <span className="asset-card-kicker">
                ASSET FLOW
              </span>


              <h2>
                자산 흐름
              </h2>


              <p>
                총자산 · 수입 · 지출 변화를
                확인해보세요.
              </p>

            </div>


            <div className="asset-chart-unit">
              1눈금 50만원
            </div>

          </div>


          <div className="asset-line-chart">

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

              <div className="asset-empty">
                아직 자산 흐름 데이터가 없습니다.
              </div>

            )}

          </div>

        </article>


        {/* ================================================= */}
        {/* 소비 패턴 */}
        {/* ================================================= */}

        <article
          className="
            asset-card
            asset-category-card
          "
        >

          <div className="asset-card-header">

            <div>

              <span className="asset-card-kicker">
                SPENDING
              </span>


              <h2>
                소비 패턴
              </h2>


              <p>
                이번 달 카테고리별 지출입니다.
              </p>

            </div>

          </div>


          {categoryExpenses.length > 0 ? (

            <>

              <div className="asset-doughnut-chart">

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

                <div className="asset-category-highlight">

                  <span>
                    가장 많이 사용한 항목
                  </span>


                  <strong>

                    {
                      CATEGORY_NAMES[
                        highestCategory.category
                          ?.toUpperCase()
                      ]

                      ?? highestCategory.category
                    }

                  </strong>


                  <p>

                    {formatMoney(
                      highestCategory.amount,
                    )}

                  </p>

                </div>

              )}

            </>

          ) : (

            <div className="asset-empty">
              이번 달 지출 데이터가 없습니다.
            </div>

          )}

        </article>

      </section>


      {/* ================================================= */}
      {/* Budget / Goal */}
      {/* ================================================= */}

      <section className="asset-bottom-grid">


        {/* ================================================= */}
        {/* Budget */}
        {/* ================================================= */}

        <article className="asset-card">

          <div className="asset-card-header">

            <div>

              <span className="asset-card-kicker">
                BUDGET
              </span>


              <h2>
                월간 예산
              </h2>


              <p>
                카테고리별 예산을 확인하세요.
              </p>

            </div>

          </div>


          {dashboard.budgets.length > 0 ? (

            <div className="asset-list">

              {dashboard.budgets.map(

                (budget) => (

                  <div
                    className="asset-list-item"
                    key={
                      budget.budgetId
                    }
                  >

                    <div>

                      <span>
                        카테고리
                      </span>


                      <strong>

                        {
                          CATEGORY_NAMES[
                            budget.categoryName
                              ?.toUpperCase()
                          ]

                          ?? budget.categoryName
                        }

                      </strong>

                    </div>


                    <strong className="asset-list-money">

                      {formatMoney(
                        budget.budgetAmount,
                      )}

                    </strong>

                  </div>

                ),

              )}

            </div>

          ) : (

            <div className="asset-empty">
              등록된 월간 예산이 없습니다.
            </div>

          )}

        </article>


        {/* ================================================= */}
        {/* Goal */}
        {/* ================================================= */}

        <article className="asset-card">

          <div className="asset-card-header">

            <div>

              <span className="asset-card-kicker">
                GOAL
              </span>


              <h2>
                자산 목표
              </h2>


              <p>
                여행과 자산 목표를 관리하세요.
              </p>

            </div>

          </div>


          {dashboard.goals.length > 0 ? (

            <div className="asset-goal-list">

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
                      className="asset-goal-item"
                      key={
                        goal.goalId
                      }
                    >

                      <div className="asset-goal-title-row">

                        <div>

                          <span>
                            목표
                          </span>


                          <strong>
                            {goal.goalName}
                          </strong>

                        </div>


                        <strong>

                          {Math.round(
                            progressRate,
                          )}%

                        </strong>

                      </div>


                      <div className="asset-progress-track">

                        <div
                          className="asset-progress-bar"
                          style={{

                            width:
                              `${Math.min(
                                100,
                                progressRate,
                              )}%`,

                          }}
                        />

                      </div>


                      <div className="asset-goal-money">

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

                        <small>

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

            <div className="asset-empty">
              등록된 자산 목표가 없습니다.
            </div>

          )}

        </article>

      </section>

    </div>,

  )
}