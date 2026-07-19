import { describe, expect, it } from "vitest";

import type { TaskStatus, TaskType } from "../../lib/api/types";
import {
  formatTaskInstant,
  formatTaskStatus,
  formatTaskType,
} from "./work-task-formatters";

describe("work-task formatters", () => {
  it("keeps unknown server enum values visible", () => {
    expect(formatTaskType("NEW_TASK_TYPE" as TaskType)).toBe("NEW_TASK_TYPE");
    expect(formatTaskStatus("NEW_TASK_STATUS" as TaskStatus)).toBe("NEW_TASK_STATUS");
  });

  it("falls back safely when a timestamp is malformed", () => {
    expect(formatTaskInstant("not-a-timestamp")).toBe("Không xác định");
  });
});
