/** Domain DTO shapes aligned with controller records (not invented list fields). */

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface FarmResponse {
  id: string;
  code: string;
  name: string;
  address: string;
  province: string;
  totalAreaHa: number | string;
  latitude: number | null;
  longitude: number | null;
  status: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface PlotResponse {
  id: string;
  farmId: string;
  code: string;
  name: string;
  areaHa: number | string;
  soilType: string | null;
  status: string;
  version: number;
}

export interface CropResponse {
  id: string;
  code: string;
  name: string;
  scientificName: string | null;
  category: string | null;
  status: string;
}

export interface CropCycleResponse {
  id: string;
  code: string;
  farmId: string;
  plotId: string;
  cropId: string;
  cropVarietyId: string | null;
  plannedStartDate: string;
  plannedEndDate: string | null;
  actualStartDate: string | null;
  actualEndDate: string | null;
  stage: string;
  status: string;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface WorkTaskResponse {
  id: string;
  code: string;
  cropCycleId: string;
  plotId: string;
  taskType: string;
  title: string;
  description: string | null;
  priority: string;
  assignedEmployeeId: string | null;
  scheduledStart: string | null;
  scheduledEnd: string | null;
  actualStart: string | null;
  actualEnd: string | null;
  status: string;
  notes: string | null;
  createdAt: string;
  version: number;
}

export interface HarvestBatchResponse {
  id: string;
  code: string;
  cropCycleId: string;
  plotId: string;
  warehouseId: string;
  productCode: string;
  grossWeightKg: number | string;
  netWeightKg: number | string;
  qualityGrade: string;
  status: string;
  notes: string | null;
  createdAt: string;
}

export interface InventoryItemResponse {
  id: string;
  warehouseId: string;
  sku: string;
  name: string;
  itemType: string;
  unit: string;
  onHandQuantity: number | string;
  reservedQuantity: number | string;
  availableQuantity: number | string;
  version: number;
}

export interface SalesOrderResponse {
  id: string;
  orderNumber: string;
  customerId: string;
  status: string;
  inventoryItemId: string;
  quantity: number | string;
  reservationId: string | null;
  correlationId: string | null;
  failureReason: string | null;
  sagaStatus: string | null;
  sagaStep: string | null;
  createdAt: string;
}

export interface DeviceResponse {
  id: string;
  deviceCode: string;
  plotId: string;
  name: string;
  status: string;
  lastSeenAt: string | null;
}

export interface IngestResultResponse {
  readingId: string | null;
  alertRaised: boolean;
  alertId: string | null;
  message: string | null;
}

export interface PublicTraceabilityResponse {
  traceabilityCode: string;
  productName: string;
  varietyName: string | null;
  farmName: string;
  plotCode: string;
  plantingDate: string | null;
  harvestDate: string | null;
  qualityGrade: string | null;
  netWeightKg: number | string | null;
  careSummary: string | null;
  qrUrl: string | null;
  batchLabel: string | null;
}

export interface AssistantCapabilities {
  provider: string;
  generationAvailable: boolean;
  streaming: boolean;
  tools: string[];
  reason: string | null;
}

export interface ConversationSummary {
  id: string;
  title: string;
  farmId: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface ConversationMessage {
  id: string;
  role: "USER" | "ASSISTANT" | "SYSTEM" | "TOOL";
  content: string;
  createdAt: string;
  generationId: string | null;
}

/** API surfaces the console may enable as live actions. */
export const LIVE_API_CAPABILITIES = {
  farmsList: true,
  farmDetail: true,
  farmPlotsList: true,
  cropsList: true,
  cropCyclesList: true,
  cropCycleDetail: true,
  workTasksList: true,
  workTaskDetail: true,
  harvestGetById: true,
  harvestComplete: true,
  inventoryGetById: true,
  inventoryList: false,
  salesOrderGetById: true,
  salesOrderList: false,
  iotRegisterDevice: true,
  iotIngestReading: true,
  iotDeviceList: false,
  adminUsersList: true,
  publicTraceByCode: true,
  assistantChat: true,
  dashboardAggregate: false,
} as const;

export type LiveApiCapability = keyof typeof LIVE_API_CAPABILITIES;
