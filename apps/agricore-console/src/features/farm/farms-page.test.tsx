import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "../../app/app";
import { authenticatedFetch, jsonResponse, page, requestUrl } from "./farm-test-fixtures";

describe("Farms page", () => {
  beforeEach(() => {
    window.history.pushState({}, "", "/farms");
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("loads accessible farms and switches the active farm's real plots", async () => {
    const fetchMock = authenticatedFetch();
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByText("Lô cà phê A01")).toBeInTheDocument();
    const farmUrl = fetchMock.mock.calls
      .map(([input]) => requestUrl(input))
      .find((url) => url.pathname === "/api/v1/farms");
    expect(farmUrl?.searchParams.get("page")).toBe("0");
    expect(farmUrl?.searchParams.get("size")).toBe("20");
    expect(farmUrl?.searchParams.get("sort")).toBe("code,asc");

    fireEvent.click(screen.getByRole("button", { name: /FARM-LD-01.*Nông trại Lâm Đồng/i }));

    expect(await screen.findByText("Lô chè B01")).toBeInTheDocument();
    expect(screen.getByText("Nông trại đang hoạt động")).toBeInTheDocument();
    const plotUrls = fetchMock.mock.calls
      .map(([input]) => requestUrl(input))
      .filter((url) => url.pathname.endsWith("/plots"));
    expect(plotUrls).toHaveLength(2);
    expect(plotUrls.every((url) => url.searchParams.get("page") === "0")).toBe(true);
    expect(plotUrls.every((url) => url.searchParams.get("size") === "20")).toBe(true);
  });

  it("shows a stable empty state without requesting plots", async () => {
    const fetchMock = authenticatedFetch(page([]));
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByText("Chưa có nông trại được cấp quyền")).toBeInTheDocument();
    await waitFor(() => {
      expect(
        fetchMock.mock.calls.some(([input]) => requestUrl(input).pathname.endsWith("/plots")),
      ).toBe(false);
    });
  });

  it("keeps the page shell and offers retry when farms are unavailable", async () => {
    const successFetch = authenticatedFetch();
    let farmAttempts = 0;
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      if (requestUrl(input).pathname === "/api/v1/farms" && farmAttempts++ < 2) {
        return jsonResponse(
          {
            timestamp: "2026-07-19T00:00:00Z",
            status: 503,
            error: "Service Unavailable",
            code: "FARM_SERVICE_UNAVAILABLE",
            message: "Farm service unavailable",
            path: "/api/v1/farms",
          },
          503,
        );
      }
      return successFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByRole("heading", { name: "Nông trại & lô canh tác" })).toBeVisible();
    expect(await screen.findByRole("alert", {}, { timeout: 3_000 })).toHaveTextContent(
      "Không thể tải danh sách nông trại",
    );
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeEnabled();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("Lô cà phê A01")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
