import { useState, type FormEvent } from "react";

import { Button } from "../../components/ui/button";
import type { CreateWorkTaskRequest, TaskType } from "../../lib/api/types";
import {
  formatTaskPriority,
  formatTaskType,
  workTaskPriorities,
  workTaskTypes,
} from "./work-task-formatters";
import { WorkTaskMutationError } from "./work-task-mutation-error";

export type WorkTaskCreateDraft = Omit<CreateWorkTaskRequest, "cropCycleId" | "plotId">;

interface WorkTaskCreateFormProps {
  cycleCode: string;
  error: Error | null;
  isPending: boolean;
  isDisabled: boolean;
  onSubmit: (draft: WorkTaskCreateDraft) => void;
  onRecoverError: () => void;
}

function localInstant(value: string): string | null | undefined {
  if (!value) return null;
  const instant = new Date(value);
  return Number.isNaN(instant.getTime()) ? undefined : instant.toISOString();
}

const controlClass =
  "h-11 rounded-control border border-border bg-surface px-3 text-base text-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-info md:text-sm";

export function WorkTaskCreateForm({
  cycleCode,
  error,
  isPending,
  isDisabled,
  onSubmit,
  onRecoverError,
}: WorkTaskCreateFormProps) {
  const [code, setCode] = useState("");
  const [taskType, setTaskType] = useState<TaskType | "">("");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState("MEDIUM");
  const [scheduledStart, setScheduledStart] = useState("");
  const [scheduledEnd, setScheduledEnd] = useState("");
  const startInstant = localInstant(scheduledStart);
  const endInstant = localInstant(scheduledEnd);
  const invalidInstant = startInstant === undefined || endInstant === undefined;
  const endBeforeStart = Boolean(startInstant && endInstant && endInstant < startInstant);
  const validTaskType = taskType && workTaskTypes.includes(taskType) ? taskType : "";
  const controlsDisabled = isPending || isDisabled;
  const canSubmit = Boolean(code.trim() && title.trim() && validTaskType)
    && !invalidInstant
    && !endBeforeStart;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (controlsDisabled || !canSubmit || !validTaskType || startInstant === undefined || endInstant === undefined) {
      return;
    }
    onSubmit({
      code: code.trim(),
      taskType: validTaskType,
      title: title.trim(),
      description: description.trim() || null,
      priority,
      scheduledStart: startInstant,
      scheduledEnd: endInstant,
    });
  }

  return (
    <form className="mb-5 rounded-card border border-border bg-forest-50/50 p-4" aria-busy={isPending} onSubmit={handleSubmit}>
      <h3 className="text-lg font-semibold text-ink">Tạo công việc mới</h3>
      <p className="mt-1 text-sm text-muted">Gắn trực tiếp với mùa vụ <span translate="no">{cycleCode}</span> và lô hiện tại.</p>
      <div className="mt-4 grid gap-4 md:grid-cols-2">
        <label className="grid gap-1.5">
          <span className="text-xs font-semibold uppercase tracking-wide text-muted">Mã công việc</span>
          <input className={controlClass} aria-label="Mã công việc" autoComplete="off" maxLength={64} value={code} onChange={(event) => setCode(event.target.value)} disabled={controlsDisabled} required />
        </label>
        <label className="grid gap-1.5">
          <span className="text-xs font-semibold uppercase tracking-wide text-muted">Loại công việc</span>
          <select className={controlClass} aria-label="Loại công việc" value={validTaskType} onChange={(event) => setTaskType(event.target.value as TaskType | "")} disabled={controlsDisabled} required>
            <option value="">Chọn loại công việc</option>
            {workTaskTypes.map((value) => <option key={value} value={value}>{formatTaskType(value)}</option>)}
          </select>
        </label>
        <label className="grid gap-1.5 md:col-span-2">
          <span className="text-xs font-semibold uppercase tracking-wide text-muted">Tiêu đề công việc</span>
          <input className={controlClass} aria-label="Tiêu đề công việc" autoComplete="off" maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} disabled={controlsDisabled} required />
        </label>
        <label className="grid gap-1.5 md:col-span-2">
          <span className="text-xs font-semibold uppercase tracking-wide text-muted">Mô tả công việc (tuỳ chọn)</span>
          <textarea className={`${controlClass} min-h-24 py-2`} aria-label="Mô tả công việc" autoComplete="off" rows={3} value={description} onChange={(event) => setDescription(event.target.value)} disabled={controlsDisabled} />
        </label>
        <label className="grid gap-1.5">
          <span className="text-xs font-semibold uppercase tracking-wide text-muted">Mức ưu tiên</span>
          <select className={controlClass} aria-label="Mức ưu tiên" value={priority} onChange={(event) => setPriority(event.target.value)} disabled={controlsDisabled}>
            {workTaskPriorities.map((value) => <option key={value} value={value}>{formatTaskPriority(value)}</option>)}
          </select>
        </label>
        <div className="hidden md:block" aria-hidden="true" />
        <label className="grid gap-1.5">
          <span className="text-xs font-semibold uppercase tracking-wide text-muted">Bắt đầu dự kiến</span>
          <input className={controlClass} aria-label="Bắt đầu dự kiến" type="datetime-local" value={scheduledStart} onChange={(event) => setScheduledStart(event.target.value)} disabled={controlsDisabled} />
        </label>
        <label className="grid gap-1.5">
          <span className="text-xs font-semibold uppercase tracking-wide text-muted">Kết thúc dự kiến</span>
          <input className={controlClass} aria-label="Kết thúc dự kiến" type="datetime-local" value={scheduledEnd} onChange={(event) => setScheduledEnd(event.target.value)} disabled={controlsDisabled} />
        </label>
      </div>
      {invalidInstant || endBeforeStart ? (
        <p className="mt-3 text-sm font-medium text-danger" role="alert">
          {invalidInstant ? "Thời gian dự kiến không hợp lệ." : "Kết thúc dự kiến không được trước thời điểm bắt đầu."}
        </p>
      ) : null}
      {error ? <WorkTaskMutationError actionLabel="tạo công việc" error={error} isRecovering={isPending} onRecover={onRecoverError} /> : null}
      <Button className="mt-4 min-h-11" type="submit" disabled={controlsDisabled || !canSubmit}>
        {isPending ? "Đang tạo…" : "Tạo công việc"}
      </Button>
      {isPending ? <span className="sr-only" role="status">Đang tạo công việc.</span> : null}
    </form>
  );
}
