import { useEffect, useState } from "react";

import {
  getAiManagerSettingsApi,
  changeFinanceConsentApi,
} from "../../api/aiManagerSettings";

import type { AiFinanceMonthlySummary } from "../../api/aiManagerHome";

/**
 * 금융 정보 활용 설정 카드.
 *
 * 사용자가 직접 허용해야만 금융 현황이 표시된다.
 * 사용 중지를 누르면 즉시 화면에서 금액을 숨긴다.
 */
interface FinanceConsentCardProps {
  finance: AiFinanceMonthlySummary | null;
  onSettingsChanged: () => Promise<void>;
}

/**
 * 사용자에게 보여줄 항목명 변환.
 *
 * 예:
 * FOOD → 식비
 */
const CATEGORY_LABELS: Record<string, string> = {
  FOOD: "식비",
  TRANSPORT: "교통비",
  SHOPPING: "쇼핑",
  CULTURE: "문화·여가",
  HOUSING: "주거비",
  MEDICAL: "의료비",
  SALARY: "급여",
  TRAVEL: "여행비",
  NEGO: "네고 결제",
  ETC: "기타",
};

function categoryLabel(category: string | null): string {
  if (!category) {
    return "기록 없음";
  }

  // DB에 소문자 또는 공백 포함 코드가 저장된 경우에도 동일한 라벨을 표시한다.
  const normalized = category.trim().toUpperCase();
  return CATEGORY_LABELS[normalized] ?? category;
}

/**
 * 금액을 읽기 쉬운 원화 형식으로 표시한다.
 */
function formatWon(amount: number): string {
  if (!Number.isFinite(amount)) {
    return "-";
  }

  return `${amount.toLocaleString("ko-KR")}원`;
}

export default function FinanceConsentCard({
  finance,
  onSettingsChanged,
}: FinanceConsentCardProps) {
  // null: 아직 설정을 불러오는 중
  const [enabled, setEnabled] = useState<boolean | null>(null);

  const [loading, setLoading] = useState(true);

  const [saving, setSaving] = useState(false);

  const [error, setError] = useState<string | null>(null);
  const [reloadCount, setReloadCount] = useState(0);

  /**
   * 화면을 열었을 때 실제 저장된 설정을 조회한다.
   *
   * 이전 사용자의 정보가 남아 있지 않도록
   * 설정 확인 전에는 금액을 표시하지 않는다.
   */
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setEnabled(null);

    const load = async () => {
      try {
        const settings = await getAiManagerSettingsApi();

        if (!cancelled) {
          setEnabled(settings.financeInsightEnabled);
        }
      } catch {
        if (!cancelled) {
          setError("설정을 불러오지 못했어요. 다시 시도해 주세요.");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    void load();

    return () => {
      cancelled = true;
    };
  }, [reloadCount]);

  /**
   * 사용자가 버튼을 눌렀을 때만 변경한다.
   *
   * 저장 요청이 성공한 뒤 화면에 반영한다.
   * 오류가 발생하면 기존 설정을 유지한다.
   */
  const handleToggle = async () => {
    if (enabled === null || saving) {
      return;
    }

    const nextEnabled = !enabled;

    setSaving(true);
    setError(null);

    try {
      const updated = await changeFinanceConsentApi(nextEnabled);

      setEnabled(updated.financeInsightEnabled);
    } catch {
      setError("설정을 변경하지 못했어요. 잠시 후 다시 시도해 주세요.");
      setSaving(false);
      return;
    }

    /*
     * 설정 저장 후 AI 매니저의 홈 정보를 다시 조회한다.
     *
     * 사용 중지 시 기존 금융 정보도
     * 화면에서 사라지도록 한다.
     */
    try {
      await onSettingsChanged();
    } catch {
      setError(
        "설정은 저장됐지만 정보를 새로 불러오지 못했어요. 화면을 다시 열어 주세요.",
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <section
      className="
                rounded-3xl
                am-surface-card
                p-5

                md:p-6
            "
    >
      <div
        className="
                    flex
                    flex-col
                    gap-4
                    sm:flex-row
                    sm:items-center
                    sm:justify-between
                "
      >
        <div className="max-w-xl">
          <p
            className="
                            text-[11px]
                            font-bold
                            tracking-wide
                            am-accent
                        "
          >
            나의 자산 관리
          </p>

          <h2
            className="
                            mt-1
                            text-lg
                            font-bold
                            am-text
                        "
          >
            이번 달 수입·지출 한눈에 보기
          </h2>

          <p
            className="
                            mt-2
                            text-sm
                            leading-6
                            am-muted
                        "
          >
            허용하면 가계부에 기록한 수입과 지출을 AI 매니저 화면에서 확인할 수
            있어요. 언제든 사용을 중지할 수 있습니다.
          </p>

          <p className="mt-1 text-xs am-muted">
            이 설정으로 결제나 계좌이체가 실행되지는 않아요.
          </p>
        </div>

        <button
          type="button"
          aria-pressed={enabled === true}
          disabled={loading || saving || enabled === null}
          onClick={() => void handleToggle()}
          className={`
                        shrink-0
                        rounded-2xl
                        px-5
                        py-3
                        text-sm
                        font-bold
                        shadow-sm
                        transition
                        disabled:cursor-not-allowed
                        disabled:opacity-50
                        ${
                          enabled
                            ? "am-secondary-button"
                            : "am-primary-button"
                        }
                    `}
        >
          {loading
            ? "확인 중..."
            : saving
              ? "저장 중..."
              : enabled
                ? "사용 중지"
                : "허용하기"}
        </button>
      </div>

      {error && (
        <div className="mt-4">
          <p role="alert" className="text-sm am-error-text">{error}</p>
          {enabled === null && !loading && (
            <button type="button" className="am-secondary-button mt-3" onClick={() => setReloadCount((value) => value + 1)}>
              설정 다시 불러오기
            </button>
          )}
        </div>
      )}

      {/* 허용 여부가 확인된 경우에만 금액을 표시한다. */}
      {enabled === true &&
        (finance ? (
          <div
            className="
                            mt-6
                            grid
                            grid-cols-1
                            gap-3
                            sm:grid-cols-2
                            lg:grid-cols-4
                        "
          >
            <FinanceValue
              label="이번 달 수입"
              value={formatWon(finance.totalIncome)}
            />

            <FinanceValue
              label="이번 달 지출"
              value={formatWon(finance.totalExpense)}
            />

            <FinanceValue
              label="이번 달 수입 − 지출"
              value={formatWon(finance.netAmount)}
            />

            <FinanceValue
              label="가장 큰 지출"
              value={categoryLabel(finance.topCategory)}
            />

            <p
              className="
                                text-xs
                                am-muted
                                sm:col-span-2
                                lg:col-span-4
                            "
            >
              가계부 기록 {finance.sourceRecordCount}건을 기준으로 계산했어요.
              실제 계좌 잔액과는 다를 수 있어요.
            </p>
          </div>
        ) : (
          <p className="mt-5 text-sm am-muted">
            아직 이번 달 수입·지출 요약이 없어요. 가계부 기록을 확인해 주세요.
          </p>
        ))}
    </section>
  );
}

/**
 * 금융 금액을 표시하는 작은 카드.
 *
 * 각 카드에 같은 디자인을 적용하기 위해 분리했다.
 */
function FinanceValue({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl am-inset p-4">
      <p className="text-xs am-muted">{label}</p>

      <p
        className="
                    mt-2
                    break-words
                    text-lg
                    font-bold
                    am-text
                "
      >
        {value}
      </p>
    </div>
  );
}
