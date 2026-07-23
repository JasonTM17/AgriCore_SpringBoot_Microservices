import type { ApiClient } from "../../lib/api/client";
import type { components as GatewayComponents } from "../../lib/api/generated/gateway";

type GatewaySchemas = GatewayComponents["schemas"];

export type RegisterDeviceRequest = GatewaySchemas["RegisterDeviceRequest"];
export type DeviceResponse = GatewaySchemas["DeviceResponse"];
export type IngestReadingRequest = GatewaySchemas["IngestReadingRequest"];
export type IngestResultResponse = GatewaySchemas["IngestResultResponse"];

export function registerIotDevice(
  api: ApiClient,
  request: RegisterDeviceRequest,
  signal?: AbortSignal,
): Promise<DeviceResponse> {
  return api.request<DeviceResponse>("/api/v1/iot/devices", {
    method: "POST",
    body: request,
    ...(signal ? { signal } : {}),
  });
}

export function ingestIotReading(
  api: ApiClient,
  request: IngestReadingRequest,
  signal?: AbortSignal,
): Promise<IngestResultResponse> {
  return api.request<IngestResultResponse>("/api/v1/iot/readings", {
    method: "POST",
    body: request,
    ...(signal ? { signal } : {}),
  });
}
