import { onlineManager } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
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

function serviceError(status: 403 | 503) {
  const unavailable = status === 503;
  return jsonResponse({
    status,
    error: unavailable ? "Service Unavailable" : "Forbidden",
    code: unavailable ? "WORK_SERVICE_UNAVAILABLE" : "FARM_ACCESS_DENIED",
    message: unavailable ? "temporarily unavailable" : "access denied",
    path: workPath,
  }, status);
}

describe("work-task workspace resilience", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/crop-cycles/${cycleA.id}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    onlineManager.setOnline(true);
    vi.unstubAllGlobals();
  });

  it("lets the operator recover manually after the automatic retry", async () => {
    const baseFetch = authenticatedFetch();
    let workRequests = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === workPath) {
        workRequests += 1;
        return workRequests <= 2 ? serviceError(503) : jsonResponse(page([taskA]));
      }
      return baseFetch(input);
    }));

    render(<App />);

    const alert = await screen.findByRole("alert", { name: "Không thể tải công việc" }, { timeout: 3_000 });
    expect(alert).toHaveTextContent("WORK_SERVICE_UNAVAILABLE");
    expect(workRequests).toBe(2);
    fireEvent.click(screen.getByRole("button", { name: "Thử tải lại công việc" }));
    expect(await screen.findByText(taskA.title)).toBeInTheDocument();
    expect(workRequests).toBe(3);
  });

  it("hides cached tasks immediately when reconnect revalidation returns 403", async () => {
    const baseFetch = authenticatedFetch();
    let workRequests = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === workPath) {
        workRequests += 1;
        return workRequests === 1 ? jsonResponse(page([taskA])) : serviceError(403);
      }
      return baseFetch(input);
    }));

    render(<App />);
    expect(await screen.findByText(taskA.title)).toBeInTheDocument();

    act(() => onlineManager.setOnline(false));
    act(() => onlineManager.setOnline(true));

    expect(await screen.findByRole("alert", { name: "Không thể tải công việc" })).toHaveTextContent(
      "FARM_ACCESS_DENIED",
    );
    await waitFor(() => expect(screen.queryByText(taskA.title)).not.toBeInTheDocument());
    expect(workRequests).toBe(2);
  });

  it("does not reopen a cached page after access is denied on a later page", async () => {
    const baseFetch = authenticatedFetch();
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === workPath) {
        if (url.searchParams.get("page") === "1") return serviceError(403);
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

    expect(await screen.findByRole("alert", { name: "Không thể tải công việc" })).toHaveTextContent(
      "FARM_ACCESS_DENIED",
    );
    expect(screen.queryByText(taskA.title)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Về trang trước" })).not.toBeInTheDocument();
  });

  it("labels cached tasks as the last successful result during a transient outage", async () => {
    const baseFetch = authenticatedFetch();
    let workRequests = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === workPath) {
        workRequests += 1;
        return workRequests === 1 ? jsonResponse(page([taskA])) : serviceError(503);
      }
      return baseFetch(input);
    }));

    render(<App />);
    expect(await screen.findByText(taskA.title)).toBeInTheDocument();

    act(() => onlineManager.setOnline(false));
    act(() => onlineManager.setOnline(true));

    expect(await screen.findByRole(
      "alert",
      { name: "Không thể tải công việc" },
      { timeout: 3_000 },
    )).toHaveTextContent("Dữ liệu bên dưới là kết quả tải thành công gần nhất.");
    expect(screen.getByText(taskA.title)).toBeInTheDocument();
    expect(workRequests).toBe(3);
  });
});
