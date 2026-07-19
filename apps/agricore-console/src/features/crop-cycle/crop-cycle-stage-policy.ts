import type { CycleStage } from "../../lib/api/types";

/**
 * Client-side projection of the crop-cycle domain transition policy.
 *
 * The backend remains authoritative; this map only prevents offering actions
 * that the service will reject and must stay in lockstep with its policy.
 */
export const CROP_CYCLE_STAGE_TRANSITIONS = {
  PLANNED: ["LAND_PREPARATION", "CANCELLED"],
  LAND_PREPARATION: ["SOWING", "CANCELLED"],
  SOWING: ["GROWING", "CANCELLED"],
  GROWING: ["FERTILIZING", "PEST_CONTROL", "HARVESTING", "CANCELLED"],
  FERTILIZING: ["GROWING", "PEST_CONTROL", "HARVESTING", "CANCELLED"],
  PEST_CONTROL: ["GROWING", "FERTILIZING", "HARVESTING", "CANCELLED"],
  HARVESTING: ["COMPLETED", "CANCELLED"],
  COMPLETED: [],
  CANCELLED: [],
} as const satisfies Readonly<Record<CycleStage, readonly CycleStage[]>>;

export function allowedNextStages(stage: CycleStage): readonly CycleStage[] {
  return CROP_CYCLE_STAGE_TRANSITIONS[stage];
}

/** Matches the backend's idempotent same-stage behavior. */
export function isLegalStageTransition(from: CycleStage, to: CycleStage): boolean {
  return from === to || allowedNextStages(from).includes(to);
}
