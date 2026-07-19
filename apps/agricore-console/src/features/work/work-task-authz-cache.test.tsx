import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "../../app/app";
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

function errorBody(status: number, code: string) {
  return {
    status,
    error: status === 403 ? "Forbidden" : "Bad Request",
    code,
    message: status === 403 ? "access denied" : "invalid request",
    path: workPath,
  };
}

describe("work-task authorization cache", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/crop-cycles/${cycleA.id}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("evicts cached cycle pages after a later page loses access", async () => {
    const baseFetch = authenticatedFetch();
    let pageZeroRequests = 0;
    let resolveReload!: (response: Response) => void;
    const pendingReload = new Promise<Response>((resolve) => {
      resolveReload = resolve;
    });
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === workPath) {
        if (url.searchParams.get("page") === "1") {
          return jsonResponse(errorBody(403, "FARM_ACCESS_DENIED"), 403);
        }
        pageZeroRequests += 1;
        if (pageZeroRequests > 1) return pendingReload;
        return jsonResponse({
          ...page([taskA]),
          totalElements: 21,
          totalPages: 2,
          last: false,
        });
      }
      return baseFetch(input);
    }));

    render(<App />);
    expect(await screen.findByText(taskA.title)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Trang sau" }));
    await screen.findByRole("alert", { name: "Không thể tải công việc" });

    fireEvent.click(screen.getByRole("link", { name: "← Quay lại danh sách mùa vụ" }));
    fireEvent.click(await screen.findByRole("link", { name: "Xem chi tiết" }));

    await waitFor(() => expect(pageZeroRequests).toBe(2));
    expect(screen.queryByText(taskA.title)).not.toBeInTheDocument();
    resolveReload(new Response(JSON.stringify(errorBody(403, "FARM_ACCESS_DENIED")), {
      status: 403,
      headers: { "Content-Type": "application/json" },
    }));
  });

  it("does not retry deterministic 400 responses", async () => {
    const baseFetch = authenticatedFetch();
    let workRequests = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === workPath) {
        workRequests += 1;
        return jsonResponse(errorBody(400, "INVALID_WORK_FILTER"), 400);
      }
      return baseFetch(input);
    }));

    render(<App />);

    expect(await screen.findByRole(
      "alert",
      { name: "Không thể tải công việc" },
      { timeout: 3_000 },
    )).toHaveTextContent("INVALID_WORK_FILTER");
    expect(workRequests).toBe(1);
  });
});
