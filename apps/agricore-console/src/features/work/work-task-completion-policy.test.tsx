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

describe("work-task completion policy", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/crop-cycles/${cycleA.id}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("offers completion but not management actions to a field worker", async () => {
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
        permissions: [],
        lastLoginAt: null,
        createdAt: "2026-07-19T00:00:00Z",
      },
    } satisfies WebAuthTokensResponse;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === "/api/v1/auth/web/refresh") return jsonResponse(fieldWorkerSession);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === workPath) return jsonResponse(page([taskA]));
      return baseFetch(input);
    }));

    render(<App />);

    expect(await screen.findByText(taskA.title)).toBeInTheDocument();
    expect(screen.getByLabelText(`Ghi chú hoàn tất cho ${taskA.code}`)).toBeInTheDocument();
    expect(screen.queryByLabelText(`ID nhân sự cho ${taskA.code}`)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Tạo công việc" })).not.toBeInTheDocument();
  });

  it.each(["COMPLETED", "CANCELLED"] as const)(
    "does not offer completion for a %s task",
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
      expect(screen.queryByLabelText(`Ghi chú hoàn tất cho ${terminalTask.code}`)).not.toBeInTheDocument();
    },
  );
});
