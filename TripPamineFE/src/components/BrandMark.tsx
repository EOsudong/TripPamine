/**
 * TripPamine 브랜드 마크.
 * 그라데이션 블롭 + 비행 궤적.
 */
export default function BrandMark({
                              small = false,
                          }: {
    small?: boolean;
}) {
    const size = small ? "h-6 w-6 rounded-lg" : "h-8 w-8 rounded-xl";

    return (
        <span
            className={`
        relative flex ${size} shrink-0
        items-center justify-center overflow-hidden
        tp-gradient-bg
        shadow-[0_0_20px_rgba(124,108,255,0.5)]
      `}
        >
            <svg
                viewBox="0 0 24 24"
                className={small ? "h-3.5 w-3.5" : "h-4.5 w-4.5"}
                fill="none"
                stroke="white"
                strokeWidth={2.2}
                strokeLinecap="round"
                strokeLinejoin="round"
            >
                <path d="M4 17c4-1 6-4 8-8s4-5 8-5" />
                <circle cx="20" cy="4" r="1.6" fill="white" stroke="none" />
                <circle cx="4" cy="17" r="2.2" fill="white" stroke="none" />
            </svg>
        </span>
    );
}
