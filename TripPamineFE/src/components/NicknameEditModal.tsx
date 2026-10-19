import { useEffect, useState } from "react";
import {
  NICKNAME_MAX,
  NICKNAME_MIN,
  NICKNAME_PATTERN,
  checkNicknameApi,
  notifyUserUpdated,
  updateNicknameApi,
  type MyInfoResponse,
} from "../api/auth";
import { extractQuestErrorMessage } from "../api/quest";

// [닉네임 변경] 모달.
// 입력 → (0.4초 멈추면) 형식 검사 + 서버 중복 확인 → 저장.
// 저장에 성공하면 notifyUserUpdated()로 헤더 등 다른 화면에도 새 닉네임을 반영한다.
// 서버도 같은 규칙으로 다시 검사하므로, 여기 검사는 사용자 안내용이다.

interface NicknameEditModalProps {
  currentUserName: string | null;
  onClose: () => void;
  onSaved: (info: MyInfoResponse) => void;
}

type CheckState =
  | { kind: "idle" }
  | { kind: "checking" }
  | { kind: "ok"; message: string }
  | { kind: "error"; message: string };

function validateFormat(value: string): string | null {
  if (value.length < NICKNAME_MIN || value.length > NICKNAME_MAX) {
    return `닉네임은 ${NICKNAME_MIN}~${NICKNAME_MAX}자로 입력해 주세요.`;
  }
  if (!NICKNAME_PATTERN.test(value)) {
    return "한글, 영문, 숫자, 밑줄(_)만 사용할 수 있어요.";
  }
  return null;
}

export default function NicknameEditModal({ currentUserName, onClose, onSaved }: NicknameEditModalProps) {
  const [value, setValue] = useState(currentUserName ?? "");
  const [check, setCheck] = useState<CheckState>({ kind: "idle" });
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const trimmed = value.trim();
  const unchanged = trimmed === (currentUserName ?? "");

  useEffect(() => {
    setSaveError(null);
    if (trimmed === "" || unchanged) {
      setCheck({ kind: "idle" });
      return;
    }
    const formatError = validateFormat(trimmed);
    if (formatError) {
      setCheck({ kind: "error", message: formatError });
      return;
    }

    setCheck({ kind: "checking" });
    let cancelled = false;
    const timer = window.setTimeout(() => {
      checkNicknameApi(trimmed)
        .then((result) => {
          if (cancelled) return;
          setCheck(result.available ? { kind: "ok", message: result.message } : { kind: "error", message: result.message });
        })
        .catch((err) => {
          if (!cancelled) setCheck({ kind: "error", message: extractQuestErrorMessage(err, "중복 확인에 실패했어요.") });
        });
    }, 400);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [trimmed, unchanged]);

  // ESC로 닫기
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !saving) onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose, saving]);

  const canSave = check.kind === "ok" && !saving;

  const handleSave = async () => {
    if (!canSave) return;
    setSaving(true);
    setSaveError(null);
    try {
      const info = await updateNicknameApi(trimmed);
      notifyUserUpdated();
      onSaved(info);
    } catch (err) {
      setSaveError(extractQuestErrorMessage(err, "닉네임을 바꾸지 못했어요. 잠시 후 다시 시도해 주세요."));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/70 p-4" onClick={() => !saving && onClose()}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="nickname-modal-title"
        className="w-full max-w-md rounded-[2rem] border border-white/10 bg-night-950 p-6 text-white shadow-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <p className="text-[10px] font-black tracking-[0.22em] text-cyan-300">PROFILE</p>
        <h2 id="nickname-modal-title" className="mt-1 font-display text-2xl font-bold">닉네임 변경</h2>
        <p className="mt-1 text-sm text-white/50">
          {NICKNAME_MIN}~{NICKNAME_MAX}자, 한글·영문·숫자·밑줄(_)만 쓸 수 있어요.
        </p>

        <form
          className="mt-5"
          onSubmit={(event) => {
            event.preventDefault();
            void handleSave();
          }}
        >
          <div className="relative">
            <input
              autoFocus
              value={value}
              maxLength={NICKNAME_MAX + 4}
              onChange={(event) => setValue(event.target.value)}
              placeholder="새 닉네임"
              className="w-full rounded-2xl border border-white/15 bg-white/5 px-4 py-3 pr-16 text-base font-bold outline-none placeholder:text-white/30 focus:border-cyan-400"
            />
            <span className="absolute right-4 top-1/2 -translate-y-1/2 text-xs font-bold text-white/40">
              {trimmed.length}/{NICKNAME_MAX}
            </span>
          </div>

          <p
            className={`mt-2 min-h-[1.25rem] text-xs font-semibold ${
              check.kind === "ok" ? "text-emerald-300" : check.kind === "error" ? "text-rose-300" : "text-white/40"
            }`}
          >
            {check.kind === "checking" && "확인 중…"}
            {check.kind === "ok" && `✓ ${check.message}`}
            {check.kind === "error" && check.message}
            {check.kind === "idle" && unchanged && trimmed !== "" && "지금 쓰고 있는 닉네임이에요."}
          </p>

          {saveError && (
            <div className="mt-2 rounded-xl border border-rose-400/30 bg-rose-500/10 px-4 py-2.5 text-sm text-rose-200">{saveError}</div>
          )}

          <div className="mt-5 flex gap-2">
            <button
              type="button"
              onClick={onClose}
              disabled={saving}
              className="flex-1 rounded-2xl bg-white/10 py-3 text-sm font-bold hover:bg-white/15 disabled:opacity-50"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={!canSave}
              className="flex-1 rounded-2xl bg-cyan-400 py-3 text-sm font-black text-night-950 hover:bg-cyan-300 disabled:cursor-not-allowed disabled:bg-white/10 disabled:text-white/30"
            >
              {saving ? "저장 중…" : "저장"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
