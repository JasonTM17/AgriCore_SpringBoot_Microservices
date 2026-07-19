import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "../../app/app";
import {
  authenticatedFetch,
  cycleA,
  farmA,
  jsonResponse,
  requestUrl,
} from "../crop-cycle/crop-cycle-test-fixtures";
import {
  harvestBatch,
  harvestPlot,
  inventoryAcknowledged,
  publishedProducer,
  traceabilityAcknowledged,
} from "./harvest-test-fixtures";

const completePath = "/api/v1/harvests/complete";
const plotPath = `/api/v1/plots/${harvestPlot.id}`;
const receiptPath = `/api/v1/harvests/${harvestBatch.id}`;

function fillRequiredFields(): void {
  fireEvent.change(screen.getByLabelText("Mã thu hoạch"), { target: { value: " HARVEST-001 " } });
  fireEvent.change(screen.getByLabelText("Kho nhận hàng (UUID)"), { target: { value: harvestBatch.warehouseId } });
  fireEvent.change(screen.getByLabelText("Mã sản phẩm"), { target: { value: " COFFEE-ROBUSTA " } });
  fireEvent.change(screen.getByLabelText("Khối lượng thô (kg)"), { target: { value: "3500" } });
  fireEvent.change(screen.getByLabelText("Khối lượng thực (kg)"), { target: { value: "3300" } });
  fireEvent.change(screen.getByLabelText("Phân loại chất lượng"), { target: { value: " GRADE_A " } });
}

