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
const employeeId = "10000000-0000-0000-0000-000000000019";
const unassignedTask = {
  ...taskA,
  assignedEmployeeId: null,
  status: "CREATED",
  version: 0,
} satisfies WorkTaskResponse;

describe("work-task assignment workspace", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/crop-cycles/${cycleA.id}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("assigns the selected task and refreshes server state", async () => {
    const baseFetch = authenticatedFetch();
    const assignedTask = {
      ...unassignedTask,
      assignedEmployeeId: employeeId,
      status: "ASSIGNED",
      version: 1,
    } satisfies WorkTaskResponse;
    let assigned = false;
    let postedBody: unknown;
    let assignmentRequests = 0;
    let assignedWorkReads = 0;

    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === `${workPath}/${unassignedTask.id}/assign`) {
        assignmentRequests += 1;
        if (typeof init?.body !== "string") throw new Error("Expected a serialized assignment body");
        postedBody = JSON.parse(init.body);
        assigned = true;
        return jsonResponse(assignedTask);
      }
      if (url.pathname === workPath) {
        if (assigned) assignedWorkReads += 1;
        return jsonResponse(page([assigned ? assignedTask : unassignedTask]));
      }
      return baseFetch(input);
    }));

    render(<App />);
    expect(await screen.findByText(unassignedTask.title)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(`ID nhân sự cho ${unassignedTask.code}`), {
      target: { value: employeeId },
    });
    fireEvent.click(screen.getByRole("button", { name: `Xác nhận phân công ${unassignedTask.code}` }));

    await waitFor(() => expect(postedBody).toEqual({ assignedEmployeeId: employeeId }));
    await waitFor(() => expect(assignedWorkReads).toBeGreaterThan(0), { timeout: 3_000 });
    expect(await screen.findByText(employeeId, { selector: "span" })).toBeInTheDocument();
    expect(screen.getByRole("status", { name: "Phân công công việc thành công" })).toHaveTextContent(
      unassignedTask.code,
    );
    expect(assignmentRequests).toBe(1);
  });

  it("keeps the employee draft and does not retry a terminal-state conflict", async () => {
    const baseFetch = authenticatedFetch();
    let assignmentRequests = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === `${workPath}/${unassignedTask.id}/assign`) {
        assignmentRequests += 1;
        return jsonResponse({
          status: 409,
          error: "Conflict",
          code: "TASK_TERMINAL",
          message: "Cannot assign a terminal task",
          path: `${workPath}/${unassignedTask.id}/assign`,
        }, 409);
      }
      if (url.pathname === workPath) return jsonResponse(page([unassignedTask]));
      return baseFetch(input);
    }));

    render(<App />);
    expect(await screen.findByText(unassignedTask.title)).toBeInTheDocument();
    const input = screen.getByLabelText(`ID nhân sự cho ${unassignedTask.code}`);
    fireEvent.change(input, { target: { value: employeeId } });
    fireEvent.click(screen.getByRole("button", { name: `Xác nhận phân công ${unassignedTask.code}` }));

    expect(await screen.findByRole("alert", { name: "Không thể phân công công việc" })).toHaveTextContent(
      "TASK_TERMINAL",
    );
    expect(input).toHaveValue(employeeId);
    expect(assignmentRequests).toBe(1);
  });
});
