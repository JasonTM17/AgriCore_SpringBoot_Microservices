import type { TaskStatus } from "../../lib/api/types";

export function canAssignTask(status: TaskStatus): boolean {
  return status === "CREATED" || status === "ASSIGNED" || status === "OVERDUE";
}

export function canStartTask(status: TaskStatus): boolean {
  return status === "ASSIGNED" || status === "OVERDUE";
}

export function canCompleteTask(status: TaskStatus): boolean {
  return status === "IN_PROGRESS";
}
