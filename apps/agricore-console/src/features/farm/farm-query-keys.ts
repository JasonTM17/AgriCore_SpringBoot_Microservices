import type { FarmListParams, PlotListParams } from "./farm-api";

export const farmQueryKeys = {
  all: ["farms"] as const,
  subject: (subject: string) => ["farms", subject] as const,
  lists: (subject: string) => ["farms", subject, "list"] as const,
  list: (subject: string, params: FarmListParams) =>
    ["farms", subject, "list", params] as const,
  detail: (subject: string, farmId: string) => ["farms", subject, "detail", farmId] as const,
  plotDetail: (subject: string, plotId: string) =>
    ["farms", subject, "plot", "detail", plotId] as const,
  plots: (subject: string, farmId: string, params: PlotListParams) =>
    ["farms", subject, farmId, "plots", params] as const,
};
