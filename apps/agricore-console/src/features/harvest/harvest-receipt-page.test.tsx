import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "../../app/app";
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
  inventoryPending,
  publishedProducer,
  traceabilityPending,
} from "./harvest-test-fixtures";

const harvestPath = `/api/v1/harvests/${harvestId}`;
const producerPath = `${harvestPath}/completion-event`;
const repairPath = `${producerPath}/republish`;
const inventoryPath = `/api/v1/inventory/events/harvest-completed/${eventId}/acknowledgement`;
const traceabilityPath = `/api/v1/traceability/events/harvest-completed/${eventId}/acknowledgement`;

describe("harvest receipt page", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/harvests/${harvestId}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("rebuilds the receipt and projection state from APIs after a direct reload", async () => {
    const baseFetch = authenticatedFetch();
    const requestedPaths: string[] = [];
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const path = requestUrl(input).pathname;
      requestedPaths.push(path);
      if (path === harvestPath) return jsonResponse(harvestBatch);
      if (path === producerPath) return jsonResponse(publishedProducer);
      if (path === inventoryPath) return jsonResponse(inventoryAcknowledged);
      if (path === traceabilityPath) return jsonResponse(traceabilityPending);
      return baseFetch(input);
    }));

    render(<App />);

    expect(await screen.findByRole("heading", { name: `Biên nhận ${harvestBatch.code}` }))
      .toBeInTheDocument();
    expect(await screen.findByText("Đã phát sự kiện")).toBeInTheDocument();
    expect(await screen.findByText("Đã nhập kho")).toBeInTheDocument();
    expect(await screen.findByText("Đang chờ lô truy xuất")).toBeInTheDocument();
    expect(requestedPaths).toEqual(expect.arrayContaining([
      harvestPath,
      producerPath,
      inventoryPath,
      traceabilityPath,
    ]));
  });

  it("keeps a legacy receipt readable without probing consumer acknowledgements", async () => {
    const baseFetch = authenticatedFetch();
    const requestedPaths: string[] = [];
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const path = requestUrl(input).pathname;
      requestedPaths.push(path);
      if (path === harvestPath) {
        return jsonResponse({ ...harvestBatch, lastOutboxEventId: null });
      }
      if (path === producerPath) {
        return jsonResponse({
          ...publishedProducer,
          eventId: null,
          state: "UNAVAILABLE",
          createdAt: null,
          publishedAt: null,
          publishAttempts: 0,
        });
      }
      return baseFetch(input);
    }));

    render(<App />);

    expect(await screen.findByText("Bản ghi cũ không có event ID ổn định")).toBeInTheDocument();
    expect(requestedPaths).not.toContain(inventoryPath);
    expect(requestedPaths).not.toContain(traceabilityPath);
    expect(screen.queryByRole("button", { name: "Gửi lại sự kiện gốc" })).not.toBeInTheDocument();
  });

  it("renders an in-progress batch without assuming completion measurements", async () => {
    const baseFetch = authenticatedFetch();
    const requestedPaths: string[] = [];
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const path = requestUrl(input).pathname;
      requestedPaths.push(path);
      if (path === harvestPath) {
        return jsonResponse({
          ...harvestBatch,
          status: "IN_PROGRESS",
          grossWeightKg: null,
          netWeightKg: null,
          qualityGrade: null,
          harvestedAt: null,
          lastOutboxEventId: null,
        });
      }
      if (path === producerPath) {
        return jsonResponse({
          ...publishedProducer,
          eventId: null,
          state: "UNAVAILABLE",
          createdAt: null,
          publishedAt: null,
          publishAttempts: 0,
        });
      }
      return baseFetch(input);
    }));

    render(<App />);

    expect(await screen.findByRole("heading", { name: `Biên nhận ${harvestBatch.code}` }))
      .toBeInTheDocument();
    expect(screen.getAllByText("—")).toHaveLength(4);
    expect(screen.getByText("Chưa phát sinh")).toBeInTheDocument();
    expect(await screen.findByText("Chưa phát sinh sự kiện")).toBeInTheDocument();
    expect(screen.queryByText("Không có (legacy)")).not.toBeInTheDocument();
    expect(requestedPaths).not.toContain(inventoryPath);
    expect(requestedPaths).not.toContain(traceabilityPath);
  });

  it("requeues the same event and publishes the authoritative retry state", async () => {
    const baseFetch = authenticatedFetch();
    let repairRequests = 0;
    vi.stubGlobal("confirm", vi.fn(() => true));
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = requestUrl(input).pathname;
      if (path === harvestPath) return jsonResponse(harvestBatch);
      if (path === producerPath) return jsonResponse(publishedProducer);
      if (path === inventoryPath) return jsonResponse(inventoryPending);
      if (path === traceabilityPath) return jsonResponse(traceabilityPending);
      if (path === repairPath && init?.method === "POST") {
        repairRequests += 1;
        return jsonResponse({
          ...publishedProducer,
          state: "RETRYING",
          publishedAt: null,
          publishAttempts: 1,
        });
      }
      return baseFetch(input);
    }));

    render(<App />);
    const repair = await screen.findByRole("button", { name: "Gửi lại sự kiện gốc" });
    fireEvent.click(repair);

    expect(await screen.findByText("Đang gửi lại sự kiện")).toBeInTheDocument();
    await waitFor(() => expect(repairRequests).toBe(1));
    expect(screen.getByText(eventId)).toBeInTheDocument();
  });
});
