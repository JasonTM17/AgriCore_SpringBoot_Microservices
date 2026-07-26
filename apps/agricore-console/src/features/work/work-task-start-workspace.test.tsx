import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "../../app/app";
import {
  authenticatedFetch,
  cycleA,
  jsonResponse,
  page,
  requestUrl,
} from "../crop-cycle/crop-cycle-test-fixtures";
import { inProgressTaskA, taskA } from "./work-task-test-fixtures";

const detailPath = `/api/v1/crop-cycles/${cycleA.id}`;
const workPath = "/api/v1/work-tasks";

describe("work-task start workspace", () => {
  beforeEach(() => {
    window.history.pushState({}, "", `/crop-cycles/${cycleA.id}`);
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("starts an assigned task before exposing completion", async () => {
    const baseFetch = authenticatedFetch();
    let started = false;
    let startRequests = 0;
    let startedWorkReads = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.pathname === detailPath) return jsonResponse(cycleA);
      if (url.pathname === `${workPath}/${taskA.id}/start` && init?.method === "POST") {
        startRequests += 1;
        expect(init.body).toBeUndefined();
        started = true;
        return jsonResponse(inProgressTaskA);
      }
      if (url.pathname === workPath) {
        if (started) startedWorkReads += 1;
        return jsonResponse(page([started ? inProgressTaskA : taskA]));
      }
      return baseFetch(input);
    }));

    render(<App />);

    expect(await screen.findByText(taskA.title)).toBeInTheDocument();
    expect(screen.queryByLabelText(`Ghi chú hoàn tất cho ${taskA.code}`)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: `Bắt đầu ${taskA.code}` }));

    await waitFor(() => expect(startRequests).toBe(1));
    await waitFor(() => expect(startedWorkReads).toBeGreaterThan(0), { timeout: 3_000 });
    expect(screen.getByLabelText(`Ghi chú hoàn tất cho ${taskA.code}`)).toBeInTheDocument();
    expect(screen.getByRole("status", { name: "Bắt đầu công việc thành công" })).toHaveTextContent(taskA.code);
  });
});
