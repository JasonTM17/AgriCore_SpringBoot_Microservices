import { Button } from "./button";

export function PaginationControls({
  page,
  totalPages,
  isFetching,
  label,
  onPrevious,
  onNext,
}: {
  page: number;
  totalPages: number;
  isFetching: boolean;
  label?: string;
  onPrevious: () => void;
  onNext: () => void;
}) {
  return totalPages > 1 ? (
    <div
      className="flex items-center justify-between gap-3 border-t border-border pt-4"
      {...(label ? { role: "navigation", "aria-label": label } : {})}
    >
      <p className="text-xs text-muted" aria-live="polite">
        Trang {page + 1} / {totalPages}
        {isFetching ? " · Đang cập nhật" : ""}
      </p>
      <div className="flex gap-2">
        <Button
          variant="secondary"
          disabled={page === 0 || isFetching}
          aria-label={label ? `${label}: trang trước` : undefined}
          onClick={onPrevious}
        >
          Trước
        </Button>
        <Button
          variant="secondary"
          disabled={page + 1 >= totalPages || isFetching}
          aria-label={label ? `${label}: trang sau` : undefined}
          onClick={onNext}
        >
          Sau
        </Button>
      </div>
    </div>
  ) : null;
}
