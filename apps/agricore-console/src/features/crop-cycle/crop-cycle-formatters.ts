import type { CycleStage, CycleStatus } from "../../lib/api/types";

const dateFormatter = new Intl.DateTimeFormat("vi-VN", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  timeZone: "UTC",
});

const stageLabels: Record<CycleStage, string> = {
  PLANNED: "Đã lên kế hoạch",
  LAND_PREPARATION: "Chuẩn bị đất",
  SOWING: "Gieo trồng",
  GROWING: "Sinh trưởng",
  FERTILIZING: "Bón phân",
  PEST_CONTROL: "Kiểm soát sâu bệnh",
  HARVESTING: "Đang thu hoạch",
  COMPLETED: "Đã hoàn tất",
  CANCELLED: "Đã hủy",
};

const statusLabels: Record<CycleStatus, string> = {
  DRAFT: "Bản nháp",
  ACTIVE: "Đang hoạt động",
  COMPLETED: "Đã hoàn tất",
  CANCELLED: "Đã hủy",
};

export function cycleStageLabel(stage: CycleStage): string {
  return stageLabels[stage];
}

export function cycleStatusLabel(status: CycleStatus): string {
  return statusLabels[status];
}

export function formatCycleDate(value: string | null): string {
  if (!value) {
    return "Chưa cập nhật";
  }
  return dateFormatter.format(new Date(`${value}T00:00:00Z`));
}

export function formatCycleDateRange(start: string, end: string | null): string {
  return `${formatCycleDate(start)} – ${formatCycleDate(end)}`;
}

export function shortResourceId(value: string): string {
  return value.slice(-8).toUpperCase();
}
