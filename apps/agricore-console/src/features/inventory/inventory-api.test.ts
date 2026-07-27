import { describe, expect, it, vi } from "vitest";

import { ApiClient } from "../../lib/api/client";
import {
  confirmReservation,
  getInventoryItem,
  getReservationByReference,
  releaseReservation,
  reserveStock,
} from "./inventory-api";

type FetchFn = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

function client() {
  const fetchImpl: FetchFn = vi.fn(() => Promise.resolve(new Response("{}", { status: 200 })));
  return { api: new ApiClient({ getAccessToken: () => "token", setAccessToken: () => undefined, fetchImpl }), fetchImpl };
}

describe("inventory API", () => {
  it("reads a known item and encodes reservation references", async () => {
    const { api, fetchImpl } = client();
    await getInventoryItem(api, "item/id");
    await getReservationByReference(api, "SALES ORDER", "order/id");
    expect(vi.mocked(fetchImpl).mock.calls.map(([input]) => input)).toEqual([
      "/api/v1/inventory/items/item%2Fid",
      "/api/v1/inventory/reservations/by-reference?referenceType=SALES+ORDER&referenceId=order%2Fid",
    ]);
  });

  it("posts reserve, confirm, and release actions", async () => {
    const { api, fetchImpl } = client();
    const request = { inventoryItemId: "item", quantity: 2.5, referenceType: "SALES_ORDER", referenceId: "SO-1" };
    await reserveStock(api, request);
    await confirmReservation(api, "reservation/id");
    await releaseReservation(api, "reservation/id");
    expect(vi.mocked(fetchImpl).mock.calls.map(([input, init]) => [input, init?.method])).toEqual([
      ["/api/v1/inventory/reservations", "POST"],
      ["/api/v1/inventory/reservations/reservation%2Fid/confirm", "POST"],
      ["/api/v1/inventory/reservations/reservation%2Fid/release", "POST"],
    ]);
    expect(vi.mocked(fetchImpl).mock.calls[0]?.[1]?.body).toBe(JSON.stringify(request));
  });
});
