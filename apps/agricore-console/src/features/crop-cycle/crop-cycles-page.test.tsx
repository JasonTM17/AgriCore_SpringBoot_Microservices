import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "../../app/app";
import {
  authenticatedFetch,
  cycleA,
  farmA,
  farmB,
  jsonResponse,
  page,
  requestUrl,
} from "./crop-cycle-test-fixtures";

describe("Crop cycles page", () => {
  beforeEach(() => {
    window.history.pushState({}, "", "/crop-cycles");
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("loads cycles for the active farm and switches authoritative farm scope", async () => {
    const fetchMock = authenticatedFetch();
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByText("CYCLE-1")).toBeInTheDocument();
    const initialUrl = fetchMock.mock.calls
      .map(([input]) => requestUrl(input))
      .find((url) => url.pathname === "/api/v1/crop-cycles");
    expect(initialUrl?.searchParams.get("farmId")).toBe(farmA.id);
    expect(initialUrl?.searchParams.get("page")).toBe("0");
    expect(initialUrl?.searchParams.get("size")).toBe("20");

    fireEvent.change(screen.getByLabelText("Nông trại đang hoạt động"), {
      target: { value: farmB.id },
    });

    expect(await screen.findByText("CYCLE-2")).toBeInTheDocument();
    await waitFor(() => {
      expect(fetchMock.mock.calls
        .map(([input]) => requestUrl(input))
        .some((url) => url.pathname === "/api/v1/crop-cycles" && url.searchParams.get("farmId") === farmB.id))
        .toBe(true);
    });
  });

  it("shows membership guidance and never requests cycles without a farm", async () => {
    const fetchMock = authenticatedFetch(page([]));
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByText("Chưa có nông trại được cấp quyền")).toBeInTheDocument();
    expect(fetchMock.mock.calls
      .map(([input]) => requestUrl(input))
      .some((url) => url.pathname === "/api/v1/crop-cycles"))
      .toBe(false);
  });

  it("recovers the farm scope before requesting crop cycles", async () => {
    let unavailable = true;
    const successFetch = authenticatedFetch();
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      if (requestUrl(input).pathname === "/api/v1/farms" && unavailable) {
        return jsonResponse({
          timestamp: "2026-07-19T00:00:00Z",
          status: 503,
          error: "Service Unavailable",
          code: "FARM_SERVICE_UNAVAILABLE",
          message: "farm service unavailable",
          path: "/api/v1/farms",
        }, 503);
      }
      return successFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByRole("alert", {}, { timeout: 3_000 })).toHaveTextContent(
      "Không thể tải phạm vi nông trại",
    );
    expect(fetchMock.mock.calls
      .map(([input]) => requestUrl(input))
      .some((url) => url.pathname === "/api/v1/crop-cycles"))
      .toBe(false);
    unavailable = false;
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText(cycleA.code)).toBeInTheDocument();
  });

  it("keeps the selected farm and retries a temporary crop-cycle failure", async () => {
    let unavailable = true;
    const successFetch = authenticatedFetch();
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      if (requestUrl(input).pathname === "/api/v1/crop-cycles" && unavailable) {
        return jsonResponse({
          timestamp: "2026-07-19T00:00:00Z",
          status: 503,
          error: "Service Unavailable",
          code: "FARM_ACCESS_UNAVAILABLE",
          message: "farm access unavailable",
          path: "/api/v1/crop-cycles",
        }, 503);
      }
      return successFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByRole("alert", {}, { timeout: 3_000 })).toHaveTextContent(
      "Không thể tải danh sách mùa vụ",
    );
    expect(screen.getByLabelText("Nông trại đang hoạt động")).toHaveValue(farmA.id);
    unavailable = false;
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText(cycleA.code)).toBeInTheDocument();
  });

  it("recovers from an empty nonzero cycle page", async () => {
    const successFetch = authenticatedFetch();
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === "/api/v1/crop-cycles") {
        return url.searchParams.get("page") === "1"
          ? jsonResponse(page([], 1, 2))
          : jsonResponse(page([cycleA], 0, 2));
      }
      return successFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByText(cycleA.code)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Sau" }));
    expect(await screen.findByText("Trang mùa vụ này không còn dữ liệu")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Về trang trước" }));
    expect(await screen.findByText(cycleA.code)).toBeInTheDocument();
  });
});
