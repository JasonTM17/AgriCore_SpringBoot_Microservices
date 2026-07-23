import type {
  HarvestBatchResponse,
  HarvestCompletionEventStatusResponse,
  InventoryHarvestProjectionAcknowledgementResponse,
  PlotResponse,
  TraceabilityHarvestProjectionAcknowledgementResponse,
} from "../../lib/api/types";

export const harvestId = "60000000-0000-0000-0000-000000000001";
export const eventId = "61000000-0000-0000-0000-000000000001";

export const harvestPlot = {
  id: "30000000-0000-0000-0000-000000000001",
  farmId: "20000000-0000-0000-0000-000000000001",
  areaId: null,
  code: "PLOT-A1",
  name: "Lô cà phê A1",
  areaInHectares: 12.5,
  soilType: "BASALT",
  status: "IN_USE",
  latitude: null,
  longitude: null,
  createdAt: "2026-07-19T00:00:00Z",
  updatedAt: "2026-07-19T00:00:00Z",
  version: 0,
} satisfies PlotResponse;

export const harvestBatch = {
  id: harvestId,
  code: "HARVEST-001",
  cropCycleId: "50000000-0000-0000-0000-000000000001",
  plotId: "30000000-0000-0000-0000-000000000001",
  warehouseId: "70000000-0000-0000-0000-000000000001",
  productCode: "COFFEE-ROBUSTA",
  grossWeightKg: 3500,
  netWeightKg: 3300,
  qualityGrade: "GRADE_A",
  status: "COMPLETED",
  startedAt: "2026-07-19T07:45:00Z",
  harvestedAt: "2026-07-19T08:30:00Z",
  notes: "Đợt đầu mùa",
  lastOutboxEventId: eventId,
  createdAt: "2026-07-19T08:30:00Z",
  version: 0,
} satisfies HarvestBatchResponse;

export const publishedProducer = {
  harvestId,
  eventId,
  producer: "HARVEST",
  state: "PUBLISHED",
  createdAt: "2026-07-19T08:30:00Z",
  publishedAt: "2026-07-19T08:30:02Z",
  publishAttempts: 1,
} satisfies HarvestCompletionEventStatusResponse;

export const inventoryAcknowledged = {
  eventId,
  projection: "INVENTORY",
  state: "ACKNOWLEDGED",
  acknowledgedAt: "2026-07-19T08:30:03Z",
} satisfies InventoryHarvestProjectionAcknowledgementResponse;

export const inventoryPending = {
  ...inventoryAcknowledged,
  state: "NOT_ACKNOWLEDGED",
  acknowledgedAt: null,
} satisfies InventoryHarvestProjectionAcknowledgementResponse;

export const traceabilityAcknowledged = {
  eventId,
  projection: "TRACEABILITY",
  state: "ACKNOWLEDGED",
  acknowledgedAt: "2026-07-19T08:30:04Z",
} satisfies TraceabilityHarvestProjectionAcknowledgementResponse;

export const traceabilityPending = {
  ...traceabilityAcknowledged,
  state: "NOT_ACKNOWLEDGED",
  acknowledgedAt: null,
} satisfies TraceabilityHarvestProjectionAcknowledgementResponse;
