import { Button } from "../../components/ui/button";
import { ApiClientError } from "../../lib/api/errors";

export function PublicTraceabilitySkeleton() {
  return (
    <div className="space-y-5" role="status" aria-label="Đang tải dữ liệu truy xuất">
      <div className="h-40 animate-pulse rounded-card bg-forest-100" />
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="h-32 animate-pulse rounded-card bg-forest-100" />
        <div className="h-32 animate-pulse rounded-card bg-forest-100" />
      </div>
      <div className="h-64 animate-pulse rounded-card bg-forest-100" />
    </div>
  );
}

export function PublicTraceabilityInvalid() {
  return (
    <section
      className="rounded-card border border-danger/30 bg-red-50 p-6 text-center"
      role="alert"
      aria-label="Mã truy xuất không hợp lệ"
    >
      <h1 className="text-xl font-semibold text-danger">Mã truy xuất không hợp lệ</h1>
      <p className="mt-2 text-sm leading-6 text-ink">Mã trên đường dẫn phải có từ 1 đến 64 ký tự.</p>
    </section>
  );
}

export function PublicTraceabilityError({
  error,
  isRetrying,
  onRetry,
}: {
  error: Error;
  isRetrying: boolean;
  onRetry: () => void;
}) {
  const apiError = error instanceof ApiClientError ? error : null;
  const delayed = apiError?.status === 404;
  const title = delayed ? "Chưa có dữ liệu truy xuất" : "Không thể tải dữ liệu truy xuất";
  const message = delayed
    ? "Mã có thể chưa đúng hoặc lô truy xuất đang được đồng bộ. Hãy đợi một lúc rồi kiểm tra lại."
    : apiError?.status === 401 || apiError?.status === 403
      ? "Endpoint công khai đang cấu hình không đúng. Bạn không cần đăng nhập để xem trang này."
      : "Dịch vụ truy xuất tạm thời gián đoạn. Vui lòng thử lại.";
  const supportCode = apiError && apiError.code !== "UNKNOWN_ERROR" ? apiError.code : null;

  return (
    <section
      className="rounded-card border border-warning/40 bg-amber-50 p-6 text-center"
      role="alert"
      aria-label={title}
    >
      <h1 className="text-xl font-semibold text-ink">{title}</h1>
      <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-ink">{message}</p>
      {supportCode ? <p className="mt-2 text-xs text-muted">Mã hỗ trợ: {supportCode}</p> : null}
      <Button className="mt-5 min-h-11" variant="secondary" onClick={onRetry} disabled={isRetrying}>
        {isRetrying ? "Đang kiểm tra…" : "Kiểm tra lại"}
      </Button>
    </section>
  );
}
