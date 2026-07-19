import type {
  HarvestCompletionEventStatusResponse,
  InventoryHarvestProjectionAcknowledgementResponse,
  TraceabilityHarvestProjectionAcknowledgementResponse,
} from "../../lib/api/types";

const weightFormatter = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 });
const dateTimeFormatter = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "Asia/Ho_Chi_Minh",
});

export function formatHarvestWeight(value: number): string {
  return `${weightFormatter.format(value)} kg`;
}

export function formatHarvestInstant(value: string | null): string {
  if (!value) return "Chưa có";
  const instant = new Date(value);
  return Number.isNaN(instant.getTime()) ? value : dateTimeFormatter.format(instant);
}

export function producerStateLabel(
  state: HarvestCompletionEventStatusResponse["state"],
): string {
  const labels = {
    UNAVAILABLE: "Bản ghi cũ không có event ID ổn định",
    ENQUEUED: "Đang chờ phát sự kiện",
    RETRYING: "Đang gửi lại sự kiện",
    PUBLISHED: "Đã phát sự kiện",
  } as const;
  return labels[state];
}

export function inventoryStateLabel(
  state: InventoryHarvestProjectionAcknowledgementResponse["state"],
): string {
  return state === "ACKNOWLEDGED" ? "Đã nhập kho" : "Đang chờ nhập kho";
}

export function traceabilityStateLabel(
  state: TraceabilityHarvestProjectionAcknowledgementResponse["state"],
): string {
  return state === "ACKNOWLEDGED" ? "Đã tạo lô truy xuất" : "Đang chờ lô truy xuất";
}
