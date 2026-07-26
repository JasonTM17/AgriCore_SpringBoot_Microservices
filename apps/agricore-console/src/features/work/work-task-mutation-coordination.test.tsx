import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "../../app/app";
import type { WorkTaskResponse } from "../../lib/api/types";
import {
  authenticatedFetch,
  cycleA,
  jsonResponse,
  page,
  requestUrl,
} from "../crop-cycle/crop-cycle-test-fixtures";
import { inProgressTaskA, taskA } from "./work-task-test-fixtures";

const detailPath = `/api/v1/crop-cycles/${cycleA.id}`;
const workPath = "/api/v1/work-tasks";
const replacementEmployeeId = "a0000000-b000-4000-8000-c00000000019";

describe("work-task mutation coordination", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/crop-cycles/${cycleA.id}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("disables create and assignment while completion is pending", async () => {
    const baseFetch = authenticatedFetch();
    const executionTask = {
      ...inProgressTaskA,
      id: "60000000-0000-0000-0000-000000000002",
      code: "TASK-IRR-002",
      title: "Tưới khu B buổi sáng",
    } satisfies WorkTaskResponse;
    const completedTask = {
      ...executionTask,
      actualEnd: "2026-07-20T02:10:00Z",
      status: "COMPLETED",
      notes: null,
      version: 3,
    } satisfies WorkTaskResponse;
    let resolveCompletion!: (response: Response) => void;
    const pendingCompletion = new Promise<Response>((resolve) => {
      resolveCompletion = resolve;
    });
    let completed = false;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === `${workPath}/${executionTask.id}/complete` && init?.method === "POST") {
        return pendingCompletion;
      }
      if (url.pathname === workPath) {
        return jsonResponse(page([taskA, completed ? completedTask : executionTask]));
      }
      return baseFetch(input);
    }));

    render(<App />);
    expect(await screen.findByText(taskA.title)).toBeInTheDocument();
    expect(await screen.findByText(executionTask.title)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(`ID nhân sự cho ${taskA.code}`), {
      target: { value: replacementEmployeeId },
    });
    fireEvent.change(screen.getByLabelText("Mã công việc"), { target: { value: "TASK-NEXT-001" } });
    fireEvent.change(screen.getByLabelText("Loại công việc"), { target: { value: "IRRIGATION" } });
    fireEvent.change(screen.getByLabelText("Tiêu đề công việc"), { target: { value: "Tưới khu kế tiếp" } });
    const assignButton = screen.getByRole("button", { name: `Xác nhận phân công ${taskA.code}` });
    const createButton = screen.getByRole("button", { name: "Tạo công việc" });
    expect(assignButton).toBeEnabled();
    expect(createButton).toBeEnabled();

    fireEvent.click(screen.getByRole("button", { name: `Xác nhận hoàn tất ${executionTask.code}` }));
    await waitFor(() => {
      expect(assignButton).toBeDisabled();
      expect(createButton).toBeDisabled();
    });

    completed = true;
    resolveCompletion(new Response(JSON.stringify(completedTask), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }));
    expect(await screen.findByText("Đã hoàn thành")).toBeInTheDocument();
  });
});
