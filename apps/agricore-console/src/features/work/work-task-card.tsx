import type { WorkTaskResponse } from "../../lib/api/types";
import { WorkTaskAssignmentForm } from "./work-task-assignment-form";
import {
  formatTaskInstant,
  formatTaskPriority,
  formatTaskStatus,
  formatTaskType,
} from "./work-task-formatters";
import { canAssignTask } from "./work-task-policy";

export interface WorkTaskAssignmentActions {
  canAssign: boolean;
  error: Error | null;
  isPending: boolean;
  isDisabled: boolean;
  successMessage: string | null;
  onAssign: (assignedEmployeeId: string) => void;
  onRecoverError: () => void;
}

export function WorkTaskCard({
  task,
  assignment,
}: {
  task: WorkTaskResponse;
  assignment: WorkTaskAssignmentActions;
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
      {assignment.canAssign && canAssignTask(task.status) ? (
        <WorkTaskAssignmentForm
          key={`${task.id}-${task.version}`}
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
    </article>
  );
}
