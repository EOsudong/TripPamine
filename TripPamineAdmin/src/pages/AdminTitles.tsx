import { useEffect, useMemo, useState } from "react";
import AdminLayout from "../components/AdminLayout";

import { ApiError } from "../api/client";
import type { AdminTitle, TitleConditionType, TitleUpsertRequest } from "../types/api";
import {
  createTitle,
  deleteTitle,
  getTitleList,
  updateTitle,
} from "../api/title";

type TitleFormTarget =
  | { mode: "create" }
  | { mode: "edit"; title: AdminTitle }
  | null;

const CONDITION_TYPE_LABEL: Record<TitleConditionType, string> = {
  FIRST_QUEST_CLEAR: "첫 퀘스트 완료 시",
  QUEST_COUNT: "누적 퀘스트 완료 수 도달 시",
  LEVEL_REACHED: "레벨 도달 시",
};

// 목록 테이블에 보여줄 "획득 조건" 한 줄 요약
function conditionSummary(title: AdminTitle): string {
  switch (title.requiredConditionType) {
    case "FIRST_QUEST_CLEAR":
      return "퀘스트 1회 완료";
    case "QUEST_COUNT":
      return `퀘스트 ${title.requiredConditionValue ?? "?"}개 완료`;
    case "LEVEL_REACHED":
      return `레벨 ${title.requiredConditionValue ?? "?"} 달성`;
    default:
      return "-";
  }
}

