import type { CropCycleListParams } from "./crop-cycle-api";

export const cropCycleQueryKeys = {
  all: ["crop-cycles"] as const,
  lists: (subject: string) => ["crop-cycles", subject, "list"] as const,
  farmLists: (subject: string, farmId: string) =>
    ["crop-cycles", subject, "list", farmId] as const,
  list: (subject: string, params: CropCycleListParams) =>
    ["crop-cycles", subject, "list", params.farmId, params] as const,
};
