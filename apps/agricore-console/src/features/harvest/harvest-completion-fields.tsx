import type {
  HarvestCompletionDraft,
  HarvestCompletionErrors,
} from "./harvest-completion-validation";

export type EditableHarvestField = Exclude<keyof HarvestCompletionDraft, "cropCycleId" | "plotId">;

interface HarvestFieldProps {
  name: EditableHarvestField;
  label: string;
  value: string;
  error: string | undefined;
  disabled: boolean;
  maxLength?: number;
  inputMode?: "decimal";
  multiline?: boolean;
  onChange: (field: EditableHarvestField, value: string) => void;
}

const controlClass =
  "rounded-control border border-border bg-surface px-3 text-base text-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-info md:text-sm";

function HarvestField({
  name,
  label,
  value,
  error,
  disabled,
  maxLength,
  inputMode,
  multiline = false,
  onChange,
}: HarvestFieldProps) {
  const errorId = `harvest-${name}-error`;
  const shared = {
    className: `${controlClass} ${multiline ? "min-h-24 py-2" : "h-11"}`,
    "aria-label": label,
    "aria-invalid": error !== undefined,
    "aria-describedby": error ? errorId : undefined,
    autoComplete: "off",
    value,
    disabled,
    ...(maxLength ? { maxLength } : {}),
    onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
      onChange(name, event.target.value),
  };

  return (
    <label className={`grid gap-1.5 ${multiline ? "md:col-span-2" : ""}`}>
      <span className="text-xs font-semibold uppercase tracking-wide text-muted">{label}</span>
      {multiline ? <textarea {...shared} rows={3} /> : <input {...shared} inputMode={inputMode} />}
      {error ? <span id={errorId} className="text-sm font-medium text-danger">{error}</span> : null}
    </label>
  );
}

export function HarvestCompletionFields({
  draft,
  errors,
  disabled,
  onChange,
}: {
  draft: HarvestCompletionDraft;
  errors: HarvestCompletionErrors;
  disabled: boolean;
  onChange: (field: EditableHarvestField, value: string) => void;
}) {
  return (
    <div className="grid gap-4 md:grid-cols-2">
      <HarvestField name="code" label="Mã thu hoạch" value={draft.code} error={errors.code} disabled={disabled} maxLength={64} onChange={onChange} />
      <HarvestField name="warehouseId" label="Kho nhận hàng (UUID)" value={draft.warehouseId} error={errors.warehouseId} disabled={disabled} maxLength={36} onChange={onChange} />
      <HarvestField name="productCode" label="Mã sản phẩm" value={draft.productCode} error={errors.productCode} disabled={disabled} maxLength={64} onChange={onChange} />
      <HarvestField name="qualityGrade" label="Phân loại chất lượng" value={draft.qualityGrade} error={errors.qualityGrade} disabled={disabled} maxLength={32} onChange={onChange} />
      <HarvestField name="grossWeightKg" label="Khối lượng thô (kg)" value={draft.grossWeightKg} error={errors.grossWeightKg} disabled={disabled} inputMode="decimal" onChange={onChange} />
      <HarvestField name="netWeightKg" label="Khối lượng thực (kg)" value={draft.netWeightKg} error={errors.netWeightKg} disabled={disabled} inputMode="decimal" onChange={onChange} />
      <HarvestField name="productName" label="Tên sản phẩm (tuỳ chọn)" value={draft.productName} error={errors.productName} disabled={disabled} maxLength={200} onChange={onChange} />
      <HarvestField name="notes" label="Ghi chú nội bộ (tuỳ chọn)" value={draft.notes} error={errors.notes} disabled={disabled} multiline onChange={onChange} />
      <HarvestField name="careSummary" label="Tóm tắt chăm sóc công khai (tuỳ chọn)" value={draft.careSummary} error={errors.careSummary} disabled={disabled} maxLength={1000} multiline onChange={onChange} />
    </div>
  );
}
