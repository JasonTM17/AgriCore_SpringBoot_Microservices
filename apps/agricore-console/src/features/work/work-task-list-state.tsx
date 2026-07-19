import { Button } from "../../components/ui/button";
import { ApiClientError } from "../../lib/api/errors";

export function WorkTaskListSkeleton() {
  return (
    <div className="grid gap-4 md:grid-cols-2" role="status" aria-label="Đang tải công việc…">
      {[0, 1].map((index) => (
        <div key={index} className="h-48 animate-pulse rounded-card bg-forest-50 motion-reduce:animate-none" />
      ))}
    </div>
  );
}

export function WorkTaskLoadError({
  error,
  onRetry,
  onBack,
  hasCachedData,
}: {
  error: Error;
  onRetry: () => void;
  onBack: (() => void) | null;
  hasCachedData: boolean;
}) {
  const supportCode = error instanceof ApiClientError ? error.code : null;
  const unavailable = error instanceof ApiClientError && (error.status === 403 || error.status === 404);
  return (
    <div
      className="rounded-control border border-danger/30 bg-red-50 p-4"
      role="alert"
      aria-label="Không thể tải công việc"
    >
      <p className="text-sm font-semibold text-ink">
        {unavailable
          ? "Danh sách công việc không còn khả dụng trong phạm vi hiện tại."
          : "Không thể tải danh sách công việc. Dịch vụ có thể đang gián đoạn."}
      </p>
      {supportCode ? <p className="mt-1 text-xs text-muted">Mã hỗ trợ: {supportCode}</p> : null}
      {!unavailable && hasCachedData ? (
        <p className="mt-1 text-xs text-muted">Dữ liệu bên dưới là kết quả tải thành công gần nhất.</p>
      ) : null}
      <div className="mt-3 flex flex-wrap gap-2">
        <Button className="min-h-11" variant="secondary" onClick={onRetry}>
          Thử tải lại công việc
        </Button>
        {onBack && !unavailable ? (
          <Button className="min-h-11" variant="secondary" onClick={onBack}>
            Về trang trước
          </Button>
        ) : null}
      </div>
    </div>
  );
}
