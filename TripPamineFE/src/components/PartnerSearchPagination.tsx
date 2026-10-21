import { ChevronLeft, ChevronRight } from "lucide-react";
import "./PartnerSearchPagination.css";

interface PartnerSearchPaginationProps {
  currentPage: number; // 사용자에게 표시하는 1부터 시작하는 페이지
  totalPages: number;
  loading: boolean;
  onPageChange: (page: number) => void;
}

/** 투어·티켓과 숙박에서 같은 페이지 이동 UX를 사용한다. */
export default function PartnerSearchPagination({
  currentPage, totalPages, loading, onPageChange,
}: PartnerSearchPaginationProps) {
  if (totalPages <= 1) return null;
  const first = Math.max(1, Math.min(currentPage - 2, totalPages - 4));
  const last = Math.min(totalPages, first + 4);
  const pages = Array.from({ length: last - first + 1 }, (_, index) => first + index);

  return (
    <nav className="ar-partner-pager" aria-label="검색 결과 페이지 이동">
      <button type="button" disabled={loading || currentPage <= 1}
        onClick={() => onPageChange(currentPage - 1)} aria-label="이전 페이지">
        <ChevronLeft size={16} aria-hidden="true" /> 이전
      </button>
      <div className="ar-partner-pager-pages">
        {pages.map((page) => (
          <button key={page} type="button" disabled={loading} aria-label={`${page}페이지`}
            aria-current={currentPage === page ? "page" : undefined}
            onClick={() => onPageChange(page)}>{page}</button>
        ))}
      </div>
      <button type="button" disabled={loading || currentPage >= totalPages}
        onClick={() => onPageChange(currentPage + 1)} aria-label="다음 페이지">
        다음 <ChevronRight size={16} aria-hidden="true" />
      </button>
      <p className="ar-partner-pager-count">{currentPage.toLocaleString("ko-KR")} / {totalPages.toLocaleString("ko-KR")}페이지</p>
    </nav>
  );
}
