import { describe, expect, it } from "vitest";

import { ApiClientError } from "../../lib/api/errors";
import {
  normalizeTraceabilityCode,
  retryPublicTraceability,
} from "./public-traceability-query";

function apiError(status: number): ApiClientError {
  return new ApiClientError(status, null, "request failed");
}

describe("public traceability query policy", () => {
  it("normalizes valid codes and rejects route-boundary violations", () => {
    expect(normalizeTraceabilityCode(" coffee-1234 ")).toBe("COFFEE-1234");
    expect(normalizeTraceabilityCode("   ")).toBeNull();
    expect(normalizeTraceabilityCode("A".repeat(65))).toBeNull();
  });

  it("retries one transient failure but never retries auth or not-found responses", () => {
    expect(retryPublicTraceability(0, apiError(500))).toBe(true);
    expect(retryPublicTraceability(1, apiError(500))).toBe(false);
    expect(retryPublicTraceability(0, apiError(429))).toBe(true);
    expect(retryPublicTraceability(0, apiError(404))).toBe(false);
    expect(retryPublicTraceability(0, apiError(401))).toBe(false);
  });
});
