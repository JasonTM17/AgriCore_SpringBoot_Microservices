import type { CompleteHarvestRequest } from "../../lib/api/types";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const WEIGHT_PATTERN = /^(?:0|[1-9]\d*)(?:\.\d{1,3})?$/;
const MAX_WEIGHT_KG = 99_999_999_999.999;

export interface HarvestCompletionDraft {
  code: string;
  cropCycleId: string;
  plotId: string;
  warehouseId: string;
  productCode: string;
  grossWeightKg: string;
  netWeightKg: string;
  qualityGrade: string;
  notes: string;
  productName: string;
  careSummary: string;
}

export interface HarvestCompletionContext {
  farmName: string | null;
  plotCode: string | null;
}

export type HarvestCompletionField = keyof HarvestCompletionDraft | "farmName" | "plotCode";
export type HarvestCompletionErrors = Partial<Record<HarvestCompletionField, string>>;

export type HarvestCompletionValidationResult =
  | { valid: true; request: CompleteHarvestRequest }
  | { valid: false; errors: HarvestCompletionErrors };

function requiredText(
  value: string,
  field: HarvestCompletionField,
  label: string,
  maxLength: number,
  errors: HarvestCompletionErrors,
): string | null {
  const normalized = value.trim();
  if (!normalized) {
    errors[field] = `${label} là bắt buộc.`;
    return null;
  }
  if (normalized.length > maxLength) {
    errors[field] = `${label} không được vượt quá ${maxLength} ký tự.`;
    return null;
  }
  return normalized;
}

function optionalText(
  value: string | null,
  field: HarvestCompletionField,
  label: string,
  maxLength: number,
  errors: HarvestCompletionErrors,
): string | null {
  const normalized = value?.trim() ?? "";
  if (!normalized) return null;
  if (normalized.length > maxLength) {
    errors[field] = `${label} không được vượt quá ${maxLength} ký tự.`;
    return null;
  }
  return normalized;
}

function uuid(
  value: string,
  field: HarvestCompletionField,
  label: string,
  errors: HarvestCompletionErrors,
): string | null {
  const normalized = value.trim();
  if (!UUID_PATTERN.test(normalized)) {
    errors[field] = `${label} phải là UUID hợp lệ.`;
    return null;
  }
  return normalized;
}

function weight(
  value: string,
  field: "grossWeightKg" | "netWeightKg",
  label: string,
  errors: HarvestCompletionErrors,
): number | null {
  const normalized = value.trim();
  if (!WEIGHT_PATTERN.test(normalized)) {
    errors[field] = `${label} phải là số dương với tối đa 3 chữ số thập phân.`;
    return null;
  }
  const parsed = Number(normalized);
  if (!Number.isFinite(parsed) || parsed <= 0 || parsed > MAX_WEIGHT_KG) {
    errors[field] = `${label} phải lớn hơn 0 và không vượt quá ${MAX_WEIGHT_KG} kg.`;
    return null;
  }
  return parsed;
}

export function validateHarvestCompletionDraft(
  draft: HarvestCompletionDraft,
  context: HarvestCompletionContext,
): HarvestCompletionValidationResult {
  const errors: HarvestCompletionErrors = {};
  const code = requiredText(draft.code, "code", "Mã thu hoạch", 64, errors);
  const cropCycleId = uuid(draft.cropCycleId, "cropCycleId", "Mùa vụ", errors);
  const plotId = uuid(draft.plotId, "plotId", "Lô đất", errors);
  const warehouseId = uuid(draft.warehouseId, "warehouseId", "Kho nhận hàng", errors);
  const productCode = requiredText(draft.productCode, "productCode", "Mã sản phẩm", 64, errors);
  const grossWeightKg = weight(draft.grossWeightKg, "grossWeightKg", "Khối lượng thô", errors);
  const netWeightKg = weight(draft.netWeightKg, "netWeightKg", "Khối lượng thực", errors);
  const qualityGrade = requiredText(draft.qualityGrade, "qualityGrade", "Phân loại chất lượng", 32, errors);
  const productName = optionalText(draft.productName, "productName", "Tên sản phẩm", 200, errors);
  const careSummary = optionalText(draft.careSummary, "careSummary", "Tóm tắt chăm sóc", 1000, errors);
  const farmName = optionalText(context.farmName, "farmName", "Tên nông trại", 200, errors);
  const plotCode = optionalText(context.plotCode, "plotCode", "Mã lô đất", 64, errors);

  if (grossWeightKg !== null && netWeightKg !== null && netWeightKg > grossWeightKg) {
    errors.netWeightKg = "Khối lượng thực không được lớn hơn khối lượng thô.";
  }

  if (
    Object.keys(errors).length > 0
    || code === null
    || cropCycleId === null
    || plotId === null
    || warehouseId === null
    || productCode === null
    || grossWeightKg === null
    || netWeightKg === null
    || qualityGrade === null
  ) {
    return { valid: false, errors };
  }

  return {
    valid: true,
    request: {
      code,
      cropCycleId,
      plotId,
      warehouseId,
      productCode,
      grossWeightKg,
      netWeightKg,
      qualityGrade,
      notes: draft.notes.trim() || null,
      farmName,
      plotCode,
      productName,
      careSummary,
    },
  };
}
