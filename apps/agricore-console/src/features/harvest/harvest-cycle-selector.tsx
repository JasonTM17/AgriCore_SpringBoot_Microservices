import { Button } from "../../components/ui/button";
import { EmptyState } from "../../components/ui/empty-state";
import { PaginationControls } from "../../components/ui/pagination-controls";
import { ApiClientError } from "../../lib/api/errors";
import type { CropCyclePageResponse, PlotResponse } from "../../lib/api/types";
import { cycleStageLabel } from "../crop-cycle/crop-cycle-formatters";

interface HarvestCycleSelectorProps {
  data: CropCyclePageResponse | undefined;
  error: Error | null;
  isPending: boolean;
  isFetching: boolean;
  selectedCycleId: string;
  plot: PlotResponse | null;
  plotError: Error | null;
  plotMismatch: boolean;
  isPlotPending: boolean;
  disabled: boolean;
  onSelect: (cycleId: string) => void;
  onRetry: () => void;
  onPrevious: () => void;
  onNext: () => void;
}

export function HarvestCycleSelector({
  data,
  error,
  isPending,
  isFetching,
  selectedCycleId,
  plot,
  plotError,
  plotMismatch,
  isPlotPending,
  disabled,
  onSelect,
  onRetry,
  onPrevious,
  onNext,
}: HarvestCycleSelectorProps) {
  const supportCode = error instanceof ApiClientError ? error.code : null;
  const plotSupportCode = plotError instanceof ApiClientError ? plotError.code : null;

  return (
    <div className="rounded-control border border-border bg-canvas/60 p-4">
      {isPending ? <p className="text-sm text-muted" role="status">Đang tải mùa vụ…</p> : null}
      {!isPending && error ? (
        <div className="rounded-control border border-danger/30 bg-red-50 p-4" role="alert">
          <p className="font-semibold text-danger">Không thể tải mùa vụ trong phạm vi đã chọn</p>
          {supportCode ? <p className="mt-1 text-xs text-muted">Mã hỗ trợ: {supportCode}</p> : null}
          <Button className="mt-3" variant="secondary" onClick={onRetry}>Thử lại</Button>
        </div>
      ) : null}
      {!isPending && !error && data?.totalElements === 0 ? (
        <EmptyState
          title="Nông trại chưa có mùa vụ"
          description="Cần một mùa vụ thật trước khi ghi nhận đợt thu hoạch."
        />
      ) : null}
      {!isPending && !error && data && data.content.length > 0 ? (
        <div className="space-y-4">
          <label className="grid gap-1.5">
            <span className="text-xs font-semibold uppercase tracking-wide text-muted">Mùa vụ</span>
            <select
              className="h-11 rounded-control border border-border bg-surface px-3 text-base text-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-info md:text-sm"
              aria-label="Mùa vụ"
              value={selectedCycleId}
              onChange={(event) => onSelect(event.target.value)}
              disabled={disabled || isFetching}
            >
              <option value="">Chọn mùa vụ</option>
              {data.content.map((cycle) => (
                <option key={cycle.id} value={cycle.id}>
                  {cycle.code} · {cycleStageLabel(cycle.stage)}
                </option>
              ))}
            </select>
          </label>
          {selectedCycleId && isPlotPending ? (
            <p className="text-sm text-muted" role="status">Đang xác minh lô đất…</p>
          ) : null}
          {selectedCycleId && (plotError || plotMismatch) ? (
            <div className="rounded-control border border-danger/30 bg-red-50 p-3" role="alert">
              <p className="text-sm font-semibold text-danger">
                {plotMismatch
                  ? "Lô đất không khớp với mùa vụ và nông trại đã xác minh."
                  : "Không thể xác minh lô đất của mùa vụ."}
              </p>
              {plotSupportCode ? <p className="mt-1 text-xs text-muted">Mã hỗ trợ: {plotSupportCode}</p> : null}
            </div>
          ) : null}
          {plot ? (
            <p className="rounded-control bg-forest-50 px-3 py-2 text-sm font-semibold text-forest-900">
              {plot.code} · {plot.name}
            </p>
          ) : null}
          <PaginationControls
            page={data.page}
            totalPages={data.totalPages}
            isFetching={isFetching}
            label="Phân trang mùa vụ thu hoạch"
            onPrevious={onPrevious}
            onNext={onNext}
          />
        </div>
      ) : null}
    </div>
  );
}
