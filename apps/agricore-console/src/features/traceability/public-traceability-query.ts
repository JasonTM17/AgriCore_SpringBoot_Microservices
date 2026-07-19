import { ApiClientError } from "../../lib/api/errors";

export const publicTraceabilityQueryKeys = {
  all: ["public-traceability"] as const,
  detail: (traceabilityCode: string) =>
    ["public-traceability", "detail", traceabilityCode] as const,
};

export function normalizeTraceabilityCode(value: string): string | null {
  const normalized = value.trim().toUpperCase();
  return normalized.length >= 1 && normalized.length <= 64 ? normalized : null;
}

export function retryPublicTraceability(failureCount: number, error: Error): boolean {
  if (failureCount >= 1) return false;
  if (!(error instanceof ApiClientError)) return true;
  return error.status === 408 || error.status === 429 || error.status >= 500;
}