export default function AdminTitles() {
  const [titles, setTitles] = useState<AdminTitle[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [formTarget, setFormTarget] = useState<TitleFormTarget>(null);

  async function loadTitles() {
    setLoading(true);
    setError(null);
    try {
      const data = await getTitleList();
      setTitles(data);
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : "칭호 목록을 불러오지 못했습니다.",
      );
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadTitles();
  }, []);

  const sortedTitles = useMemo(
    () => [...titles].sort((a, b) => a.titleId - b.titleId),
    [titles],
  );

  async function handleDelete(title: AdminTitle) {
    if (!window.confirm(`"${title.titleName}" 칭호를 삭제할까요?`)) return;

    setDeletingId(title.titleId);
    setError(null);
    try {
      await deleteTitle(title.titleId);
      await loadTitles();
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : "칭호 삭제에 실패했습니다. 이미 획득했거나 장착 중인 사용자가 있는지 확인해주세요.",
      );
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <AdminLayout>
      <div className="flex flex-col gap-4 mb-6 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-800">칭호 관리</h1>
          <p className="text-sm text-slate-500 mt-1">
            유저가 레벨/퀘스트 완료 조건을 만족하면 자동으로 지급되는 칭호를 등록하고
            관리합니다.
          </p>
        </div>
        <button
          onClick={() => setFormTarget({ mode: "create" })}
          className="px-4 py-2.5 rounded-lg bg-indigo-500 hover:bg-indigo-400 text-white text-sm font-semibold transition-colors"
        >
          + 새 칭호 등록
        </button>
      </div>

      {error && (
        <p className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-4 py-2.5 mb-4">
          {error}
        </p>
      )}

      <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-slate-50 border-b border-slate-200 text-left text-xs font-semibold text-slate-500 uppercase tracking-wide">
              <th className="px-5 py-3">ID</th>
              <th className="px-5 py-3">아이콘</th>
              <th className="px-5 py-3">칭호명</th>
              <th className="px-5 py-3">설명</th>
              <th className="px-5 py-3">획득 조건</th>
              <th className="px-5 py-3 text-right">관리</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6} className="px-5 py-10 text-center text-slate-400">
                  불러오는 중...
                </td>
              </tr>
            ) : sortedTitles.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-5 py-10 text-center text-slate-400">
                  등록된 칭호가 없습니다.
                </td>
              </tr>
            ) : (
              sortedTitles.map((title) => (
                <tr
                  key={title.titleId}
                  className="border-b border-slate-100 last:border-0 hover:bg-slate-50/70"
                >
                  <td className="px-5 py-3.5 text-slate-400 font-mono text-xs">
                    #{title.titleId}
                  </td>
                  <td className="px-5 py-3.5">
                    {title.iconUrl ? (
                      <img
                        src={title.iconUrl}
                        alt={title.titleName}
                        className="h-8 w-8 rounded-full object-cover border border-slate-200"
                        onError={(event) => {
                          event.currentTarget.style.display = "none";
                        }}
                      />
                    ) : (
                      <span className="h-8 w-8 rounded-full bg-slate-100 inline-flex items-center justify-center text-slate-400 text-xs">
                        🏆
                      </span>
                    )}
                  </td>
                  <td className="px-5 py-3.5 text-slate-700 font-medium">
                    {title.titleName}
                  </td>
                  <td className="px-5 py-3.5 text-slate-500 text-xs max-w-xs truncate">
                    {title.description ?? "-"}
                  </td>
                  <td className="px-5 py-3.5 text-slate-500 text-xs">
                    {conditionSummary(title)}
                  </td>
                  <td className="px-5 py-3.5 text-right whitespace-nowrap">
                    <button
                      onClick={() => setFormTarget({ mode: "edit", title })}
                      className="px-3 py-1.5 rounded-lg text-xs font-semibold text-indigo-600 hover:bg-indigo-50 transition-colors"
                    >
                      수정
                    </button>
                    <button
                      onClick={() => handleDelete(title)}
                      disabled={deletingId === title.titleId}
                      className="px-3 py-1.5 rounded-lg text-xs font-semibold text-red-600 hover:bg-red-50 disabled:text-slate-300 disabled:hover:bg-transparent transition-colors"
                    >
                      {deletingId === title.titleId ? "삭제 중..." : "삭제"}
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {formTarget && (
        <TitleFormModal
          target={formTarget}
          onClose={() => setFormTarget(null)}
          onSuccess={() => {
            setFormTarget(null);
            loadTitles();
          }}
        />
      )}
    </AdminLayout>
  );
}

interface TitleFormModalProps {
  target: Exclude<TitleFormTarget, null>;
  onClose: () => void;
  onSuccess: () => void;
}

function TitleFormModal({ target, onClose, onSuccess }: TitleFormModalProps) {
  const isEdit = target.mode === "edit";
  const sourceTitle = isEdit ? target.title : undefined;

  const [titleName, setTitleName] = useState(sourceTitle?.titleName ?? "");
  const [description, setDescription] = useState(sourceTitle?.description ?? "");
  const [iconUrl, setIconUrl] = useState(sourceTitle?.iconUrl ?? "");
  const [requiredConditionType, setRequiredConditionType] = useState<TitleConditionType>(
    sourceTitle?.requiredConditionType ?? "FIRST_QUEST_CLEAR",
  );
  const [requiredConditionValue, setRequiredConditionValue] = useState(
    sourceTitle?.requiredConditionValue != null ? String(sourceTitle.requiredConditionValue) : "",
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const needsConditionValue = requiredConditionType !== "FIRST_QUEST_CLEAR";

  function validate(): string | null {
    if (!titleName.trim()) return "칭호명을 입력해주세요.";

    if (needsConditionValue) {
      const value = Number(requiredConditionValue);
      if (requiredConditionValue.trim() === "" || !Number.isInteger(value) || value < 1) {
        return requiredConditionType === "QUEST_COUNT"
          ? "누적 완료 퀘스트 수를 1 이상의 정수로 입력해주세요."
          : "도달 레벨을 1 이상의 정수로 입력해주세요.";
      }
    }

    return null;
  }

  async function handleSubmit() {
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    const request: TitleUpsertRequest = {
      titleName: titleName.trim(),
      description: description.trim() === "" ? undefined : description.trim(),
      iconUrl: iconUrl.trim() === "" ? undefined : iconUrl.trim(),
      requiredConditionType,
      requiredConditionValue: needsConditionValue
        ? Number(requiredConditionValue)
        : undefined,
    };

    setSubmitting(true);
    setError(null);
    try {
      if (target.mode === "edit") {
        await updateTitle(target.title.titleId, request);
      } else {
        await createTitle(request);
      }
      onSuccess();
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : "저장에 실패했습니다.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 bg-slate-950/60 flex items-center justify-center p-3 z-50">
      <div className="bg-white rounded-2xl w-full max-w-lg max-h-[94vh] flex flex-col shadow-2xl overflow-hidden">
        <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-6 py-5">
          <div>
            <h2 className="font-bold text-lg text-slate-800">
              {isEdit ? "칭호 수정" : "새 칭호 등록"}
            </h2>
            <p className="text-sm text-slate-500 mt-1">
              조건을 만족한 유저에게 자동으로 지급됩니다.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="h-9 w-9 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700"
          >
            ✕
          </button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto p-6 space-y-4">
          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1.5">
              칭호명
            </label>
            <input
              type="text"
              value={titleName}
              onChange={(event) => setTitleName(event.target.value)}
              placeholder="예) 여행 마스터"
              className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700"
            />
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1.5">
              설명 (선택)
            </label>
            <textarea
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="유저에게 보여줄 칭호 설명을 입력하세요."
              rows={2}
              className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700 resize-none"
            />
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1.5">
              획득 조건
            </label>
            <select
              value={requiredConditionType}
              onChange={(event) => {
                const nextType = event.target.value as TitleConditionType;
                setRequiredConditionType(nextType);
                if (nextType === "FIRST_QUEST_CLEAR") {
                  setRequiredConditionValue("");
                }
              }}
              className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700 bg-white"
            >
              {Object.entries(CONDITION_TYPE_LABEL).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>

          {needsConditionValue && (
            <div>
              <label className="text-xs font-semibold text-slate-600 block mb-1.5">
                {requiredConditionType === "QUEST_COUNT" ? "누적 완료 퀘스트 수" : "도달 레벨"}
              </label>
              <input
                type="number"
                inputMode="numeric"
                min="1"
                step="1"
                value={requiredConditionValue}
                onChange={(event) => setRequiredConditionValue(event.target.value)}
                placeholder={requiredConditionType === "QUEST_COUNT" ? "10" : "25"}
                className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700"
              />
            </div>
          )}

          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1.5">
              아이콘 URL (선택)
            </label>
            <input
              type="text"
              value={iconUrl}
              onChange={(event) => setIconUrl(event.target.value)}
              placeholder="https://..."
              className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-xs text-slate-700"
            />
          </div>

          <div className="rounded-2xl bg-slate-950 p-5 text-white shadow-lg flex items-center gap-3">
            {iconUrl.trim() ? (
              <img
                src={iconUrl}
                alt="미리보기"
                className="h-10 w-10 rounded-full object-cover border border-white/20"
                onError={(event) => {
                  event.currentTarget.style.display = "none";
                }}
              />
            ) : (
              <span className="h-10 w-10 rounded-full bg-white/10 inline-flex items-center justify-center text-lg">
                🏆
              </span>
            )}
            <div>
              <p className="font-black text-base">
                {titleName.trim() || "칭호명을 입력해주세요"}
              </p>
              <p className="text-[11px] text-slate-400 mt-0.5">
                {requiredConditionType === "FIRST_QUEST_CLEAR"
                  ? "퀘스트 1회 완료"
                  : requiredConditionType === "QUEST_COUNT"
                    ? `퀘스트 ${requiredConditionValue || "?"}개 완료`
                    : `레벨 ${requiredConditionValue || "?"} 달성`}
              </p>
            </div>
          </div>

          {error && (
            <p className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-3.5 py-3">
              {error}
            </p>
          )}
        </div>

        <div className="flex justify-end gap-2 border-t border-slate-200 bg-slate-50 px-6 py-4">
          <button
            type="button"
            onClick={onClose}
            className="px-5 py-2.5 rounded-lg border border-slate-200 bg-white text-slate-600 text-sm font-semibold hover:bg-slate-50"
          >
            취소
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={submitting}
            className="px-5 py-2.5 rounded-lg bg-indigo-500 hover:bg-indigo-400 disabled:bg-slate-300 text-white text-sm font-semibold"
          >
            {submitting ? "저장 중..." : isEdit ? "수정 완료" : "칭호 등록"}
          </button>
        </div>
      </div>
    </div>
  );
}
