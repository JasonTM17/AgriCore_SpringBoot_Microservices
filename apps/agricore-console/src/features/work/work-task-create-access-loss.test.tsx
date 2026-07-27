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
import { taskA } from "./work-task-test-fixtures";

const detailPath = `/api/v1/crop-cycles/${cycleA.id}`;
const workPath = "/api/v1/work-tasks";

describe("work-task creation access loss", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/crop-cycles/${cycleA.id}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("removes the local success message when list revalidation loses access", async () => {
    const baseFetch = authenticatedFetch();
    const createdTask = {
      ...taskA,
      code: "TASK-ACCESS-LOSS",
      taskType: "SOWING",
      title: "Gieo trước khi mất quyền",
      assignedEmployeeId: null,
      status: "CREATED",
    } satisfies WorkTaskResponse;
    let denyListReads = false;

    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === workPath && init?.method === "POST") {
        denyListReads = true;
        return jsonResponse(createdTask, 201);
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
      if (url.pathname === workPath) return jsonResponse(page([]));
      return baseFetch(input);
    }));

    render(<App />);
    await screen.findByText("Chưa có công việc nào cho mùa vụ này.");
    fireEvent.change(screen.getByLabelText("Mã công việc"), { target: { value: createdTask.code } });
    fireEvent.change(screen.getByLabelText("Loại công việc"), { target: { value: "SOWING" } });
    fireEvent.change(screen.getByLabelText("Tiêu đề công việc"), { target: { value: createdTask.title } });
    fireEvent.click(screen.getByRole("button", { name: "Tạo công việc" }));

    expect(await screen.findByRole("alert", { name: "Không thể tải công việc" })).toHaveTextContent(
      "FARM_ACCESS_DENIED",
    );
    expect(screen.queryByRole("status", { name: "Tạo công việc thành công" })).not.toBeInTheDocument();
    expect(screen.queryByText(createdTask.code)).not.toBeInTheDocument();

    denyListReads = false;
    fireEvent.click(screen.getByRole("button", { name: "Thử tải lại công việc" }));
    await screen.findByText("Chưa có công việc nào cho mùa vụ này.");
    expect(screen.queryByRole("status", { name: "Tạo công việc thành công" })).not.toBeInTheDocument();
    expect(screen.queryByText(createdTask.code)).not.toBeInTheDocument();
  });
});
