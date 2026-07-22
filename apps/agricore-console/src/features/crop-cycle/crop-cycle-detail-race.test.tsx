import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { onlineManager } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "../../app/app";
import {
  authenticatedFetch,
  cycleA,
  jsonResponse,
  requestUrl,
} from "./crop-cycle-test-fixtures";

const detailPath = `/api/v1/crop-cycles/${cycleA.id}`;
const stagePath = `${detailPath}/stage`;

async function flushQuerySettlement() {
  await act(async () => {
    await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
  });
}

describe("Crop cycle detail request ordering", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/crop-cycles/${cycleA.id}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    onlineManager.setOnline(true);
    vi.unstubAllGlobals();
  });

  it("cancels a reconnect refetch that starts while the stage request is pending", async () => {
    const baseFetch = authenticatedFetch();
    const updatedCycle = { ...cycleA, stage: "HARVESTING", version: 1 } as const;
    let detailReads = 0;
    let staleBodyRead = false;
    let resolveStageResponse!: (response: Response) => void;
    let resolveStaleResponse!: (response: Response) => void;
    const pendingStageResponse = new Promise<Response>((resolve) => {
      resolveStageResponse = resolve;
    });
    const staleResponse = new Promise<Response>((resolve) => {
      resolveStaleResponse = resolve;
    });
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath && init?.method === "GET") {
        detailReads += 1;
        return detailReads === 1 ? jsonResponse(cycleA) : staleResponse;
      }
      if (url.pathname === stagePath && init?.method === "PATCH") {
        return pendingStageResponse;
      }
      return baseFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    await screen.findByRole("heading", { name: "Chi tiết mùa vụ" });
    fireEvent.change(screen.getByLabelText("Giai đoạn tiếp theo"), {
      target: { value: "HARVESTING" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Cập nhật giai đoạn" }));
    await waitFor(() => expect(fetchMock.mock.calls.some(([input, init]) =>
      requestUrl(input).pathname === stagePath && init?.method === "PATCH",
    )).toBe(true));

    onlineManager.setOnline(false);
    onlineManager.setOnline(true);
    await waitFor(() => expect(detailReads).toBe(2));
    resolveStageResponse(new Response(JSON.stringify(updatedCycle), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }));
    await screen.findByText("Phiên bản 1");

    const stale = new Response(JSON.stringify(cycleA), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
    const readStaleBody = stale.json.bind(stale);
    stale.json = async () => {
      const body: unknown = await readStaleBody();
      staleBodyRead = true;
      return body;
    };
    resolveStaleResponse(stale);

    await waitFor(() => expect(staleBodyRead).toBe(true));
    await flushQuerySettlement();
    expect(screen.getByText("Phiên bản 1")).toBeInTheDocument();
    expect(screen.getByText("Đang thu hoạch")).toBeInTheDocument();
  });
});
