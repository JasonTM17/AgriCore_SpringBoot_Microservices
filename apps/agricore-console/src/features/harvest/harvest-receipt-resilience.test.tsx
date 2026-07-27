import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "../../app/app";
import type { UserResponse } from "../../lib/api/types";
import {
  authenticatedFetch,
  jsonResponse,
  requestUrl,
} from "../crop-cycle/crop-cycle-test-fixtures";
import {
  eventId,
  harvestBatch,
  harvestId,
  inventoryAcknowledged,
  publishedProducer,
  traceabilityAcknowledged,
} from "./harvest-test-fixtures";

const harvestPath = `/api/v1/harvests/${harvestId}`;
const producerPath = `${harvestPath}/completion-event`;
const inventoryPath = `/api/v1/inventory/events/harvest-completed/${eventId}/acknowledgement`;
const traceabilityPath = `/api/v1/traceability/events/harvest-completed/${eventId}/acknowledgement`;

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

describe("harvest receipt resilience", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/harvests/${harvestId}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("keeps the receipt and successful projection visible when one consumer is unavailable", async () => {
    const baseFetch = authenticatedFetch();
    let traceabilityAvailable = false;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const path = requestUrl(input).pathname;
      if (path === harvestPath) return jsonResponse(harvestBatch);
      if (path === producerPath) return jsonResponse(publishedProducer);
      if (path === inventoryPath) return jsonResponse(inventoryAcknowledged);
      if (path === traceabilityPath) {
        return traceabilityAvailable
          ? jsonResponse(traceabilityAcknowledged)
          : jsonResponse({
            status: 503,
            error: "Service Unavailable",
            code: "TRACEABILITY_UNAVAILABLE",
            message: "unavailable",
            path,
          }, 503);
      }
      return baseFetch(input);
    }));

    render(<App />);

    expect(await screen.findByRole("heading", { name: `Biên nhận ${harvestBatch.code}` }))
      .toBeInTheDocument();
    expect(await screen.findByText("Đã nhập kho")).toBeInTheDocument();
    expect(await screen.findByText("Không thể đọc trạng thái truy xuất", {}, { timeout: 4_000 }))
      .toBeInTheDocument();

    traceabilityAvailable = true;
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("Đã tạo lô truy xuất")).toBeInTheDocument();
  });

  it("does not call restricted acknowledgement or repair APIs for a read-only role", async () => {
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
      if (path === harvestPath) return jsonResponse(harvestBatch);
      if (path === producerPath) return jsonResponse(publishedProducer);
      return baseFetch(input);
    }));

    render(<App />);

    expect(await screen.findByRole("heading", { name: `Biên nhận ${harvestBatch.code}` }))
      .toBeInTheDocument();
    expect(screen.getAllByText("Vai trò hiện tại không được phép xem acknowledgement"))
      .toHaveLength(2);
    await waitFor(() => expect(requestedPaths).toContain(producerPath));
    expect(requestedPaths).not.toContain(inventoryPath);
    expect(requestedPaths).not.toContain(traceabilityPath);
    expect(screen.queryByRole("button", { name: "Gửi lại sự kiện gốc" })).not.toBeInTheDocument();
  });

  it("revalidates harvest access before refreshing event and consumer state", async () => {
    const baseFetch = authenticatedFetch();
    let accessRevoked = false;
    let inventoryRequests = 0;
    let traceabilityRequests = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const path = requestUrl(input).pathname;
      if (path === harvestPath) {
        return accessRevoked
          ? jsonResponse({
            status: 403,
            error: "Forbidden",
            code: "FARM_ACCESS_DENIED",
            message: "denied",
            path,
          }, 403)
          : jsonResponse(harvestBatch);
      }
      if (path === producerPath) return jsonResponse(publishedProducer);
      if (path === inventoryPath) {
        inventoryRequests += 1;
        return jsonResponse(inventoryAcknowledged);
      }
      if (path === traceabilityPath) {
        traceabilityRequests += 1;
        return jsonResponse(traceabilityAcknowledged);
      }
      return baseFetch(input);
    }));

    render(<App />);
    expect(await screen.findByText("Đã tạo lô truy xuất")).toBeInTheDocument();
    expect(inventoryRequests).toBe(1);
    expect(traceabilityRequests).toBe(1);

    accessRevoked = true;
    fireEvent.click(screen.getByRole("button", { name: "Tải lại tất cả" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("không còn quyền");
    await waitFor(() => {
      expect(inventoryRequests).toBe(1);
      expect(traceabilityRequests).toBe(1);
    });
  });
});
