import { Button } from "../../components/ui/button";
import { ApiClientError } from "../../lib/api/errors";
import type { WorkTaskPageResponse } from "../../lib/api/types";
import { WorkTaskCard } from "./work-task-card";
import { WorkTaskCreateForm, type WorkTaskCreateDraft } from "./work-task-create-form";

interface WorkTaskListPanelProps {
  cycleCode: string;
  data: WorkTaskPageResponse | undefined;
  error: Error | null;
  isPending: boolean;
  isFetching: boolean;
  canGoPrevious: boolean;
  canCreate: boolean;
  createError: Error | null;
  createFormResetKey: number;
  createSuccessMessage: string | null;
  isCreating: boolean;
  isCreateDisabled: boolean;
  onCreate: (draft: WorkTaskCreateDraft) => void;
  onRecoverCreateError: () => void;
  onRetry: () => void;
  onPrevious: () => void;
  onNext: () => void;
}

function TaskListSkeleton() {
  return (
    <div className="grid gap-4 md:grid-cols-2" role="status" aria-label="Đang tải công việc…">
      {[0, 1].map((index) => (
        <div key={index} className="h-48 animate-pulse rounded-card bg-forest-50 motion-reduce:animate-none" />
      ))}
    </div>
  );
}

function TaskLoadError({
  error,
  onRetry,
  onBack,
  hasCachedData,
}: {
  error: Error;
  onRetry: () => void;
  onBack: (() => void) | null;
  hasCachedData: boolean;
}) {
  const supportCode = error instanceof ApiClientError ? error.code : null;
  const unavailable = error instanceof ApiClientError && (error.status === 403 || error.status === 404);
  return (
    <div
      className="rounded-control border border-danger/30 bg-red-50 p-4"
      role="alert"
      aria-label="Không thể tải công việc"
    >
      <p className="text-sm font-semibold text-ink">
        {unavailable
          ? "Danh sách công việc không còn khả dụng trong phạm vi hiện tại."
          : "Không thể tải danh sách công việc. Dịch vụ có thể đang gián đoạn."}
      </p>
      {supportCode ? <p className="mt-1 text-xs text-muted">Mã hỗ trợ: {supportCode}</p> : null}
      {!unavailable && hasCachedData ? (
        <p className="mt-1 text-xs text-muted">Dữ liệu bên dưới là kết quả tải thành công gần nhất.</p>
      ) : null}
      <div className="mt-3 flex flex-wrap gap-2">
        <Button className="min-h-11" variant="secondary" onClick={onRetry}>
          Thử tải lại công việc
        </Button>
        {onBack && !unavailable ? (
          <Button className="min-h-11" variant="secondary" onClick={onBack}>
            Về trang trước
          </Button>
        ) : null}
      </div>
    </div>
  );
}

export function WorkTaskListPanel({
  cycleCode,
  data,
  error,
  isPending,
  isFetching,
  canGoPrevious,
  canCreate,
  createError,
  createFormResetKey,
  createSuccessMessage,
  isCreating,
  isCreateDisabled,
  onCreate,
  onRecoverCreateError,
  onRetry,
  onPrevious,
  onNext,
}: WorkTaskListPanelProps) {
  return (
    <section className="rounded-card border border-border bg-surface p-5 md:p-6" aria-labelledby="work-task-heading">
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-forest-700">Field work</p>
          <h2 id="work-task-heading" className="mt-1 text-balance text-2xl font-bold text-ink">Công việc mùa vụ</h2>
          <p className="mt-1 text-sm text-muted">
            Phạm vi mùa vụ <span translate="no">{cycleCode}</span>; dữ liệu từ work-service.
          </p>
        </div>
        {isFetching && data ? <span className="text-xs font-medium text-info" role="status">Đang cập nhật…</span> : null}
      </div>

      {canCreate ? (
        <>
          <WorkTaskCreateForm
            key={`${cycleCode}-${createFormResetKey}`}
            cycleCode={cycleCode}
            error={createError}
            isPending={isCreating}
            isDisabled={isCreateDisabled}
            onSubmit={onCreate}
            onRecoverError={onRecoverCreateError}
          />
          {createSuccessMessage ? (
            <p
              className="mb-4 rounded-control border border-forest-200 bg-forest-50 px-4 py-3 text-sm font-medium text-forest-900"
              role="status"
              aria-label="Tạo công việc thành công"
              aria-live="polite"
            >
              {createSuccessMessage}
            </p>
          ) : null}
        </>
      ) : null}

      {error ? (
        <div className="mb-4">
          <TaskLoadError
            error={error}
            onRetry={onRetry}
            onBack={canGoPrevious ? onPrevious : null}
            hasCachedData={Boolean(data)}
          />
        </div>
      ) : null}
      {isPending ? <TaskListSkeleton /> : null}
      {!isPending && data?.content.length === 0 ? (
        <div className="rounded-control border border-dashed border-border bg-canvas p-6 text-sm text-muted">
          Chưa có công việc nào cho mùa vụ này.
        </div>
      ) : null}

      {data?.content.length ? (
        <div className="grid gap-4 md:grid-cols-2">
          {data.content.map((task) => <WorkTaskCard key={task.id} task={task} />)}
        </div>
      ) : null}

      {data && data.totalPages > 1 ? (
        <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4">
          <p className="text-sm tabular-nums text-muted">Trang {data.page + 1} / {data.totalPages}</p>
          <div className="flex gap-2">
            <Button className="min-h-11" variant="secondary" disabled={data.first || isFetching} onClick={onPrevious}>Trang trước</Button>
            <Button className="min-h-11" variant="secondary" disabled={data.last || isFetching} onClick={onNext}>Trang sau</Button>
          </div>
        </div>
      ) : null}
    </section>
  );
}
