import { Button } from "../../components/ui/button";
import { ApiClientError } from "../../lib/api/errors";
import type { WorkTaskPageResponse } from "../../lib/api/types";
import {
  formatTaskInstant,
  formatTaskPriority,
  formatTaskStatus,
  formatTaskType,
} from "./work-task-formatters";

interface WorkTaskListPanelProps {
  cycleCode: string;
  data: WorkTaskPageResponse | undefined;
  error: Error | null;
  isPending: boolean;
  isFetching: boolean;
  canGoPrevious: boolean;
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
          {data.content.map((task) => (
            <article key={task.id} className="min-w-0 rounded-card border border-border bg-canvas p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <p
                    className="break-all text-xs font-semibold uppercase tracking-[0.12em] text-forest-700"
                    translate="no"
                  >
                    {task.code}
                  </p>
                  <h3 className="mt-1 break-words text-lg font-bold text-ink">{task.title}</h3>
                </div>
                <span className="rounded-full bg-forest-50 px-2.5 py-1 text-xs font-semibold text-forest-900">
                  {formatTaskStatus(task.status)}
                </span>
              </div>
              {task.description ? (
                <p className="mt-3 whitespace-pre-wrap break-words text-sm leading-6 text-muted">{task.description}</p>
              ) : null}
              <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
                <div><dt className="text-muted">Loại công việc</dt><dd className="mt-1 font-medium text-ink">{formatTaskType(task.taskType)}</dd></div>
                <div><dt className="text-muted">Ưu tiên</dt><dd className="mt-1 font-medium text-ink">{formatTaskPriority(task.priority)}</dd></div>
                <div><dt className="text-muted">Bắt đầu dự kiến</dt><dd className="mt-1 font-medium text-ink">{formatTaskInstant(task.scheduledStart)}</dd></div>
                <div><dt className="text-muted">Kết thúc dự kiến</dt><dd className="mt-1 font-medium text-ink">{formatTaskInstant(task.scheduledEnd)}</dd></div>
                <div className="sm:col-span-2">
                  <dt className="text-muted">Nhân sự được giao</dt>
                  <dd className="mt-1 break-all font-medium text-ink">
                    {task.assignedEmployeeId
                      ? <span translate="no">{task.assignedEmployeeId}</span>
                      : "Chưa phân công"}
                  </dd>
                </div>
              </dl>
              <p className="mt-4 text-xs tabular-nums text-muted">Phiên bản {task.version}</p>
            </article>
          ))}
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
