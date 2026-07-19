import type { WorkTaskListParams } from "./work-task-api";

export const workTaskQueryKeys = {
  all: ["work-tasks"] as const,
  subject: (subject: string) => ["work-tasks", subject] as const,
  lists: (subject: string) => ["work-tasks", subject, "list"] as const,
  cycleLists: (subject: string, cropCycleId: string) =>
    ["work-tasks", subject, "list", cropCycleId] as const,
  list: (subject: string, params: WorkTaskListParams) => [
    "work-tasks",
    subject,
    "list",
    params.cropCycleId,
    params.plotId,
    params,
  ] as const,
  detail: (subject: string, taskId: string) =>
    ["work-tasks", subject, "detail", taskId] as const,
};
