import type {
  HarvestBatchResponse,
  HarvestCompletionEventStatusResponse,
  InventoryHarvestProjectionAcknowledgementResponse,
  TraceabilityHarvestProjectionAcknowledgementResponse,
} from "../../lib/api/types";

export const harvestId = "60000000-0000-0000-0000-000000000001";
export const eventId = "61000000-0000-0000-0000-000000000001";

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
