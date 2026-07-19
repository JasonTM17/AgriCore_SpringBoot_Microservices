import { Button } from "../../components/ui/button";
import { EmptyState } from "../../components/ui/empty-state";
import { ApiClientError } from "../../lib/api/errors";
import type { FarmResponse, PlotPageResponse } from "../../lib/api/types";
import { formatArea, plotStatusLabel } from "./farm-formatters";
import { PaginationControls } from "./pagination-controls";

function PlotListSkeleton() {
  return (
    <div className="grid gap-3 sm:grid-cols-2" role="status" aria-label="Đang tải lô canh tác">
      {[0, 1, 2, 3].map((item) => (
        <div key={item} className="h-32 animate-pulse rounded-card bg-forest-50" />
      ))}
    </div>
  );
}

export function PlotListPanel({
  farm,
  data,
  error,
  isPending,
  isFetching,
  waitingForFarm,
  onRetry,
  onPrevious,
  onNext,
}: {
  farm: FarmResponse | null;
  data: PlotPageResponse | undefined;
  error: Error | null;
  isPending: boolean;
  isFetching: boolean;
  waitingForFarm: boolean;
  onRetry: () => void;
  onPrevious: () => void;
  onNext: () => void;
}) {
  const supportCode = error instanceof ApiClientError ? error.code : null;

  return (
    <section
      className="min-h-[34rem] rounded-card border border-border bg-surface p-5 shadow-sm"
      aria-labelledby="plot-list-heading"
      aria-busy={waitingForFarm || isPending || isFetching}
    >
      <div className="mb-5 flex items-start justify-between gap-3">
        <div>
          <h2 id="plot-list-heading" className="text-lg font-semibold text-ink">
            Lô canh tác
          </h2>
          <p className="mt-1 text-sm text-muted">
            {farm ? `${farm.code} · ${farm.name}` : "Chọn nông trại để xem lô"}
          </p>
        </div>
        {isFetching && !isPending ? (
          <span className="text-xs font-medium text-info" role="status">
            Đang cập nhật
          </span>
        ) : null}
      </div>

      {waitingForFarm || isPending ? <PlotListSkeleton /> : null}

      {!waitingForFarm && !farm ? (
        <div className="grid min-h-80 place-items-center rounded-card border border-dashed border-border px-6 text-center">
          <p className="max-w-sm text-sm leading-6 text-muted">
            Khi tài khoản có farm membership, lô canh tác của nông trại đang hoạt động sẽ xuất hiện ở đây.
          </p>
        </div>
      ) : null}

      {!waitingForFarm && farm && !isPending && error ? (
        <div className="rounded-card border border-danger/30 bg-red-50 p-5" role="alert">
          <h3 className="font-semibold text-danger">Không thể tải lô canh tác</h3>
          <p className="mt-2 text-sm text-ink">
            Nông trại có thể không còn khả dụng hoặc farm-service đang gián đoạn.
          </p>
          {supportCode ? <p className="mt-2 text-xs text-muted">Mã hỗ trợ: {supportCode}</p> : null}
          <Button className="mt-4" variant="secondary" onClick={onRetry}>
            Thử lại
          </Button>
        </div>
      ) : null}

      {!waitingForFarm && farm && !isPending && !error && data?.content.length === 0 ? (
        data.totalElements === 0 ? (
          <EmptyState
            title="Nông trại này chưa có lô canh tác"
            description="Tạo lô từ API quản trị hoặc chọn một nông trại khác để tiếp tục."
          />
        ) : (
          <EmptyState
            title="Trang lô canh tác này không còn dữ liệu"
            description="Dữ liệu có thể vừa thay đổi. Quay về trang trước để tiếp tục."
            action={
              <Button variant="secondary" onClick={data.page > 0 ? onPrevious : onRetry}>
                {data.page > 0 ? "Về trang trước" : "Tải lại"}
              </Button>
            }
          />
        )
      ) : null}

      {!waitingForFarm && farm && !isPending && !error && data && data.content.length > 0 ? (
        <div className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2">
            {data.content.map((plot) => (
              <article key={plot.id} className="rounded-card border border-border bg-canvas/50 p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-xs font-bold uppercase tracking-wide text-forest-700">
                      {plot.code}
                    </p>
                    <h3 className="mt-1 font-semibold text-ink">{plot.name}</h3>
                  </div>
                  <span className="rounded-full bg-forest-50 px-2.5 py-1 text-xs font-medium text-forest-900">
                    {plotStatusLabel(plot.status)}
                  </span>
                </div>
                <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
                  <div>
                    <dt className="text-xs text-muted">Diện tích</dt>
                    <dd className="mt-1 font-medium text-ink">{formatArea(plot.areaInHectares)}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted">Loại đất</dt>
                    <dd className="mt-1 font-medium text-ink">{plot.soilType ?? "Chưa cập nhật"}</dd>
                  </div>
                </dl>
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
