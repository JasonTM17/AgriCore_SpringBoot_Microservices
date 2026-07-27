import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "../../app/app";
import {
  authenticatedFetch,
  cycleA,
  cycleB,
  farmA,
  farmB,
  jsonResponse,
  page,
  requestUrl,
} from "./crop-cycle-test-fixtures";

describe("Crop cycle authorization race", () => {
  beforeEach(() => {
    window.history.pushState({}, "", "/crop-cycles");
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("recovers the same farm after reauthorization without retrying the denial", async () => {
    let accessRestored = false;
    let deniedCycleRequests = 0;
    const baseFetch = authenticatedFetch();
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === "/api/v1/farms") {
        return jsonResponse(page([farmA]));
      }
      if (url.pathname === `/api/v1/farms/${farmA.id}`) return jsonResponse(farmA);
      if (url.pathname === "/api/v1/crop-cycles") {
        if (url.searchParams.get("farmId") === farmA.id) {
          if (accessRestored) return jsonResponse(page([cycleA]));
          deniedCycleRequests += 1;
          return jsonResponse({
            timestamp: "2026-07-19T00:00:00Z",
            status: 403,
            error: "Forbidden",
            code: "FARM_ACCESS_DENIED",
            message: "farm membership revoked",
            path: url.pathname,
          }, 403);
        }
        return jsonResponse({}, 404);
      }
      return baseFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByRole("alert", {}, { timeout: 3_000 })).toHaveTextContent(
      "Nông trại đã không còn trong phạm vi truy cập",
    );
    expect(deniedCycleRequests).toBe(1);
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();

    accessRestored = true;
    fireEvent.click(screen.getByRole("button", { name: "Tải lại phạm vi" }));

    expect(await screen.findByText(cycleA.code)).toBeInTheDocument();
    await waitFor(() => {
      expect(fetchMock.mock.calls
        .map(([input]) => requestUrl(input))
        .filter((url) => url.pathname === "/api/v1/crop-cycles" && url.searchParams.get("farmId") === farmA.id))
        .toHaveLength(2);
    });
    expect(deniedCycleRequests).toBe(1);
  });

  it("switches to a refreshed farm without retrying the denied farm", async () => {
    let scopeReloaded = false;
    let deniedCycleRequests = 0;
    const baseFetch = authenticatedFetch();
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === "/api/v1/farms") {
        return jsonResponse(page([scopeReloaded ? farmB : farmA]));
      }
      if (url.pathname === `/api/v1/farms/${farmA.id}`) return jsonResponse(farmA);
      if (url.pathname === `/api/v1/farms/${farmB.id}`) return jsonResponse(farmB);
      if (url.pathname === "/api/v1/crop-cycles") {
        if (url.searchParams.get("farmId") === farmA.id) {
          deniedCycleRequests += 1;
          return jsonResponse({}, 403);
        }
        return jsonResponse(page([cycleB]));
      }
      return baseFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByRole("alert", {}, { timeout: 3_000 })).toHaveTextContent(
      "Nông trại đã không còn trong phạm vi truy cập",
    );
    expect(deniedCycleRequests).toBe(1);

    scopeReloaded = true;
    fireEvent.click(screen.getByRole("button", { name: "Tải lại phạm vi" }));

    expect(await screen.findByText(cycleB.code)).toBeInTheDocument();
    expect(deniedCycleRequests).toBe(1);
  });
});
