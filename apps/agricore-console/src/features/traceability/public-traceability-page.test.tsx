import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "../../app/app";
import {
  anonymousRefresh,
  jsonResponse,
  publicTraceability,
  requestUrl,
  traceabilityCode,
} from "./public-traceability-test-fixtures";

const publicPath = `/public/api/v1/traceability/${traceabilityCode}`;

describe("public traceability page", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/public/traceability/${traceabilityCode.toLowerCase()}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("renders public-safe provenance without starting session bootstrap", async () => {
    let refreshRequests = 0;
    let publicInit: RequestInit | undefined;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = requestUrl(input).pathname;
      if (path === "/api/v1/auth/web/refresh") {
        refreshRequests += 1;
        return anonymousRefresh();
      }
      if (path === publicPath) {
        publicInit = init;
        return jsonResponse({
          ...publicTraceability,
          harvestBatchId: "private-harvest-id",
          employeeId: "private-employee-id",
          internalCost: 12_500_000,
        });
      }
      return jsonResponse({}, 404);
    }));

    render(<App />);

    expect(await screen.findByRole("heading", { name: publicTraceability.productName })).toBeInTheDocument();
    expect(screen.getByText(publicTraceability.batchLabel)).toBeInTheDocument();
    expect(screen.getByRole("img", { name: `Mã QR truy xuất ${traceabilityCode}` }))
      .toHaveAttribute("src", publicTraceability.qrImageUrl);
    expect(screen.getByText("Nông trại Đắk Lắk")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Hành trình sản phẩm" })).toBeInTheDocument();
    expect(screen.getAllByText("15/03/2026")).toHaveLength(2);
    expect(document.body).not.toHaveTextContent("harvestBatchId");
    expect(document.body).not.toHaveTextContent("employeeId");
    expect(document.body).not.toHaveTextContent("internalCost");
    expect(document.body).not.toHaveTextContent(publicTraceability.qrUrl);
    expect(new Headers(publicInit?.headers).has("Authorization")).toBe(false);
    expect(publicInit?.credentials).toBe("omit");
    expect(refreshRequests).toBe(0);
  });

  it("explains a delayed or unknown projection and retries manually", async () => {
    let publicRequests = 0;
    let refreshRequests = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const path = requestUrl(input).pathname;
      if (path === "/api/v1/auth/web/refresh") {
        refreshRequests += 1;
        return anonymousRefresh();
      }
      if (path === publicPath) {
        publicRequests += 1;
        return publicRequests === 1
          ? jsonResponse({
              status: 404,
              error: "Not Found",
              code: "TRACEABILITY_NOT_FOUND",
              message: "not ready",
              path,
            }, 404)
          : jsonResponse(publicTraceability);
      }
      return jsonResponse({}, 404);
    }));

    render(<App />);

    const delayed = await screen.findByRole("alert", { name: "Chưa có dữ liệu truy xuất" });
    expect(delayed).toHaveTextContent("đang được đồng bộ");
    expect(publicRequests).toBe(1);
    fireEvent.click(screen.getByRole("button", { name: "Kiểm tra lại" }));

    expect(await screen.findByRole("heading", { name: publicTraceability.productName })).toBeInTheDocument();
    expect(publicRequests).toBe(2);
    expect(refreshRequests).toBe(0);
    expect(window.location.pathname).toBe(`/public/traceability/${traceabilityCode.toLowerCase()}`);
  });

  it("renders nullable public fields without inventing provenance", async () => {
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const path = requestUrl(input).pathname;
      if (path === "/api/v1/auth/web/refresh") return anonymousRefresh();
      if (path === publicPath) {
        return jsonResponse({
          ...publicTraceability,
          varietyName: null,
          farmName: null,
          plotCode: null,
          plantingDate: null,
          qualityGrade: null,
          netWeightKg: null,
          careSummary: null,
        });
      }
      return jsonResponse({}, 404);
    }));

    render(<App />);

    await screen.findByRole("heading", { name: publicTraceability.productName });
    expect(screen.getAllByText("Chưa công bố").length).toBeGreaterThanOrEqual(5);
    expect(document.body).not.toHaveTextContent("Invalid Date");
    expect(document.body).not.toHaveTextContent("NaN");
  });

  it("rejects an oversized route code before calling the public API", async () => {
    const oversizedCode = "A".repeat(65);
    const oversizedPath = `/public/api/v1/traceability/${oversizedCode}`;
    window.history.pushState({}, "", `/public/traceability/${oversizedCode}`);
    let oversizedRequests = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const path = requestUrl(input).pathname;
      if (path === "/api/v1/auth/web/refresh") return anonymousRefresh();
      if (path === oversizedPath) oversizedRequests += 1;
      return jsonResponse({}, 404);
    }));

    render(<App />);

    expect(await screen.findByRole("alert", { name: "Mã truy xuất không hợp lệ" })).toBeInTheDocument();
    await waitFor(() => expect(oversizedRequests).toBe(0));
  });
});
