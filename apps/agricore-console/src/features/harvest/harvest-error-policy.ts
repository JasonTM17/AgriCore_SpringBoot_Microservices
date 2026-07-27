import { ApiClientError } from "../../lib/api/errors";

export function isHarvestUnavailable(error: unknown): error is ApiClientError {
  return error instanceof ApiClientError && (error.status === 403 || error.status === 404);
}

export function retryHarvestFailure(failureCount: number, error: Error): boolean {
  if (failureCount >= 1) return false;
  if (!(error instanceof ApiClientError)) return true;
  return error.status === 408 || error.status === 429 || error.status >= 500;
}

export function harvestErrorSupportCode(error: Error | null): string | null {
  return error instanceof ApiClientError ? error.code : null;
}
