import { describe, expect, it, vi } from "vitest";

import { ApiClient } from "../../lib/api/client";
import { createSalesOrder, getSalesOrder, reconcileSalesOrder } from "./sales-api";

type FetchFn = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

describe("sales API", () => {
  it("posts the sales order contract and encodes detail IDs", async () => {
    const fetchImpl: FetchFn = vi.fn(() => Promise.resolve(new Response("{}", { status: 201 })));
    const api = new ApiClient({ getAccessToken: () => "token", setAccessToken: () => undefined, fetchImpl });
    const request = {
      orderNumber: "SO-2026-001",
      customerId: "customer-id",
      inventoryItemId: "item-id",
      quantity: 10,
      unitPrice: 12000,
      currencyCode: "VND",
    };
    await createSalesOrder(api, request);
    await getSalesOrder(api, "order/id");
    await reconcileSalesOrder(api, "order/id", "RELEASE");
    expect(vi.mocked(fetchImpl).mock.calls.map(([input, init]) => [input, init?.method])).toEqual([
      ["/api/v1/sales/orders", "POST"],
      ["/api/v1/sales/orders/order%2Fid", "GET"],
      ["/api/v1/sales/orders/order%2Fid/reconcile?action=RELEASE", "POST"],
    ]);
    expect(vi.mocked(fetchImpl).mock.calls[0]?.[1]?.body).toBe(JSON.stringify(request));
  });
});
