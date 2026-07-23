import { describe, expect, it, vi } from "vitest";

import { ApiClient } from "../../lib/api/client";
import type { CompleteHarvestRequest } from "../../lib/api/types";
import {
  completeHarvest,
  getHarvest,
  getHarvestCompletionEventStatus,
  getInventoryHarvestProjectionAcknowledgement,
  getTraceabilityHarvestProjectionAcknowledgement,
  republishHarvestCompletionEvent,
} from "./harvest-api";

type FetchFn = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

function jsonResponse(body: unknown): Promise<Response> {
  return Promise.resolve(new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  }));
}

function client(fetchImpl: FetchFn): ApiClient {
  return new ApiClient({
    getAccessToken: () => "access-token",
    setAccessToken: () => undefined,
    fetchImpl,
  });
}

describe("harvest API", () => {
  it("posts the complete-harvest contract unchanged", async () => {
    const fetchImpl: FetchFn = vi.fn(() => jsonResponse({}));
    const signal = new AbortController().signal;
    const request: CompleteHarvestRequest = {
      code: "HARVEST-001",
      cropCycleId: "50000000-0000-0000-0000-000000000001",
      plotId: "30000000-0000-0000-0000-000000000001",
      warehouseId: "70000000-0000-0000-0000-000000000001",
      productCode: "COFFEE-ROBUSTA",
      grossWeightKg: 3500,
      netWeightKg: 3300,
      qualityGrade: "GRADE_A",
      notes: "Đợt đầu mùa",
      farmName: "Nông trại Tây Nguyên",
      plotCode: "PLOT-A1",
      productName: "Cà phê Robusta",
      careSummary: "Tưới nhỏ giọt và bón phân hữu cơ.",
    };

    await completeHarvest(client(fetchImpl), request, signal);

    const [input, init] = vi.mocked(fetchImpl).mock.calls[0] ?? [];
    expect(input).toBe("/api/v1/harvests/complete");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBe(JSON.stringify(request));
    expect(init?.signal).toBeInstanceOf(AbortSignal);
  });

  it("encodes harvest IDs for detail, producer status, and repair", async () => {
    const fetchImpl: FetchFn = vi.fn(() => jsonResponse({}));
    const api = client(fetchImpl);

    await getHarvest(api, "harvest/id?part");
    await getHarvestCompletionEventStatus(api, "harvest/id?part");
    await republishHarvestCompletionEvent(api, "harvest/id?part");

    expect(vi.mocked(fetchImpl).mock.calls.map(([input, init]) => [input, init?.method]))
      .toEqual([
        ["/api/v1/harvests/harvest%2Fid%3Fpart", "GET"],
        ["/api/v1/harvests/harvest%2Fid%3Fpart/completion-event", "GET"],
        ["/api/v1/harvests/harvest%2Fid%3Fpart/completion-event/republish", "POST"],
      ]);
  });

  it("reads inventory and traceability acknowledgement by encoded event ID", async () => {
    const fetchImpl: FetchFn = vi.fn(() => jsonResponse({}));
    const api = client(fetchImpl);
    const signal = new AbortController().signal;

    await getInventoryHarvestProjectionAcknowledgement(api, "event/id", "warehouse/id", signal);
    await getTraceabilityHarvestProjectionAcknowledgement(api, "event/id", signal);

    const [inventoryInput, inventoryInit] = vi.mocked(fetchImpl).mock.calls[0] ?? [];
    const [traceInput, traceInit] = vi.mocked(fetchImpl).mock.calls[1] ?? [];
    expect(inventoryInput).toBe(
      "/api/v1/inventory/events/harvest-completed/event%2Fid/acknowledgement?warehouseId=warehouse%2Fid",
    );
    expect(inventoryInit?.method).toBe("GET");
    expect(inventoryInit?.signal).toBeInstanceOf(AbortSignal);
    expect(traceInput).toBe(
      "/api/v1/traceability/events/harvest-completed/event%2Fid/acknowledgement",
    );
    expect(traceInit?.method).toBe("GET");
    expect(traceInit?.signal).toBeInstanceOf(AbortSignal);
  });
});
