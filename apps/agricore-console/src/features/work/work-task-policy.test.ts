import { describe, expect, it } from "vitest";

import type { TaskStatus } from "../../lib/api/types";
import { canAssignTask, canCompleteTask } from "./work-task-policy";

const activeStatuses: readonly TaskStatus[] = [
  "CREATED",
  "ASSIGNED",
  "IN_PROGRESS",
  "OVERDUE",
];

describe("work-task action policy", () => {
  it.each(activeStatuses)("allows assign and complete for backend-active status %s", (status) => {
    expect(canAssignTask(status)).toBe(true);
    expect(canCompleteTask(status)).toBe(true);
  });

  it("hides no-op or forbidden actions for terminal statuses", () => {
    expect(canAssignTask("COMPLETED")).toBe(false);
    expect(canAssignTask("CANCELLED")).toBe(false);
    expect(canCompleteTask("COMPLETED")).toBe(false);
    expect(canCompleteTask("CANCELLED")).toBe(false);
  });
});
