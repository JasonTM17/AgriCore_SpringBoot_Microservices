import type { ApiClient } from "../../lib/api/client";
import type {
  FarmPageResponse,
  FarmSort,
  FarmStatus,
  PlotPageResponse,
} from "../../lib/api/types";

export interface FarmListParams {
  page: number;
  size: number;
  sort: FarmSort;
  province?: string;
  status?: FarmStatus;
}

export interface PlotListParams {
  page: number;
  size: number;
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

export function listFarms(
  api: ApiClient,
  params: FarmListParams,
  signal?: AbortSignal,
): Promise<FarmPageResponse> {
  const search = queryString([
    ["province", params.province],
    ["status", params.status],
    ["page", params.page],
    ["size", params.size],
    ["sort", params.sort],
  ]);
  return api.request<FarmPageResponse>(`/api/v1/farms?${search}`, {
    method: "GET",
    ...(signal ? { signal } : {}),
  });
}

export function listFarmPlots(
  api: ApiClient,
  farmId: string,
  params: PlotListParams,
  signal?: AbortSignal,
): Promise<PlotPageResponse> {
  const search = queryString([
    ["page", params.page],
    ["size", params.size],
  ]);
  return api.request<PlotPageResponse>(
    `/api/v1/farms/${encodeURIComponent(farmId)}/plots?${search}`,
    { method: "GET", ...(signal ? { signal } : {}) },
  );
}
