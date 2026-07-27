import type { ApiClient } from "../../lib/api/client";

export interface CreateSalesOrderRequest {
  orderNumber: string;
  customerId: string;
  inventoryItemId: string;
  quantity: number;
  unitPrice?: number | null;
  currencyCode?: string | null;
}

export interface SalesOrderItemResponse {
  lineNumber: number;
  inventoryItemId: string;
  quantity: number;
  unitPrice: number | null;
  lineTotal: number | null;
  currencyCode: string | null;
}

export interface SalesOrderResponse {
  id: string;
  orderNumber: string;
  customerId: string;
  status: string;
  inventoryItemId: string;
  quantity: number;
  reservationId: string | null;
  correlationId: string | null;
  failureReason: string | null;
  sagaStatus: string | null;
  sagaStep: string | null;
  sagaRetryCount: number | null;
  sagaNextAttemptAt: string | null;
  sagaCompletedAt: string | null;
  createdAt: string;
  currencyCode: string | null;
  subtotalAmount: number | null;
  totalAmount: number | null;
  items: SalesOrderItemResponse[];
}

export function createSalesOrder(
  api: ApiClient,
  request: CreateSalesOrderRequest,
  signal?: AbortSignal,
): Promise<SalesOrderResponse> {
  return api.request<SalesOrderResponse>("/api/v1/sales/orders", {
    method: "POST",
    body: request,
    ...(signal ? { signal } : {}),
  });
}

export function getSalesOrder(
  api: ApiClient,
  orderId: string,
  signal?: AbortSignal,
): Promise<SalesOrderResponse> {
  return api.request<SalesOrderResponse>(
    `/api/v1/sales/orders/${encodeURIComponent(orderId)}`,
    { method: "GET", ...(signal ? { signal } : {}) },
  );
}

export function reconcileSalesOrder(
  api: ApiClient,
  orderId: string,
  action: "RELEASE" | "CONFIRM",
  signal?: AbortSignal,
): Promise<SalesOrderResponse> {
  return api.request<SalesOrderResponse>(
    `/api/v1/sales/orders/${encodeURIComponent(orderId)}/reconcile?action=${encodeURIComponent(action)}`,
    { method: "POST", ...(signal ? { signal } : {}) },
  );
}
