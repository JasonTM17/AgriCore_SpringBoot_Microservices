import type { ApiClient } from "../../lib/api/client";
import type {
  CompleteHarvestRequest,
  HarvestBatchResponse,
  HarvestCompletionEventStatusResponse,
  InventoryHarvestProjectionAcknowledgementResponse,
  TraceabilityHarvestProjectionAcknowledgementResponse,
} from "../../lib/api/types";

export function completeHarvest(
  api: ApiClient,
  request: CompleteHarvestRequest,
  signal?: AbortSignal,
): Promise<HarvestBatchResponse> {
  return api.request<HarvestBatchResponse>("/api/v1/harvests/complete", {
    method: "POST",
    body: request,
    ...(signal ? { signal } : {}),
  });
}

export function getHarvest(
  api: ApiClient,
  harvestId: string,
  signal?: AbortSignal,
): Promise<HarvestBatchResponse> {
  return api.request<HarvestBatchResponse>(
    `/api/v1/harvests/${encodeURIComponent(harvestId)}`,
    { method: "GET", ...(signal ? { signal } : {}) },
  );
}

export function getHarvestCompletionEventStatus(
  api: ApiClient,
  harvestId: string,
  signal?: AbortSignal,
): Promise<HarvestCompletionEventStatusResponse> {
  return api.request<HarvestCompletionEventStatusResponse>(
    `/api/v1/harvests/${encodeURIComponent(harvestId)}/completion-event`,
    { method: "GET", ...(signal ? { signal } : {}) },
  );
}

export function republishHarvestCompletionEvent(
  api: ApiClient,
  harvestId: string,
  signal?: AbortSignal,
): Promise<HarvestCompletionEventStatusResponse> {
  return api.request<HarvestCompletionEventStatusResponse>(
    `/api/v1/harvests/${encodeURIComponent(harvestId)}/completion-event/republish`,
    { method: "POST", ...(signal ? { signal } : {}) },
  );
}

export function getInventoryHarvestProjectionAcknowledgement(
  api: ApiClient,
  eventId: string,
  signal?: AbortSignal,
): Promise<InventoryHarvestProjectionAcknowledgementResponse> {
  return api.request<InventoryHarvestProjectionAcknowledgementResponse>(
    `/api/v1/inventory/events/harvest-completed/${encodeURIComponent(eventId)}/acknowledgement`,
    { method: "GET", ...(signal ? { signal } : {}) },
  );
}

export function getTraceabilityHarvestProjectionAcknowledgement(
  api: ApiClient,
  eventId: string,
  signal?: AbortSignal,
): Promise<TraceabilityHarvestProjectionAcknowledgementResponse> {
  return api.request<TraceabilityHarvestProjectionAcknowledgementResponse>(
    `/api/v1/traceability/events/harvest-completed/${encodeURIComponent(eventId)}/acknowledgement`,
    { method: "GET", ...(signal ? { signal } : {}) },
  );
}
