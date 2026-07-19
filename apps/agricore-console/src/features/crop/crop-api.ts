import type { ApiClient } from "../../lib/api/client";
import type { CropPageResponse } from "../../lib/api/types";

export interface CropListParams {
  page: number;
  size: number;
  category?: string;
  q?: string;
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

export function listCrops(
  api: ApiClient,
  params: CropListParams,
  signal?: AbortSignal,
): Promise<CropPageResponse> {
  const search = queryString([
    ["category", params.category],
    ["q", params.q],
    ["page", params.page],
    ["size", params.size],
  ]);
  return api.request<CropPageResponse>(`/api/v1/crops?${search}`, {
    method: "GET",
    ...(signal ? { signal } : {}),
  });
}
