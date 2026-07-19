import { render, screen } from "@testing-library/react";
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
const unassignedTask = {
  ...taskA,
  assignedEmployeeId: null,
  status: "CREATED",
  version: 0,
} satisfies WorkTaskResponse;

describe("work-task assignment policy", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/crop-cycles/${cycleA.id}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("does not offer assignment to a field worker", async () => {
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
      if (url.pathname === workPath) return jsonResponse(page([unassignedTask]));
      return baseFetch(input);
    }));

    render(<App />);

    expect(await screen.findByText(unassignedTask.title)).toBeInTheDocument();
    expect(screen.queryByLabelText(`ID nhân sự cho ${unassignedTask.code}`)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", {
      name: `Xác nhận phân công ${unassignedTask.code}`,
    })).not.toBeInTheDocument();
  });

  it.each(["COMPLETED", "CANCELLED"] as const)(
    "does not offer assignment for a %s task",
    async (status) => {
      const baseFetch = authenticatedFetch();
      const terminalTask = {
        ...taskA,
        status,
        actualEnd: "2026-07-20T02:00:00Z",
      } satisfies WorkTaskResponse;
      vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
        const url = requestUrl(input);
        if (url.pathname === detailPath) return jsonResponse(cycleA);
        if (url.pathname === workPath) return jsonResponse(page([terminalTask]));
        return baseFetch(input);
      }));

      render(<App />);

      expect(await screen.findByText(terminalTask.title)).toBeInTheDocument();
      expect(screen.queryByLabelText(`ID nhân sự cho ${terminalTask.code}`)).not.toBeInTheDocument();
    },
  );
});
