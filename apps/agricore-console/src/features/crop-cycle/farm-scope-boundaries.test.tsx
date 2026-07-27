import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "../../app/app";
import type { CropCycleResponse, FarmResponse } from "../../lib/api/types";
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

function farm(index: number): FarmResponse {
  const suffix = String(index).padStart(12, "0");
  return {
    ...farmA,
    id: `21000000-0000-0000-0000-${suffix}`,
    code: `FARM-${String(index).padStart(2, "0")}`,
    name: `Nông trại ${index}`,
  };
}

function farmPage(content: FarmResponse[], pageNumber: number) {
  return {
    content,
    page: pageNumber,
    size: 20,
    totalElements: 21,
    totalPages: 2,
    first: pageNumber === 0,
    last: pageNumber === 1,
  };
}

describe("Crop cycle farm-scope boundaries", () => {
  beforeEach(() => {
    window.history.pushState({}, "", "/crop-cycles");
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("selects an authorized farm beyond the first 20 memberships", async () => {
    const farms = Array.from({ length: 21 }, (_, index) => farm(index + 1));
    const farmsById = new Map(farms.map((item) => [item.id, item]));
    const baseFetch = authenticatedFetch();
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === "/api/v1/farms") {
        const farmPageNumber = Number(url.searchParams.get("page") ?? "0");
        return jsonResponse(farmPage(farmPageNumber === 0 ? farms.slice(0, 20) : farms.slice(20), farmPageNumber));
      }
      if (url.pathname.startsWith("/api/v1/farms/")) {
        const requestedFarm = farmsById.get(url.pathname.split("/").at(-1) ?? "");
        return requestedFarm ? jsonResponse(requestedFarm) : jsonResponse({}, 404);
      }
      if (url.pathname === "/api/v1/crop-cycles") {
        const farmId = url.searchParams.get("farmId") ?? "";
        const cycle = {
          ...cycleB,
          farmId,
          code: `CYCLE-${farmsById.get(farmId)?.code ?? "UNKNOWN"}`,
        } satisfies CropCycleResponse;
        return jsonResponse(page([cycle]));
      }
      return baseFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByText("CYCLE-FARM-01", {}, { timeout: 3_000 })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Phân trang nông trại: trang sau" }));
    fireEvent.change(await screen.findByLabelText("Nông trại đang hoạt động"), {
      target: { value: farms[20]?.id },
    });

    expect(await screen.findByText("CYCLE-FARM-21", {}, { timeout: 3_000 })).toBeInTheDocument();
    expect(fetchMock.mock.calls
      .map(([input]) => requestUrl(input))
      .some((url) => url.pathname === "/api/v1/crop-cycles" && url.searchParams.get("farmId") === farms[20]?.id))
      .toBe(true);
  });

  it("blocks a revoked farm before cycle access and reloads an authorized scope", async () => {
    let scopeReloaded = false;
    const baseFetch = authenticatedFetch();
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === "/api/v1/farms") {
        return jsonResponse(page([scopeReloaded ? farmB : farmA]));
      }
      if (url.pathname === `/api/v1/farms/${farmA.id}`) {
        return jsonResponse({
          timestamp: "2026-07-19T00:00:00Z",
          status: 404,
          error: "Not Found",
          code: "FARM_NOT_FOUND",
          message: "farm membership revoked",
          path: url.pathname,
        }, 404);
      }
      if (url.pathname === `/api/v1/farms/${farmB.id}`) return jsonResponse(farmB);
      if (url.pathname === "/api/v1/crop-cycles") return jsonResponse(page([cycleB]));
      return baseFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByRole("alert", {}, { timeout: 3_000 })).toHaveTextContent(
      "Nông trại đã không còn trong phạm vi truy cập",
    );
    expect(fetchMock.mock.calls
      .map(([input]) => requestUrl(input))
      .some((url) => url.pathname === "/api/v1/crop-cycles" && url.searchParams.get("farmId") === farmA.id))
      .toBe(false);

    scopeReloaded = true;
    fireEvent.click(screen.getByRole("button", { name: "Tải lại phạm vi" }));

    expect(await screen.findByText(cycleB.code)).toBeInTheDocument();
    await waitFor(() => {
      expect(fetchMock.mock.calls
        .map(([input]) => requestUrl(input))
        .some((url) => url.pathname === "/api/v1/crop-cycles" && url.searchParams.get("farmId") === farmB.id))
        .toBe(true);
    });
  });

  it("returns to the last available page after farm memberships shrink", async () => {
    let pageZeroRequests = 0;
    const baseFetch = authenticatedFetch();
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === "/api/v1/farms") {
        const pageNumber = Number(url.searchParams.get("page") ?? "0");
        if (pageNumber === 0) {
          pageZeroRequests += 1;
          return pageZeroRequests === 1
            ? jsonResponse(farmPage([farmA], 0))
            : jsonResponse(page([farmA]));
        }
        return jsonResponse({
          content: [],
          page: 1,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          first: false,
          last: true,
        });
      }
      return baseFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByText(cycleA.code, {}, { timeout: 3_000 })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Phân trang nông trại: trang sau" }));

    await waitFor(() => expect(pageZeroRequests).toBeGreaterThan(1), { timeout: 3_000 });
    expect(screen.getByLabelText("Nông trại đang hoạt động")).toHaveValue(farmA.id);
    expect(screen.queryByRole("button", { name: "Phân trang nông trại: trang sau" }))
      .not.toBeInTheDocument();
  });
});
