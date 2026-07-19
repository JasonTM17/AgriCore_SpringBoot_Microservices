import type { ApiClient } from "../../lib/api/client";
import type { PublicTraceabilityResponse } from "../../lib/api/types";

export function getPublicTraceability(
  api: ApiClient,
  traceabilityCode: string,
  signal?: AbortSignal,
): Promise<PublicTraceabilityResponse> {
  return api.publicGet<PublicTraceabilityResponse>(
    `/public/api/v1/traceability/${encodeURIComponent(traceabilityCode)}`,
    signal,
  );
}