describe("harvest completion page", () => {
  beforeEach(() => {
    window.history.pushState({}, "", "/harvests");
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("derives farm and plot metadata from APIs, completes once, then opens the receipt", async () => {
    const baseFetch = authenticatedFetch();
    let postedBody: unknown;
    let completionRequests = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = requestUrl(input).pathname;
      if (path === plotPath) return jsonResponse(harvestPlot);
      if (path === completePath && init?.method === "POST") {
        completionRequests += 1;
        if (typeof init.body !== "string") throw new Error("Expected serialized harvest body");
        postedBody = JSON.parse(init.body) as unknown;
        return jsonResponse(harvestBatch, 201);
      }
      if (path === receiptPath) return jsonResponse(harvestBatch);
      if (path === `${receiptPath}/completion-event`) return jsonResponse(publishedProducer);
      if (path.includes("/inventory/events/harvest-completed/")) return jsonResponse(inventoryAcknowledged);
      if (path.includes("/traceability/events/harvest-completed/")) return jsonResponse(traceabilityAcknowledged);
      return baseFetch(input);
    }));

    render(<App />);
    expect(await screen.findByRole("heading", { name: "Hoàn tất thu hoạch" })).toBeInTheDocument();
    fireEvent.change(await screen.findByLabelText("Mùa vụ"), { target: { value: cycleA.id } });
    expect(await screen.findByText(`${harvestPlot.code} · ${harvestPlot.name}`)).toBeInTheDocument();
    fillRequiredFields();
    fireEvent.click(screen.getByRole("button", { name: "Hoàn tất và tạo biên nhận" }));

    await waitFor(() => expect(postedBody).toEqual({
      code: harvestBatch.code,
      cropCycleId: cycleA.id,
      plotId: cycleA.plotId,
      warehouseId: harvestBatch.warehouseId,
      productCode: harvestBatch.productCode,
      grossWeightKg: harvestBatch.grossWeightKg,
      netWeightKg: harvestBatch.netWeightKg,
      qualityGrade: harvestBatch.qualityGrade,
      notes: null,
      farmName: farmA.name,
      plotCode: harvestPlot.code,
      productName: null,
      careSummary: null,
    }));
    expect(completionRequests).toBe(1);
    expect(await screen.findByRole("heading", { name: `Biên nhận ${harvestBatch.code}` }))
      .toBeInTheDocument();
    expect(window.location.pathname).toBe(`/harvests/${harvestBatch.id}`);
  });

  it("blocks net weight above gross weight before calling the API", async () => {
    const baseFetch = authenticatedFetch();
    let completionRequests = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = requestUrl(input).pathname;
      if (path === plotPath) return jsonResponse(harvestPlot);
      if (path === completePath && init?.method === "POST") completionRequests += 1;
      return baseFetch(input);
    }));

    render(<App />);
    await screen.findByRole("heading", { name: "Hoàn tất thu hoạch" });
    fireEvent.change(await screen.findByLabelText("Mùa vụ"), { target: { value: cycleA.id } });
    await screen.findByText(`${harvestPlot.code} · ${harvestPlot.name}`);
    fillRequiredFields();
    fireEvent.change(screen.getByLabelText("Khối lượng thô (kg)"), { target: { value: "100" } });
    fireEvent.change(screen.getByLabelText("Khối lượng thực (kg)"), { target: { value: "110" } });
    fireEvent.click(screen.getByRole("button", { name: "Hoàn tất và tạo biên nhận" }));

    expect(await screen.findByText("Khối lượng thực không được lớn hơn khối lượng thô."))
      .toBeInTheDocument();
    expect(completionRequests).toBe(0);
  });

  it("keeps the draft and does not retry a duplicate-code conflict", async () => {
    const baseFetch = authenticatedFetch();
    let completionRequests = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = requestUrl(input).pathname;
      if (path === plotPath) return jsonResponse(harvestPlot);
      if (path === completePath && init?.method === "POST") {
        completionRequests += 1;
        return jsonResponse({
          status: 409,
          error: "Conflict",
          code: "HARVEST_CODE_EXISTS",
          message: "exists",
          path,
        }, 409);
      }
      return baseFetch(input);
    }));

    render(<App />);
    await screen.findByRole("heading", { name: "Hoàn tất thu hoạch" });
    fireEvent.change(await screen.findByLabelText("Mùa vụ"), { target: { value: cycleA.id } });
    await screen.findByText(`${harvestPlot.code} · ${harvestPlot.name}`);
    fillRequiredFields();
    fireEvent.click(screen.getByRole("button", { name: "Hoàn tất và tạo biên nhận" }));

    expect(await screen.findByRole("alert", { name: "Không thể hoàn tất thu hoạch" }))
      .toHaveTextContent("Mã thu hoạch đã tồn tại");
    expect(screen.getByLabelText("Mã thu hoạch")).toHaveValue(" HARVEST-001 ");
    expect(completionRequests).toBe(1);
  });

  it("preserves the draft and fails closed when farm access is revoked during completion", async () => {
    const baseFetch = authenticatedFetch();
    let accessRevoked = false;
    let completionRequests = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = requestUrl(input).pathname;
      if (path === `/api/v1/farms/${farmA.id}` && accessRevoked) {
        return jsonResponse({
          status: 403,
          error: "Forbidden",
          code: "FARM_ACCESS_DENIED",
          message: "denied",
          path,
        }, 403);
      }
      if (path === plotPath) return jsonResponse(harvestPlot);
      if (path === completePath && init?.method === "POST") {
        completionRequests += 1;
        accessRevoked = true;
        return jsonResponse({
          status: 403,
          error: "Forbidden",
          code: "FARM_ACCESS_DENIED",
          message: "denied",
          path,
        }, 403);
      }
      return baseFetch(input);
    }));

    render(<App />);
    await screen.findByRole("heading", { name: "Hoàn tất thu hoạch" });
    fireEvent.change(await screen.findByLabelText("Mùa vụ"), { target: { value: cycleA.id } });
    await screen.findByText(`${harvestPlot.code} · ${harvestPlot.name}`);
    fillRequiredFields();
    fireEvent.click(screen.getByRole("button", { name: "Hoàn tất và tạo biên nhận" }));

    expect(await screen.findByRole("alert", { name: "Không thể hoàn tất thu hoạch" }))
      .toHaveTextContent("Quyền truy cập đã thay đổi");
    expect(screen.getByLabelText("Mã thu hoạch")).toHaveValue(" HARVEST-001 ");
    expect(completionRequests).toBe(1);

    fireEvent.click(screen.getByRole("button", { name: "Xác minh lại ngữ cảnh" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Nông trại không còn trong phạm vi truy cập",
    );
    expect(screen.queryByRole("heading", { name: "Hoàn tất thu hoạch" })).not.toBeInTheDocument();
    expect(completionRequests).toBe(1);
  });
});
