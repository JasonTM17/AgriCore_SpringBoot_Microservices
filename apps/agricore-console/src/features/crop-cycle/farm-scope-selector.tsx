import { Button } from "../../components/ui/button";
import { EmptyState } from "../../components/ui/empty-state";
import { PaginationControls } from "../../components/ui/pagination-controls";
import { ApiClientError } from "../../lib/api/errors";
import type { FarmPageResponse, FarmResponse } from "../../lib/api/types";

function FarmScopeSkeleton() {
  return (
    <div className="h-24 animate-pulse rounded-card bg-forest-50" role="status" aria-label="Đang tải phạm vi nông trại" />
  );
}

export function FarmScopeSelector({
  data,
  error,
  isPending,
  isFetching,
  activeFarm,
  validationError,
  isValidating,
  excludedFarmId,
  onSelect,
  onRetry,
  onRetryValidation,
  onResetScope,
  onPreviousPage,
  onNextPage,
}: {
  data: FarmPageResponse | undefined;
  error: Error | null;
  isPending: boolean;
  isFetching: boolean;
  activeFarm: FarmResponse | null;
  validationError: Error | null;
  isValidating: boolean;
  excludedFarmId: string | null;
  onSelect: (farm: FarmResponse) => void;
  onRetry: () => void;
  onRetryValidation: () => void;
  onResetScope: () => void;
  onPreviousPage: () => void;
  onNextPage: () => void;
}) {
  const farms = (data?.content ?? []).filter((farm) => farm.id !== excludedFarmId);
  const selectableActiveFarm = activeFarm?.id === excludedFarmId ? null : activeFarm;
  const options = selectableActiveFarm && !farms.some((farm) => farm.id === selectableActiveFarm.id)
    ? [selectableActiveFarm, ...farms]
    : farms;
  const accessDenied = validationError instanceof ApiClientError
    && (validationError.status === 403 || validationError.status === 404);

  return (
    <section className="rounded-card border border-border bg-surface p-5 shadow-sm" aria-labelledby="farm-scope-heading">
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h2 id="farm-scope-heading" className="text-base font-semibold text-ink">
            Phạm vi nông trại
          </h2>
          <p className="mt-1 text-sm text-muted">
            Lựa chọn này chỉ đặt ngữ cảnh; crop-cycle-service vẫn xác minh membership.
          </p>
        </div>
        {(isFetching || isValidating) && !isPending ? (
          <span className="text-xs font-medium text-info" role="status">
            {isValidating ? "Đang xác minh quyền" : "Đang cập nhật"}
          </span>
        ) : null}
      </div>

      {isPending ? <FarmScopeSkeleton /> : null}

      {!isPending && error ? (
        <div className="rounded-card border border-danger/30 bg-red-50 p-4" role="alert">
          <p className="font-semibold text-danger">Không thể tải phạm vi nông trại</p>
          <p className="mt-1 text-sm text-ink">Thử kết nối lại farm-service trước khi tải mùa vụ.</p>
          <Button className="mt-3" variant="secondary" onClick={onRetry}>Thử lại</Button>
        </div>
      ) : null}

      {!isPending && !error && data?.totalElements === 0 ? (
        <EmptyState
          title="Chưa có nông trại được cấp quyền"
          description="Tài khoản cần farm membership trước khi có thể xem mùa vụ."
        />
      ) : null}

      {!isPending && !error && options.length > 0 ? (
        <div className="space-y-4">
          <label className="grid gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted">
            Nông trại đang hoạt động
            <select
              className="h-11 rounded-control border border-border bg-surface px-3 text-sm font-medium normal-case tracking-normal text-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info"
              value={selectableActiveFarm?.id ?? ""}
              onChange={(event) => {
                const farm = options.find((item) => item.id === event.target.value);
                if (farm) onSelect(farm);
              }}
            >
              {!selectableActiveFarm ? <option value="" disabled>Chọn nông trại</option> : null}
              {options.map((farm) => (
                <option key={farm.id} value={farm.id}>{farm.code} · {farm.name}</option>
              ))}
            </select>
          </label>
          {data ? (
            <PaginationControls
              page={data.page}
              totalPages={data.totalPages}
              isFetching={isFetching}
              label="Phân trang nông trại"
              onPrevious={onPreviousPage}
              onNext={onNextPage}
            />
          ) : null}
        </div>
      ) : null}

      {!isPending && validationError ? (
        <div className="mt-4 rounded-card border border-danger/30 bg-red-50 p-4" role="alert">
          <p className="font-semibold text-danger">
            {accessDenied ? "Nông trại đã không còn trong phạm vi truy cập" : "Không thể xác minh nông trại"}
          </p>
          <p className="mt-1 text-sm text-ink">
            {accessDenied
              ? "Danh sách cần được tải lại trước khi tiếp tục xem mùa vụ."
              : "Farm-service đang gián đoạn nên yêu cầu mùa vụ chưa được gửi."}
          </p>
          <Button
            className="mt-3"
            variant="secondary"
            onClick={accessDenied ? onResetScope : onRetryValidation}
          >
            {accessDenied ? "Tải lại phạm vi" : "Xác minh lại"}
          </Button>
        </div>
      ) : null}
    </section>
  );
}
