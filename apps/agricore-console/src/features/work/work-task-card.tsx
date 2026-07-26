import { Button } from "../../components/ui/button";
import type { WorkTaskResponse } from "../../lib/api/types";
import { WorkTaskAssignmentForm } from "./work-task-assignment-form";
import { WorkTaskCompletionForm, type WorkTaskCompletionDraft } from "./work-task-completion-form";
import {
  formatTaskInstant,
  formatTaskPriority,
  formatTaskStatus,
  formatTaskType,
} from "./work-task-formatters";
import { WorkTaskMutationError } from "./work-task-mutation-error";
import { canAssignTask, canCompleteTask, canStartTask } from "./work-task-policy";

export interface WorkTaskAssignmentActions {
  canAssign: boolean;
  error: Error | null;
  isPending: boolean;
  isDisabled: boolean;
  successMessage: string | null;
  onAssign: (assignedEmployeeId: string) => void;
  onRecoverError: () => void;
}

export interface WorkTaskCompletionActions {
  canComplete: boolean;
  error: Error | null;
  isPending: boolean;
  isDisabled: boolean;
  successMessage: string | null;
  onComplete: (draft: WorkTaskCompletionDraft) => void;
  onRecoverError: () => void;
}

export interface WorkTaskStartActions {
  canStart: boolean;
  error: Error | null;
  isPending: boolean;
  isDisabled: boolean;
  successMessage: string | null;
  onStart: () => void;
  onRecoverError: () => void;
}

export function WorkTaskCard({
  task,
  assignment,
  start,
  completion,
}: {
  task: WorkTaskResponse;
  assignment: WorkTaskAssignmentActions;
  start: WorkTaskStartActions;
  completion: WorkTaskCompletionActions;
}) {
  return (
    <article className="min-w-0 rounded-card border border-border bg-canvas p-4">
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
        <div><dt className="text-muted">Bắt đầu thực tế</dt><dd className="mt-1 font-medium text-ink">{formatTaskInstant(task.actualStart)}</dd></div>
        <div><dt className="text-muted">Kết thúc thực tế</dt><dd className="mt-1 font-medium text-ink">{formatTaskInstant(task.actualEnd)}</dd></div>
        <div className="sm:col-span-2">
          <dt className="text-muted">Nhân sự được giao</dt>
          <dd className="mt-1 break-all font-medium text-ink">
            {task.assignedEmployeeId
              ? <span translate="no">{task.assignedEmployeeId}</span>
              : "Chưa phân công"}
          </dd>
        </div>
        {task.notes ? (
          <div className="sm:col-span-2">
            <dt className="text-muted">Ghi chú hoàn tất</dt>
            <dd className="mt-1 whitespace-pre-wrap break-words font-medium text-ink">{task.notes}</dd>
          </div>
        ) : null}
      </dl>
      <p className="mt-4 text-xs tabular-nums text-muted">Phiên bản {task.version}</p>
      {completion.successMessage ? (
        <p
          className="mt-4 rounded-control border border-forest-200 bg-forest-50 px-3 py-2 text-sm font-medium text-forest-900"
          role="status"
          aria-label="Hoàn tất công việc thành công"
          aria-live="polite"
        >
          {completion.successMessage}
        </p>
      ) : null}
      {start.successMessage ? (
        <p
          className="mt-4 rounded-control border border-forest-200 bg-forest-50 px-3 py-2 text-sm font-medium text-forest-900"
          role="status"
          aria-label="Bắt đầu công việc thành công"
          aria-live="polite"
        >
          {start.successMessage}
        </p>
      ) : null}
      {assignment.canAssign && canAssignTask(task.status) ? (
        <WorkTaskAssignmentForm
          key={`assign-${task.id}-${task.version}`}
          taskCode={task.code}
          currentAssignedEmployeeId={task.assignedEmployeeId}
          error={assignment.error}
          isPending={assignment.isPending}
          isDisabled={assignment.isDisabled}
          successMessage={assignment.successMessage}
          onSubmit={assignment.onAssign}
          onRecoverError={assignment.onRecoverError}
        />
      ) : null}
      {start.canStart && canStartTask(task.status) ? (
        <section className="mt-4 border-t border-border pt-4" aria-busy={start.isPending}>
          <h4 className="text-sm font-semibold text-ink">Thực hiện công việc</h4>
          <p className="mt-1 text-xs leading-5 text-muted">
            Ghi nhận thời điểm bắt đầu thực tế trước khi hoàn tất công việc.
          </p>
          {start.error ? (
            <WorkTaskMutationError
              actionLabel="bắt đầu công việc"
              error={start.error}
              isRecovering={start.isPending}
              onRecover={start.onRecoverError}
            />
          ) : null}
          <Button
            className="mt-3 min-h-11 w-full sm:w-auto"
            aria-label={`Bắt đầu ${task.code}`}
            disabled={start.isPending || start.isDisabled}
            onClick={start.onStart}
          >
            {start.isPending ? "Đang bắt đầu…" : "Bắt đầu công việc"}
          </Button>
        </section>
      ) : null}
      {completion.canComplete && canCompleteTask(task.status) ? (
        <WorkTaskCompletionForm
          key={`complete-${task.id}-${task.version}`}
          taskCode={task.code}
          currentNotes={task.notes}
          error={completion.error}
          isPending={completion.isPending}
          isDisabled={completion.isDisabled}
          onSubmit={completion.onComplete}
          onRecoverError={completion.onRecoverError}
        />
      ) : null}
    </article>
  );
}
