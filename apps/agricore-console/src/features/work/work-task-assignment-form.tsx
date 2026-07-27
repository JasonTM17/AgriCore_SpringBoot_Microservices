import { useState, type FormEvent } from "react";

import { Button } from "../../components/ui/button";
import { WorkTaskMutationError } from "./work-task-mutation-error";

const UUID_PATTERN = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;

interface WorkTaskAssignmentFormProps {
  taskCode: string;
  currentAssignedEmployeeId: string | null;
  error: Error | null;
  isPending: boolean;
  isDisabled: boolean;
  successMessage: string | null;
  onSubmit: (assignedEmployeeId: string) => void;
  onRecoverError: () => void;
}

export function WorkTaskAssignmentForm({
  taskCode,
  currentAssignedEmployeeId,
  error,
  isPending,
  isDisabled,
  successMessage,
  onSubmit,
  onRecoverError,
}: WorkTaskAssignmentFormProps) {
  const [employeeId, setEmployeeId] = useState(currentAssignedEmployeeId ?? "");
  const [attemptedSubmit, setAttemptedSubmit] = useState(false);
  const normalizedEmployeeId = employeeId.trim().toLowerCase();
  const validEmployeeId = UUID_PATTERN.test(normalizedEmployeeId);
  const changedAssignment = normalizedEmployeeId !== (currentAssignedEmployeeId ?? "").toLowerCase();
  const controlsDisabled = isPending || isDisabled;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAttemptedSubmit(true);
    if (controlsDisabled || !validEmployeeId || !changedAssignment) return;
    onSubmit(normalizedEmployeeId);
  }

  return (
    <form className="mt-4 border-t border-border pt-4" aria-busy={isPending} onSubmit={handleSubmit}>
      <h4 className="text-sm font-semibold text-ink">Phân công nhân sự</h4>
      <p className="mt-1 text-xs leading-5 text-muted">
        Nhập UUID tài khoản nhân sự do hệ thống định danh cung cấp.
      </p>
      <label className="mt-3 grid gap-1.5">
        <span className="text-xs font-semibold uppercase tracking-wide text-muted">ID nhân sự</span>
        <input
          className="h-11 rounded-control border border-border bg-surface px-3 text-base text-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-info md:text-sm"
          aria-label={`ID nhân sự cho ${taskCode}`}
          aria-invalid={attemptedSubmit && !validEmployeeId}
          autoComplete="off"
          maxLength={64}
          spellCheck={false}
          value={employeeId}
          onChange={(event) => {
            setEmployeeId(event.target.value);
            setAttemptedSubmit(false);
          }}
          disabled={controlsDisabled}
          required
        />
      </label>
      {attemptedSubmit && !validEmployeeId ? (
        <p className="mt-2 text-sm font-medium text-danger" role="alert">ID nhân sự phải là UUID hợp lệ.</p>
      ) : null}
      {currentAssignedEmployeeId && !changedAssignment ? (
        <p className="mt-2 text-xs text-muted">Nhập ID khác để phân công lại công việc.</p>
      ) : null}
      {error ? (
        <WorkTaskMutationError
          actionLabel="phân công công việc"
          error={error}
          isRecovering={isPending}
          onRecover={onRecoverError}
        />
      ) : null}
      {successMessage ? (
        <p
          className="mt-3 rounded-control border border-forest-200 bg-forest-50 px-3 py-2 text-sm font-medium text-forest-900"
          role="status"
          aria-label="Phân công công việc thành công"
          aria-live="polite"
        >
          {successMessage}
        </p>
      ) : null}
      <Button
        className="mt-3 min-h-11 w-full sm:w-auto"
        type="submit"
        aria-label={`Xác nhận phân công ${taskCode}`}
        disabled={controlsDisabled || !normalizedEmployeeId || !changedAssignment}
      >
        {isPending ? "Đang phân công…" : "Xác nhận phân công"}
      </Button>
    </form>
  );
}
