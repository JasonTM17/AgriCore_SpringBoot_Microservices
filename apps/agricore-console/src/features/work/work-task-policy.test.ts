import { describe, expect, it } from "vitest";

import type { TaskStatus } from "../../lib/api/types";
import { canAssignTask, canCompleteTask, canStartTask } from "./work-task-policy";

const allStatuses: readonly TaskStatus[] = [
  "CREATED", "ASSIGNED", "IN_PROGRESS", "COMPLETED", "CANCELLED", "OVERDUE",
];

describe("work-task action policy", () => {
  it.each(allStatuses)("matches the backend lifecycle for %s", (status) => {
    expect(canAssignTask(status)).toBe(
      status === "CREATED" || status === "ASSIGNED" || status === "OVERDUE",
    );
    expect(canStartTask(status)).toBe(status === "ASSIGNED" || status === "OVERDUE");
    expect(canCompleteTask(status)).toBe(status === "IN_PROGRESS");
  });
});
