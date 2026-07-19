import { useState, type FormEvent } from "react";

import { Button } from "../../components/ui/button";
import type { CompleteTaskRequest } from "../../lib/api/types";
import { WorkTaskMutationError } from "./work-task-mutation-error";

export type WorkTaskCompletionDraft = CompleteTaskRequest;
const WORK_TASK_NOTES_MAX_LENGTH = 2000;

interface WorkTaskCompletionFormProps {
  taskCode: string;
  currentNotes: string | null;
  error: Error | null;
  isPending: boolean;
  isDisabled: boolean;
  onSubmit: (draft: WorkTaskCompletionDraft) => void;
  onRecoverError: () => void;
}

export function WorkTaskCompletionForm({
  taskCode,
  currentNotes,
  error,
  isPending,
  isDisabled,
  onSubmit,
  onRecoverError,
}: WorkTaskCompletionFormProps) {
  const [notes, setNotes] = useState(currentNotes ?? "");
  const controlsDisabled = isPending || isDisabled;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (controlsDisabled) return;
    onSubmit({ notes: notes.trim() || null });
  }

  return (
    <form className="mt-4 border-t border-border pt-4" aria-busy={isPending} onSubmit={handleSubmit}>
      <h4 className="text-sm font-semibold text-ink">Hoàn tất công việc</h4>
      <p className="mt-1 text-xs leading-5 text-muted">
        Hệ thống ghi nhận thời điểm hoàn tất theo giờ máy chủ. Ghi chú là tùy chọn.
      </p>
      <label className="mt-3 grid gap-1.5">
        <span className="text-xs font-semibold uppercase tracking-wide text-muted">Ghi chú hoàn tất</span>
        <textarea
          className="min-h-24 rounded-control border border-border bg-surface px-3 py-2 text-base text-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-info md:text-sm"
          aria-label={`Ghi chú hoàn tất cho ${taskCode}`}
          value={notes}
          onChange={(event) => setNotes(event.target.value)}
          disabled={controlsDisabled}
          maxLength={WORK_TASK_NOTES_MAX_LENGTH}
          rows={3}
        />
      </label>
      {error ? (
        <WorkTaskMutationError
          actionLabel="hoàn tất công việc"
          error={error}
          isRecovering={isPending}
          onRecover={onRecoverError}
        />
      ) : null}
      <Button
        className="mt-3 min-h-11 w-full sm:w-auto"
        type="submit"
        aria-label={`Xác nhận hoàn tất ${taskCode}`}
        disabled={controlsDisabled}
      >
        {isPending ? "Đang hoàn tất…" : "Xác nhận hoàn tất"}
      </Button>
    </form>
  );
}
