import { Link } from "@tanstack/react-router";

import { Button } from "../../components/ui/button";
import { EmptyState } from "../../components/ui/empty-state";
import { PaginationControls } from "../../components/ui/pagination-controls";
import { ApiClientError } from "../../lib/api/errors";
import type { CropCyclePageResponse, FarmResponse } from "../../lib/api/types";
import {
  cycleStageLabel,
  cycleStatusLabel,
  formatCycleDate,
  formatCycleDateRange,
  shortResourceId,
} from "./crop-cycle-formatters";

function CropCycleListSkeleton() {
  return (
    <div className="grid gap-4 lg:grid-cols-2" role="status" aria-label="Đang tải danh sách mùa vụ">
      {[0, 1, 2, 3].map((item) => (
        <div key={item} className="h-56 animate-pulse rounded-card bg-forest-50" />
      ))}
    </div>
  );
}

export function CropCycleListPanel({
  farm,
  data,
  error,
  isPending,
  isFetching,
  onRetry,
  onPrevious,
  onNext,
}: {
  farm: FarmResponse | null;
  data: CropCyclePageResponse | undefined;
  error: Error | null;
  isPending: boolean;
  isFetching: boolean;
  onRetry: () => void;
  onPrevious: () => void;
  onNext: () => void;
}) {
  const supportCode = error instanceof ApiClientError ? error.code : null;

  return (
    <section
      className="min-h-[34rem] rounded-card border border-border bg-surface p-5 shadow-sm"
      aria-labelledby="cycle-list-heading"
      aria-busy={isPending || isFetching}
    >
      <div className="mb-5 flex items-start justify-between gap-3">
        <div>
          <h2 id="cycle-list-heading" className="text-lg font-semibold text-ink">Mùa vụ</h2>
          <p className="mt-1 text-sm text-muted">
            {farm ? `${farm.code} · ${farm.name}` : "Chọn nông trại để xem mùa vụ"}
          </p>
        </div>
        {isFetching && !isPending ? (
          <span className="text-xs font-medium text-info" role="status">Đang cập nhật</span>
        ) : null}
      </div>

      {isPending ? <CropCycleListSkeleton /> : null}

      {!isPending && farm && error ? (
        <div className="rounded-card border border-danger/30 bg-red-50 p-5" role="alert">
          <h3 className="font-semibold text-danger">Không thể tải danh sách mùa vụ</h3>
          <p className="mt-2 text-sm text-ink">
            Quyền truy cập có thể vừa thay đổi hoặc crop-cycle-service đang gián đoạn.
          </p>
          {supportCode ? <p className="mt-2 text-xs text-muted">Mã hỗ trợ: {supportCode}</p> : null}
          <Button className="mt-4" variant="secondary" onClick={onRetry}>Thử lại</Button>
        </div>
      ) : null}

      {!isPending && farm && !error && data?.content.length === 0 ? (
        data.totalElements === 0 ? (
          <EmptyState
            title="Nông trại này chưa có mùa vụ"
            description="Khi một mùa vụ được tạo qua API, trạng thái hiện tại sẽ xuất hiện ở đây."
          />
        ) : (
          <EmptyState
            title="Trang mùa vụ này không còn dữ liệu"
            description="Dữ liệu có thể vừa thay đổi. Quay về trang trước để tiếp tục."
            action={
              <Button variant="secondary" onClick={data.page > 0 ? onPrevious : onRetry}>
                {data.page > 0 ? "Về trang trước" : "Tải lại"}
              </Button>
            }
          />
        )
      ) : null}

      {!isPending && farm && !error && data && data.content.length > 0 ? (
        <div className="space-y-4">
          <div className="grid gap-4 lg:grid-cols-2">
            {data.content.map((cycle) => (
              <article key={cycle.id} className="rounded-card border border-border bg-canvas/50 p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-xs font-bold uppercase tracking-wide text-forest-700">{cycle.code}</p>
                    <h3 className="mt-1 font-semibold text-ink">{cycleStageLabel(cycle.stage)}</h3>
                  </div>
                  <span className="rounded-full bg-forest-50 px-2.5 py-1 text-xs font-medium text-forest-900">
                    {cycleStatusLabel(cycle.status)}
                  </span>
                </div>
                <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
                  <div className="col-span-2">
                    <dt className="text-xs text-muted">Kế hoạch</dt>
                    <dd className="mt-1 font-medium text-ink">
                      {formatCycleDateRange(cycle.plannedStartDate, cycle.plannedEndDate)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted">ID lô rút gọn</dt>
                    <dd className="mt-1 font-mono text-xs font-semibold text-ink" title={cycle.plotId}>
                      {shortResourceId(cycle.plotId)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted">ID cây rút gọn</dt>
                    <dd className="mt-1 font-mono text-xs font-semibold text-ink" title={cycle.cropId}>
                      {shortResourceId(cycle.cropId)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted">Bắt đầu thực tế</dt>
                    <dd className="mt-1 font-medium text-ink">{formatCycleDate(cycle.actualStartDate)}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted">Kết thúc thực tế</dt>
                    <dd className="mt-1 font-medium text-ink">{formatCycleDate(cycle.actualEndDate)}</dd>
                  </div>
                </dl>
                {cycle.notes ? <p className="mt-4 text-sm leading-6 text-muted">{cycle.notes}</p> : null}
                <Link
                  to="/crop-cycles/$cycleId"
                  params={{ cycleId: cycle.id }}
                  className="mt-4 inline-flex text-sm font-semibold text-forest-800 underline-offset-4 hover:underline"
                >
                  Xem chi tiết
                </Link>
              </article>
            ))}
          </div>
          <PaginationControls
            page={data.page}
            totalPages={data.totalPages}
            isFetching={isFetching}
            onPrevious={onPrevious}
            onNext={onNext}
          />
        </div>
      ) : null}
    </section>
  );
}
