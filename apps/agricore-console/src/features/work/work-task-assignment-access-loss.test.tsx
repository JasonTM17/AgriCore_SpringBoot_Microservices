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
const employeeId = "10000000-0000-0000-0000-000000000019";
const unassignedTask = {
  ...taskA,
  assignedEmployeeId: null,
  status: "CREATED",
  version: 0,
} satisfies WorkTaskResponse;

function assignCurrentTask() {
  fireEvent.change(screen.getByLabelText(`ID nhân sự cho ${unassignedTask.code}`), {
    target: { value: employeeId },
  });
  fireEvent.click(screen.getByRole("button", {
    name: `Xác nhận phân công ${unassignedTask.code}`,
  }));
}

describe("work-task assignment access loss", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/crop-cycles/${cycleA.id}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("hides cached tasks after assignment loses access and can revalidate permissions", async () => {
    const baseFetch = authenticatedFetch();
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === `${workPath}/${unassignedTask.id}/assign` && init?.method === "POST") {
        return jsonResponse({
          status: 403,
          error: "Forbidden",
          code: "FARM_ACCESS_DENIED",
          message: "access denied",
          path: `${workPath}/${unassignedTask.id}/assign`,
        }, 403);
      }
      if (url.pathname === workPath) return jsonResponse(page([unassignedTask]));
      return baseFetch(input);
    }));

    render(<App />);
    expect(await screen.findByText(unassignedTask.title)).toBeInTheDocument();
    assignCurrentTask();

    expect(await screen.findByRole("alert", { name: "Không thể tải công việc" })).toHaveTextContent(
      "FARM_ACCESS_DENIED",
    );
    expect(screen.queryByText(unassignedTask.title)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Thử tải lại công việc" }));
    expect(await screen.findByText(unassignedTask.title)).toBeInTheDocument();
  });

  it("removes assignment success when list revalidation loses access", async () => {
    const baseFetch = authenticatedFetch();
    const assignedTask = {
      ...unassignedTask,
      assignedEmployeeId: employeeId,
      status: "ASSIGNED",
      version: 1,
    } satisfies WorkTaskResponse;
    let assignmentCompleted = false;
    let denyListReads = false;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === `${workPath}/${unassignedTask.id}/assign` && init?.method === "POST") {
        assignmentCompleted = true;
        denyListReads = true;
        return jsonResponse(assignedTask);
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
        return jsonResponse(page([assignmentCompleted ? assignedTask : unassignedTask]));
      }
      return baseFetch(input);
    }));

    render(<App />);
    expect(await screen.findByText(unassignedTask.title)).toBeInTheDocument();
    assignCurrentTask();

    await screen.findByRole("alert", { name: "Không thể tải công việc" });
    expect(screen.queryByRole("status", { name: "Phân công công việc thành công" })).not.toBeInTheDocument();

    denyListReads = false;
    fireEvent.click(screen.getByRole("button", { name: "Thử tải lại công việc" }));
    expect(await screen.findByText(employeeId, { selector: "span" })).toBeInTheDocument();
    expect(screen.queryByRole("status", { name: "Phân công công việc thành công" })).not.toBeInTheDocument();
  });
});
