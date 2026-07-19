import type { TaskStatus } from "../../lib/api/types";

function isTerminal(status: TaskStatus): boolean {
  return status === "COMPLETED" || status === "CANCELLED";
}

export function canAssignTask(status: TaskStatus): boolean {
  return !isTerminal(status);
}

export function canCompleteTask(status: TaskStatus): boolean {
  return !isTerminal(status);
}
