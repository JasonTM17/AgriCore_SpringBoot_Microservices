import type { ApiClient } from "../../lib/api/client";
import type { CropCyclePageResponse } from "../../lib/api/types";

export interface CropCycleListParams {
  farmId: string;
  page: number;
  size: number;
  plotId?: string;
}

function queryString(values: ReadonlyArray<readonly [string, string | number | undefined]>): string {
  const search = new URLSearchParams();
  values.forEach(([key, value]) => {
    if (value !== undefined && value !== "") {
      search.set(key, String(value));
    }
  });
  return search.toString();
}

export function listCropCycles(
  api: ApiClient,
  params: CropCycleListParams,
  signal?: AbortSignal,
): Promise<CropCyclePageResponse> {
  const search = queryString([
    ["farmId", params.farmId],
    ["plotId", params.plotId],
    ["page", params.page],
    ["size", params.size],
  ]);
  return api.request<CropCyclePageResponse>(`/api/v1/crop-cycles?${search}`, {
    method: "GET",
    ...(signal ? { signal } : {}),
  });
}
