import { useEffect, useRef, useState } from "react";
import type { ChangeEvent, FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  ArrowUpRight,
  CalendarDays,
  Check,
  Compass,
  Gift,
  Loader2,
  MapPin,
  Pencil,
  Plus,
  RefreshCw,
  Sparkles,
  Trash2,
  Users,
  Wallet,
} from "lucide-react";
import type { TravelPlan, TravelPlanFormState } from "../types";
import {
  getTravelPlansApi,
  createTravelPlanApi,
  updateTravelPlanApi,
  deleteTravelPlanApi,
} from "../api/travel";
import MysteryTour from "./MysteryTour";

const EMPTY_FORM: TravelPlanFormState = {
  planName: "",
  totalBudget: "",
  companionType: "",
  companionCount: "",
  blindYn: "N",
  startDate: "",
  endDate: "",
};

/** 여행 관리와 미스터리 투어를 공통 작업 화면으로 제공한다. */
export default function Hero() {
  const [activeTab, setActiveTab] = useState<"planner" | "mystery">("planner");
  const [plans, setPlans] = useState<TravelPlan[]>([]);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<TravelPlanFormState>({ ...EMPTY_FORM });
  const [listLoading, setListLoading] = useState(true);
  const [listError, setListError] = useState("");
  const [formError, setFormError] = useState("");
  const [actionError, setActionError] = useState("");
  const [notice, setNotice] = useState("");
  const [pending, setPending] = useState<"save" | number | null>(null);
  const pendingRef = useRef(false);
  const requestRef = useRef(0);
  const mountedRef = useRef(false);
  const nameInputRef = useRef<HTMLInputElement>(null);
  const listHeadingRef = useRef<HTMLHeadingElement>(null);

  // 조회 실패와 빈 목록을 구분하고 늦게 도착한 응답은 반영하지 않는다.
  const loadPlans = async () => {
    const request = ++requestRef.current;
    setListLoading(true);
    setListError("");
    try {
      const data = await getTravelPlansApi();
      if (mountedRef.current && request === requestRef.current) setPlans(data);
    } catch {
      if (mountedRef.current && request === requestRef.current)
        setListError("여행 목록을 불러오지 못했어요. 다시 불러와 주세요.");
    } finally {
      if (mountedRef.current && request === requestRef.current)
        setListLoading(false);
    }
  };

  useEffect(() => {
    mountedRef.current = true;
    void loadPlans();
    return () => {
      mountedRef.current = false;
      requestRef.current += 1;
    };
  }, []);

  const handleChange = (
    event: ChangeEvent<HTMLInputElement | HTMLSelectElement>,
  ) => {
    const { name, value } = event.target;
    setForm((previous) => {
      const next = {...previous, [name]: value};
      if (name === "companionType") {
        if (value === "ALONE") next.companionCount = "1";
        else if (previous.companionType === "ALONE") next.companionCount = "";
      }
      return next;
    });
    setFormError("");
  };

  const resetForm = () => {
    setEditingId(null);
    setForm({ ...EMPTY_FORM });
    setFormError("");
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (pendingRef.current) return;
    const budget = Number(form.totalBudget);
    if (!form.planName.trim()) {
      setFormError("여행 이름을 입력해 주세요.");
      nameInputRef.current?.focus();
      return;
    }
    if (!form.totalBudget.trim() || !Number.isFinite(budget) || budget < 0) {
      setFormError("예산은 0원 이상의 숫자로 입력해 주세요.");
      return;
    }
    const count = form.companionCount.trim() === "" ? null : Number(form.companionCount);
    if (count !== null && (!Number.isInteger(count) || count < 1 || count > 20)) {
      setFormError("여행 인원은 본인 포함 최소 1명, 최대 20명까지 가능합니다.");
      return;
    }
    const isGroup = ["FRIEND", "FAMILY", "COUPLE"].includes(form.companionType);
    if (count !== null && isGroup && count < 2) {
      setFormError("친구, 가족, 연인은 본인 포함 2명 이상이어야 합니다.");
      return;
    }
    // 백엔드와 동일하게 과거 출발 일시와 역전된 기간을 차단한다.
    if (form.startDate && new Date(form.startDate).getTime() < Date.now()) {
      setFormError("출발 일시는 현재 이후로 선택해 주세요.");
      return;
    }
    if (form.startDate && form.endDate && form.endDate < form.startDate) {
      setFormError("종료 일시는 출발 일시 이후로 선택해 주세요.");
      return;
    }
    const requestData = {
      planName: form.planName.trim(),
      totalBudget: budget,
      companionType: form.companionType || null,
      companionCount: count,
      blindYn: form.blindYn,
      startDate: form.startDate || null,
      endDate: form.endDate || null,
    };
    pendingRef.current = true;
    setPending("save");
    setFormError("");
    setActionError("");
    setNotice("");
    try {
      if (editingId !== null) await updateTravelPlanApi(editingId, requestData);
      else await createTravelPlanApi(requestData);
      if (!mountedRef.current) return;
      setNotice(
        editingId !== null
          ? "여행 계획을 수정했어요."
          : "새 여행을 등록했어요. AI 추천에서 코스를 확인해 보세요.",
      );
      resetForm();
      // 저장 성공 뒤 목록 갱신에 실패해도 저장 실패로 안내하지 않는다.
      await loadPlans();
      listHeadingRef.current?.focus({ preventScroll: true });
    } catch {
      if (mountedRef.current)
        setFormError(
          "저장 결과를 확인하지 못했어요. 목록을 새로고침해 반영 여부를 확인한 뒤 다시 시도해 주세요.",
        );
    } finally {
      pendingRef.current = false;
      if (mountedRef.current) setPending(null);
    }
  };

  const handleEdit = (plan: TravelPlan) => {
    if (pendingRef.current) return;
    setEditingId(plan.planId);
    setForm({
      planName: plan.planName,
      totalBudget: String(plan.totalBudget),
      companionType: plan.companionType || "",
      companionCount: plan.companionType === "ALONE" ? "1" : String(plan.companionCount ?? ""),
      blindYn: plan.blindYn || "N",
      startDate: plan.startDate?.slice(0, 16) || "",
      endDate: plan.endDate?.slice(0, 16) || "",
    });
    setFormError("");
    setActionError("");
    setNotice("");
    nameInputRef.current?.focus({ preventScroll: true });
    document
      .getElementById("tw-form-heading")
      ?.scrollIntoView({ block: "start" });
  };

  const handleDelete = async (plan: TravelPlan) => {
    if (pendingRef.current) return;
    if (!window.confirm(`“${plan.planName}” 여행 계획을 삭제할까요?`)) return;
    pendingRef.current = true;
    setPending(plan.planId);
    setActionError("");
    setNotice("");
    try {
      await deleteTravelPlanApi(plan.planId);
      if (!mountedRef.current) return;
      setPlans((previous) =>
        previous.filter((item) => item.planId !== plan.planId),
      );
      if (editingId === plan.planId) resetForm();
      setNotice("여행 계획을 삭제했어요.");
      await loadPlans();
      listHeadingRef.current?.focus({ preventScroll: true });
    } catch {
      if (mountedRef.current)
        setActionError(
          "삭제 결과를 확인하지 못했어요. 목록을 새로고침해 반영 여부를 확인해 주세요.",
        );
    } finally {
      pendingRef.current = false;
      if (mountedRef.current) setPending(null);
    }
  };

  const now = new Date();
  const minDateTime = new Date(now.getTime() - now.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
  const busy = pending !== null;

  return (
    <div className="tw-page" id="ai-planner">
      <section className="tw-hero" aria-labelledby="tw-heading">
        <div className="absolute inset-0 -z-10 tp-aurora" aria-hidden="true" />
        <div
          className="absolute inset-0 -z-10 tp-dot-grid opacity-40"
          aria-hidden="true"
        />
        <div>
          <p className="tw-eyebrow">
            <Compass size={14} aria-hidden="true" />
            TRIPPAMINE TRAVEL
          </p>
          <h1 id="tw-heading">나의 여행 계획</h1>
          <p className="tw-description">
            일정과 예산을 정하고, 나에게 맞는 여행을 준비하세요.
            <br className="tw-desktop-break" /> 등록한 여행은 AI 추천에서 코스로
            이어갈 수 있어요.
          </p>
        </div>
        <Link to="/ai-recommend" className="tw-button tw-button--primary">
          <Sparkles size={16} aria-hidden="true" />
          AI 추천 보기
          <ArrowUpRight size={14} aria-hidden="true" />
        </Link>
      </section>

      <div className="tw-switcher" role="group" aria-label="여행 방식">
        <button
          type="button"
          aria-pressed={activeTab === "planner"}
          disabled={busy}
          onClick={() => setActiveTab("planner")}
        >
          <Compass size={16} aria-hidden="true" />
          여행 계획
        </button>
        <button
          type="button"
          aria-pressed={activeTab === "mystery"}
          disabled={busy}
          onClick={() => setActiveTab("mystery")}
        >
          <Gift size={16} aria-hidden="true" />
          미스터리 투어
        </button>
      </div>

      {activeTab === "planner" ? (
        <div id="tw-planner-panel">
          {notice && (
            <p className="tw-notice" role="status">
              <Check size={16} aria-hidden="true" />
              {notice}
            </p>
          )}
          {actionError && (
            <p className="tw-error" role="alert">
              {actionError}
            </p>
          )}
          <div className="tw-workspace">
            <section
              className="tw-form-card tp-card-dark"
              aria-labelledby="tw-form-heading"
            >
              <p className="tw-eyebrow">
                {editingId !== null ? "EDIT PLAN" : "NEW PLAN"}
              </p>
              <h2 id="tw-form-heading">
                {editingId !== null ? "여행 계획 수정" : "새 여행 등록"}
              </h2>
              <p className="tw-section-description">
                이름과 예산을 입력해 주세요. 일정·동행자는 나중에 정해도
                괜찮아요.
              </p>
              <form onSubmit={handleSubmit} aria-busy={pending === "save"}>
                <fieldset disabled={busy} className="tw-fields">
                  <div className="tw-field">
                    <label htmlFor="tw-plan-name">
                      여행 이름 <span>필수</span>
                    </label>
                    <input
                      ref={nameInputRef}
                      id="tw-plan-name"
                      name="planName"
                      value={form.planName}
                      onChange={handleChange}
                      placeholder="예: 전주에서 보내는 주말"
                      required
                      aria-describedby={formError ? "tw-form-error" : undefined}
                    />
                  </div>
                  <div className="tw-field">
                    <label htmlFor="tw-budget">
                      총 예산 <span>필수</span>
                    </label>
                    <div className="tw-input-unit">
                      <input
                        id="tw-budget"
                        type="number"
                        name="totalBudget"
                        min="0"
                        step="any"
                        inputMode="decimal"
                        value={form.totalBudget}
                        onChange={handleChange}
                        placeholder="예: 300000"
                        required
                      />
                      <span>원</span>
                    </div>
                    {form.totalBudget &&
                      Number.isFinite(Number(form.totalBudget)) &&
                      Number(form.totalBudget) >= 0 && (
                        <p className="tw-field-hint">
                          {Number(form.totalBudget).toLocaleString("ko-KR")}원
                        </p>
                      )}
                  </div>
                  <div className="tw-field">
                    <label htmlFor="tw-companion">
                      동행자 <span>선택</span>
                    </label>
                    <select
                      id="tw-companion"
                      name="companionType"
                      value={form.companionType}
                      onChange={handleChange}
                    >
                      <option value="">아직 정하지 않았어요</option>
                      <option value="ALONE">혼자</option>
                      <option value="FRIEND">친구</option>
                      <option value="FAMILY">가족</option>
                      <option value="COUPLE">연인</option>
                    </select>
                  </div>
                  <div className="tw-field">
                    <label htmlFor="tw-companion-count">
                      동행 인원 <span>선택</span>
                    </label>
                    <div className="tw-input-unit">
                      <input
                        id="tw-companion-count"
                        type="number"
                        name="companionCount"
                        min="1"
                        max="20"
                        step="1"
                        inputMode="numeric"
                        value={form.companionCount}
                        onChange={handleChange}
                        placeholder="예: 3"
                        disabled={form.companionType === "ALONE"}
                      />
                      <span>명 (본인 포함)</span>
                    </div>
                  </div>
                  <div className="tw-field">
                    <label htmlFor="tw-start">
                      출발 일시 <span>선택</span>
                    </label>
                    <input
                      id="tw-start"
                      type="datetime-local"
                      name="startDate"
                      value={form.startDate}
                      onChange={handleChange}
                      min={minDateTime}
                    />
                  </div>
                  <div className="tw-field">
                    <label htmlFor="tw-end">
                      종료 일시 <span>선택</span>
                    </label>
                    <input
                      id="tw-end"
                      type="datetime-local"
                      name="endDate"
                      value={form.endDate}
                      onChange={handleChange}
                      min={form.startDate || minDateTime}
                    />
                  </div>
                  {formError && (
                    <p id="tw-form-error" className="tw-error" role="alert">
                      {formError}
                    </p>
                  )}
                  <div className="tw-form-actions">
                    <button
                      type="submit"
                      className="tw-button tw-button--primary"
                    >
                      {pending === "save" ? (
                        <Loader2
                          size={16}
                          className="animate-spin"
                          aria-hidden="true"
                        />
                      ) : editingId !== null ? (
                        <Check size={16} aria-hidden="true" />
                      ) : (
                        <Plus size={16} aria-hidden="true" />
                      )}
                      {pending === "save"
                        ? "저장 중…"
                        : editingId !== null
                          ? "수정 저장"
                          : "여행 등록"}
                    </button>
                    {editingId !== null && (
                      <button
                        type="button"
                        className="tw-button tw-button--secondary"
                        onClick={resetForm}
                      >
                        수정 취소
                      </button>
                    )}
                  </div>
                </fieldset>
              </form>
            </section>

            <section
              className="tw-list-section"
              aria-labelledby="tw-list-heading"
              aria-busy={listLoading}
            >
              <div className="tw-list-heading">
                <div>
                  <p className="tw-eyebrow">MY TRIPS</p>
                  <h2 id="tw-list-heading" ref={listHeadingRef} tabIndex={-1}>
                    등록한 여행{" "}
                    <span>{listLoading || listError ? "—" : plans.length}</span>
                  </h2>
                </div>
                <button
                  type="button"
                  className="tw-button tw-button--secondary"
                  disabled={busy || listLoading}
                  onClick={() => void loadPlans()}
                >
                  <RefreshCw size={14} aria-hidden="true" />
                  새로고침
                </button>
              </div>
              {listLoading ? (
                <div className="tw-state tp-card-dark" role="status">
                  <Loader2
                    size={26}
                    className="animate-spin"
                    aria-hidden="true"
                  />
                  <h3>여행 목록을 불러오고 있어요</h3>
                </div>
              ) : listError ? (
                <div className="tw-state tp-card-dark">
                  <p role="alert">{listError}</p>
                  <button
                    type="button"
                    className="tw-button tw-button--secondary"
                    disabled={busy}
                    onClick={() => void loadPlans()}
                  >
                    <RefreshCw size={15} aria-hidden="true" />
                    목록 다시 불러오기
                  </button>
                </div>
              ) : plans.length === 0 ? (
                <div className="tw-state tp-card-dark">
                  <MapPin size={32} aria-hidden="true" />
                  <h3>첫 여행을 계획해 볼까요?</h3>
                  <p>이름과 예산만 등록해도 시작할 수 있어요.</p>
                  <button
                    type="button"
                    className="tw-button tw-button--secondary"
                    onClick={() => nameInputRef.current?.focus()}
                  >
                    새 여행 입력하기
                    <Plus size={14} aria-hidden="true" />
                  </button>
                </div>
              ) : (
                <div className="tw-plan-list">
                  {plans.map((plan) => (
                    <article
                      key={plan.planId}
                      className="tw-plan tp-card-dark"
                      data-editing={editingId === plan.planId}
                      aria-labelledby={`tw-plan-${plan.planId}`}
                    >
                      <div className="tw-plan-top">
                        <span>
                          <MapPin size={12} aria-hidden="true" />
                          여행 계획
                        </span>
                        {plan.blindYn === "Y" && (
                          <span>
                            <Gift size={12} aria-hidden="true" />
                            미스터리
                          </span>
                        )}
                        {editingId === plan.planId && <span>수정 중</span>}
                      </div>
                      <h3 id={`tw-plan-${plan.planId}`}>{plan.planName}</h3>
                      <p className="tw-plan-date">
                        <CalendarDays size={15} aria-hidden="true" />
                        <span>
                          {formatDate(plan.startDate)} ~{" "}
                          {formatDate(plan.endDate)}
                        </span>
                      </p>
                      <dl className="tw-plan-facts">
                        <div>
                          <dt>
                            <Wallet size={13} aria-hidden="true" />총 예산
                          </dt>
                          <dd>
                            {Number(plan.totalBudget).toLocaleString("ko-KR")}원
                          </dd>
                        </div>
                        <div>
                          <dt>
                            <Users size={13} aria-hidden="true" />
                            여행 인원
                          </dt>
                          <dd>{companionSummary(plan.companionType, plan.companionCount)}</dd>
                        </div>
                      </dl>
                      <div className="tw-plan-actions">
                        <button
                          type="button"
                          className="tw-button tw-button--secondary"
                          disabled={busy}
                          onClick={() => handleEdit(plan)}
                          aria-label={`${plan.planName} 수정`}
                        >
                          <Pencil size={14} aria-hidden="true" />
                          수정
                        </button>
                        <button
                          type="button"
                          className="tw-button tw-button--danger"
                          disabled={busy}
                          onClick={() => void handleDelete(plan)}
                          aria-label={`${plan.planName} 삭제`}
                        >
                          {pending === plan.planId ? (
                            <Loader2
                              size={14}
                              className="animate-spin"
                              aria-hidden="true"
                            />
                          ) : (
                            <Trash2 size={14} aria-hidden="true" />
                          )}
                          {pending === plan.planId ? "삭제 중…" : "삭제"}
                        </button>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </section>
          </div>
        </div>
      ) : (
        <div id="tw-mystery-panel">
          <MysteryTour />
        </div>
      )}
    </div>
  );
}

function companionLabel(type: string | null): string {
  return (
    (
      {
        ALONE: "혼자",
        FRIEND: "친구",
        FAMILY: "가족",
        COUPLE: "연인",
      } as Record<string, string>
    )[type ?? ""] ??
    (type || "동행자 미정")
  );
}

function companionSummary(type: string | null, count: number | null): string {
  return `${companionLabel(type)} · ${count === null ? "인원 미설정" : `${count}명`}`;
}

function formatDate(value: string | null): string {
  return value
    ? value.slice(0, 16).replace("T", " ").replaceAll("-", ".")
    : "일정 미정";
}
