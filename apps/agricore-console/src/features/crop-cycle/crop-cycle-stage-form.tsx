import { useId, useState, type FormEvent } from "react";

import { Button } from "../../components/ui/button";
import type { CycleStage } from "../../lib/api/types";
import { cycleStageLabel } from "./crop-cycle-formatters";

interface CropCycleStageFormProps {
  cycleCode: string;
  allowedStages: readonly CycleStage[];
  isPending: boolean;
  isDisabled: boolean;
  onSubmit: (stage: CycleStage, notes: string | null) => void;
}

const MAX_STAGE_NOTES_LENGTH = 2_000;

export function CropCycleStageForm({
  cycleCode,
  allowedStages,
  isPending,
  isDisabled,
  onSubmit,
}: CropCycleStageFormProps) {
  const [stage, setStage] = useState<CycleStage | "">("");
  const [notes, setNotes] = useState("");
  const notesHelpId = useId();
  const notesErrorId = useId();
  const notesTooLong = notes.length > MAX_STAGE_NOTES_LENGTH;
  const controlsDisabled = isPending || isDisabled;
  const validStage = stage && allowedStages.includes(stage) ? stage : "";

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (controlsDisabled || !validStage || notesTooLong) return;
    if (
      validStage === "CANCELLED"
      && !window.confirm(`Hủy mùa vụ ${cycleCode}? Mùa vụ sẽ kết thúc và không thể chuyển sang giai đoạn khác.`)
    ) {
      return;
    }
    onSubmit(validStage, notes.trim() || null);
  }

  return (
    <form
      aria-busy={isPending}
      className="mt-6 rounded-card border border-border bg-forest-50/50 p-4"
      onSubmit={handleSubmit}
    >
      <h2 className="text-base font-semibold text-ink">Cập nhật giai đoạn</h2>
      <p className="mt-1 text-sm leading-6 text-muted">
        Chỉ các transition hợp lệ từ backend mới được hiển thị.
      </p>
      <div className="mt-4 grid gap-4 md:grid-cols-2">
        <label className="grid gap-1.5">
          <span className="text-xs font-semibold uppercase tracking-wide text-muted">
            Giai đoạn tiếp theo
          </span>
          <select
            aria-label="Giai đoạn tiếp theo"
            autoComplete="off"
            className="h-11 rounded-control border border-border bg-surface px-3 text-base text-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-info md:text-sm"
            value={validStage}
            onChange={(event) => setStage(event.target.value as CycleStage | "")}
            disabled={controlsDisabled}
            name="stage"
            required
          >
            <option value="">Chọn giai đoạn</option>
            {allowedStages.map((nextStage) => (
              <option key={nextStage} value={nextStage}>
                {cycleStageLabel(nextStage)}
              </option>
            ))}
          </select>
        </label>
        <label className="grid gap-1.5">
          <span className="text-xs font-semibold uppercase tracking-wide text-muted">
            Ghi chú (tuỳ chọn)
          </span>
          <textarea
            aria-label="Ghi chú chuyển giai đoạn"
            aria-describedby={`${notesHelpId}${notesTooLong ? ` ${notesErrorId}` : ""}`}
            aria-invalid={notesTooLong}
            autoComplete="off"
            className="min-h-11 rounded-control border border-border bg-surface px-3 py-2 text-base text-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-info md:text-sm"
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            disabled={controlsDisabled}
            maxLength={MAX_STAGE_NOTES_LENGTH}
            name="notes"
            rows={2}
          />
          <span id={notesHelpId} className="text-xs text-muted" aria-live="polite" aria-atomic="true">
            {notes.length.toLocaleString("vi-VN")}/{MAX_STAGE_NOTES_LENGTH.toLocaleString("vi-VN")} ký tự
          </span>
          {notesTooLong ? (
            <span id={notesErrorId} className="text-xs font-medium text-danger" role="alert">
              Ghi chú không được vượt quá 2.000 ký tự.
            </span>
          ) : null}
        </label>
      </div>
      <Button className="mt-4 min-h-11 md:min-h-10" type="submit" disabled={controlsDisabled || !validStage || notesTooLong}>
        {isPending ? "Đang lưu…" : "Cập nhật giai đoạn"}
      </Button>
      {isPending ? <span className="sr-only" role="status">Đang lưu thay đổi giai đoạn.</span> : null}
    </form>
  );
}
