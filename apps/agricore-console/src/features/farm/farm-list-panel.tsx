import { Button } from "../../components/ui/button";
import { EmptyState } from "../../components/ui/empty-state";
import { PaginationControls } from "../../components/ui/pagination-controls";
import { ApiClientError } from "../../lib/api/errors";
import type { FarmPageResponse, FarmResponse } from "../../lib/api/types";
import { formatArea, farmStatusLabel } from "./farm-formatters";

function FarmListSkeleton() {
  return (
    <div className="space-y-3" role="status" aria-label="Đang tải danh sách nông trại">
      {[0, 1, 2].map((item) => (
        <div key={item} className="h-28 animate-pulse rounded-card bg-forest-50" />
      ))}
    </div>
  );
}

export function FarmListPanel({
  data,
  error,
  isPending,
  isFetching,
  activeFarmId,
  onSelect,
  onRetry,
  onPrevious,
  onNext,
}: {
  data: FarmPageResponse | undefined;
  error: Error | null;
  isPending: boolean;
  isFetching: boolean;
  activeFarmId: string | null;
  onSelect: (farm: FarmResponse) => void;
  onRetry: () => void;
  onPrevious: () => void;
  onNext: () => void;
}) {
  const supportCode = error instanceof ApiClientError ? error.code : null;

  return (
    <section
      className="min-h-[34rem] rounded-card border border-border bg-surface p-5 shadow-sm"
      aria-labelledby="farm-list-heading"
      aria-busy={isPending || isFetching}
    >
      <div className="mb-5 flex items-start justify-between gap-3">
        <div>
          <h2 id="farm-list-heading" className="text-lg font-semibold text-ink">
            Nông trại được phép truy cập
          </h2>
          <p className="mt-1 text-sm text-muted">
            {data ? `${data.totalElements} nông trại` : "Dữ liệu từ farm-service"}
          </p>
        </div>
        {isFetching && !isPending ? (
          <span className="text-xs font-medium text-info" role="status">
            Đang cập nhật
          </span>
        ) : null}
      </div>

      {isPending ? <FarmListSkeleton /> : null}

      {!isPending && error ? (
        <div className="rounded-card border border-danger/30 bg-red-50 p-5" role="alert">
          <h3 className="font-semibold text-danger">Không thể tải danh sách nông trại</h3>
          <p className="mt-2 text-sm text-ink">
            Kiểm tra kết nối dịch vụ rồi thử lại. Trang và phiên đăng nhập vẫn được giữ nguyên.
          </p>
          {supportCode ? <p className="mt-2 text-xs text-muted">Mã hỗ trợ: {supportCode}</p> : null}
          <Button className="mt-4" variant="secondary" onClick={onRetry}>
            Thử lại
          </Button>
        </div>
      ) : null}

      {!isPending && !error && data?.content.length === 0 ? (
        data.totalElements === 0 ? (
          <EmptyState
            title="Chưa có nông trại được cấp quyền"
            description="Tài khoản hiện tại chưa có membership nông trại. Hãy liên hệ quản trị viên hoặc quản lý nông trại."
          />
        ) : (
          <EmptyState
            title="Trang nông trại này không còn dữ liệu"
            description="Dữ liệu có thể vừa thay đổi. Quay về trang trước để tiếp tục."
            action={
              <Button variant="secondary" onClick={data.page > 0 ? onPrevious : onRetry}>
                {data.page > 0 ? "Về trang trước" : "Tải lại"}
              </Button>
            }
          />
        )
      ) : null}

      {!isPending && !error && data && data.content.length > 0 ? (
        <div className="space-y-3">
          <div className="space-y-3">
            {data.content.map((farm) => {
              const active = farm.id === activeFarmId;
              return (
                <button
                  key={farm.id}
                  type="button"
                  aria-pressed={active}
                  aria-label={`${farm.code} ${farm.name}`}
                  onClick={() => onSelect(farm)}
                  className={`w-full rounded-card border p-4 text-left transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info ${
                    active
                      ? "border-forest-700 bg-forest-50"
                      : "border-border bg-surface hover:border-forest-100 hover:bg-forest-50/40"
                  }`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="text-xs font-bold uppercase tracking-wide text-forest-700">
                        {farm.code}
                      </p>
                      <p className="mt-1 font-semibold text-ink">{farm.name}</p>
                    </div>
                    {active ? (
                      <span className="rounded-full bg-forest-700 px-2.5 py-1 text-xs font-semibold text-white">
                        Nông trại đang hoạt động
                      </span>
                    ) : null}
                  </div>
                  <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted">
                    <span>{farm.province ?? "Chưa cập nhật tỉnh"}</span>
                    <span>{formatArea(farm.totalAreaHa)}</span>
                    <span>{farmStatusLabel(farm.status)}</span>
                  </div>
                </button>
              );
            })}
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
