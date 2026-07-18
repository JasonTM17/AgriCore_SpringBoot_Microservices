import type { ApiClient } from "./client";
import type {
  CropCycleResponse,
  CropResponse,
  DeviceResponse,
  FarmResponse,
  HarvestBatchResponse,
  IngestResultResponse,
  InventoryItemResponse,
  PageResponse,
  PlotResponse,
  PublicTraceabilityResponse,
  SalesOrderResponse,
  WorkTaskResponse,
  AssistantCapabilities,
  ConversationMessage,
  ConversationSummary,
} from "./domain-types";
import type { UserResponse } from "./types";

function withSignal(signal?: AbortSignal): { signal?: AbortSignal } {
  return signal ? { signal } : {};
}

/** Domain operations via the shared authenticated ApiClient. */
export function createDomainApi(api: ApiClient) {
  return {
    listFarms(page = 0, size = 20, signal?: AbortSignal) {
      return api.request<PageResponse<FarmResponse>>(
        `/api/v1/farms?page=${page}&size=${size}`,
        { method: "GET", auth: true, ...withSignal(signal) },
      );
    },
    getFarm(farmId: string, signal?: AbortSignal) {
      return api.request<FarmResponse>(`/api/v1/farms/${farmId}`, {
        method: "GET",
        auth: true,
        ...withSignal(signal),
      });
    },
    listPlots(farmId: string, page = 0, size = 50, signal?: AbortSignal) {
      return api.request<PageResponse<PlotResponse>>(
        `/api/v1/farms/${farmId}/plots?page=${page}&size=${size}`,
        { method: "GET", auth: true, ...withSignal(signal) },
      );
    },
    listCrops(page = 0, size = 50, signal?: AbortSignal) {
      return api.request<PageResponse<CropResponse>>(
        `/api/v1/crops?page=${page}&size=${size}`,
        { method: "GET", auth: true, ...withSignal(signal) },
      );
    },
    listCropCycles(page = 0, size = 20, signal?: AbortSignal) {
      return api.request<PageResponse<CropCycleResponse>>(
        `/api/v1/crop-cycles?page=${page}&size=${size}`,
        { method: "GET", auth: true, ...withSignal(signal) },
      );
    },
    getCropCycle(cycleId: string, signal?: AbortSignal) {
      return api.request<CropCycleResponse>(`/api/v1/crop-cycles/${cycleId}`, {
        method: "GET",
        auth: true,
        ...withSignal(signal),
      });
    },
    changeCycleStage(
      cycleId: string,
      body: { stage: string; version: number; notes?: string },
      signal?: AbortSignal,
    ) {
      return api.request<CropCycleResponse>(`/api/v1/crop-cycles/${cycleId}/stage`, {
        method: "POST",
        auth: true,
        body,
        ...withSignal(signal),
      });
    },
    listWorkTasks(page = 0, size = 20, signal?: AbortSignal) {
      return api.request<PageResponse<WorkTaskResponse>>(
        `/api/v1/work-tasks?page=${page}&size=${size}`,
        { method: "GET", auth: true, ...withSignal(signal) },
      );
    },
    getWorkTask(taskId: string, signal?: AbortSignal) {
      return api.request<WorkTaskResponse>(`/api/v1/work-tasks/${taskId}`, {
        method: "GET",
        auth: true,
        ...withSignal(signal),
      });
    },
    completeWorkTask(taskId: string, body: { version: number; notes?: string }, signal?: AbortSignal) {
      return api.request<WorkTaskResponse>(`/api/v1/work-tasks/${taskId}/complete`, {
        method: "POST",
        auth: true,
        body,
        ...withSignal(signal),
      });
    },
    getHarvest(harvestId: string, signal?: AbortSignal) {
      return api.request<HarvestBatchResponse>(`/api/v1/harvests/${harvestId}`, {
        method: "GET",
        auth: true,
        ...withSignal(signal),
      });
    },
    completeHarvest(
      body: {
        code: string;
        cropCycleId: string;
        plotId: string;
        warehouseId: string;
        productCode: string;
        grossWeightKg: number;
        netWeightKg: number;
        qualityGrade: string;
        notes?: string;
        farmName?: string;
        plotCode?: string;
        productName?: string;
        careSummary?: string;
      },
      signal?: AbortSignal,
    ) {
      return api.request<HarvestBatchResponse>(`/api/v1/harvests/complete`, {
        method: "POST",
        auth: true,
        body,
        ...withSignal(signal),
      });
    },
    getInventoryItem(itemId: string, signal?: AbortSignal) {
      return api.request<InventoryItemResponse>(`/api/v1/inventory/items/${itemId}`, {
        method: "GET",
        auth: true,
        ...withSignal(signal),
      });
    },
    getSalesOrder(orderId: string, signal?: AbortSignal) {
      return api.request<SalesOrderResponse>(`/api/v1/sales/orders/${orderId}`, {
        method: "GET",
        auth: true,
        ...withSignal(signal),
      });
    },
    registerIotDevice(
      body: { deviceCode: string; plotId: string; name: string },
      signal?: AbortSignal,
    ) {
      return api.request<DeviceResponse>(`/api/v1/iot/devices`, {
        method: "POST",
        auth: true,
        body,
        ...withSignal(signal),
      });
    },
    ingestIotReading(
      body: {
        deviceCode: string;
        metric: string;
        value: number;
        unit: string;
        recordedAt?: string;
      },
      signal?: AbortSignal,
    ) {
      return api.request<IngestResultResponse>(`/api/v1/iot/readings`, {
        method: "POST",
        auth: true,
        body,
        ...withSignal(signal),
      });
    },
    listAdminUsers(page = 0, size = 20, signal?: AbortSignal) {
      return api.request<PageResponse<UserResponse>>(
        `/api/v1/admin/users?page=${page}&size=${size}`,
        { method: "GET", auth: true, ...withSignal(signal) },
      );
    },
    getPublicTrace(code: string, signal?: AbortSignal) {
      return api.request<PublicTraceabilityResponse>(
        `/public/api/v1/traceability/${encodeURIComponent(code)}`,
        { method: "GET", auth: false, ...withSignal(signal) },
      );
    },
    assistantCapabilities(signal?: AbortSignal) {
      return api.request<AssistantCapabilities>(`/api/v1/assistant/capabilities`, {
        method: "GET",
        auth: true,
        ...withSignal(signal),
      });
    },
    listConversations(signal?: AbortSignal) {
      return api.request<ConversationSummary[]>(`/api/v1/assistant/conversations`, {
        method: "GET",
        auth: true,
        ...withSignal(signal),
      });
    },
    createConversation(body: { title?: string; farmId?: string }, signal?: AbortSignal) {
      return api.request<ConversationSummary>(`/api/v1/assistant/conversations`, {
        method: "POST",
        auth: true,
        body,
        ...withSignal(signal),
      });
    },
    listMessages(conversationId: string, signal?: AbortSignal) {
      return api.request<ConversationMessage[]>(
        `/api/v1/assistant/conversations/${conversationId}/messages`,
        { method: "GET", auth: true, ...withSignal(signal) },
      );
    },
    startGeneration(
      conversationId: string,
      body: { content: string; idempotencyKey: string },
      signal?: AbortSignal,
    ) {
      return api.request<{ generationId: string; status: string }>(
        `/api/v1/assistant/conversations/${conversationId}/generations`,
        { method: "POST", auth: true, body, ...withSignal(signal) },
      );
    },
  };
}

export type DomainApi = ReturnType<typeof createDomainApi>;
