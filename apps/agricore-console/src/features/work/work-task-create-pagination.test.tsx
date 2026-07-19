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
import { taskA } from "./work-task-test-fixtures";

const detailPath = `/api/v1/crop-cycles/${cycleA.id}`;
const workPath = "/api/v1/work-tasks";

describe("work-task creation pagination", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/crop-cycles/${cycleA.id}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("returns to and refreshes the first page after creating from a later page", async () => {
    const baseFetch = authenticatedFetch();
    const laterTask = {
      ...taskA,
      id: "60000000-0000-0000-0000-000000000002",
      code: "TASK-LATER-001",
      title: "Công việc trang hai",
    } satisfies WorkTaskResponse;
    const createdTask = {
      ...taskA,
      id: "60000000-0000-0000-0000-000000000003",
      code: "TASK-NEW-PAGE",
      taskType: "SOWING",
      title: "Gieo sau khi phân trang",
      assignedEmployeeId: null,
      status: "CREATED",
    } satisfies WorkTaskResponse;
    const requestedPages: string[] = [];
    let created = false;

    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === workPath && init?.method === "POST") {
        created = true;
        return jsonResponse(createdTask, 201);
      }
      if (url.pathname === workPath) {
        const requestedPage = url.searchParams.get("page") ?? "0";
        requestedPages.push(requestedPage);
        if (requestedPage === "1") return jsonResponse(page([laterTask], 1, 2));
        const firstPageTasks: WorkTaskResponse[] = created ? [createdTask] : [taskA];
        return jsonResponse(page(firstPageTasks, 0, created ? 1 : 2));
      }
      return baseFetch(input);
    }));

    render(<App />);
    expect(await screen.findByText(taskA.title)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Trang sau" }));
    expect(await screen.findByText(laterTask.title)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Mã công việc"), { target: { value: createdTask.code } });
    fireEvent.change(screen.getByLabelText("Loại công việc"), { target: { value: "SOWING" } });
    fireEvent.change(screen.getByLabelText("Tiêu đề công việc"), { target: { value: createdTask.title } });
    fireEvent.click(screen.getByRole("button", { name: "Tạo công việc" }));

    expect(await screen.findByText(createdTask.title)).toBeInTheDocument();
    expect(screen.queryByText(laterTask.title)).not.toBeInTheDocument();
    await waitFor(() => expect(requestedPages).toContain("0"));
    expect(requestedPages.slice(0, 2)).toEqual(["0", "1"]);
    expect(screen.queryByText("Trang 2 / 2")).not.toBeInTheDocument();
  });
});
