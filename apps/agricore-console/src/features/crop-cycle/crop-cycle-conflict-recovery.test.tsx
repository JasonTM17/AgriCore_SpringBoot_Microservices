import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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
const conflictBody = {
  timestamp: "2026-07-19T00:00:00Z",
  status: 409,
  error: "Conflict",
  code: "OPTIMISTIC_LOCK",
  message: "reload the latest state before retrying",
  path: stagePath,
};

describe("Crop cycle conflict recovery", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/crop-cycles/${cycleA.id}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("locks stale retry and preserves the draft until reload succeeds", async () => {
    const baseFetch = authenticatedFetch();
    const externallyUpdated = { ...cycleA, version: 1 } as const;
    const committed = { ...externallyUpdated, stage: "HARVESTING", version: 2 } as const;
    let detailReads = 0;
    let stageWrites = 0;
    let resolveReload!: (response: Response) => void;
    const pendingReload = new Promise<Response>((resolve) => {
      resolveReload = resolve;
    });
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath && init?.method === "GET") {
        detailReads += 1;
        return detailReads === 1 ? jsonResponse(cycleA) : pendingReload;
      }
      if (url.pathname === stagePath && init?.method === "POST") {
        stageWrites += 1;
        return stageWrites === 1 ? jsonResponse(conflictBody, 409) : jsonResponse(committed);
      }
      return baseFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    await screen.findByRole("heading", { name: "Chi tiết mùa vụ" });
    fireEvent.change(screen.getByLabelText("Giai đoạn tiếp theo"), {
      target: { value: "HARVESTING" },
    });
    fireEvent.change(screen.getByLabelText("Ghi chú chuyển giai đoạn"), {
      target: { value: "Giữ ghi chú này" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Cập nhật giai đoạn" }));
    await screen.findByText(/Mùa vụ vừa thay đổi/);

    fireEvent.click(screen.getByRole("button", { name: "Tải lại trạng thái" }));
    await waitFor(() => expect(detailReads).toBe(2));
    expect(screen.getByRole("button", { name: "Cập nhật giai đoạn" })).toBeDisabled();
    fireEvent.submit(screen.getByLabelText("Ghi chú chuyển giai đoạn").closest("form")!);
    expect(stageWrites).toBe(1);

    resolveReload(await jsonResponse(externallyUpdated));
    await screen.findByText("Phiên bản 1");
    await waitFor(() => expect(screen.getByRole("button", { name: "Cập nhật giai đoạn" })).toBeEnabled());
    expect(screen.getByLabelText("Giai đoạn tiếp theo")).toHaveValue("HARVESTING");
    expect(screen.getByLabelText("Ghi chú chuyển giai đoạn")).toHaveValue("Giữ ghi chú này");

    fireEvent.click(screen.getByRole("button", { name: "Cập nhật giai đoạn" }));
    await screen.findByText("Phiên bản 2");
    expect(stageWrites).toBe(2);
    expect(screen.getByLabelText("Ghi chú chuyển giai đoạn")).toHaveValue("");
  });

  it("keeps cached detail and draft locked when conflict reload fails", async () => {
    const baseFetch = authenticatedFetch();
    let detailReads = 0;
    let stageWrites = 0;
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath && init?.method === "GET") {
        detailReads += 1;
        if (detailReads === 1) return jsonResponse(cycleA);
        return jsonResponse({ status: 503, code: "FARM_ACCESS_UNAVAILABLE", message: "unavailable" }, 503);
      }
      if (url.pathname === stagePath && init?.method === "POST") {
        stageWrites += 1;
        return jsonResponse(conflictBody, 409);
      }
      return baseFetch(input);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    await screen.findByRole("heading", { name: "Chi tiết mùa vụ" });
    fireEvent.change(screen.getByLabelText("Giai đoạn tiếp theo"), {
      target: { value: "HARVESTING" },
    });
    fireEvent.change(screen.getByLabelText("Ghi chú chuyển giai đoạn"), {
      target: { value: "Không làm mất" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Cập nhật giai đoạn" }));
    await screen.findByText(/Mùa vụ vừa thay đổi/);
    fireEvent.click(screen.getByRole("button", { name: "Tải lại trạng thái" }));

    await waitFor(() => expect(detailReads).toBe(3), { timeout: 3_000 });
    expect(await screen.findByText(/Không thể làm mới mùa vụ/)).toBeInTheDocument();
    expect(screen.getByText("Phiên bản 0")).toBeInTheDocument();
    expect(screen.getByLabelText("Ghi chú chuyển giai đoạn")).toHaveValue("Không làm mất");
    expect(screen.getByRole("button", { name: "Cập nhật giai đoạn" })).toBeDisabled();
    expect(stageWrites).toBe(1);
  });
});
