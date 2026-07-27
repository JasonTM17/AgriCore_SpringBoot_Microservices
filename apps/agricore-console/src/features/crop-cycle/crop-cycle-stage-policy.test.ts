import { describe, expect, it } from "vitest";

import type { CycleStage } from "../../lib/api/types";
import { allowedNextStages, isLegalStageTransition } from "./crop-cycle-stage-policy";

const stages: readonly CycleStage[] = [
  "PLANNED",
  "LAND_PREPARATION",
  "SOWING",
  "GROWING",
  "FERTILIZING",
  "PEST_CONTROL",
  "HARVESTING",
  "COMPLETED",
  "CANCELLED",
];

const expectedTransitions: Readonly<Record<CycleStage, readonly CycleStage[]>> = {
  PLANNED: ["LAND_PREPARATION", "CANCELLED"],
  LAND_PREPARATION: ["SOWING", "CANCELLED"],
  SOWING: ["GROWING", "CANCELLED"],
  GROWING: ["FERTILIZING", "PEST_CONTROL", "HARVESTING", "CANCELLED"],
  FERTILIZING: ["GROWING", "PEST_CONTROL", "HARVESTING", "CANCELLED"],
  PEST_CONTROL: ["GROWING", "FERTILIZING", "HARVESTING", "CANCELLED"],
  HARVESTING: ["COMPLETED", "CANCELLED"],
  COMPLETED: [],
  CANCELLED: [],
};

describe("crop-cycle stage policy", () => {
  it.each(stages)("returns the backend destinations for %s", (from) => {
    expect(allowedNextStages(from)).toEqual(expectedTransitions[from]);
  });

  it("accepts idempotent requests for every stage, including terminal stages", () => {
    for (const stage of stages) {
      expect(isLegalStageTransition(stage, stage)).toBe(true);
    }
  });

  it("rejects every transition not declared by the backend policy", () => {
    for (const from of stages) {
      for (const to of stages) {
        const expected = from === to || expectedTransitions[from].includes(to);
        expect(isLegalStageTransition(from, to)).toBe(expected);
      }
    }
  });

  it("keeps terminal stages without any non-idempotent destination", () => {
    expect(allowedNextStages("COMPLETED")).toEqual([]);
    expect(allowedNextStages("CANCELLED")).toEqual([]);
    for (const terminal of ["COMPLETED", "CANCELLED"] as const) {
      for (const destination of stages) {
        if (destination !== terminal) {
          expect(isLegalStageTransition(terminal, destination)).toBe(false);
        }
      }
    }
  });
});
