import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "../../app/app";
import { authenticatedFetch, crops, jsonResponse, page, requestUrl } from "./crop-test-fixtures";

describe("Crops page", () => {
  beforeEach(() => {
    window.history.pushState({}, "", "/crops");
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("loads the real catalog and sends explicit search filters", async () => {
    const fetchMock = authenticatedFetch();
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByText("Cà phê Robusta")).toBeInTheDocument();
    const initialUrl = fetchMock.mock.calls
      .map(([input]) => requestUrl(input))
      .find((url) => url.pathname === "/api/v1/crops");
    expect(initialUrl?.searchParams.get("page")).toBe("0");
    expect(initialUrl?.searchParams.get("size")).toBe("20");

    fireEvent.change(screen.getByLabelText("Tìm theo tên"), { target: { value: "cà phê" } });
    fireEvent.change(screen.getByLabelText("Mã danh mục"), { target: { value: "PERENNIAL" } });
    fireEvent.click(screen.getByRole("button", { name: "Lọc danh mục" }));

    await waitFor(() => {
      const urls = fetchMock.mock.calls
        .map(([input]) => requestUrl(input))
        .filter((url) => url.pathname === "/api/v1/crops");
      expect(urls.some((url) => url.searchParams.get("q") === "cà phê" && url.searchParams.get("category") === "PERENNIAL")).toBe(true);
    });
  });

  it("keeps a recoverable shell for an unavailable catalog", async () => {
    let unavailable = true;
    const successFetch = authenticatedFetch();
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      if (requestUrl(input).pathname === "/api/v1/crops" && unavailable) {
        return jsonResponse(
          {
            timestamp: "2026-07-19T00:00:00Z",
            status: 503,
            error: "Service Unavailable",
            code: "CROP_CATALOG_UNAVAILABLE",
            message: "catalog unavailable",
            path: "/api/v1/crops",
          },
          503,
        );
      }
      return successFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByRole("heading", { name: "Danh mục cây trồng", level: 1 })).toBeVisible();
    expect(await screen.findByRole("alert", {}, { timeout: 3_000 })).toHaveTextContent(
      "Không thể tải danh mục cây trồng",
    );
    unavailable = false;
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("Cà phê Robusta")).toBeInTheDocument();
  });

  it("offers a previous-page recovery action when a nonzero page is empty", async () => {
    const successFetch = authenticatedFetch();
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === "/api/v1/crops") {
        return url.searchParams.get("page") === "1" ? jsonResponse(page([], 1, 2)) : jsonResponse(page(crops, 0, 2));
      }
      return successFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByText("Cà phê Robusta")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Sau" }));
    expect(await screen.findByText("Trang danh mục này không còn dữ liệu")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Về trang trước" }));
    expect(await screen.findByText("Cà phê Robusta")).toBeInTheDocument();
  });
});
