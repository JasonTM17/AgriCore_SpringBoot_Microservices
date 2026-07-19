import type { FarmStatus, PlotStatus } from "../../lib/api/types";

const hectares = new Intl.NumberFormat("vi-VN", {
  maximumFractionDigits: 2,
});

const farmStatusLabels: Record<FarmStatus, string> = {
  ACTIVE: "Đang hoạt động",
  INACTIVE: "Ngừng hoạt động",
  MAINTENANCE: "Bảo trì",
};

const plotStatusLabels: Record<PlotStatus, string> = {
  AVAILABLE: "Sẵn sàng",
  PREPARING: "Đang chuẩn bị",
  IN_USE: "Đang canh tác",
  RESTING: "Đang nghỉ đất",
  MAINTENANCE: "Bảo trì",
  INACTIVE: "Ngừng hoạt động",
};

export function formatArea(value: number | null): string {
  return value === null ? "Chưa cập nhật" : `${hectares.format(value)} ha`;
}

export function farmStatusLabel(status: FarmStatus): string {
  return farmStatusLabels[status];
}

export function plotStatusLabel(status: PlotStatus): string {
  return plotStatusLabels[status];
}
