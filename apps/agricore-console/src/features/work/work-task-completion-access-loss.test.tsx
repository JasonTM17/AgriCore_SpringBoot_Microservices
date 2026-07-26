import { fireEvent, render, screen } from "@testing-library/react";
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
const notes = "Đã hoàn tất tại hiện trường.";

function completeCurrentTask() {
  fireEvent.change(screen.getByLabelText(`Ghi chú hoàn tất cho ${taskA.code}`), {
    target: { value: notes },
  });
  fireEvent.click(screen.getByRole("button", { name: `Xác nhận hoàn tất ${taskA.code}` }));
}

describe("work-task completion access loss", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/crop-cycles/${cycleA.id}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("hides cached tasks after completion loses access and can revalidate permissions", async () => {
    const baseFetch = authenticatedFetch();
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === `${workPath}/${taskA.id}/complete` && init?.method === "POST") {
        return jsonResponse({
          status: 403,
          error: "Forbidden",
          code: "FARM_ACCESS_DENIED",
          message: "access denied",
          path: `${workPath}/${taskA.id}/complete`,
        }, 403);
      }
      if (url.pathname === workPath) return jsonResponse(page([taskA]));
      return baseFetch(input);
    }));

    render(<App />);
    expect(await screen.findByText(taskA.title)).toBeInTheDocument();
    completeCurrentTask();

    expect(await screen.findByRole("alert", { name: "Không thể tải công việc" })).toHaveTextContent(
      "FARM_ACCESS_DENIED",
    );
    expect(screen.queryByText(taskA.title)).not.toBeInTheDocument();
    expect(screen.queryByText(taskA.code)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Thử tải lại công việc" }));
    expect(await screen.findByText(taskA.title)).toBeInTheDocument();
  });

  it("removes completion success when list revalidation loses access", async () => {
    const baseFetch = authenticatedFetch();
    const completedTask = {
      ...taskA,
      actualEnd: "2026-07-20T02:10:00Z",
      status: "COMPLETED",
      notes,
      version: 2,
    } satisfies WorkTaskResponse;
    let completionFinished = false;
    let denyListReads = false;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === `${workPath}/${taskA.id}/complete` && init?.method === "POST") {
        completionFinished = true;
        denyListReads = true;
        return jsonResponse(completedTask);
      }
      if (url.pathname === workPath && denyListReads) {
        return jsonResponse({
          status: 403,
          error: "Forbidden",
          code: "FARM_ACCESS_DENIED",
          message: "access denied",
          path: workPath,
        }, 403);
      }
      if (url.pathname === workPath) {
        return jsonResponse(page([completionFinished ? completedTask : taskA]));
      }
      return baseFetch(input);
    }));

    render(<App />);
    expect(await screen.findByText(taskA.title)).toBeInTheDocument();
    completeCurrentTask();

    await screen.findByRole("alert", { name: "Không thể tải công việc" });
    expect(screen.queryByRole("status", { name: "Hoàn tất công việc thành công" })).not.toBeInTheDocument();

    denyListReads = false;
    fireEvent.click(screen.getByRole("button", { name: "Thử tải lại công việc" }));
    expect(await screen.findByText(notes)).toBeInTheDocument();
    expect(screen.queryByRole("status", { name: "Hoàn tất công việc thành công" })).not.toBeInTheDocument();
  });
});
