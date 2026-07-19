import { Button } from "./button";

export function PaginationControls({
  page,
  totalPages,
  isFetching,
  onPrevious,
  onNext,
}: {
  page: number;
  totalPages: number;
  isFetching: boolean;
  onPrevious: () => void;
  onNext: () => void;
}) {
  return totalPages > 1 ? (
    <div className="flex items-center justify-between gap-3 border-t border-border pt-4">
      <p className="text-xs text-muted" aria-live="polite">
        Trang {page + 1} / {totalPages}
        {isFetching ? " · Đang cập nhật" : ""}
      </p>
      <div className="flex gap-2">
        <Button variant="secondary" disabled={page === 0 || isFetching} onClick={onPrevious}>
          Trước
        </Button>
        <Button
          variant="secondary"
          disabled={page + 1 >= totalPages || isFetching}
          onClick={onNext}
        >
          Sau
        </Button>
      </div>
    </div>
  ) : null;
}
