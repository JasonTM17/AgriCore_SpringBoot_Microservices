import type { FarmListParams, PlotListParams } from "./farm-api";

export const farmQueryKeys = {
  all: ["farms"] as const,
  list: (subject: string, params: FarmListParams) =>
    ["farms", subject, "list", params] as const,
  plots: (subject: string, farmId: string, params: PlotListParams) =>
    ["farms", subject, farmId, "plots", params] as const,
};
