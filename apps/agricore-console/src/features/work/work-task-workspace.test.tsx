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

function detailPath(): string {
  return `/api/v1/crop-cycles/${cycleA.id}`;
}

describe("work-task workspace", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/crop-cycles/${cycleA.id}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("loads tasks with both authoritative cycle and plot scopes", async () => {
    const baseFetch = authenticatedFetch();
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath()) return jsonResponse(cycleA);
      if (url.pathname === "/api/v1/work-tasks") return jsonResponse(page([taskA]));
      return baseFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    expect(await screen.findByRole("heading", { name: "Công việc mùa vụ" })).toBeInTheDocument();
    expect(await screen.findByText(taskA.title)).toBeInTheDocument();
    expect(screen.getByText("Tưới nước", { selector: "dd" })).toBeInTheDocument();
    expect(screen.getByText("Đã phân công")).toBeInTheDocument();

    await waitFor(() => {
      const taskRequest = fetchMock.mock.calls
        .map(([input]) => requestUrl(input))
        .find((url) => url.pathname === "/api/v1/work-tasks");
      expect(taskRequest?.searchParams.get("cropCycleId")).toBe(cycleA.id);
      expect(taskRequest?.searchParams.get("plotId")).toBe(cycleA.plotId);
    });
  });

  it("shows a stable empty state without inventing work records", async () => {
    const baseFetch = authenticatedFetch();
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath()) return jsonResponse(cycleA);
      if (url.pathname === "/api/v1/work-tasks") return jsonResponse(page([]));
      return baseFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    expect(await screen.findByText("Chưa có công việc nào cho mùa vụ này.")).toBeInTheDocument();
  });

  it("returns to the cached previous page when a later page fails", async () => {
    const baseFetch = authenticatedFetch();
    let failedPageRequests = 0;
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath()) return jsonResponse(cycleA);
      if (url.pathname === "/api/v1/work-tasks") {
        if (url.searchParams.get("page") === "0") {
          return jsonResponse({
            ...page([taskA]),
            totalElements: 21,
            totalPages: 2,
            last: false,
          });
        }
        failedPageRequests += 1;
        return jsonResponse({
          status: 503,
          error: "Service Unavailable",
          code: "WORK_SERVICE_UNAVAILABLE",
          message: "temporarily unavailable",
          path: "/api/v1/work-tasks",
        }, 503);
      }
      return baseFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    expect(await screen.findByText(taskA.title)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Trang sau" }));
    expect(await screen.findByRole(
      "alert",
      { name: "Không thể tải công việc" },
      { timeout: 3_000 },
    )).toHaveTextContent("WORK_SERVICE_UNAVAILABLE");
    expect(failedPageRequests).toBe(2);

    fireEvent.click(screen.getByRole("button", { name: "Về trang trước" }));
    expect(await screen.findByText(taskA.title)).toBeInTheDocument();
  });

  it("recovers when the requested page no longer exists", async () => {
    const baseFetch = authenticatedFetch();
    let pageZeroRequests = 0;
    let pageOneRequests = 0;
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath()) return jsonResponse(cycleA);
      if (url.pathname === "/api/v1/work-tasks") {
        if (url.searchParams.get("page") === "1") {
          pageOneRequests += 1;
          return jsonResponse(page([], 1, 1));
        }
        pageZeroRequests += 1;
        return jsonResponse(page([taskA], 0, pageZeroRequests === 1 ? 2 : 1));
      }
      return baseFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    expect(await screen.findByText(taskA.title)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Trang sau" }));

    await waitFor(() => expect(pageOneRequests).toBeGreaterThan(0));
    await waitFor(() => expect(pageZeroRequests).toBeGreaterThan(1));
    expect(screen.getByText(taskA.title)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Trang sau" })).not.toBeInTheDocument();
  });

  it("keeps crop-cycle detail visible when work-service is unavailable", async () => {
    const baseFetch = authenticatedFetch();
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath()) return jsonResponse(cycleA);
      if (url.pathname === "/api/v1/work-tasks") {
        return jsonResponse({
          status: 503,
          error: "Service Unavailable",
          code: "FARM_ACCESS_UNAVAILABLE",
          message: "temporarily unavailable",
          path: "/api/v1/work-tasks",
        }, 503);
      }
      return baseFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    expect(await screen.findByRole("heading", { name: "Chi tiết mùa vụ" })).toBeInTheDocument();
    expect(await screen.findByRole(
      "alert",
      { name: "Không thể tải công việc" },
      { timeout: 3_000 },
    ))
      .toHaveTextContent("FARM_ACCESS_UNAVAILABLE");
    expect(screen.getByRole("button", { name: "Thử tải lại công việc" })).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([input]) =>
      requestUrl(input).pathname === "/api/v1/work-tasks",
    )).toHaveLength(2);
  });
});
