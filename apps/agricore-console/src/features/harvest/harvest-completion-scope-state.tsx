import { Button } from "../../components/ui/button";
import { ApiClientError } from "../../lib/api/errors";

export function HarvestScopeStatus({ message }: { message: string }) {
  return (
    <section className="rounded-card border border-border bg-surface p-5 shadow-sm" role="status">
      <p className="text-sm text-muted">{message}</p>
    </section>
  );
}

export function HarvestScopeError({
  error,
  mismatch = false,
  actionLabel = "Xác minh lại",
  onRetry,
}: {
  error: Error | null;
  mismatch?: boolean;
  actionLabel?: string;
  onRetry: () => void;
}) {
  const accessDenied = error instanceof ApiClientError
    && (error.status === 403 || error.status === 404);
  const supportCode = error instanceof ApiClientError ? error.code : null;
  return (
    <section className="rounded-card border border-danger/30 bg-red-50 p-5" role="alert">
      <h2 className="font-semibold text-danger">
        {accessDenied
          ? "Nông trại không còn trong phạm vi truy cập"
          : mismatch ? "Dữ liệu nông trại không khớp phạm vi" : "Không thể xác minh nông trại"}
      </h2>
      <p className="mt-2 text-sm text-ink">
        {accessDenied
          ? "Quyền đã thay đổi; mùa vụ và form hoàn tất chưa được tải."
          : "Cần xác minh lại farm-service trước khi gửi dữ liệu thu hoạch."}
      </p>
      {supportCode ? <p className="mt-2 text-xs text-muted">Mã hỗ trợ: {supportCode}</p> : null}
      <Button className="mt-4" variant="secondary" onClick={onRetry}>{actionLabel}</Button>
    </section>
  );
}
