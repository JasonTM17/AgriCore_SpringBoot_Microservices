import { useState, type FormEvent } from "react";

import { Button } from "../../components/ui/button";
import type {
  CompleteHarvestRequest,
  CropCyclePageResponse,
  CropCycleResponse,
  FarmResponse,
  PlotResponse,
} from "../../lib/api/types";
import { HarvestCompletionError } from "./harvest-completion-error";
import {
  HarvestCompletionFields,
  type EditableHarvestField,
} from "./harvest-completion-fields";
import { HarvestCycleSelector } from "./harvest-cycle-selector";
import {
  validateHarvestCompletionDraft,
  type HarvestCompletionDraft,
  type HarvestCompletionErrors,
} from "./harvest-completion-validation";

const EMPTY_DRAFT: HarvestCompletionDraft = {
  code: "",
  cropCycleId: "",
  plotId: "",
  warehouseId: "",
  productCode: "",
  grossWeightKg: "",
  netWeightKg: "",
  qualityGrade: "",
  notes: "",
  productName: "",
  careSummary: "",
};

interface HarvestCompletionFormProps {
  farm: FarmResponse;
  cycles: CropCyclePageResponse | undefined;
  cyclesError: Error | null;
  isCyclesPending: boolean;
  isCyclesFetching: boolean;
  selectedCycle: CropCycleResponse | null;
  selectedCycleId: string;
  plot: PlotResponse | null;
  plotError: Error | null;
  plotMismatch: boolean;
  isPlotPending: boolean;
  isSubmitting: boolean;
  isContextRefreshing: boolean;
  mutationError: Error | null;
  onSelectCycle: (cycleId: string) => void;
  onRetryCycles: () => void;
  onPreviousCycles: () => void;
  onNextCycles: () => void;
  onSubmit: (request: CompleteHarvestRequest) => void;
  onEdit: () => void;
  onRecoverContext: () => void;
}

export function HarvestCompletionForm(props: HarvestCompletionFormProps) {
  const [draft, setDraft] = useState(EMPTY_DRAFT);
  const [errors, setErrors] = useState<HarvestCompletionErrors>({});
  const controlsDisabled = props.isSubmitting || props.isContextRefreshing;
  const contextReady = props.selectedCycle !== null
    && props.plot !== null
    && !props.plotMismatch
    && props.plotError === null;

  function updateDraft(field: EditableHarvestField, value: string) {
    setDraft((current) => ({ ...current, [field]: value }));
    setErrors((current) => {
      const next = { ...current };
      delete next[field];
      return next;
    });
    props.onEdit();
  }

  function selectCycle(cycleId: string) {
    setErrors((current) => {
      const next = { ...current };
      delete next.cropCycleId;
      delete next.plotId;
      return next;
    });
    props.onEdit();
    props.onSelectCycle(cycleId);
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (controlsDisabled) return;
    const result = validateHarvestCompletionDraft(
      {
        ...draft,
        cropCycleId: props.selectedCycle?.id ?? "",
        plotId: props.selectedCycle?.plotId ?? "",
      },
      { farmName: props.farm.name, plotCode: props.plot?.code ?? null },
    );
    if (!result.valid) {
      setErrors(result.errors);
      return;
    }
    if (!contextReady) return;
    setErrors({});
    props.onSubmit(result.request);
  }

  return (
    <form
      className="rounded-card border border-border bg-surface p-5 shadow-sm"
      aria-busy={controlsDisabled}
      onSubmit={handleSubmit}
      noValidate
    >
      <div>
        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-forest-700">Harvest completion</p>
        <h2 className="mt-2 text-xl font-semibold text-ink">Hoàn tất thu hoạch</h2>
        <p className="mt-1 text-sm leading-6 text-muted">
          {props.farm.code} · {props.farm.name}. Hoàn tất chỉ khởi động đồng bộ kho và truy xuất.
        </p>
      </div>

      <div className="mt-5">
        <HarvestCycleSelector
          data={props.cycles}
          error={props.cyclesError}
          isPending={props.isCyclesPending}
          isFetching={props.isCyclesFetching}
          selectedCycleId={props.selectedCycleId}
          plot={props.plot}
          plotError={props.plotError}
          plotMismatch={props.plotMismatch}
          isPlotPending={props.isPlotPending}
          disabled={controlsDisabled}
          onSelect={selectCycle}
          onRetry={props.onRetryCycles}
          onPrevious={props.onPreviousCycles}
          onNext={props.onNextCycles}
        />
      </div>

      <p className="mt-5 text-sm text-muted">
        ID kho được nhập theo contract hiện tại; hệ thống chưa có endpoint danh sách kho để chọn an toàn.
      </p>
      <div className="mt-4">
        <HarvestCompletionFields
          draft={draft}
          errors={errors}
          disabled={controlsDisabled}
          onChange={updateDraft}
        />
      </div>

      {props.mutationError ? (
        <HarvestCompletionError
          error={props.mutationError}
          isRecovering={props.isContextRefreshing}
          onRecover={props.onRecoverContext}
        />
      ) : null}
      <Button className="mt-5 min-h-11" type="submit" disabled={controlsDisabled || !contextReady}>
        {props.isSubmitting ? "Đang hoàn tất…" : "Hoàn tất và tạo biên nhận"}
      </Button>
      {props.isSubmitting ? <span className="sr-only" role="status">Đang hoàn tất thu hoạch.</span> : null}
    </form>
  );
}
