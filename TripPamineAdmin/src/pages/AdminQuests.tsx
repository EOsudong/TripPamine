// 퀘스트 관리 화면. 백엔드 AdminQuestController(GET/POST/PUT/DELETE /admin/quests)를 사용.
// 페이징 없이 전체 목록을 한 번에 보여준다 (회원 목록과 달리 퀘스트 개수는 운영상 많지 않을 것으로 가정).
import { useEffect, useState } from "react"
import AdminLayout from "../components/AdminLayout"
// @ts-ignore

import { ApiError } from "../api/client"
import type { AdminQuest, QuestUpsertRequest } from "../types/api"
import {createQuest, deleteQuest, getQuestList, updateQuest} from "../api/quest.ts";

export default function AdminQuests() {
    const [quests, setQuests] = useState<AdminQuest[]>([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [deletingId, setDeletingId] = useState<number | null>(null)

    // null = 모달 닫힘, "new" = 신규 등록, AdminQuest = 해당 퀘스트 수정
    const [editTarget, setEditTarget] = useState<AdminQuest | "new" | null>(null)

    async function loadQuests() {
        setLoading(true)
        setError(null)
        try {
            const data = await getQuestList()
            setQuests(data)
        } catch (err) {
            setError(err instanceof ApiError ? err.message : "퀘스트 목록을 불러오지 못했습니다.")
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadQuests()
    }, [])

    async function handleDelete(quest: AdminQuest) {
        if (!window.confirm(`"${quest.questName}" 퀘스트를 삭제할까요?`)) return

        setDeletingId(quest.questId)
        setError(null)
        try {
            await deleteQuest(quest.questId)
            await loadQuests()
        } catch (err) {
            // 진행/완료한 유저 기록이 남아있으면 백엔드가 409 + 안내 메시지를 내려줌 (하드 삭제 방지 정책)
            setError(err instanceof ApiError ? err.message : "퀘스트 삭제에 실패했습니다.")
        } finally {
            setDeletingId(null)
        }
    }

    return (
        <AdminLayout>
            <div className="flex items-center justify-between mb-1">
                <h1 className="text-xl font-bold text-slate-800">퀘스트 관리</h1>
                <button
                    onClick={() => setEditTarget("new")}
                    className="px-4 py-2 rounded-lg bg-indigo-500 hover:bg-indigo-400 text-white text-sm font-semibold transition-colors"
                >
                    + 새 퀘스트 등록
                </button>
            </div>
            <p className="text-sm text-slate-500 mb-6">
                GPS 반경 검증 기반 실시간 퀘스트를 등록/수정/삭제합니다. 이미 유저가 진행 중이거나 완료한
                퀘스트는 기록 보존을 위해 삭제가 제한됩니다.
            </p>

            {error && (
                <p className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-4 py-2.5 mb-4">{error}</p>
            )}

            <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden overflow-x-auto">
                <table className="w-full text-sm">
                    <thead>
                    <tr className="bg-slate-50 border-b border-slate-200 text-left text-xs font-semibold text-slate-500 uppercase tracking-wide">
                        <th className="px-5 py-3">퀘스트명</th>
                        <th className="px-5 py-3">목표 좌표</th>
                        <th className="px-5 py-3">클리어 반경</th>
                        <th className="px-5 py-3">보상 포인트</th>
                        <th className="px-5 py-3 text-right">관리</th>
                    </tr>
                    </thead>
                    <tbody>
                    {loading ? (
                        <tr>
                            <td colSpan={5} className="px-5 py-10 text-center text-slate-400">
                                불러오는 중...
                            </td>
                        </tr>
                    ) : quests.length === 0 ? (
                        <tr>
                            <td colSpan={5} className="px-5 py-10 text-center text-slate-400">
                                등록된 퀘스트가 없습니다.
                            </td>
                        </tr>
                    ) : (
                        quests.map((quest) => (
                            <tr key={quest.questId} className="border-b border-slate-100 last:border-0">
                                <td className="px-5 py-3.5 text-slate-700 font-medium">{quest.questName}</td>
                                <td className="px-5 py-3.5 text-slate-500 font-mono text-xs">
                                    {quest.targetLat.toFixed(5)}, {quest.targetLng.toFixed(5)}
                                </td>
                                <td className="px-5 py-3.5 text-slate-500">{quest.clearRadius}m</td>
                                <td className="px-5 py-3.5 text-slate-500">{quest.rewardPoint.toLocaleString()} P</td>
                                <td className="px-5 py-3.5 text-right whitespace-nowrap">
                                    <button
                                        onClick={() => setEditTarget(quest)}
                                        className="px-3 py-1.5 rounded-lg text-xs font-semibold text-indigo-600 hover:bg-indigo-50 transition-colors mr-1"
                                    >
                                        수정
                                    </button>
                                    <button
                                        onClick={() => handleDelete(quest)}
                                        disabled={deletingId === quest.questId}
                                        className="px-3 py-1.5 rounded-lg text-xs font-semibold text-red-600 hover:bg-red-50 disabled:text-slate-300 disabled:hover:bg-transparent transition-colors"
                                    >
                                        {deletingId === quest.questId ? "삭제 중..." : "삭제"}
                                    </button>
                                </td>
                            </tr>
                        ))
                    )}
                    </tbody>
                </table>
            </div>

            {editTarget && (
                <QuestFormModal
                    quest={editTarget === "new" ? null : editTarget}
                    onClose={() => setEditTarget(null)}
                    onSuccess={() => {
                        setEditTarget(null)
                        loadQuests()
                    }}
                />
            )}
        </AdminLayout>
    )
}

interface QuestFormModalProps {
    quest: AdminQuest | null // null이면 신규 등록
    onClose: () => void
    onSuccess: () => void
}

// 등록/수정을 공용으로 처리하는 모달 폼. 백엔드 QuestRequest 검증 규칙(@NotBlank, @NotNull, @Min(1))과
// 동일한 수준으로 프론트에서도 먼저 걸러서, 잘못된 값으로 요청을 보내 서버 400을 받는 왕복을 줄인다.
function QuestFormModal({ quest, onClose, onSuccess }: QuestFormModalProps) {
    const isEdit = quest !== null

    const [questName, setQuestName] = useState(quest?.questName ?? "")
    const [targetLat, setTargetLat] = useState(quest ? String(quest.targetLat) : "")
    const [targetLng, setTargetLng] = useState(quest ? String(quest.targetLng) : "")
    const [rewardPoint, setRewardPoint] = useState(quest ? String(quest.rewardPoint) : "0")
    const [clearRadius, setClearRadius] = useState(quest ? String(quest.clearRadius) : "100")

    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState<string | null>(null)

    function validate(): string | null {
        if (!questName.trim()) return "퀘스트명을 입력해주세요."

        const lat = Number(targetLat)
        const lng = Number(targetLng)
        if (targetLat.trim() === "" || Number.isNaN(lat)) return "목표 위도를 숫자로 입력해주세요."
        if (targetLng.trim() === "" || Number.isNaN(lng)) return "목표 경도를 숫자로 입력해주세요."
        if (lat < -90 || lat > 90) return "위도는 -90 ~ 90 사이여야 합니다."
        if (lng < -180 || lng > 180) return "경도는 -180 ~ 180 사이여야 합니다."

        if (rewardPoint.trim() !== "" && Number.isNaN(Number(rewardPoint))) return "보상 포인트는 숫자로 입력해주세요."

        if (clearRadius.trim() !== "") {
            const radius = Number(clearRadius)
            if (Number.isNaN(radius) || radius < 1) return "클리어 반경은 1m 이상의 숫자여야 합니다."
        }

        return null
    }

    async function handleSubmit() {
        const validationError = validate()
        if (validationError) {
            setError(validationError)
            return
        }

        const request: QuestUpsertRequest = {
            questName: questName.trim(),
            targetLat: Number(targetLat),
            targetLng: Number(targetLng),
            rewardPoint: rewardPoint.trim() === "" ? undefined : Number(rewardPoint),
            clearRadius: clearRadius.trim() === "" ? undefined : Number(clearRadius),
        }

        setSubmitting(true)
        setError(null)
        try {
            if (isEdit) {
                await updateQuest(quest.questId, request)
            } else {
                await createQuest(request)
            }
            onSuccess()
        } catch (err) {
            setError(err instanceof ApiError ? err.message : "저장에 실패했습니다.")
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <div className="fixed inset-0 bg-slate-900/50 flex items-center justify-center p-4 z-50">
            <div className="bg-white rounded-2xl p-6 w-full max-w-md">
                <h2 className="font-bold text-slate-800 mb-1">{isEdit ? "퀘스트 수정" : "새 퀘스트 등록"}</h2>
                <p className="text-sm text-slate-500 mb-4">
                    {isEdit ? `"${quest.questName}" 퀘스트 정보를 수정합니다.` : "GPS 반경 검증 기반 퀘스트를 새로 등록합니다."}
                </p>

                <div className="space-y-3.5">
                    <div>
                        <label className="text-xs font-semibold text-slate-600 block mb-1.5">퀘스트명</label>
                        <input
                            type="text"
                            value={questName}
                            onChange={(e) => setQuestName(e.target.value)}
                            placeholder="예) 보령 머드 광장 현지 인증"
                            className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700 placeholder-slate-400 transition-colors"
                        />
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                        <div>
                            <label className="text-xs font-semibold text-slate-600 block mb-1.5">목표 위도 (Lat)</label>
                            <input
                                type="text"
                                inputMode="decimal"
                                value={targetLat}
                                onChange={(e) => setTargetLat(e.target.value)}
                                placeholder="36.3113940"
                                className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700 placeholder-slate-400 transition-colors font-mono"
                            />
                        </div>
                        <div>
                            <label className="text-xs font-semibold text-slate-600 block mb-1.5">목표 경도 (Lng)</label>
                            <input
                                type="text"
                                inputMode="decimal"
                                value={targetLng}
                                onChange={(e) => setTargetLng(e.target.value)}
                                placeholder="126.5133640"
                                className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700 placeholder-slate-400 transition-colors font-mono"
                            />
                        </div>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                        <div>
                            <label className="text-xs font-semibold text-slate-600 block mb-1.5">클리어 반경 (m)</label>
                            <input
                                type="text"
                                inputMode="numeric"
                                value={clearRadius}
                                onChange={(e) => setClearRadius(e.target.value)}
                                placeholder="100"
                                className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700 placeholder-slate-400 transition-colors"
                            />
                            <p className="text-[11px] text-slate-400 mt-1">비워두면 기본값 100m로 저장됩니다.</p>
                        </div>
                        <div>
                            <label className="text-xs font-semibold text-slate-600 block mb-1.5">보상 포인트</label>
                            <input
                                type="text"
                                inputMode="numeric"
                                value={rewardPoint}
                                onChange={(e) => setRewardPoint(e.target.value)}
                                placeholder="1500"
                                className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm text-slate-700 placeholder-slate-400 transition-colors"
                            />
                        </div>
                    </div>
                </div>

                {error && <p className="text-xs text-red-500 mt-3">{error}</p>}

                <div className="flex gap-2 mt-5">
                    <button
                        onClick={onClose}
                        className="flex-1 py-2.5 rounded-lg border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50 transition-colors"
                    >
                        취소
                    </button>
                    <button
                        onClick={handleSubmit}
                        disabled={submitting}
                        className="flex-1 py-2.5 rounded-lg bg-indigo-500 hover:bg-indigo-400 disabled:bg-slate-300 text-white text-sm font-semibold transition-colors"
                    >
                        {submitting ? "저장 중..." : isEdit ? "수정 완료" : "등록하기"}
                    </button>
                </div>
            </div>
        </div>
    )
}
