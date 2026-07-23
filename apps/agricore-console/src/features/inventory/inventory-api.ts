import type { ApiClient } from "../../lib/api/client";
import type { components as GatewayComponents } from "../../lib/api/generated/gateway";

type GatewaySchemas = GatewayComponents["schemas"];

export type InventoryItemResponse = GatewaySchemas["InventoryItemResponse"];
export type ReservationResponse = GatewaySchemas["ReservationResponse"];
export type ReservationReleaseResponse = GatewaySchemas["ReservationReleaseResponse"];

export interface ReserveStockRequest {
  inventoryItemId: string;
  quantity: number;
  referenceType: string;
  referenceId: string;
}

export function getInventoryItem(
  api: ApiClient,
  itemId: string,
  signal?: AbortSignal,
): Promise<InventoryItemResponse> {
  return api.request<InventoryItemResponse>(
    `/api/v1/inventory/items/${encodeURIComponent(itemId)}`,
    { method: "GET", ...(signal ? { signal } : {}) },
  );
}

export function reserveStock(
  api: ApiClient,
  request: ReserveStockRequest,
  signal?: AbortSignal,
): Promise<ReservationResponse> {
  return api.request<ReservationResponse>("/api/v1/inventory/reservations", {
    method: "POST",
    body: request,
    ...(signal ? { signal } : {}),
  });
}

export function getReservationByReference(
  api: ApiClient,
  referenceType: string,
  referenceId: string,
  signal?: AbortSignal,
): Promise<ReservationResponse> {
  const search = new URLSearchParams({ referenceType, referenceId });
  return api.request<ReservationResponse>(
    `/api/v1/inventory/reservations/by-reference?${search.toString()}`,
    { method: "GET", ...(signal ? { signal } : {}) },
  );
}

export function confirmReservation(
  api: ApiClient,
  reservationId: string,
  signal?: AbortSignal,
): Promise<ReservationResponse> {
  return api.request<ReservationResponse>(
    `/api/v1/inventory/reservations/${encodeURIComponent(reservationId)}/confirm`,
    { method: "POST", ...(signal ? { signal } : {}) },
  );
}

export function releaseReservation(
  api: ApiClient,
  reservationId: string,
  signal?: AbortSignal,
): Promise<ReservationReleaseResponse> {
  return api.request<ReservationReleaseResponse>(
    `/api/v1/inventory/reservations/${encodeURIComponent(reservationId)}/release`,
    { method: "POST", ...(signal ? { signal } : {}) },
  );
}
