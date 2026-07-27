import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "../../app/app";
import type { UserResponse } from "../../lib/api/types";
import {
  authenticatedFetch,
  cycleA,
  farmA,
  farmB,
  jsonResponse,
  page,
  requestUrl,
} from "../crop-cycle/crop-cycle-test-fixtures";
import { harvestPlot } from "./harvest-test-fixtures";

const fieldWorker = {
  id: "10000000-0000-0000-0000-000000000009",
  email: "worker@agricore.test",
  fullName: "Nhân viên hiện trường",
  status: "ACTIVE",
  roles: ["FIELD_WORKER"],
  permissions: [],
  lastLoginAt: null,
  createdAt: "2026-07-19T00:00:00Z",
} satisfies UserResponse;

describe("harvest page access boundaries", () => {
  beforeEach(() => {
    window.history.pushState({}, "", "/harvests");
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("offers receipt lookup but no completion workflow to a read-only role", async () => {
    const baseFetch = authenticatedFetch();
    const requestedPaths: string[] = [];
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = requestUrl(input).pathname;
      requestedPaths.push(path);
      if (path === "/api/v1/auth/web/refresh") {
        const response = await baseFetch(input);
        const session = await response.json() as Record<string, unknown>;
        return jsonResponse({ ...session, user: fieldWorker });
      }
      return baseFetch(input);
    }));

    render(<App />);

    expect(await screen.findByRole("heading", { name: "Thu hoạch & đồng bộ" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Mở biên nhận đã có" })).toBeInTheDocument();
    expect(screen.getByText("Vai trò hiện tại chỉ được xem biên nhận đã có.")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Hoàn tất thu hoạch" })).not.toBeInTheDocument();
    await waitFor(() => expect(requestedPaths).toContain("/api/v1/farms"));
    expect(requestedPaths).not.toContain("/api/v1/crop-cycles");
  });

  it("validates active-farm access before loading cycles or enabling completion", async () => {
    const baseFetch = authenticatedFetch();
    let cycleRequests = 0;
    let farmListRequests = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const path = requestUrl(input).pathname;
      if (path === "/api/v1/farms") {
        farmListRequests += 1;
        if (farmListRequests > 1) return jsonResponse(page([farmB]));
      }
      if (path === `/api/v1/farms/${farmA.id}`) {
        return jsonResponse({
          status: 403,
          error: "Forbidden",
          code: "FARM_ACCESS_DENIED",
          message: "denied",
          path,
        }, 403);
      }
      if (path === "/api/v1/crop-cycles") cycleRequests += 1;
      return baseFetch(input);
    }));

    render(<App />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Nông trại không còn trong phạm vi truy cập",
    );
    expect(screen.queryByRole("heading", { name: "Hoàn tất thu hoạch" })).not.toBeInTheDocument();
    expect(cycleRequests).toBe(0);

    fireEvent.click(screen.getByRole("button", { name: "Tải lại phạm vi" }));
    expect(await screen.findByRole("heading", { name: "Hoàn tất thu hoạch" })).toBeInTheDocument();
    expect(await screen.findByLabelText("Mùa vụ")).toHaveValue("");
    expect(cycleRequests).toBe(1);
  });

  it("rejects a plot response that crosses the validated farm scope", async () => {
    const baseFetch = authenticatedFetch();
    let completionRequests = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = requestUrl(input).pathname;
      if (path === `/api/v1/plots/${harvestPlot.id}`) {
        return jsonResponse({ ...harvestPlot, farmId: "20000000-0000-0000-0000-000000000099" });
      }
      if (path === "/api/v1/harvests/complete" && init?.method === "POST") {
        completionRequests += 1;
      }
      return baseFetch(input);
    }));

    render(<App />);
    await screen.findByRole("heading", { name: "Hoàn tất thu hoạch" });
    fireEvent.change(await screen.findByLabelText("Mùa vụ"), { target: { value: cycleA.id } });

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Lô đất không khớp với mùa vụ và nông trại đã xác minh",
    );
    expect(screen.getByRole("button", { name: "Hoàn tất và tạo biên nhận" })).toBeDisabled();
    expect(completionRequests).toBe(0);
  });
});
