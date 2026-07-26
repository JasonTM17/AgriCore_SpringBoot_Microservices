import type { WorkTaskResponse } from "../../lib/api/types";
import { cycleA } from "../crop-cycle/crop-cycle-test-fixtures";

export const taskA = {
  id: "60000000-0000-0000-0000-000000000001",
  code: "TASK-IRR-001",
  cropCycleId: cycleA.id,
  plotId: cycleA.plotId,
  taskType: "IRRIGATION",
  title: "Tưới khu A buổi sáng",
  description: "Kiểm tra độ ẩm trước khi tưới.",
  priority: "HIGH",
  assignedEmployeeId: "10000000-0000-0000-0000-000000000009",
  scheduledStart: "2026-07-20T01:00:00Z",
  scheduledEnd: "2026-07-20T02:00:00Z",
  actualStart: null,
  actualEnd: null,
  status: "ASSIGNED",
  notes: null,
  createdAt: "2026-07-19T00:00:00Z",
  version: 1,
  materials: [],
  attachments: [],
} satisfies WorkTaskResponse;

export const inProgressTaskA = {
  ...taskA,
  actualStart: "2026-07-20T01:15:00Z",
  status: "IN_PROGRESS",
  version: 2,
} satisfies WorkTaskResponse;
