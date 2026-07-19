import type { ApiClient } from "../../lib/api/client";
import type {
  AssignTaskRequest,
  CompleteTaskRequest,
  CreateWorkTaskRequest,
  WorkTaskPageResponse,
  WorkTaskResponse,
} from "../../lib/api/types";

export interface WorkTaskListParams {
  cropCycleId: string;
  plotId: string;
  page: number;
  size: number;
}

function queryString(values: ReadonlyArray<readonly [string, string | number]>): string {
  const search = new URLSearchParams();
  values.forEach(([key, value]) => search.set(key, String(value)));
  return search.toString();
}

export function listWorkTasks(
  api: ApiClient,
  params: WorkTaskListParams,
  signal?: AbortSignal,
): Promise<WorkTaskPageResponse> {
  const search = queryString([
    ["cropCycleId", params.cropCycleId],
    ["plotId", params.plotId],
    ["page", params.page],
    ["size", params.size],
  ]);
  return api.request<WorkTaskPageResponse>(`/api/v1/work-tasks?${search}`, {
    method: "GET",
    ...(signal ? { signal } : {}),
  });
}

export function getWorkTask(
  api: ApiClient,
  taskId: string,
  signal?: AbortSignal,
): Promise<WorkTaskResponse> {
  return api.request<WorkTaskResponse>(`/api/v1/work-tasks/${encodeURIComponent(taskId)}`, {
    method: "GET",
    ...(signal ? { signal } : {}),
  });
}

export function createWorkTask(
  api: ApiClient,
  request: CreateWorkTaskRequest,
  signal?: AbortSignal,
): Promise<WorkTaskResponse> {
  return api.request<WorkTaskResponse>("/api/v1/work-tasks", {
    method: "POST",
    body: request,
    ...(signal ? { signal } : {}),
  });
}

export function assignWorkTask(
  api: ApiClient,
  taskId: string,
  request: AssignTaskRequest,
  signal?: AbortSignal,
): Promise<WorkTaskResponse> {
  return api.request<WorkTaskResponse>(
    `/api/v1/work-tasks/${encodeURIComponent(taskId)}/assign`,
    {
      method: "POST",
      body: request,
      ...(signal ? { signal } : {}),
    },
  );
}

export function completeWorkTask(
  api: ApiClient,
  taskId: string,
  request: CompleteTaskRequest,
  signal?: AbortSignal,
): Promise<WorkTaskResponse> {
  return api.request<WorkTaskResponse>(
    `/api/v1/work-tasks/${encodeURIComponent(taskId)}/complete`,
    {
      method: "POST",
      body: request,
      ...(signal ? { signal } : {}),
    },
  );
}
