import type { TaskStatus, TaskType } from "../../lib/api/types";

const taskTypeLabels: Record<TaskType, string> = {
  LAND_PREPARATION: "Chuẩn bị đất",
  SOWING: "Gieo trồng",
  IRRIGATION: "Tưới nước",
  FERTILIZING: "Bón phân",
  PESTICIDE: "Phun thuốc",
  PRUNING: "Cắt tỉa",
  INSPECTION: "Kiểm tra cây",
  PEST_CONTROL: "Xử lý sâu bệnh",
  HARVEST: "Thu hoạch",
  MAINTENANCE: "Bảo trì thiết bị",
};

const taskStatusLabels: Record<TaskStatus, string> = {
  CREATED: "Mới tạo",
  ASSIGNED: "Đã phân công",
  IN_PROGRESS: "Đang thực hiện",
  COMPLETED: "Đã hoàn thành",
  CANCELLED: "Đã hủy",
  OVERDUE: "Quá hạn",
};

const priorityLabels: Readonly<Record<string, string>> = {
  LOW: "Thấp",
  MEDIUM: "Trung bình",
  HIGH: "Cao",
  URGENT: "Khẩn cấp",
};

export const workTaskTypes = Object.keys(taskTypeLabels) as TaskType[];
export const workTaskPriorities = Object.keys(priorityLabels);

const taskInstantFormatter = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "short",
  timeStyle: "short",
});

export function formatTaskType(value: TaskType): string {
  return taskTypeLabels[value] ?? value;
}

export function formatTaskStatus(value: TaskStatus): string {
  return taskStatusLabels[value] ?? value;
}

export function formatTaskPriority(value: string): string {
  return priorityLabels[value] ?? value;
}

export function formatTaskInstant(value: string | null): string {
  if (!value) return "Chưa cập nhật";
  const instant = new Date(value);
  return Number.isNaN(instant.getTime()) ? "Không xác định" : taskInstantFormatter.format(instant);
}
