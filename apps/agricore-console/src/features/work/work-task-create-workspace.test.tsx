import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "../../app/app";
import type { WebAuthTokensResponse, WorkTaskResponse } from "../../lib/api/types";
import {
  authenticatedFetch,
  cycleA,
  jsonResponse,
  page,
  requestUrl,
} from "../crop-cycle/crop-cycle-test-fixtures";
import { taskA } from "./work-task-test-fixtures";

const detailPath = `/api/v1/crop-cycles/${cycleA.id}`;
const workPath = "/api/v1/work-tasks";

function fillCreateForm(code = "TASK-NEW-001") {
  fireEvent.change(screen.getByLabelText("Mã công việc"), { target: { value: code } });
  fireEvent.change(screen.getByLabelText("Loại công việc"), { target: { value: "SOWING" } });
  fireEvent.change(screen.getByLabelText("Tiêu đề công việc"), { target: { value: "Gieo khu A" } });
}

describe("work-task creation workspace", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/crop-cycles/${cycleA.id}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("creates with authoritative cycle and plot scopes, then refreshes the list", async () => {
    const baseFetch = authenticatedFetch();
    const createdTask = {
      ...taskA,
      code: "TASK-NEW-001",
      taskType: "SOWING",
      title: "Gieo khu A",
      assignedEmployeeId: null,
      status: "CREATED",
    } satisfies WorkTaskResponse;
    let created = false;
    let postedBody: unknown;
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === workPath && init?.method === "POST") {
        if (typeof init.body !== "string") throw new Error("Expected a serialized JSON request body");
        postedBody = JSON.parse(init.body);
        created = true;
        return jsonResponse(createdTask, 201);
      }
      if (url.pathname === workPath) return jsonResponse(page(created ? [createdTask] : []));
      return baseFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    await screen.findByRole("heading", { name: "Tạo công việc mới" });
    await screen.findByText("Chưa có công việc nào cho mùa vụ này.");
    fillCreateForm();
    fireEvent.change(screen.getByLabelText("Mô tả công việc"), { target: { value: " Gieo đúng mật độ " } });
    fireEvent.click(screen.getByRole("button", { name: "Tạo công việc" }));

    await waitFor(() => expect(postedBody).toEqual({
      code: "TASK-NEW-001",
      cropCycleId: cycleA.id,
      plotId: cycleA.plotId,
      taskType: "SOWING",
      title: "Gieo khu A",
      description: "Gieo đúng mật độ",
      priority: "MEDIUM",
      scheduledStart: null,
      scheduledEnd: null,
    }));
    expect(await screen.findByText(createdTask.title)).toBeInTheDocument();
    expect(screen.getByRole("status", { name: "Tạo công việc thành công" })).toHaveTextContent(
      "Đã tạo công việc TASK-NEW-001",
    );
    expect(screen.getByLabelText("Mã công việc")).toHaveValue("");
  });

  it("keeps the draft and does not retry a duplicate code conflict", async () => {
    const baseFetch = authenticatedFetch();
    let createRequests = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === workPath && init?.method === "POST") {
        createRequests += 1;
        return jsonResponse({
          status: 409,
          error: "Conflict",
          code: "TASK_CODE_EXISTS",
          message: "Task code already exists",
          path: workPath,
        }, 409);
      }
      if (url.pathname === workPath) return jsonResponse(page([]));
      return baseFetch(input);
    }));

    render(<App />);
    await screen.findByRole("heading", { name: "Tạo công việc mới" });
    await screen.findByText("Chưa có công việc nào cho mùa vụ này.");
    fillCreateForm("TASK-DUPLICATE");
    fireEvent.click(screen.getByRole("button", { name: "Tạo công việc" }));

    expect(await screen.findByRole("alert", { name: "Không thể tạo công việc" })).toHaveTextContent(
      "Mã công việc đã tồn tại",
    );
    expect(screen.getByLabelText("Mã công việc")).toHaveValue("TASK-DUPLICATE");
    expect(createRequests).toBe(1);
  });

  it("hides cached tasks after create loses access and can revalidate permissions", async () => {
    const baseFetch = authenticatedFetch();
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === workPath && init?.method === "POST") {
        return jsonResponse({
          status: 403,
          error: "Forbidden",
          code: "FARM_ACCESS_DENIED",
          message: "access denied",
          path: workPath,
        }, 403);
      }
      if (url.pathname === workPath) return jsonResponse(page([taskA]));
      return baseFetch(input);
    }));

    render(<App />);
    expect(await screen.findByText(taskA.title)).toBeInTheDocument();
    fillCreateForm("TASK-FORBIDDEN");
    fireEvent.click(screen.getByRole("button", { name: "Tạo công việc" }));

    await screen.findByRole("alert", { name: "Không thể tạo công việc" });
    expect(screen.queryByText(taskA.title)).not.toBeInTheDocument();
    const recover = screen.getByRole("button", { name: "Tải lại danh sách công việc" });
    expect(recover).toBeEnabled();
    fireEvent.click(recover);
    expect(await screen.findByText(taskA.title)).toBeInTheDocument();
  });

  it("does not offer creation to a field worker", async () => {
    const baseFetch = authenticatedFetch();
    const fieldWorkerSession = {
      accessToken: "field-worker-access-token",
      tokenType: "Bearer",
      expiresIn: 900,
      user: {
        id: "10000000-0000-0000-0000-000000000002",
        email: "worker@agricore.test",
        fullName: "Nhân viên hiện trường",
        status: "ACTIVE",
        roles: ["FIELD_WORKER"],
        lastLoginAt: null,
        createdAt: "2026-07-19T00:00:00Z",
      },
    } satisfies WebAuthTokensResponse;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === "/api/v1/auth/web/refresh") return jsonResponse(fieldWorkerSession);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === workPath) return jsonResponse(page([]));
      return baseFetch(input);
    }));

    render(<App />);

    await screen.findByRole("heading", { name: "Công việc mùa vụ" });
    expect(screen.queryByRole("heading", { name: "Tạo công việc mới" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Tạo công việc" })).not.toBeInTheDocument();
  });
});
