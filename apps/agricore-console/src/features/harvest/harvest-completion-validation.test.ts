import { describe, expect, it } from "vitest";

import {
  validateHarvestCompletionDraft,
  type HarvestCompletionDraft,
} from "./harvest-completion-validation";

const validDraft: HarvestCompletionDraft = {
  code: "  HARVEST-001  ",
  cropCycleId: "50000000-0000-0000-0000-000000000001",
  plotId: "30000000-0000-0000-0000-000000000001",
  warehouseId: "70000000-0000-0000-0000-000000000001",
  productCode: "  coffee-robusta  ",
  grossWeightKg: "3500.125",
  netWeightKg: "3300.125",
  qualityGrade: "  grade_a  ",
  notes: "  Đợt đầu mùa  ",
  productName: "  Cà phê Robusta  ",
  careSummary: "  Tưới nhỏ giọt và bón phân hữu cơ.  ",
};

describe("harvest completion validation", () => {
  it("builds the complete-harvest request from normalized authoritative context", () => {
    const result = validateHarvestCompletionDraft(validDraft, {
      farmName: "  Nông trại Đắk Lắk  ",
      plotCode: "  PLOT-A1  ",
    });

    expect(result).toEqual({
      valid: true,
      request: {
        code: "HARVEST-001",
        cropCycleId: validDraft.cropCycleId,
        plotId: validDraft.plotId,
        warehouseId: validDraft.warehouseId,
        productCode: "coffee-robusta",
        grossWeightKg: 3500.125,
        netWeightKg: 3300.125,
        qualityGrade: "grade_a",
        notes: "Đợt đầu mùa",
        farmName: "Nông trại Đắk Lắk",
        plotCode: "PLOT-A1",
        productName: "Cà phê Robusta",
        careSummary: "Tưới nhỏ giọt và bón phân hữu cơ.",
      },
    });
  });

  it("rejects invalid identity, precision, and cross-field weight input", () => {
    const result = validateHarvestCompletionDraft({
      ...validDraft,
      cropCycleId: "not-a-uuid",
      warehouseId: "warehouse-id",
      grossWeightKg: "100.0001",
      netWeightKg: "110",
    }, { farmName: null, plotCode: null });

    expect(result.valid).toBe(false);
    if (result.valid) throw new Error("Expected validation errors");
    expect(result.errors.cropCycleId).toBeDefined();
    expect(result.errors.warehouseId).toBeDefined();
    expect(result.errors.grossWeightKg).toContain("3 chữ số");

    const crossField = validateHarvestCompletionDraft({
      ...validDraft,
      grossWeightKg: "100",
      netWeightKg: "110",
    }, { farmName: null, plotCode: null });
    expect(crossField.valid).toBe(false);
    if (crossField.valid) throw new Error("Expected validation errors");
    expect(crossField.errors.netWeightKg).toContain("không được lớn hơn");
  });

  it("requires positive weights and contract-bounded text", () => {
    const result = validateHarvestCompletionDraft({
      ...validDraft,
      code: " ",
      productCode: "P".repeat(65),
      grossWeightKg: "0",
      netWeightKg: "-1",
      qualityGrade: "Q".repeat(33),
      productName: "N".repeat(201),
      careSummary: "C".repeat(1001),
    }, { farmName: "F".repeat(201), plotCode: "P".repeat(65) });

    expect(result.valid).toBe(false);
    if (result.valid) throw new Error("Expected validation errors");
    expect(result.errors.code).toBeDefined();
    expect(result.errors.productCode).toBeDefined();
    expect(result.errors.grossWeightKg).toBeDefined();
    expect(result.errors.netWeightKg).toBeDefined();
    expect(result.errors.qualityGrade).toBeDefined();
    expect(result.errors.productName).toBeDefined();
    expect(result.errors.careSummary).toBeDefined();
    expect(result.errors.farmName).toBeDefined();
    expect(result.errors.plotCode).toBeDefined();
  });

  it("emits explicit nulls for optional empty values without inventing metadata", () => {
    const result = validateHarvestCompletionDraft({
      ...validDraft,
      notes: "",
      productName: "",
      careSummary: "",
    }, { farmName: null, plotCode: null });

    expect(result.valid).toBe(true);
    if (!result.valid) throw new Error("Expected a valid request");
    expect(result.request).toMatchObject({
      notes: null,
      farmName: null,
      plotCode: null,
      productName: null,
      careSummary: null,
    });
  });
});
