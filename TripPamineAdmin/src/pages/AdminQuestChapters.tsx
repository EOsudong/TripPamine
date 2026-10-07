import { useEffect, useMemo, useState } from "react";
import AdminLayout from "../components/AdminLayout";

import { ApiError } from "../api/client";
import type { AdminQuestChapter, QuestChapterUpsertRequest } from "../types/api";
import {
  createChapter,
  deleteChapter,
  getChapterList,
  updateChapter,
} from "../api/quest";

type ChapterFormTarget =
  | { mode: "create" }
  | { mode: "edit"; chapter: AdminQuestChapter }
  | null;

export default function AdminQuestChapters() {
  const [chapters, setChapters] = useState<AdminQuestChapter[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [formTarget, setFormTarget] = useState<ChapterFormTarget>(null);

  async function loadChapters() {
    setLoading(true);
    setError(null);
    try {
      const data = await getChapterList();
      setChapters(data);
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : "챕터 목록을 불러오지 못했습니다.",
      );
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadChapters();
  }, []);

  const sortedChapters = useMemo(
    () => [...chapters].sort((a, b) => a.orderNo - b.orderNo || a.chapterId - b.chapterId),
    [chapters],
  );

  async function handleDelete(chapter: AdminQuestChapter) {
    if (!window.confirm(`"${chapter.chapterName}" 챕터를 삭제할까요?`)) return;

    setDeletingId(chapter.chapterId);
    setError(null);
    try {
      await deleteChapter(chapter.chapterId);
      await loadChapters();
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : "챕터 삭제에 실패했습니다. 이 챕터에 배정된 퀘스트가 남아있는지 확인해주세요.",
      );
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <AdminLayout>
      <div className="flex flex-col gap-4 mb-6 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-800">챕터 관리</h1>
          <p className="text-sm text-slate-500 mt-1">
            퀘스트를 묶어서 보여줄 챕터(지역/스토리 단위)를 등록하고 순서를 관리합니다.
          </p>
        </div>
        <button
          onClick={() => setFormTarget({ mode: "create" })}
          className="px-4 py-2.5 rounded-lg bg-indigo-500 hover:bg-indigo-400 text-white text-sm font-semibold transition-colors"
        >
          + 새 챕터 등록
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
              <th className="px-5 py-3">순서</th>
              <th className="px-5 py-3">ID</th>
              <th className="px-5 py-3">챕터명</th>
              <th className="px-5 py-3">지역</th>
              <th className="px-5 py-3">요구 레벨</th>
              <th className="px-5 py-3">설명</th>
              <th className="px-5 py-3 text-right">관리</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={7} className="px-5 py-10 text-center text-slate-400">
                  불러오는 중...
                </td>
              </tr>
            ) : sortedChapters.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-5 py-10 text-center text-slate-400">
                  등록된 챕터가 없습니다.
                </td>
              </tr>
            ) : (
              sortedChapters.map((chapter) => (
                <tr
                  key={chapter.chapterId}
                  className="border-b border-slate-100 last:border-0 hover:bg-slate-50/70"
                >
                  <td className="px-5 py-3.5 text-slate-400 font-mono text-xs">
                    {chapter.orderNo}
                  </td>
                  <td className="px-5 py-3.5 text-slate-400 font-mono text-xs">
                    #{chapter.chapterId}
                  </td>
                  <td className="px-5 py-3.5 text-slate-700 font-medium">
                    {chapter.chapterName}
                  </td>
                  <td className="px-5 py-3.5 text-slate-500">
                    {chapter.region ?? "-"}
                  </td>
                  <td className="px-5 py-3.5 text-slate-500">
                    Lv.{chapter.requiredLevel}+
                  </td>
                  <td className="px-5 py-3.5 text-slate-500 text-xs max-w-xs truncate">
                    {chapter.description ?? "-"}
                  </td>
                  <td className="px-5 py-3.5 text-right whitespace-nowrap">
                    <button
                      onClick={() => setFormTarget({ mode: "edit", chapter })}
                      className="px-3 py-1.5 rounded-lg text-xs font-semibold text-indigo-600 hover:bg-indigo-50 transition-colors"
                    >
                      수정
                    </button>
                    <button
                      onClick={() => handleDelete(chapter)}
                      disabled={deletingId === chapter.chapterId}
                      className="px-3 py-1.5 rounded-lg text-xs font-semibold text-red-600 hover:bg-red-50 disabled:text-slate-300 disabled:hover:bg-transparent transition-colors"
                    >
                      {deletingId === chapter.chapterId ? "삭제 중..." : "삭제"}
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {formTarget && (
        <ChapterFormModal
          target={formTarget}
          onClose={() => setFormTarget(null)}
          onSuccess={() => {
            setFormTarget(null);
            loadChapters();
          }}
        />
      )}
    </AdminLayout>
  );
}

interface ChapterFormModalProps {
  target: Exclude<ChapterFormTarget, null>;
  onClose: () => void;
  onSuccess: () => void;
}

function ChapterFormModal({ target, onClose, onSuccess }: ChapterFormModalProps) {
  const isEdit = target.mode === "edit";
  const sourceChapter = isEdit ? target.chapter : undefined;

  const [chapterName, setChapterName] = useState(sourceChapter?.chapterName ?? "");
  const [description, setDescription] = useState(sourceChapter?.description ?? "");
  const [region, setRegion] = useState(sourceChapter?.region ?? "");
  const [orderNo, setOrderNo] = useState(
    sourceChapter ? String(sourceChapter.orderNo) : "0",
  );
  const [requiredLevel, setRequiredLevel] = useState(
    sourceChapter ? String(sourceChapter.requiredLevel) : "1",
  );
  const [imageUrl, setImageUrl] = useState(sourceChapter?.imageUrl ?? "");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function validate(): string | null {
    if (!chapterName.trim()) return "챕터명을 입력해주세요.";

    if (orderNo.trim() !== "") {
      const order = Number(orderNo);
      if (!Number.isInteger(order)) {
        return "순서는 정수로 입력해주세요.";
      }
    }

    if (requiredLevel.trim() !== "") {
      const level = Number(requiredLevel);
      if (!Number.isInteger(level) || level < 1) {
        return "요구 레벨은 1 이상의 정수로 입력해주세요.";
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

    const request: QuestChapterUpsertRequest = {
      chapterName: chapterName.trim(),
      description: description.trim() === "" ? undefined : description.trim(),
      region: region.trim() === "" ? undefined : region.trim(),
      orderNo: orderNo.trim() === "" ? undefined : Number(orderNo),
      requiredLevel: requiredLevel.trim() === "" ? undefined : Number(requiredLevel),
      imageUrl: imageUrl.trim() === "" ? undefined : imageUrl.trim(),
    };

    setSubmitting(true);
    setError(null);
    try {
      if (target.mode === "edit") {
        await updateChapter(target.chapter.chapterId, request);
      } else {
        await createChapter(request);
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
              {isEdit ? "챕터 수정" : "새 챕터 등록"}
            </h2>
            <p className="text-sm text-slate-500 mt-1">
              퀘스트 등록 화면에서 이 챕터를 선택해 배정할 수 있습니다.
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
              챕터명
            </label>
            <input
              type="text"
              value={chapterName}
              onChange={(event) => setChapterName(event.target.value)}
              placeholder="예) 1장. 경복궁의 비밀"
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
              placeholder="유저에게 보여줄 챕터 설명을 입력하세요."
              rows={2}
              className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700 resize-none"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-semibold text-slate-600 block mb-1.5">
                지역 (선택)
              </label>
              <input
                type="text"
                value={region}
                onChange={(event) => setRegion(event.target.value)}
                placeholder="예) 서울 종로구"
                className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700"
              />
            </div>
            <div>
              <label className="text-xs font-semibold text-slate-600 block mb-1.5">
                표시 순서
              </label>
              <input
                type="number"
                inputMode="numeric"
                step="1"
                value={orderNo}
                onChange={(event) => setOrderNo(event.target.value)}
                className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700"
              />
            </div>
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1.5">
              요구 레벨
            </label>
            <input
              type="number"
              inputMode="numeric"
              min="1"
              step="1"
              value={requiredLevel}
              onChange={(event) => setRequiredLevel(event.target.value)}
              className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700"
            />
            <p className="text-[11px] text-slate-400 mt-1">
              이 레벨 미달 유저에게는 챕터 자체가 잠긴 것으로 표시됩니다.
            </p>
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1.5">
              이미지 URL (선택)
            </label>
            <input
              type="text"
              value={imageUrl}
              onChange={(event) => setImageUrl(event.target.value)}
              placeholder="https://..."
              className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-xs text-slate-700"
            />
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
            {submitting ? "저장 중..." : isEdit ? "수정 완료" : "챕터 등록"}
          </button>
        </div>
      </div>
    </div>
  );
}
