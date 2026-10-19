import AppLayout from "../layout/AppLayout";
import EmotionChatPanel from "../features/ai-manager/EmotionChatPanel";

/** 기존 /ai/emotion 주소를 유지하고 전체화면 메신저로 전환한다. */
export default function EmotionAnalysisPage() {
  return (
    <AppLayout pageClassName="tp-aurora-light" showFooter={false}>
      <div className="mx-auto flex h-[calc(100dvh-4rem)] max-w-4xl flex-col px-2 py-3 sm:px-6 sm:py-6">
        <section className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-3xl bg-white shadow-[0_20px_50px_-35px_rgba(60,40,180,0.45)]">
          <EmotionChatPanel />
        </section>
      </div>
    </AppLayout>
  );
}
