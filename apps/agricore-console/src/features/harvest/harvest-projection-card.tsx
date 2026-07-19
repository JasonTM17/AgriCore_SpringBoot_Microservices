import { Button } from "../../components/ui/button";
import { harvestErrorSupportCode } from "./harvest-error-policy";

interface HarvestProjectionCardProps {
  title: string;
  stateLabel: string;
  description: string;
  timestamp: string | null;
  error: Error | null;
  errorMessage: string;
  isPending: boolean;
  isFetching: boolean;
  onRetry: () => void;
}

export function HarvestProjectionCard({
  title,
  stateLabel,
  description,
  timestamp,
  error,
  errorMessage,
  isPending,
  isFetching,
  onRetry,
}: HarvestProjectionCardProps) {
  const supportCode = harvestErrorSupportCode(error);
  return (
    <article className="min-h-48 rounded-card border border-border bg-surface p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <h3 className="text-sm font-semibold uppercase tracking-wide text-muted">{title}</h3>
        {isFetching && !isPending ? (
          <span className="text-xs font-medium text-info" role="status">Đang cập nhật</span>
        ) : null}
      </div>
      {isPending ? (
        <div className="mt-5 space-y-3" role="status" aria-label={`Đang tải ${title}`}>
          <div className="h-6 w-40 animate-pulse rounded bg-forest-50" />
          <div className="h-12 animate-pulse rounded bg-forest-50" />
        </div>
      ) : null}
      {!isPending && error ? (
        <div className="mt-4" role="alert">
          <p className="font-semibold text-danger">{errorMessage}</p>
          {supportCode ? <p className="mt-1 text-xs text-muted">Mã hỗ trợ: {supportCode}</p> : null}
          <Button className="mt-3 min-h-11" variant="secondary" onClick={onRetry}>Thử lại</Button>
        </div>
      ) : null}
      {!isPending && !error ? (
        <div className="mt-4">
          <p className="text-lg font-bold text-forest-900">{stateLabel}</p>
          <p className="mt-2 text-sm leading-6 text-muted">{description}</p>
          {timestamp ? <p className="mt-3 text-xs text-muted">Cập nhật: {timestamp}</p> : null}
        </div>
      ) : null}
    </article>
  );
}
