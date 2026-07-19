import type { PublicTraceabilityResponse } from "../../lib/api/types";

export const traceabilityCode = "COFFEE-1234ABCD";

export const publicTraceability = {
  traceabilityCode,
  productName: "Cà phê Robusta",
  varietyName: "TR4",
  farmName: "Nông trại Đắk Lắk",
  plotCode: "PLOT-A1",
  plantingDate: "2025-03-01",
  harvestDate: "2026-03-15",
  qualityGrade: "GRADE_A",
  netWeightKg: 3300,
  careSummary: "Bón phân hữu cơ và tưới nhỏ giọt.",
  qrUrl: `https://agricore.test/public/traceability/${traceabilityCode}`,
  batchLabel: `BATCH-${traceabilityCode}`,
} satisfies PublicTraceabilityResponse;

export function jsonResponse(body: unknown, status = 200): Promise<Response> {
  return Promise.resolve(new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  }));
}

export function requestUrl(input: RequestInfo | URL): URL {
  const value = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
  return new URL(value, "http://agricore.test");
}

export function anonymousRefresh(): Promise<Response> {
  return jsonResponse({
    status: 401,
    error: "Unauthorized",
    code: "INVALID_REFRESH_TOKEN",
    message: "missing",
  }, 401);
}
