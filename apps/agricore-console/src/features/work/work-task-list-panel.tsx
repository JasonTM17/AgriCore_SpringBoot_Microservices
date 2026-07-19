import { Button } from "../../components/ui/button";
import type { WorkTaskPageResponse } from "../../lib/api/types";
import { WorkTaskCard } from "./work-task-card";
import type { WorkTaskCompletionDraft } from "./work-task-completion-form";
import { WorkTaskCreateForm, type WorkTaskCreateDraft } from "./work-task-create-form";
import { WorkTaskListSkeleton, WorkTaskLoadError } from "./work-task-list-state";

interface WorkTaskAssignmentState {
  canAssign: boolean;
  error: Error | null;
  taskId: string | null;
  isPending: boolean;
  isDisabled: boolean;
  success: { taskId: string; message: string } | null;
  onAssign: (taskId: string, assignedEmployeeId: string) => void;
  onRecoverError: () => void;
}

interface WorkTaskCompletionState {
  canComplete: boolean;
  error: Error | null;
  taskId: string | null;
  isPending: boolean;
  isDisabled: boolean;
  success: { taskId: string; message: string } | null;
  onComplete: (taskId: string, draft: WorkTaskCompletionDraft) => void;
  onRecoverError: () => void;
}

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
  assignment: WorkTaskAssignmentState;
  completion: WorkTaskCompletionState;
  onCreate: (draft: WorkTaskCreateDraft) => void;
  onRecoverCreateError: () => void;
  onRetry: () => void;
  onPrevious: () => void;
  onNext: () => void;
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
  assignment,
  completion,
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
          <WorkTaskLoadError
            error={error}
            onRetry={onRetry}
            onBack={canGoPrevious ? onPrevious : null}
            hasCachedData={Boolean(data)}
          />
        </div>
      ) : null}
      {isPending ? <WorkTaskListSkeleton /> : null}
      {!isPending && data?.content.length === 0 ? (
        <div className="rounded-control border border-dashed border-border bg-canvas p-6 text-sm text-muted">
          Chưa có công việc nào cho mùa vụ này.
        </div>
      ) : null}

      {data?.content.length ? (
        <div className="grid gap-4 md:grid-cols-2">
          {data.content.map((task) => (
            <WorkTaskCard
              key={task.id}
              task={task}
              assignment={{
                canAssign: assignment.canAssign,
                error: assignment.taskId === task.id ? assignment.error : null,
                isPending: assignment.isPending && assignment.taskId === task.id,
                isDisabled: assignment.isDisabled
                  || (assignment.isPending && assignment.taskId !== task.id),
                successMessage: assignment.success?.taskId === task.id
                  ? assignment.success.message
                  : null,
                onAssign: (assignedEmployeeId) => assignment.onAssign(task.id, assignedEmployeeId),
                onRecoverError: assignment.onRecoverError,
              }}
              completion={{
                canComplete: completion.canComplete,
                error: completion.taskId === task.id ? completion.error : null,
                isPending: completion.isPending && completion.taskId === task.id,
                isDisabled: completion.isDisabled
                  || (completion.isPending && completion.taskId !== task.id),
                successMessage: completion.success?.taskId === task.id
                  ? completion.success.message
                  : null,
                onComplete: (draft) => completion.onComplete(task.id, draft),
                onRecoverError: completion.onRecoverError,
              }}
            />
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
