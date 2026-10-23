import { useEffect, useMemo, useState } from "react";
import { ApiError } from "../api/client";
import {
  addQuestPrerequisite,
  deleteQuestPrerequisite,
  getQuestPrerequisites,
  type QuestPrerequisiteInfo,
} from "../api/quest";
import type { AdminQuest } from "../types/api";

interface Props {
  quest: AdminQuest;
  quests: AdminQuest[];
  onClose: () => void;
}

// 일반 CHAIN 단계와 다른 '퀘스트 간' 선행 조건 편집 창.
export default function QuestPrerequisiteModal({ quest, quests, onClose }: Props) {
  const [links, setLinks] = useState<QuestPrerequisiteInfo[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getQuestPrerequisites(quest.questId)
      .then((data) => { if (active) setLinks(data); })
      .catch((err: unknown) => {
        if (active) setError(err instanceof ApiError ? err.message : "선행 관계를 불러오지 못했습니다.");
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [quest.questId]);

  const candidates = useMemo(() => quests.filter((item) =>
    item.questId !== quest.questId &&
    !links.some((link) => link.prerequisiteQuestId === item.questId)
  ), [quests, links, quest.questId]);

  async function add() {
    const prerequisiteQuestId = Number(selectedId);
    if (!Number.isSafeInteger(prerequisiteQuestId) || prerequisiteQuestId < 1) {
      setError("선행 퀘스트를 선택해주세요.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      setLinks(await addQuestPrerequisite(quest.questId, prerequisiteQuestId));
      setSelectedId("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "선행 퀘스트 연결에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function remove(prerequisiteQuestId: number) {
    if (!window.confirm("선행 퀘스트 연결을 해제할까요?")) return;
    setSaving(true);
    setError(null);
    try {
      await deleteQuestPrerequisite(quest.questId, prerequisiteQuestId);
      setLinks((current) => current.filter((item) => item.prerequisiteQuestId !== prerequisiteQuestId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "선행 퀘스트 연결 해제에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4">
      <div role="dialog" aria-modal="true" aria-label="선행 퀘스트 관리" className="w-full max-w-xl rounded-2xl bg-white p-6 shadow-2xl">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-bold text-slate-800">선행 퀘스트 관리</h2>
            <p className="mt-1 text-sm text-slate-500">#{quest.questId} {quest.questName}</p>
          </div>
          <button onClick={onClose} aria-label="닫기" className="rounded-lg px-2 py-1 text-slate-500 hover:bg-slate-100">✕</button>
        </div>
        <p className="mt-4 text-xs leading-5 text-slate-500">
          아래 퀘스트를 모두 완료해야 이 퀘스트를 새로 수락할 수 있습니다(AND 조건).
          이미 시작 이력이 있는 퀘스트에는 새 조건을 추가할 수 없습니다.
          CHAIN 내부 단계와는 별개의 기능입니다.
        </p>
        <div className="mt-4 space-y-2">
          {loading && <p className="text-sm text-slate-400">연결 정보를 불러오는 중...</p>}
          {!loading && links.length === 0 && <p className="rounded-lg bg-slate-50 px-3 py-4 text-sm text-slate-500">선행 조건 없음 · 바로 수락 가능</p>}
          {links.map((link) => (
            <div key={link.prerequisiteQuestId} className="flex items-center justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2">
              <div>
                <p className="text-sm font-semibold text-slate-700">#{link.prerequisiteQuestId} {link.prerequisiteQuestName}</p>
                {!link.isActive && <p className="text-xs text-amber-600">비활성 퀘스트 · 신규 사용자는 완료하기 어려울 수 있습니다.</p>}
              </div>
              <button disabled={saving} onClick={() => remove(link.prerequisiteQuestId)} className="shrink-0 rounded-lg px-3 py-1.5 text-xs font-semibold text-red-600 hover:bg-red-50 disabled:opacity-50">연결 해제</button>
            </div>
          ))}
        </div>
        <div className="mt-5 flex gap-2">
          <select value={selectedId} onChange={(event) => setSelectedId(event.target.value)} disabled={loading || saving} className="min-w-0 flex-1 rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-700">
            <option value="">선행 퀘스트 선택</option>
            {candidates.map((item) => <option key={item.questId} value={item.questId}>#{item.questId} {item.questName}</option>)}
          </select>
          <button onClick={add} disabled={loading || saving || !selectedId} className="rounded-lg bg-indigo-500 px-4 py-2 text-sm font-semibold text-white disabled:bg-slate-300">연결</button>
        </div>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>}
        <div className="mt-5 flex justify-end"><button onClick={onClose} className="rounded-lg border border-slate-200 px-4 py-2 text-sm text-slate-600">닫기</button></div>
      </div>
    </div>
  );
}
