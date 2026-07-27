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
import { inProgressTaskA as taskA } from "./work-task-test-fixtures";

const detailPath = `/api/v1/crop-cycles/${cycleA.id}`;
const workPath = "/api/v1/work-tasks";
const notes = "Đã kiểm tra độ ẩm sau tưới.";

describe("work-task completion workspace", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/crop-cycles/${cycleA.id}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("completes the selected task and refreshes server state", async () => {
    const baseFetch = authenticatedFetch();
    const completedTask = {
      ...taskA,
      actualStart: "2026-07-20T01:15:00Z",
      actualEnd: "2026-07-20T02:10:00Z",
      status: "COMPLETED",
      notes,
      version: 2,
    } satisfies WorkTaskResponse;
    let completed = false;
    let postedBody: unknown;
    let completionRequests = 0;
    let completedWorkReads = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === `${workPath}/${taskA.id}/complete` && init?.method === "POST") {
        completionRequests += 1;
        if (typeof init.body !== "string") throw new Error("Expected a serialized completion body");
        postedBody = JSON.parse(init.body);
        completed = true;
        return jsonResponse(completedTask);
      }
      if (url.pathname === workPath) {
        if (completed) completedWorkReads += 1;
        return jsonResponse(page([completed ? completedTask : taskA]));
      }
      return baseFetch(input);
    }));

    render(<App />);
    expect(await screen.findByText(taskA.title)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(`Ghi chú hoàn tất cho ${taskA.code}`), {
      target: { value: ` ${notes} ` },
    });
    fireEvent.click(screen.getByRole("button", { name: `Xác nhận hoàn tất ${taskA.code}` }));

    await waitFor(() => expect(postedBody).toEqual({ notes }));
    await waitFor(() => expect(completedWorkReads).toBeGreaterThan(0), { timeout: 3_000 });
    expect(await screen.findByText(notes)).toBeInTheDocument();
    expect(screen.getByText("Đã hoàn thành")).toBeInTheDocument();
    expect(screen.getByRole("status", { name: "Hoàn tất công việc thành công" })).toHaveTextContent(taskA.code);
    expect(completionRequests).toBe(1);
  });

  it("keeps the notes draft and does not retry a cancelled-state conflict", async () => {
    const baseFetch = authenticatedFetch();
    let completionRequests = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === `${workPath}/${taskA.id}/complete` && init?.method === "POST") {
        completionRequests += 1;
        return jsonResponse({
          status: 409,
          error: "Conflict",
          code: "TASK_CANCELLED",
          message: "Cannot complete a cancelled task",
          path: `${workPath}/${taskA.id}/complete`,
        }, 409);
      }
      if (url.pathname === workPath) return jsonResponse(page([taskA]));
      return baseFetch(input);
    }));

    render(<App />);
    expect(await screen.findByText(taskA.title)).toBeInTheDocument();
    const input = screen.getByLabelText(`Ghi chú hoàn tất cho ${taskA.code}`);
    fireEvent.change(input, { target: { value: notes } });
    fireEvent.click(screen.getByRole("button", { name: `Xác nhận hoàn tất ${taskA.code}` }));

    expect(await screen.findByRole("alert", { name: "Không thể hoàn tất công việc" })).toHaveTextContent(
      "TASK_CANCELLED",
    );
    expect(input).toHaveValue(notes);
    expect(completionRequests).toBe(1);
  });
});
