import {
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react"

import { useNavigate } from "react-router-dom"

import {
  Chart as ChartJS,
  BarElement,
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
  Bar,
  Line,
} from "react-chartjs-2"

import AppLayout from "../layout/AppLayout"

import { useAuth } from "../context/AuthContext"
import { API_BASE_URL } from "../api/axios"
import AssetAdvicePanel
  from "../components/asset/AssetAdvicePanel"
// =====================================================
// 공통 Tailwind 클래스 (예전 AssetDashboardPage.css를 대체)
// =====================================================

const CARD_BASE =
    "tp-card min-w-0"

const CARD =
    `${CARD_BASE} p-5 md:p-7`

const CARD_HEADER =
    "mb-5 flex min-h-[58px] flex-col items-start gap-2 md:flex-row md:justify-between md:gap-5"

const CARD_KICKER =
    "tp-kicker mb-1.5 inline-block"

const CARD_TITLE =
    "tp-heading text-xl"

const CARD_DESC =
    "mt-[5px] text-[11px] leading-normal text-slate-400"

const EMPTY =
    "flex min-h-[220px] items-center justify-center rounded-2xl border border-dashed border-sky-100 bg-sky-50/30 text-center text-xs text-slate-400"

const STATE_BOX =
    "flex min-h-[calc(100vh-4rem)] flex-col items-center justify-center gap-2 text-center"


// =====================================================
// Chart.js 등록
// =====================================================

ChartJS.register(
    BarElement,
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    Tooltip,
    Legend,
    Filler,

)

ChartJS.defaults.font.family =
    '"Pretendard Variable", Pretendard, system-ui, sans-serif'
ChartJS.defaults.color = "#64748b"


// =====================================================
// Type
// =====================================================

interface BudgetInfo {
  budgetId: number
  // 백엔드 MonthlyBudget.categoryId (예: FOOD) — 카테고리명(categoryName)은 "식비" 같은 한글
  categoryId?: string | null
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
// =====================================================
// 기간별 자산 흐름 API 응답
// =====================================================

interface AssetHistoryPoint {
  date: string
  amount: number
}

interface CashFlowPoint {
  date: string
  income: number
  expense: number
}

interface AssetHistoryResponseData {
  period: string
  startDate: string
  endDate: string

  startAsset: number
  currentAsset: number

  changeAmount: number
  changeRate: number

  totalIncome: number
  totalExpense: number
  netCashFlow: number

  assetHistory: AssetHistoryPoint[]
  cashFlow: CashFlowPoint[]
}

type AssetHistoryPeriod =
    | "WEEK"
    | "MONTH"
    | "QUARTER"
    | "YEAR"


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

  // 이번 달 시작 자산
  startAsset: number

  // 현재자산 - 시작자산
  assetChangeAmount: number

  // 자산 증감률 (%)
  assetChangeRate: number

  assetHistory: AssetHistoryInfo[]
  categoryExpenses: CategoryExpenseInfo[]
  budgets: BudgetInfo[]
  goals: GoalInfo[]
}
interface AssetForecastData {
  currentAsset: number
  averageMonthlyIncome: number
  averageMonthlyExpense: number
  monthlySaving: number

  after3Months: number
  after6Months: number
  after12Months: number

  goalId: number | null
  goalName: string | null
  goalTargetAmount: number | null
  goalCurrentAmount: number | null
  goalRemainingAmount: number | null

  monthsToGoal: number | null
  expectedGoalDate: string | null

  forecastMessage: string
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
  NEGO: "AI 타임네고",
  ETC: "기타",
}


// =====================================================
// 카테고리 색상
// =====================================================

const CATEGORY_COLORS: Record<string, string> = {
  FOOD: "#7c6cff",
  TRANSPORT: "#22d3ee",
  MEDICAL: "#fb7185",
  CULTURE: "#c084fc",
  SHOPPING: "#fbbf24",
  HOUSING: "#64748b",
  SALARY: "#34d399",
  TRAVEL: "#f472b6",
  ETC: "#94a3b8",
}




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
// 미래 자산 시뮬레이터
// ===================================================

const [
  forecast,
  setForecast,
] = useState<AssetForecastData | null>(null)

const [
  forecastLoading,
  setForecastLoading,
] = useState(false)

const [
  forecastError,
  setForecastError,
] = useState<string | null>(null)
const [
  selectedTravelGoalId,
  setSelectedTravelGoalId,
] = useState<number | null>(null)

// ===================================================
// 미래자산 시뮬레이터 - 사용자가 설정하는 월 저축액
// ===================================================

const [
  customMonthlySaving,
  setCustomMonthlySaving,
] = useState("")
// ===================================================
// 여행 목표 수정
// ===================================================

const [
  editingGoalId,
  setEditingGoalId,
] = useState<number | null>(null)

const [
  editGoalName,
  setEditGoalName,
] = useState("")

const [
  editTargetAmount,
  setEditTargetAmount,
] = useState("")

const [
  editCurrentAmount,
  setEditCurrentAmount,
] = useState("")

const [
  editTargetDate,
  setEditTargetDate,
] = useState("")

const [
  updatingGoal,
  setUpdatingGoal,
] = useState(false)


// 수정 후 미래자산 시뮬레이터 재조회용
const [
  forecastRefreshKey,
  setForecastRefreshKey,
] = useState(0)

  const [
    assetHistoryPeriod,
    setAssetHistoryPeriod,
  ] = useState<AssetHistoryPeriod>("MONTH")

  const [
    periodAssetHistory,
    setPeriodAssetHistory,
  ] = useState<AssetHistoryInfo[]>([])

  const [
    assetHistoryLoading,
    setAssetHistoryLoading,
  ] = useState(false)

  const [
    assetHistoryError,
    setAssetHistoryError,
  ] = useState<string | null>(null)


  // Dashboard 응답 도착 여부 (기간별 흐름 조회 순서 제어용)
  const dashboardReady =
      dashboard !== null

// ===================================================
// 선택 가능한 여행 목표
// ===================================================

const travelGoals =
    useMemo(() => {

      return (dashboard?.goals ?? [])
          .filter((goal) => {

            const goalType =
                goal.goalType?.toUpperCase()

            const status =
                goal.status?.toUpperCase()


            return (
                goalType === "TRAVEL"
                &&
                (
                    status == null
                    || status === "ACTIVE"
                )
            )
          })

    }, [
      dashboard?.goals,
    ])
    // ===================================================
// 기본 여행 목표 선택
// ===================================================

useEffect(() => {

  if (travelGoals.length === 0) {

    setSelectedTravelGoalId(null)

    return
  }


  setSelectedTravelGoalId(
      (previousGoalId) => {

        // 기존에 선택한 여행이 목록에 남아있으면 그대로 유지
        if (
            previousGoalId != null
            &&
            travelGoals.some(
                (goal) =>
                    goal.goalId === previousGoalId,
            )
        ) {

          return previousGoalId
        }


        // 처음 들어왔으면 첫 번째 여행 선택
        return travelGoals[0].goalId
      },
  )

}, [
  travelGoals,
])
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
// 미래 자산 Forecast API
// ===================================================

useEffect(() => {

  if (
      !isLoggedIn
      || userId == null
  ) {

    setForecast(null)
    return
  }
  // Dashboard가 먼저 로딩될 때까지 기다림
if (!dashboardReady) {

  return
}


// 여행 목표가 있는데 아직 기본 목표 선택 전이라면 잠시 기다림
if (
    travelGoals.length > 0
    &&
    selectedTravelGoalId == null
) {

  return
}


  const fetchForecast =
      async () => {

        try {

          setForecastLoading(true)
          setForecastError(null)


          const accessToken =
              localStorage.getItem(
                  "accessToken",
              )


          if (!accessToken) {

            throw new Error(
                "로그인 토큰을 확인할 수 없습니다.",
            )
          }


          const forecastUrl =
          selectedTravelGoalId != null

              ? `${API_BASE_URL}/api/assets/dashboard/forecast?goalId=${selectedTravelGoalId}`

              : `${API_BASE_URL}/api/assets/dashboard/forecast`


         const response =
             await fetch(

              forecastUrl,

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


          if (!response.ok) {

            const errorText =
                await response.text()


            console.error(
                "Asset Forecast API Error =",
                errorText,
            )


            throw new Error(
                "미래 자산 예측 정보를 불러오지 못했습니다.",
            )
          }


          const data: AssetForecastData =
              await response.json()


          console.log(
              "Asset Forecast 응답 =",
              data,
          )


          setForecast(data)

        } catch (error) {

          console.error(
              "Asset Forecast Error =",
              error,
          )


          setForecast(null)


          setForecastError(

              error instanceof Error

                  ? error.message

                  : "미래 자산 예측 정보를 불러오지 못했습니다.",

          )

        } finally {

          setForecastLoading(false)
        }
      }


  fetchForecast()

}, [
  userId,
  isLoggedIn,
  dashboardReady,
  selectedTravelGoalId,
  travelGoals.length,
  forecastRefreshKey,
])

// ===================================================
// Forecast가 처음 로딩되면
// 기존 월 예상 저축액을 입력창의 기본값으로 사용
// ===================================================

useEffect(() => {

  if (
      forecast?.monthlySaving == null
  ) {
    return
  }


  setCustomMonthlySaving(
      String(
          forecast.monthlySaving,
      ),
  )

}, [
  forecast?.goalId,
  forecast?.monthlySaving,
])
// ===================================================
// 사용자가 설정한 월 저축액을 기준으로
// 미래 자산 / 목표 달성 시점 다시 계산
// ===================================================

const simulatedForecast =
    useMemo(() => {

      if (!forecast) {
        return null
      }


      // 입력창 값
      const parsedSaving =
          Number(
              customMonthlySaving
                  .replace(
                      /,/g,
                      "",
                  ),
          )


      // 음수 방지
      const monthlySaving =
          Number.isFinite(
              parsedSaving,
          )

              ? Math.max(
                  0,
                  parsedSaving,
              )

              : 0


      // =========================================
      // 미래 총자산
      // =========================================

      const after3Months =
          forecast.currentAsset
          +
          monthlySaving * 3


      const after6Months =
          forecast.currentAsset
          +
          monthlySaving * 6


      const after12Months =
          forecast.currentAsset
          +
          monthlySaving * 12


      // =========================================
      // 여행 목표까지 남은 금액
      // =========================================

      const remainingAmount =
          Math.max(
              0,
              Number(
                  forecast.goalRemainingAmount
                  ?? 0,
              ),
          )


      let monthsToGoal:
          number | null =
          null


      let expectedGoalDate:
          string | null =
          null


      // =========================================
      // 목표 달성까지 필요한 개월 수
      // =========================================

      if (
          forecast.goalName
          &&
          remainingAmount <= 0
      ) {

        monthsToGoal = 0

      } else if (
          forecast.goalName
          &&
          monthlySaving > 0
          &&
          remainingAmount > 0
      ) {

        monthsToGoal =
            Math.ceil(
                remainingAmount
                /
                monthlySaving,
            )
      }


      // =========================================
      // 예상 달성 날짜
      // =========================================

      if (
          monthsToGoal !== null
      ) {

        const date =
            new Date()


        date.setMonth(
            date.getMonth()
            +
            monthsToGoal,
        )


        const year =
            date.getFullYear()


        const month =
            String(
                date.getMonth() + 1,
            ).padStart(
                2,
                "0",
            )


        const day =
            String(
                date.getDate(),
            ).padStart(
                2,
                "0",
            )


        expectedGoalDate =
            `${year}-${month}-${day}`
      }


      // =========================================
      // ASSET COACH 메시지
      // =========================================

      let forecastMessage:
          string


      if (!forecast.goalName) {

        forecastMessage =
            `월 ${monthlySaving.toLocaleString("ko-KR")}원을 저축하면 `
            +
            `1년 후 예상 자산은 `
            +
            `${after12Months.toLocaleString("ko-KR")}원입니다.`

      } else if (
          remainingAmount <= 0
      ) {

        forecastMessage =
            `${forecast.goalName} 목표 금액을 이미 달성했습니다.`

      } else if (
          monthlySaving <= 0
      ) {

        forecastMessage =
            "월 저축액이 0원이므로 현재 설정으로는 목표 달성 시점을 계산할 수 없습니다."

      } else {

        forecastMessage =
            `매월 ${monthlySaving.toLocaleString("ko-KR")}원을 저축하면 `
            +
            `약 ${monthsToGoal}개월 후 `
            +
            `${forecast.goalName} 목표를 달성할 수 있습니다.`
      }


      return {

        ...forecast,

        monthlySaving,

        after3Months,
        after6Months,
        after12Months,

        monthsToGoal,
        expectedGoalDate,

        forecastMessage,
      }

    }, [
      forecast,
      customMonthlySaving,
    ])

  // ===================================================
  // 기간별 자산 흐름 API
  // ===================================================

  useEffect(() => {
    if (!isLoggedIn || userId == null) {
      setPeriodAssetHistory([])
      return
    }

    const fetchAssetHistory = async () => {
      try {
        setAssetHistoryLoading(true)
        setAssetHistoryError(null)

        const accessToken = localStorage.getItem("accessToken")

        if (!accessToken) {
          throw new Error("로그인 토큰을 확인할 수 없습니다.")
        }

        const response = await fetch(
            `${API_BASE_URL}/api/assets/dashboard/history?period=${assetHistoryPeriod}`,
            {
              method: "GET",
              headers: {
                Accept: "application/json",
                Authorization: `Bearer ${accessToken}`,
              },
            },
        )

        if (!response.ok) {
          const errorText = await response.text()
          console.error("Asset History API Error =", errorText)
          throw new Error("자산 흐름 정보를 불러오지 못했습니다.")
        }

       const data: AssetHistoryResponseData =
    await response.json()


console.log(
    "Asset History 응답 =",
    data,
)


// ===================================================
// cashFlow를 날짜별로 빠르게 찾기 위한 Map
// ===================================================

const cashFlowMap =
    new Map<
        string,
        CashFlowPoint
    >()


for (
    const item
    of data.cashFlow ?? []
) {

  cashFlowMap.set(
      item.date,
      item,
  )
}


// ===================================================
// 백엔드의 assetHistory + cashFlow를
// 기존 Chart 형식으로 합친다.
// ===================================================

const mergedHistory: AssetHistoryInfo[] =
    (data.assetHistory ?? [])
        .map(
            (assetPoint) => {

              const cashFlow =
                  cashFlowMap.get(
                      assetPoint.date,
                  )


              return {

                snapshotDate:
                    assetPoint.date,

                totalAsset:
                    Number(
                        assetPoint.amount
                        ?? 0,
                    ),

                totalIncome:
                    Number(
                        cashFlow?.income
                        ?? 0,
                    ),

                totalExpense:
                    Number(
                        cashFlow?.expense
                        ?? 0,
                    ),
              }
            },
        )


console.log(
    "그래프 변환 데이터 =",
    mergedHistory,
)


setPeriodAssetHistory(
    mergedHistory,
)
      } catch (error) {
        console.error("Asset History Error =", error)
        setPeriodAssetHistory([])
        setAssetHistoryError(
            error instanceof Error
                ? error.message
                : "자산 흐름 정보를 불러오지 못했습니다.",
        )
      } finally {
        setAssetHistoryLoading(false)
      }
    }

    // [수정] 오늘 Snapshot은 Dashboard 조회(GET /api/assets/dashboard) 때 저장된다.
    // 두 요청을 동시에 보내면 첫 방문 날에는 오늘 데이터가 그래프에서 빠지므로
    // Dashboard 응답을 받은 뒤에 기간별 흐름을 조회한다.
    if (!dashboardReady) {
      return
    }

    fetchAssetHistory()
  }, [
    userId,
    isLoggedIn,
    assetHistoryPeriod,
    dashboardReady,
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
// 여행 목표 삭제
// ===================================================

const handleDeleteGoal =
    async (
        goalId: number,
        goalName: string,
    ) => {

      const confirmed =
          window.confirm(
              `"${goalName}" 여행 목표를 삭제하시겠습니까?`,
          )


      if (!confirmed) {
        return
      }


      try {

        const accessToken =
            localStorage.getItem(
                "accessToken",
            )


        if (!accessToken) {

          throw new Error(
              "로그인 토큰을 확인할 수 없습니다.",
          )
        }


        const response =
            await fetch(
                `${API_BASE_URL}/api/assets/advice/travel-goal/${goalId}`,
                {
                  method: "DELETE",

                  headers: {
                    Authorization:
                        `Bearer ${accessToken}`,
                  },
                },
            )


        if (!response.ok) {

          const errorText =
              await response.text()


          console.error(
              "여행 목표 삭제 실패 =",
              errorText,
          )


          throw new Error(
              "여행 목표를 삭제하지 못했습니다.",
          )
        }


        // =========================================
        // 화면의 자산 목표 목록에서도 제거
        // =========================================

        setDashboard(
            (previous) => {

              if (!previous) {
                return previous
              }


              return {
                ...previous,

                goals:
                    previous.goals.filter(
                        (goal) =>
                            goal.goalId !== goalId,
                    ),
              }
            },
        )


        // =========================================
        // 현재 시뮬레이터에서 선택 중인 목표를
        // 삭제한 경우 선택 초기화
        // =========================================

        if (
            selectedTravelGoalId === goalId
        ) {

          setSelectedTravelGoalId(
              null,
          )

          setForecast(
              null,
          )
        }


        alert(
            "여행 목표가 삭제되었습니다.",
        )

      } catch (error) {

        console.error(
            error,
        )


        alert(
            error instanceof Error
                ? error.message
                : "여행 목표 삭제 중 문제가 발생했습니다.",
        )
      }
    }

// ===================================================
// 여행 목표 수정 시작
// ===================================================

const handleStartEdit =
    (
        goal: GoalInfo,
    ) => {

      setEditingGoalId(
          goal.goalId,
      )

      setEditGoalName(
          goal.goalName,
      )

      setEditTargetAmount(
          String(
              goal.targetAmount
              ?? 0,
          ),
      )

      setEditCurrentAmount(
          String(
              goal.currentAmount
              ?? 0,
          ),
      )

      setEditTargetDate(
          goal.targetDate
          ?? "",
      )
    }


// ===================================================
// 여행 목표 수정 취소
// ===================================================

const handleCancelEdit =
    () => {

      setEditingGoalId(null)

      setEditGoalName("")
      setEditTargetAmount("")
      setEditCurrentAmount("")
      setEditTargetDate("")
    }


// ===================================================
// 여행 목표 수정 저장
// ===================================================

const handleUpdateGoal =
    async (
        goalId: number,
    ) => {

      // =========================================
      // 1. 입력값 검사
      // =========================================

      if (
          editGoalName.trim() === ""
      ) {

        alert(
            "여행 이름을 입력해주세요.",
        )

        return
      }


      if (
          Number(editTargetAmount) <= 0
      ) {

        alert(
            "목표 금액은 1원 이상이어야 합니다.",
        )

        return
      }


      if (
          Number(editCurrentAmount) < 0
      ) {

        alert(
            "현재 모은 금액은 0원 이상이어야 합니다.",
        )

        return
      }


      if (!editTargetDate) {

        alert(
            "목표 날짜를 선택해주세요.",
        )

        return
      }


      try {

        setUpdatingGoal(true)


        // =========================================
        // 2. JWT 토큰
        // =========================================

        const accessToken =
            localStorage.getItem(
                "accessToken",
            )


        if (!accessToken) {

          throw new Error(
              "로그인 토큰을 확인할 수 없습니다.",
          )
        }


        // =========================================
        // 3. 수정된 값을 숫자로 변환
        // =========================================

        const newTargetAmount =
            Number(
                editTargetAmount,
            )


        const newCurrentAmount =
            Number(
                editCurrentAmount,
            )


        // =========================================
        // 4. 새로운 목표 달성률 계산
        //
        // 현재금액 ÷ 목표금액 × 100
        // =========================================

        const newProgressRate =

            newTargetAmount > 0

                ? Math.min(
                    100,
                    Math.max(
                        0,
                        (
                            newCurrentAmount
                            /
                            newTargetAmount
                        ) * 100,
                    ),
                )

                : 0


        console.log(
            "새 목표 금액 =",
            newTargetAmount,
        )

        console.log(
            "새 현재 금액 =",
            newCurrentAmount,
        )

        console.log(
            "새 달성률 =",
            newProgressRate,
        )


        // =========================================
        // 5. 백엔드 PUT 요청
        // =========================================

        const response =
            await fetch(

                `${API_BASE_URL}/api/assets/advice/travel-goal/${goalId}`,

                {
                  method: "PUT",

                  headers: {

                    Accept:
                        "application/json",

                    "Content-Type":
                        "application/json",

                    Authorization:
                        `Bearer ${accessToken}`,
                  },


                  body:
                      JSON.stringify({

                        goalName:
                            editGoalName.trim(),

                        targetAmount:
                            newTargetAmount,

                        currentAmount:
                            newCurrentAmount,

                        targetDate:
                            editTargetDate,
                      }),
                },
            )


        console.log(
            "여행 목표 수정 HTTP 상태 =",
            response.status,
        )


        // =========================================
        // 6. PUT 실패
        // =========================================

        if (!response.ok) {

          const errorText =
              await response.text()


          console.error(
              "여행 목표 수정 실패 =",
              errorText,
          )


          throw new Error(
              `여행 목표 수정 실패 (${response.status})`,
          )
        }


        // =========================================
        // ★ 7. 화면의 Dashboard를 즉시 변경
        //
        // 여기가 이번 수정의 핵심입니다.
        // 새로고침하지 않아도 React 화면이 즉시 바뀝니다.
        // =========================================

        setDashboard(
            (previous) => {

              if (!previous) {

                return previous
              }


              return {

                ...previous,


                goals:
                    previous.goals.map(
                        (goal) => {


                          // 수정한 목표가 아니면
                          // 그대로 유지
                          if (
                              goal.goalId
                              !== goalId
                          ) {

                            return goal
                          }


                          // 수정한 여행 목표라면
                          // 새로운 값으로 교체
                          return {

                            ...goal,


                            goalName:
                                editGoalName.trim(),


                            targetAmount:
                                newTargetAmount,


                            currentAmount:
                                newCurrentAmount,


                            targetDate:
                                editTargetDate,


                            progressRate:
                                newProgressRate,
                          }
                        },
                    ),
              }
            },
        )


        // =========================================
        // 8. 미래자산 시뮬레이터 다시 계산
        // =========================================

        setForecastRefreshKey(
            (previous) =>
                previous + 1,
        )


        // =========================================
        // 9. 수정창 닫기
        // =========================================

        handleCancelEdit()


        // =========================================
        // 10. 완료
        // =========================================

        alert(
            "여행 목표가 수정되었습니다.",
        )


      } catch (error) {

        console.error(
            "여행 목표 수정 오류 =",
            error,
        )


        alert(

            error instanceof Error

                ? error.message

                : "여행 목표 수정 중 문제가 발생했습니다.",

        )


      } finally {

        setUpdatingGoal(false)
      }
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
  // 소비 패턴 — 카테고리 랭킹
  //
  // [변경] 도넛 + 퍼센트 라벨 + 범례 조합은 조각이 많아질수록
  // 비교가 어려웠다. 금액 순으로 정렬한 가로 막대 목록으로 바꿔서
  // "무엇에 얼마나 썼는지"를 위에서부터 바로 읽을 수 있게 한다.
  // ===================================================

  const spendingRanking =
      useMemo(() => {

        const total =
            categoryExpenses.reduce(
                (sum, item) => sum + Number(item.amount ?? 0),
                0,
            )

        const sorted =
            [...categoryExpenses]
                .map((item) => ({
                  key: item.category?.toUpperCase() ?? "ETC",
                  label:
                      CATEGORY_NAMES[item.category?.toUpperCase()]
                      ?? item.category
                      ?? "기타",
                  amount: Number(item.amount ?? 0),
                }))
                .filter((item) => item.amount > 0)
                .sort((a, b) => b.amount - a.amount)

        const max =
            sorted[0]?.amount ?? 0

        return {
          total,
          items: sorted.map((item) => ({
            ...item,
            share: total > 0 ? (item.amount / total) * 100 : 0,
            width: max > 0 ? (item.amount / max) * 100 : 0,
          })),
        }
      }, [
        categoryExpenses,
      ])


  // ===================================================
  // Asset Change
  // ===================================================

  const assetChangeRate =
      dashboard?.assetChangeRate ?? 0


  const assetChangeAmount =
      dashboard?.assetChangeAmount ?? 0


  const isAssetIncrease =
      assetChangeRate > 0


  const isAssetDecrease =
      assetChangeRate < 0


  const assetChangeIcon =
      isAssetIncrease
          ? "↑"
          : isAssetDecrease
              ? "↓"
              : "−"




  const assetChangeDescription =
      isAssetIncrease
          ? "이번 달 자산이 증가했어요"
          : isAssetDecrease
              ? "이번 달 자산이 감소했어요"
              : "이번 달 자산 변동이 없어요"
  // ===================================================
  // Asset History
  // ===================================================

  const assetHistory =
      periodAssetHistory


  const assetHistoryPeriodLabel:
      Record<AssetHistoryPeriod, string> = {
    WEEK: "1주일",
    MONTH: "1개월",
    QUARTER: "1분기",
    YEAR: "1년",
  }

  const assetHistoryPeriods: {
    value: AssetHistoryPeriod
    label: string
  }[] = [
    { value: "WEEK", label: "1주일" },
    { value: "MONTH", label: "1개월" },
    { value: "QUARTER", label: "1분기" },
    { value: "YEAR", label: "1년" },
  ]


  // ===================================================
  // 차트 공통
  //
  // [변경] 예전 자산 흐름 그래프는 총자산(왼쪽 축)과 수입·지출(오른쪽 축)을
  // 한 그래프에 겹쳐서, 어떤 선이 어떤 축을 따르는지 알기 어려웠다.
  // → 축이 하나인 그래프 두 개로 분리한다.
  //   1) 총자산 추이 : 선 하나 (보라)
  //   2) 수입 vs 지출 : 묶음 막대 (파랑 / 주황 — 색각이상에서도 구분되는 조합)
  // ===================================================

  const COLOR_ASSET = "#6250d6"
  const COLOR_INCOME = "#2a78d6"
  const COLOR_EXPENSE = "#eb6834"

  const historyLabels =
      useMemo(() =>
              assetHistory.map((item) => {

                if (!item.snapshotDate) {
                  return "-"
                }

                const date =
                    new Date(`${item.snapshotDate}T00:00:00`)

                if (Number.isNaN(date.getTime())) {
                  return item.snapshotDate
                }

                return `${date.getMonth() + 1}/${date.getDate()}`
              }),
          [
            assetHistory,
          ])


  // ===================================================
  // 1) 총자산 추이 (Line)
  // ===================================================

  const assetChartData =
      useMemo(() => ({
        labels: historyLabels,
        datasets: [
          {
            label: "총자산",
            data: assetHistory.map((item) => item.totalAsset ?? 0),
            borderColor: COLOR_ASSET,
            borderWidth: 2,
            tension: 0.35,
            fill: true,
            backgroundColor: (context: { chart: ChartJS }) => {
              const { ctx, chartArea } = context.chart
              if (!chartArea) {
                return "rgba(98, 80, 214, 0.08)"
              }
              const gradient =
                  ctx.createLinearGradient(0, chartArea.top, 0, chartArea.bottom)
              gradient.addColorStop(0, "rgba(98, 80, 214, 0.22)")
              gradient.addColorStop(1, "rgba(98, 80, 214, 0)")
              return gradient
            },
            // 점은 숨기고, 마지막 값과 hover 지점만 강조
            pointRadius: assetHistory.map((_, index) =>
                index === assetHistory.length - 1 ? 5 : 0,
            ),
            pointHoverRadius: 6,
            pointBackgroundColor: COLOR_ASSET,
            pointBorderColor: "#ffffff",
            pointBorderWidth: 2,
          },
        ],
      }), [
        assetHistory,
        historyLabels,
      ])


  const assetChartOptions:
      ChartOptions<"line"> = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: {
      mode: "index",
      intersect: false,
    },
    plugins: {
      // 선이 하나뿐이라 범례 대신 카드 제목이 이름을 대신한다
      legend: {
        display: false,
      },
      tooltip: {
        backgroundColor: "#0b0b16",
        padding: 12,
        cornerRadius: 12,
        displayColors: false,
        callbacks: {
          title: (items) => items[0]?.label ?? "",
          label: (context) =>
              `총자산 ${formatMoney(Number(context.raw ?? 0))}`,
        },
      },
    },
    scales: {
      x: {
        grid: { display: false },
        border: { display: false },
        ticks: {
          color: "#94a3b8",
          maxTicksLimit: 6,
          maxRotation: 0,
        },
      },
      y: {
        // 0부터 시작하면 변화가 납작해져서 보이지 않으므로 데이터 범위에 맞춘다
        grace: "15%",
        border: { display: false },
        ticks: {
          color: "#94a3b8",
          maxTicksLimit: 4,
          callback: (value) => formatChartMoney(Number(value)),
        },
        grid: {
          color: "rgba(148, 163, 184, 0.15)",
        },
      },
    },
  }


  // ===================================================
  // 2) 수입 vs 지출 (Bar)
  // ===================================================

  const flowChartData =
      useMemo(() => ({
        labels: historyLabels,
        datasets: [
          {
            label: "수입",
            data: assetHistory.map((item) => item.totalIncome ?? 0),
            backgroundColor: COLOR_INCOME,
            borderRadius: 4,
            borderSkipped: "bottom" as const,
            maxBarThickness: 14,
            categoryPercentage: 0.6,
            barPercentage: 0.85,
          },
          {
            label: "지출",
            data: assetHistory.map((item) => item.totalExpense ?? 0),
            backgroundColor: COLOR_EXPENSE,
            borderRadius: 4,
            borderSkipped: "bottom" as const,
            maxBarThickness: 14,
            categoryPercentage: 0.6,
            barPercentage: 0.85,
          },
        ],
      }), [
        assetHistory,
        historyLabels,
      ])


  const flowChartOptions:
      ChartOptions<"bar"> = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: {
      mode: "index",
      intersect: false,
    },
    plugins: {
      // 범례는 카드 안 HTML(색 점 + 이름)로 직접 표시
      legend: {
        display: false,
      },
      tooltip: {
        backgroundColor: "#0b0b16",
        padding: 12,
        cornerRadius: 12,
        boxPadding: 4,
        usePointStyle: true,
        callbacks: {
         label: (context) =>
              ` ${context.dataset.label} ${formatMoney(Number(context.raw ?? 0))}`,
        },
      },
    },
    scales: {
      x: {
        grid: { display: false },
        border: { display: false },
        ticks: {
          color: "#94a3b8",
          maxTicksLimit: 6,
          maxRotation: 0,
        },
      },
      y: {
        beginAtZero: true,
        border: { display: false },
        ticks: {
          color: "#94a3b8",
          maxTicksLimit: 4,
          callback: (value) => formatChartMoney(Number(value)),
        },
        grid: {
          color: "rgba(148, 163, 184, 0.15)",
        },
      },
    },
  }


  // 선택 기간의 합계 / 시작 대비 변화 (그래프 위 요약 숫자)
const periodSummary =
    useMemo(() => {

      if (
          assetHistory.length === 0
      ) {

        return null
      }


      const first =
          assetHistory[0]

      const last =
          assetHistory[
              assetHistory.length - 1
          ]


      const totalIncome =
          assetHistory.reduce(

              (
                  sum,
                  item,
              ) =>

                  sum
                  + Number(
                      item.totalIncome
                      ?? 0,
                  ),

              0,
          )


      const totalExpense =
          assetHistory.reduce(

              (
                  sum,
                  item,
              ) =>

                  sum
                  + Number(
                      item.totalExpense
                      ?? 0,
                  ),

              0,
          )


      return {

        latestAsset:
            last.totalAsset
            ?? 0,

        assetDiff:
            (
                last.totalAsset
                ?? 0
            )
            -
            (
                first.totalAsset
                ?? 0
            ),

        income:
            totalIncome,

        expense:
            totalExpense,

        latestDate:
            last.snapshotDate,
      }

    }, [
      assetHistory,
    ])
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
      <AppLayout pageClassName="tp-aurora-light text-slate-900">
        {content}
      </AppLayout>
  )


  // ===================================================
  // Loading
  // ===================================================

  if (loading) {

    return renderLayout(

        <div className={STATE_BOX}>

          <div className="h-[34px] w-[34px] animate-spin rounded-full border-[3px] border-sky-100 border-t-sky-500" />


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
  // AI 인사이트 (응답 데이터로만 계산하는 요약 문장)
  // ===================================================

  const totalExpenseAmount =
      categoryExpenses.reduce(
          (sum, item) => sum + Number(item.amount ?? 0),
          0,
      )

  const savingRate =
      dashboard.totalIncome > 0
          ? Math.round(
              ((dashboard.totalIncome - dashboard.totalExpense)
                  / dashboard.totalIncome) * 100,
          )
          : null

  const insights: { icon: string; text: ReactNode }[] = []

  insights.push({
    icon: isAssetIncrease ? "📈" : isAssetDecrease ? "📉" : "➖",
    text: (
        <>
          {assetChangeDescription}{" "}
          <b className="text-white">
            ({assetChangeAmount > 0 ? "+" : ""}{formatMoney(assetChangeAmount)})
          </b>
        </>
    ),
  })

  if (highestCategory) {
    const share =
        totalExpenseAmount > 0
            ? Math.round((highestCategory.amount / totalExpenseAmount) * 100)
            : 0

    insights.push({
      icon: "🧾",
      text: (
          <>
            <b className="text-white">
              {CATEGORY_NAMES[highestCategory.category?.toUpperCase()] ?? highestCategory.category}
            </b>{" "}
            지출이 가장 많아요 · 전체 지출의 {share}%
          </>
      ),
    })
  }

  if (savingRate !== null) {
    insights.push({
      icon: savingRate >= 0 ? "💰" : "⚠️",
      text: savingRate >= 0
          ? <>이번 달 수입의 <b className="text-white">{savingRate}%</b>를 남기고 있어요</>
          : <>이번 달 지출이 수입보다 <b className="text-white">{Math.abs(savingRate)}%</b> 많아요</>,
    })
  }

  if (dashboard.goals.length > 0) {
    insights.push({
      icon: "🎯",
      text: <>진행 중인 자산 목표 <b className="text-white">{dashboard.goals.length}개</b>를 관리하고 있어요</>,
    })
  }


  // ===================================================
  // Render
  // ===================================================

  return renderLayout(
      <div className="mx-auto w-full max-w-[1400px] px-4 pb-16 pt-6 md:px-6 md:pt-8 xl:px-10">

        {/* ================================================= */}
        {/* AI 자산 리포트 히어로 */}
        {/* ================================================= */}

        <section className="relative isolate mb-6 overflow-hidden rounded-[2rem] bg-night-950 p-6 text-white shadow-[0_40px_80px_-50px_rgba(60,40,180,0.85)] md:p-9">
          <div className="absolute inset-0 -z-10 tp-aurora" />
          <div className="absolute inset-0 -z-10 tp-dot-grid opacity-40 [mask-image:linear-gradient(90deg,transparent,black_60%)]" />

          <div className="grid grid-cols-1 gap-8 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)] lg:items-end">
            <div>
              <span className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/5 px-3 py-1 text-[10px] font-black tracking-[0.2em] text-emerald-200">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-300 shadow-[0_0_8px_rgba(110,231,183,0.9)]" />
                AI ASSET REPORT · {dashboard.year}.{String(dashboard.month).padStart(2, "0")}
              </span>

              <p className="mt-6 text-sm text-white/55">현재 총자산</p>

              <p className="mt-1 break-words font-display text-4xl font-bold tracking-[-0.03em] md:text-6xl">
                {formatMoney(dashboard.totalAsset)}
              </p>

              <div className="mt-4 flex flex-wrap items-center gap-2">
                <span
                    className={`inline-flex items-center gap-1 rounded-full px-3 py-1 text-xs font-bold ${
                        isAssetIncrease
                            ? "bg-emerald-400/15 text-emerald-300"
                            : isAssetDecrease
                                ? "bg-rose-400/15 text-rose-300"
                                : "bg-white/10 text-white/60"
                    }`}
                >
                  {assetChangeIcon} {assetChangeRate > 0 ? "+" : ""}{assetChangeRate.toFixed(1)}%
                </span>
                <span className="text-xs text-white/45">
                  이번 달 시작 대비 {assetChangeAmount > 0 ? "+" : ""}{formatMoney(assetChangeAmount)}
                </span>
              </div>
            </div>

            {/* AI 인사이트 */}
            <div className="rounded-3xl border border-white/10 bg-white/[0.05] p-5 backdrop-blur">
              <p className="flex items-center gap-2 text-[11px] font-black tracking-[0.18em] text-violet-200">
                <span className="flex h-6 w-6 items-center justify-center rounded-lg tp-gradient-bg text-[11px] text-white">AI</span>
                이번 달 인사이트
              </p>

              <ul className="mt-4 space-y-2.5">
                {insights.map((insight, index) => (
                    <li
                        key={index}
                        className="flex items-start gap-3 rounded-2xl bg-white/[0.04] px-3.5 py-3 text-[13px] leading-relaxed text-white/65"
                    >
                      <span className="mt-px">{insight.icon}</span>
                      <span>{insight.text}</span>
                    </li>
                ))}
              </ul>
            </div>
          </div>
        </section>


        {/* ================================================= */}
        {/* Summary */}
        {/* ================================================= */}

        <section className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <article className={`${CARD_BASE} p-5 tp-lift`}>
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-slate-500">이번 달 수입</span>
              <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-emerald-50 text-sm text-emerald-600">↓</span>
            </div>
            <strong className="mt-3 block break-words font-display text-2xl font-bold tracking-tight text-slate-900">
              {formatMoney(dashboard.totalIncome)}
            </strong>
            <p className="mt-1 text-[11px] text-slate-400">가계부 기준 수입</p>
          </article>

          <article className={`${CARD_BASE} p-5 tp-lift`}>
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-slate-500">이번 달 지출</span>
              <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-rose-50 text-sm text-rose-500">↑</span>
            </div>
            <strong className="mt-3 block break-words font-display text-2xl font-bold tracking-tight text-slate-900">
              {formatMoney(dashboard.totalExpense)}
            </strong>
            <p className="mt-1 text-[11px] text-slate-400">가계부 기준 지출</p>
          </article>

          <article className={`${CARD_BASE} p-5 tp-lift`}>
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-slate-500">저축률</span>
              <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-sky-50 text-sm text-sky-600">%</span>
            </div>
            <strong className="mt-3 block font-display text-2xl font-bold tracking-tight text-slate-900">
              {savingRate === null ? "-" : `${savingRate}%`}
            </strong>
            <div className="mt-2.5 h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
              <div
                  className="h-full rounded-full tp-gradient-bg"
                  style={{ width: `${Math.min(100, Math.max(0, savingRate ?? 0))}%` }}
              />
            </div>
          </article>
        </section>


        {/* ================================================= */}
        {/* Charts */}
        {/* ================================================= */}

        <section className="mb-6 grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1.6fr)_minmax(340px,1fr)]">

          {/* 자산 흐름 — 총자산 추이 + 수입/지출 (축이 하나인 그래프 두 개) */}
          <article className={CARD}>
            <div className={CARD_HEADER}>
              <div>
                <span className={CARD_KICKER}>ASSET FLOW</span>
                <h2 className={CARD_TITLE}>자산 흐름</h2>
                <p className={CARD_DESC}>
                  {assetHistoryPeriodLabel[assetHistoryPeriod]} 동안 총자산과 돈의 흐름이에요.
                </p>
              </div>

              <div className="flex flex-wrap gap-1 rounded-full bg-slate-100/80 p-1">
                {assetHistoryPeriods.map((period) => {
                  const selected =
                      assetHistoryPeriod === period.value

                  return (
                      <button
                          key={period.value}
                          type="button"
                          disabled={assetHistoryLoading}
                          onClick={() =>
                              setAssetHistoryPeriod(period.value)
                          }
                          className={`rounded-full px-3.5 py-1.5 text-[11px] font-bold transition ${
                              selected
                                  ? "bg-night-950 text-white shadow-sm"
                                  : "text-slate-500 hover:text-slate-800"
                          } ${
                              assetHistoryLoading
                                  ? "cursor-wait opacity-60"
                                  : "cursor-pointer"
                          }`}
                      >
                        {period.label}
                      </button>
                  )
                })}
              </div>
            </div>

            {assetHistoryLoading ? (
                <div className={EMPTY}>선택한 기간의 자산 흐름을 불러오는 중입니다.</div>
            ) : assetHistoryError ? (
                <div className={EMPTY}>{assetHistoryError}</div>
            ) : assetHistory.length === 0 || !periodSummary ? (
                <div className={EMPTY}>선택한 기간에 자산 흐름 데이터가 없습니다.</div>
            ) : (
                <div className="space-y-6">

                  {/* ① 총자산 추이 */}
                  <div>
                    <div className="mb-3 flex flex-wrap items-end justify-between gap-2">
                      <div>
                        <p className="flex items-center gap-1.5 text-xs font-bold text-slate-500">
                          <span className="h-0.5 w-4 rounded-full" style={{ backgroundColor: COLOR_ASSET }} />
                          총자산
                        </p>
                        <p className="mt-1 font-display text-2xl font-bold tracking-tight text-slate-900">
                          {formatMoney(periodSummary.latestAsset)}
                        </p>
                      </div>
                      <span
                          className={`rounded-full px-2.5 py-1 text-[11px] font-bold ${
                              periodSummary.assetDiff > 0
                                  ? "bg-emerald-50 text-emerald-700"
                                  : periodSummary.assetDiff < 0
                                      ? "bg-rose-50 text-rose-600"
                                      : "bg-slate-100 text-slate-500"
                          }`}
                      >
                        {periodSummary.assetDiff > 0 ? "▲ " : periodSummary.assetDiff < 0 ? "▼ " : ""}
                        기간 시작 대비 {periodSummary.assetDiff > 0 ? "+" : ""}{formatMoney(periodSummary.assetDiff)}
                      </span>
                    </div>

                    <div className="relative h-[220px] w-full md:h-[240px]">
                      <Line
                          data={assetChartData}
                          options={assetChartOptions}
                      />
                    </div>
                  </div>

                  {/* ② 수입 vs 지출 */}
                  <div className="border-t border-slate-100 pt-5">
                    <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
                      <div>
                        <p className="text-xs font-bold text-slate-500">일별 수입 · 지출</p>
                        <p className="mt-0.5 text-[10px] text-slate-400">
                          선택 기간의 날짜별 수입과 지출을 보여드려요.
                        </p>
                      </div>

                      {/* 범례 겸 최근 기록일의 월 누적값 */}
                      <div className="flex items-center gap-4 text-xs">
                        <span className="flex items-center gap-1.5 text-slate-500">
                          <span className="h-2.5 w-2.5 rounded-[3px]" style={{ backgroundColor: COLOR_INCOME }} />
                          수입 <b className="text-slate-900">{formatMoney(periodSummary.income)}</b>
                        </span>
                        <span className="flex items-center gap-1.5 text-slate-500">
                          <span className="h-2.5 w-2.5 rounded-[3px]" style={{ backgroundColor: COLOR_EXPENSE }} />
                          지출 <b className="text-slate-900">{formatMoney(periodSummary.expense)}</b>
                        </span>
                      </div>
                    </div>

                    <div className="relative h-[170px] w-full md:h-[180px]">
                      <Bar
                          data={flowChartData}
                          options={flowChartOptions}
                      />
                    </div>
                  </div>
                </div>
            )}
          </article>

          {/* 소비 패턴 — 금액순 가로 막대 */}
          <article className={CARD}>
            <div className={CARD_HEADER}>
              <div>
                <span className={CARD_KICKER}>SPENDING</span>
                <h2 className={CARD_TITLE}>소비 패턴</h2>
                <p className={CARD_DESC}>이번 달 어디에 가장 많이 썼는지 순서대로 보여드려요.</p>
              </div>
            </div>

            {spendingRanking.items.length > 0 ? (
                <>
                  <div className="mb-5 flex items-end justify-between rounded-2xl bg-slate-50 px-4 py-3.5">
                    <span className="text-xs font-bold text-slate-500">이번 달 총 지출</span>
                    <strong className="font-display text-xl font-bold tracking-tight text-slate-900">
                      {formatMoney(spendingRanking.total)}
                    </strong>
                  </div>

                  <ol className="space-y-4">
                    {spendingRanking.items.map((item, index) => (
                        <li key={item.key}>
                          <div className="mb-1.5 flex items-baseline justify-between gap-3 text-sm">
                            <span className="flex items-center gap-2 font-semibold text-slate-800">
                              <span className="w-4 text-xs font-bold text-slate-400">{index + 1}</span>
                              {item.label}
                            </span>
                            <span className="shrink-0 text-slate-500">
                              <b className="font-bold text-slate-900">{formatMoney(item.amount)}</b>
                              <span className="ml-1.5 inline-block w-11 text-right text-xs">
                                {item.share.toFixed(0)}%
                              </span>
                            </span>
                          </div>

                          <div className="ml-6 h-2 overflow-hidden rounded-full bg-slate-100">
                            <div
                                className="h-full rounded-full transition-[width] duration-700"
                                style={{
                                  width: `${Math.max(item.width, 2)}%`,
                                  // 1위만 진하게, 나머지는 같은 색의 옅은 단계 — 순위가 색이 아니라 길이로 읽히도록
                                  backgroundColor: index === 0 ? COLOR_ASSET : "rgba(98, 80, 214, 0.45)",
                                }}
                            />
                          </div>
                        </li>
                    ))}
                  </ol>
                </>
            ) : (
                <div className={EMPTY}>이번 달 지출 데이터가 없습니다.</div>
            )}
          </article>
        </section>
        {/* ================================================= */}
        {/* 여행자금 맞춤 자산관리 */}
        {/* ================================================= */}

        <AssetAdvicePanel />

        {/* ================================================= */}
        {/* Budget / Goal */}
        {/* ================================================= */}

        <section className="grid grid-cols-1 gap-5 xl:grid-cols-2">

           <article className="relative overflow-hidden rounded-[2rem] bg-night-950 p-6 text-white shadow-[0_35px_70px_-45px_rgba(79,70,229,0.8)] md:p-8">

    <div className="absolute inset-0 tp-aurora opacity-70" />

    <div className="relative z-10">

      <div className="mb-7 flex flex-col gap-3 md:flex-row md:items-end md:justify-between">

        <div>

          <span className="text-[10px] font-black tracking-[0.2em] text-violet-300">
            FUTURE ASSET
          </span>

          <h2 className="mt-2 font-display text-2xl font-bold md:text-3xl">
            🔮 미래 자산 시뮬레이터
          </h2>

          <p className="mt-2 text-xs leading-relaxed text-white/50">
            최근 소비와 수입 패턴을 분석해 앞으로의 자산을 예상합니다.
          </p>

        </div>


       {forecast?.monthlySaving != null && (

    <div className="min-w-[250px] rounded-2xl border border-white/10 bg-white/[0.06] px-4 py-3">

      <div className="flex items-center justify-between gap-3">

        <p className="text-[10px] font-bold text-white/50">
          월 저축 설정
        </p>


        <button
            type="button"
            onClick={() =>
                setCustomMonthlySaving(
                    String(
                        forecast.monthlySaving,
                    ),
                )
            }
            className="cursor-pointer text-[10px] font-bold text-violet-300 transition hover:text-violet-200"
        >
          추천값으로
        </button>

      </div>


      <div className="mt-2 flex items-center gap-2">

        <input
            type="number"
            min="0"
            step="10000"
            value={
              customMonthlySaving
            }
            onChange={(event) => {

              setCustomMonthlySaving(
                  event.target.value,
              )

            }}
            className="w-full rounded-xl border border-white/10 bg-white px-3 py-2 text-right text-sm font-bold text-slate-900 outline-none focus:border-violet-400"
        />

        <span className="shrink-0 text-xs font-bold text-white/60">
          원
        </span>

      </div>


      <div className="mt-2 flex items-center justify-between gap-3">

        <span className="text-[10px] text-white/35">
          기본 추천
        </span>

        <span className="text-[10px] font-semibold text-white/55">
          {formatMoney(
              forecast.monthlySaving,
          )}
        </span>

      </div>

    </div>

)}
      </div>
        {/* ================================================= */}
{/* 여행 목표 선택 */}
{/* ================================================= */}

<div className="mb-6 rounded-2xl border border-white/10 bg-white/[0.05] p-4 md:p-5">

  <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">

    <div>

      <p className="text-[10px] font-black tracking-[0.15em] text-sky-300">
        SELECT TRAVEL GOAL
      </p>

      <p className="mt-1 text-sm font-bold text-white">
        분석할 여행 목표
      </p>

    </div>


    {travelGoals.length > 0 ? (

        <select
            value={
              selectedTravelGoalId
              ?? ""
            }
            onChange={(event) => {

              setSelectedTravelGoalId(
                  Number(
                      event.target.value,
                  ),
              )

            }}
            className="min-w-[240px] cursor-pointer rounded-xl border border-white/10 bg-white px-4 py-3 text-sm font-bold text-slate-900 outline-none"
        >

          {travelGoals.map(
              (goal) => (

                  <option
                      key={goal.goalId}
                      value={goal.goalId}
                  >
                    {goal.goalName}
                  </option>

              ),
          )}

        </select>

    ) : (

        <span className="text-xs text-white/50">
          등록된 여행 목표가 없습니다.
        </span>

    )}

  </div>

</div>


      {forecastLoading ? (

          <div className="flex min-h-[220px] items-center justify-center">

            <p className="text-sm text-white/50">
              미래 자산을 계산하고 있습니다...
            </p>

          </div>

      ) : forecastError ? (

          <div className="flex min-h-[220px] items-center justify-center">

            <p className="text-sm text-rose-300">
              {forecastError}
            </p>

          </div>

      ) : forecast ? (

          <>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">

              <div className="rounded-2xl border border-white/10 bg-white/[0.05] p-5">

                <span className="text-xs text-white/40">
                  3개월 후 내 예측 자산
                </span>

                <strong className="mt-2 block font-display text-xl font-bold md:text-2xl">
                  {formatMoney(
                      simulatedForecast?.after3Months,
                  )}
                </strong>

              </div>


              <div className="rounded-2xl border border-violet-400/20 bg-violet-400/[0.08] p-5">

                <span className="text-xs text-violet-200">
                  6개월 후 내 예측 자산
                </span>

                <strong className="mt-2 block font-display text-xl font-bold text-violet-100 md:text-2xl">
                  {formatMoney(
                      simulatedForecast?.after6Months,
                  )}
                </strong>

              </div>


              <div className="rounded-2xl border border-white/10 bg-white/[0.05] p-5">

                <span className="text-xs text-white/40">
                  1년 후 내 예측 자산
                </span>

                <strong className="mt-2 block font-display text-xl font-bold md:text-2xl">
                  {formatMoney(
                    simulatedForecast?.after12Months,
                )}
                </strong>

              </div>

            </div>


            {forecast.goalName && (

                <div className="mt-5 grid grid-cols-1 gap-4 lg:grid-cols-[1fr_1.3fr]">

                  <div className="rounded-2xl border border-white/10 bg-white/[0.05] p-5">

                    <p className="text-[10px] font-black tracking-[0.15em] text-sky-300">
                      TRAVEL GOAL
                    </p>

                    <h3 className="mt-2 text-lg font-bold">
                      ✈️ {forecast.goalName}
                    </h3>


                    <div className="mt-5 space-y-3 text-sm">

                      <div className="flex justify-between gap-4">

                        <span className="text-white/45">
                          목표 금액
                        </span>

                        <strong>
                          {formatMoney(forecast.goalTargetAmount)}
                        </strong>

                      </div>


                      <div className="flex justify-between gap-4">

                        <span className="text-white/45">
                          현재 모은 금액
                        </span>

                        <strong>
                          {formatMoney(forecast.goalCurrentAmount)}
                        </strong>

                      </div>


                      <div className="flex justify-between gap-4">

                        <span className="text-white/45">
                          남은 금액
                        </span>

                        <strong className="text-violet-200">
                          {formatMoney(forecast.goalRemainingAmount)}
                        </strong>

                      </div>

                    </div>

                  </div>


                  <div className="rounded-2xl border border-emerald-300/15 bg-emerald-300/[0.06] p-5 md:p-6">

                    <p className="text-[10px] font-black tracking-[0.15em] text-emerald-300">
                      EXPECTED ACHIEVEMENT
                    </p>


                   {simulatedForecast?.monthsToGoal !== null ? (

                        <>

                          <p className="mt-3 text-sm text-white/55">
                            현재 소비패턴을 유지하면
                          </p>

                          <p className="mt-1 font-display text-3xl font-bold text-emerald-300 md:text-4xl">
                            약 {simulatedForecast?.monthsToGoal}개월 후
                          </p>


                          {simulatedForecast?.expectedGoalDate && (

                              <p className="mt-2 text-xs text-white/40">

                                예상 달성일 ·{" "}

                                {simulatedForecast?.expectedGoalDate}

                              </p>

                          )}

                        </>

                    ) : (

                        <p className="mt-4 text-sm text-white/60">
                          현재 소비패턴으로는 목표 달성 시점을 계산하기 어렵습니다.
                        </p>

                    )}

                  </div>

                </div>

            )}


            <div className="mt-5 flex items-start gap-3 rounded-2xl border border-violet-400/15 bg-violet-400/[0.07] p-4 md:p-5">

              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl tp-gradient-bg text-xs font-black">
                AI
              </span>

              <div>

                <p className="text-[10px] font-black tracking-[0.14em] text-violet-200">
                  ASSET COACH
                </p>

                <p className="mt-1.5 text-sm leading-relaxed text-white/70">
                {simulatedForecast?.forecastMessage}
              </p>

              </div>

            </div>

          </>

      ) : (

          <div className="flex min-h-[220px] items-center justify-center">

            <p className="text-sm text-white/50">
              미래 자산 예측 데이터가 없습니다.
            </p>

          </div>

      )}

    </div>

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
        등록한 여행 목표를 관리하세요.
      </p>

    </div>

  </div>


  {travelGoals.length > 0 ? (

      <div className="flex flex-col gap-3">

        {travelGoals.map(
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
                    calculatedRate


              return (

                  <div
                      key={goal.goalId}
                      className="rounded-2xl border border-slate-100 bg-white p-4"
                  >

                    {/* ================================= */}
                    {/* 목표 이름 + 달성률 + 삭제 버튼 */}
                    {/* ================================= */}

                    <div className="mb-3 flex items-start justify-between gap-5">

                      <div>

                        <span className="block text-[10px] text-slate-400">
                          여행 목표
                        </span>

                        <strong className="text-sm text-slate-900">
                          {goal.goalName}
                        </strong>

                      </div>


                     <div className="flex items-center gap-2">

                  <strong className="font-display text-2xl font-bold tracking-tight tp-gradient-text">
                    {Math.round(progressRate)}%
                  </strong>


                  <button
                      type="button"

                      onClick={() =>
                          handleStartEdit(
                              goal,
                          )
                      }

                      className="cursor-pointer rounded-lg border border-sky-100 bg-sky-50 px-2.5 py-1.5 text-[11px] font-bold text-sky-600 transition hover:bg-sky-100"
                  >
                    수정
                  </button>


                  <button
                      type="button"

                      onClick={() =>
                          handleDeleteGoal(
                              goal.goalId,
                              goal.goalName,
                          )
                      }

                      className="cursor-pointer rounded-lg border border-rose-100 bg-rose-50 px-2.5 py-1.5 text-[11px] font-bold text-rose-500 transition hover:bg-rose-100"
                  >
                    삭제
                  </button>

                </div>

                    </div>
                  {editingGoalId === goal.goalId && (

                    <div className="mb-4 rounded-2xl border border-sky-100 bg-sky-50/50 p-4">

                      <p className="mb-3 text-xs font-bold text-sky-700">
                        여행 목표 수정
                      </p>


                      <div className="grid grid-cols-1 gap-3">

                        {/* 여행 이름 */}
                        <div>

                          <label className="mb-1 block text-[11px] font-bold text-slate-500">
                            여행 이름
                          </label>

                          <input
                              type="text"
                              value={editGoalName}
                              onChange={(event) =>
                                  setEditGoalName(
                                      event.target.value,
                                  )
                              }
                              placeholder="예: 일본 도쿄 여행"
                              className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-900 outline-none focus:border-sky-400"
                          />

                        </div>


                        {/* 목표 금액 */}
                        <div>

                          <label className="mb-1 block text-[11px] font-bold text-slate-500">
                            목표 금액
                          </label>

                          <input
                              type="number"
                              value={editTargetAmount}
                              onChange={(event) =>
                                  setEditTargetAmount(
                                      event.target.value,
                                  )
                              }
                              placeholder="예: 3000000"
                              className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-900 outline-none focus:border-sky-400"
                          />

                        </div>


                        {/* 현재 모은 금액 */}
                        <div>

                          <label className="mb-1 block text-[11px] font-bold text-slate-500">
                            현재 모은 금액
                          </label>

                          <input
                              type="number"
                              value={editCurrentAmount}
                              onChange={(event) =>
                                  setEditCurrentAmount(
                                      event.target.value,
                                  )
                              }
                              placeholder="예: 1000000"
                              className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-900 outline-none focus:border-sky-400"
                          />

                        </div>


                        {/* 목표 날짜 */}
                        <div>

                          <label className="mb-1 block text-[11px] font-bold text-slate-500">
                            목표 날짜
                          </label>

                          <input
                              type="date"
                              value={editTargetDate}
                              onChange={(event) =>
                                  setEditTargetDate(
                                      event.target.value,
                                  )
                              }
                              className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-900 outline-none focus:border-sky-400"
                          />

                        </div>

                      </div>


                      {/* 취소 / 저장 */}
                      <div className="mt-4 flex justify-end gap-2">

                        <button
                            type="button"
                            onClick={handleCancelEdit}
                            disabled={updatingGoal}
                            className="cursor-pointer rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-bold text-slate-500 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          취소
                        </button>


                        <button
                            type="button"
                            disabled={updatingGoal}
                            onClick={() =>
                                handleUpdateGoal(
                                    goal.goalId,
                                )
                            }
                            className="cursor-pointer rounded-lg bg-sky-600 px-3 py-2 text-xs font-bold text-white transition hover:bg-sky-700 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          {updatingGoal
                              ? "저장 중..."
                              : "저장"}
                        </button>

                      </div>

                    </div>

                )}        

                    {/* ================================= */}
                    {/* 진행률 */}
                    {/* ================================= */}

                    <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100">

                      <div
                          className="h-full rounded-full tp-gradient-bg transition-[width] duration-700"

                          style={{
                            width:
                                `${Math.min(
                                    100,
                                    progressRate,
                                )}%`,
                          }}
                      />

                    </div>


                    {/* ================================= */}
                    {/* 금액 */}
                    {/* ================================= */}

                    <div className="mt-2.5 flex flex-col items-start justify-between gap-1 text-[11px] text-slate-500 sm:flex-row sm:items-center">

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

                        {goal.targetDate
                            && ` · ${goal.targetDate}`}

                      </span>

                    </div>

                  </div>

              )

            },
        )}

      </div>

  ) : (

      <div className={EMPTY}>
        등록된 여행 목표가 없습니다.
      </div>

  )}

</article>


        </section>


      </div>,
  )
}
