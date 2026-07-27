import { Button } from "../../components/ui/button";
import {
  harvestErrorSupportCode,
  isHarvestUnavailable,
} from "./harvest-error-policy";

export function HarvestReceiptSkeleton() {
  return (
    <div className="space-y-6" role="status" aria-label="Đang tải biên nhận thu hoạch">
      <div className="h-24 animate-pulse rounded-card bg-forest-50" />
      <div className="h-56 animate-pulse rounded-card bg-forest-50" />
      <div className="grid gap-4 lg:grid-cols-3">
        {[0, 1, 2].map((item) => <div key={item} className="h-48 animate-pulse rounded-card bg-forest-50" />)}
      </div>
    </div>
  );
}

export function HarvestReceiptError({ error, onRetry }: { error: Error; onRetry: () => void }) {
  const unavailable = isHarvestUnavailable(error);
  const supportCode = harvestErrorSupportCode(error);
  return (
    <section className="rounded-card border border-danger/30 bg-red-50 p-6" role="alert">
      <h1 className="text-2xl font-bold text-ink">Biên nhận thu hoạch</h1>
      <p className="mt-3 text-sm leading-6 text-ink">
        {unavailable
          ? "Biên nhận không còn khả dụng hoặc bạn không còn quyền với lô thu hoạch này."
          : "Không thể tải biên nhận. Dịch vụ có thể đang gián đoạn."}
      </p>
      {supportCode ? <p className="mt-2 text-xs text-muted">Mã hỗ trợ: {supportCode}</p> : null}
      <Button className="mt-4" variant="secondary" onClick={onRetry}>Tải lại biên nhận</Button>
    </section>
  );
}
