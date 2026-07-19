import { Button } from "../../components/ui/button";
import { EmptyState } from "../../components/ui/empty-state";
import { PaginationControls } from "../../components/ui/pagination-controls";
import { ApiClientError } from "../../lib/api/errors";
import type { CropPageResponse } from "../../lib/api/types";
import {
  formatGrowthDays,
  formatHumidity,
  formatPh,
  formatTemperature,
  formatYield,
} from "./crop-formatters";

function CropCatalogSkeleton() {
  return (
    <div className="grid gap-4 lg:grid-cols-2" role="status" aria-label="Đang tải danh mục cây trồng">
      {[0, 1, 2, 3].map((item) => (
        <div key={item} className="h-64 animate-pulse rounded-card bg-forest-50" />
      ))}
    </div>
  );
}

export function CropCatalogPanel({
  data,
  error,
  isPending,
  isFetching,
  onRetry,
  onPrevious,
  onNext,
}: {
  data: CropPageResponse | undefined;
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
      className="rounded-card border border-border bg-surface p-5 shadow-sm"
      aria-labelledby="crop-catalog-heading"
      aria-busy={isPending || isFetching}
    >
      <div className="mb-5 flex items-start justify-between gap-3">
        <div>
          <h2 id="crop-catalog-heading" className="text-lg font-semibold text-ink">
            Danh mục cây trồng
          </h2>
          <p className="mt-1 text-sm text-muted">
            {data ? `${data.totalElements} loại cây` : "Dữ liệu từ crop-catalog-service"}
          </p>
        </div>
        {isFetching && !isPending ? (
          <span className="text-xs font-medium text-info" role="status">
            Đang cập nhật
          </span>
        ) : null}
      </div>

      {isPending ? <CropCatalogSkeleton /> : null}

      {!isPending && error ? (
        <div className="rounded-card border border-danger/30 bg-red-50 p-5" role="alert">
          <h3 className="font-semibold text-danger">Không thể tải danh mục cây trồng</h3>
          <p className="mt-2 text-sm text-ink">
            Kiểm tra kết nối dịch vụ rồi thử lại. Bộ lọc hiện tại được giữ nguyên.
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
            title="Không tìm thấy cây trồng"
            description="Thử thay đổi từ khóa hoặc danh mục để mở rộng kết quả tìm kiếm."
          />
        ) : (
          <EmptyState
            title="Trang danh mục này không còn dữ liệu"
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
        <div className="space-y-4">
          <div className="grid gap-4 lg:grid-cols-2">
            {data.content.map((crop) => (
              <article key={crop.id} className="rounded-card border border-border bg-canvas/50 p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-xs font-bold uppercase tracking-wide text-forest-700">
                      {crop.code}
                    </p>
                    <h3 className="mt-1 text-lg font-semibold text-ink">{crop.name}</h3>
                    <p className="mt-1 text-sm italic text-muted">
                      {crop.scientificName ?? "Chưa cập nhật tên khoa học"}
                    </p>
                  </div>
                  <span className="rounded-full bg-forest-50 px-2.5 py-1 text-xs font-medium text-forest-900">
                    {crop.category}
                  </span>
                </div>
                <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
                  <div>
                    <dt className="text-xs text-muted">Sinh trưởng</dt>
                    <dd className="mt-1 font-medium text-ink">
                      {formatGrowthDays(crop.growthDaysMin, crop.growthDaysMax)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted">Năng suất dự kiến</dt>
                    <dd className="mt-1 font-medium text-ink">
                      {formatYield(crop.expectedYieldPerHa, crop.yieldUnit)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted">Nhiệt độ</dt>
                    <dd className="mt-1 font-medium text-ink">
                      {formatTemperature(crop.tempMinC, crop.tempMaxC)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted">Độ ẩm</dt>
                    <dd className="mt-1 font-medium text-ink">
                      {formatHumidity(crop.humidityMinPct, crop.humidityMaxPct)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted">pH phù hợp</dt>
                    <dd className="mt-1 font-medium text-ink">
                      {formatPh(crop.phMin, crop.phMax)}
                    </dd>
                  </div>
                </dl>
                {crop.description ? <p className="mt-4 text-sm leading-6 text-muted">{crop.description}</p> : null}
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
