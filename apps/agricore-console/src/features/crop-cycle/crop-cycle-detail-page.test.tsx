import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "../../app/app";
import type { UserResponse } from "../../lib/api/types";
import {
  authenticatedFetch,
  cycleA,
  jsonResponse,
  requestUrl,
} from "./crop-cycle-test-fixtures";

const fieldWorker: UserResponse = {
  id: "10000000-0000-0000-0000-000000000009",
  email: "worker@agricore.test",
  fullName: "Nhân viên hiện trường",
  status: "ACTIVE",
  roles: ["FIELD_WORKER"],
  lastLoginAt: null,
  createdAt: "2026-07-19T00:00:00Z",
};

function detailPath(): string {
  return `/api/v1/crop-cycles/${cycleA.id}`;
}

function stagePath(): string {
  return `${detailPath()}/stage`;
}

describe("Crop cycle detail page", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/crop-cycles/${cycleA.id}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("loads the authoritative detail and exposes legal stage actions", async () => {
    const baseFetch = authenticatedFetch();
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      if (requestUrl(input).pathname === detailPath()) return jsonResponse(cycleA);
      return baseFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    expect(await screen.findByRole("heading", { name: "Chi tiết mùa vụ" })).toBeInTheDocument();
    expect(screen.getByText(cycleA.code, { selector: "p" })).toBeInTheDocument();
    expect(screen.getByText("Phiên bản 0")).toBeInTheDocument();
    expect(screen.getByLabelText("Giai đoạn tiếp theo")).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Đang thu hoạch" })).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input]) => requestUrl(input).pathname === detailPath())).toBe(true);
  });

  it("sends one legal stage mutation and updates from the server response", async () => {
    const baseFetch = authenticatedFetch();
    const updated = { ...cycleA, stage: "HARVESTING", version: 1 } as const;
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.pathname === stagePath() && init?.method === "POST") return jsonResponse(updated);
      if (url.pathname === detailPath()) return jsonResponse(cycleA);
      return baseFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    await screen.findByRole("heading", { name: "Chi tiết mùa vụ" });
    fireEvent.change(screen.getByLabelText("Giai đoạn tiếp theo"), { target: { value: "HARVESTING" } });
    fireEvent.click(screen.getByRole("button", { name: "Cập nhật giai đoạn" }));

    await waitFor(() => expect(screen.getByText("Đang thu hoạch")).toBeInTheDocument());
    const stageCalls = fetchMock.mock.calls.filter(([input, init]) =>
      requestUrl(input).pathname === stagePath() && init?.method === "POST",
    );
    expect(stageCalls).toHaveLength(1);
    expect(stageCalls[0]?.[1]?.body).toBe(JSON.stringify({ stage: "HARVESTING", notes: null }));
  });

  it("requires explicit confirmation before cancelling a crop cycle", async () => {
    const baseFetch = authenticatedFetch();
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      void init;
      if (requestUrl(input).pathname === detailPath()) return jsonResponse(cycleA);
      return baseFetch(input);
    });
    const confirmMock = vi.fn(() => false);
    vi.stubGlobal("fetch", fetchMock);
    vi.stubGlobal("confirm", confirmMock);

    render(<App />);
    await screen.findByRole("heading", { name: "Chi tiết mùa vụ" });
    fireEvent.change(screen.getByLabelText("Giai đoạn tiếp theo"), { target: { value: "CANCELLED" } });
    fireEvent.click(screen.getByRole("button", { name: "Cập nhật giai đoạn" }));

    expect(confirmMock).toHaveBeenCalledOnce();
    expect(fetchMock.mock.calls.some(([input, init]) =>
      requestUrl(input).pathname === stagePath() && init?.method === "POST",
    )).toBe(false);
  });

  it("does not offer mutation controls to a read-only role", async () => {
    const baseFetch = authenticatedFetch();
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === "/api/v1/auth/web/refresh") {
        return baseFetch(input).then(async (response) => {
          const session = await response.json() as Record<string, unknown>;
          return jsonResponse({ ...session, user: fieldWorker });
        });
      }
      if (url.pathname === detailPath()) return jsonResponse(cycleA);
      return baseFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    expect(await screen.findByText(/Tài khoản của bạn chỉ có quyền xem/)).toBeInTheDocument();
    expect(screen.queryByLabelText("Giai đoạn tiếp theo")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cập nhật giai đoạn" })).not.toBeInTheDocument();
  });

  it("turns a revoked detail scope into an unavailable state", async () => {
    const baseFetch = authenticatedFetch();
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath()) {
        return jsonResponse({ status: 403, error: "Forbidden", code: "FARM_ACCESS_DENIED", message: "denied", path: detailPath() }, 403);
      }
      return baseFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    expect(await screen.findByRole("alert")).toHaveTextContent("không còn quyền truy cập");
    expect(screen.queryByLabelText("Giai đoạn tiếp theo")).not.toBeInTheDocument();
  });
});
