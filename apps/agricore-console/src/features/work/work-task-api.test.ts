import { describe, expect, it, vi } from "vitest";

import { ApiClient } from "../../lib/api/client";
import type {
  AssignTaskRequest,
  CompleteTaskRequest,
  CreateWorkTaskRequest,
} from "../../lib/api/types";
import {
  assignWorkTask,
  completeWorkTask,
  createWorkTask,
  getWorkTask,
  listWorkTasks,
  startWorkTask,
} from "./work-task-api";

type FetchFn = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

function jsonResponse(body: unknown): Promise<Response> {
  return Promise.resolve(new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  }));
}

function client(fetchImpl: FetchFn): ApiClient {
  return new ApiClient({
    getAccessToken: () => null,
    setAccessToken: () => undefined,
    fetchImpl,
  });
}

describe("work-task API", () => {
  it("always sends the authoritative cycle and plot scopes when listing", async () => {
    const fetchImpl: FetchFn = vi.fn(() => jsonResponse({ content: [] }));
    const signal = new AbortController().signal;

    await listWorkTasks(client(fetchImpl), {
      cropCycleId: "cycle/id",
      plotId: "plot id",
      page: 2,
      size: 20,
    }, signal);

    const [input, init] = vi.mocked(fetchImpl).mock.calls[0] ?? [];
    expect(input).toBe(
      "/api/v1/work-tasks?cropCycleId=cycle%2Fid&plotId=plot+id&page=2&size=20",
    );
    expect(init?.method).toBe("GET");
    expect(init?.signal).toBeInstanceOf(AbortSignal);
  });

  it("encodes the task ID for detail requests", async () => {
    const fetchImpl: FetchFn = vi.fn(() => jsonResponse({}));

    await getWorkTask(client(fetchImpl), "task/id?part");

    const [input, init] = vi.mocked(fetchImpl).mock.calls[0] ?? [];
    expect(input).toBe("/api/v1/work-tasks/task%2Fid%3Fpart");
    expect(init?.method).toBe("GET");
  });

  it("posts the typed create request unchanged", async () => {
    const fetchImpl: FetchFn = vi.fn(() => jsonResponse({}));
    const request: CreateWorkTaskRequest = {
      code: "TASK-001",
      cropCycleId: "50000000-0000-0000-0000-000000000001",
      plotId: "30000000-0000-0000-0000-000000000001",
      taskType: "IRRIGATION",
      title: "Tưới buổi sáng",
      description: null,
      priority: "HIGH",
      scheduledStart: "2026-07-20T01:00:00.000Z",
      scheduledEnd: "2026-07-20T02:00:00.000Z",
    };

    await createWorkTask(client(fetchImpl), request);

    const [input, init] = vi.mocked(fetchImpl).mock.calls[0] ?? [];
    expect(input).toBe("/api/v1/work-tasks");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBe(JSON.stringify(request));
  });

  it("encodes mutation IDs and forwards lifecycle requests", async () => {
    const fetchImpl: FetchFn = vi.fn(() => jsonResponse({}));
    const api = client(fetchImpl);
    const signal = new AbortController().signal;
    const assignRequest: AssignTaskRequest = {
      assignedEmployeeId: "10000000-0000-0000-0000-000000000009",
    };
    const completeRequest: CompleteTaskRequest = { notes: "Đã hoàn thành" };

    await assignWorkTask(api, "task/id", assignRequest, signal);
    await startWorkTask(api, "task/id", signal);
    await completeWorkTask(api, "task/id", completeRequest, signal);

    const [assignInput, assignInit] = vi.mocked(fetchImpl).mock.calls[0] ?? [];
    const [startInput, startInit] = vi.mocked(fetchImpl).mock.calls[1] ?? [];
    const [completeInput, completeInit] = vi.mocked(fetchImpl).mock.calls[2] ?? [];
    expect(assignInput).toBe("/api/v1/work-tasks/task%2Fid/assign");
    expect(assignInit?.method).toBe("POST");
    expect(assignInit?.body).toBe(JSON.stringify(assignRequest));
    expect(assignInit?.signal).toBeInstanceOf(AbortSignal);
    expect(startInput).toBe("/api/v1/work-tasks/task%2Fid/start");
    expect(startInit?.method).toBe("POST");
    expect(startInit?.body).toBeUndefined();
    expect(startInit?.signal).toBeInstanceOf(AbortSignal);
    expect(completeInput).toBe("/api/v1/work-tasks/task%2Fid/complete");
    expect(completeInit?.method).toBe("POST");
    expect(completeInit?.body).toBe(JSON.stringify(completeRequest));
    expect(completeInit?.signal).toBeInstanceOf(AbortSignal);
  });
});
