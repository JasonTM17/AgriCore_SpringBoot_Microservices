import { act, renderHook, waitFor } from "@testing-library/react";
import { StrictMode, type ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import type { FetchFn } from "../../lib/api/event-stream-test-fixtures";
import type { runAssistantGeneration } from "./assistant-generation-runner";
import {
  controllerApi,
  generationResponse,
  jsonResponse,
  TEST_CONVERSATION_ID,
  TEST_GENERATION_ID,
  TEST_IDEMPOTENCY_KEY,
} from "./assistant-generation-controller-test-fixtures";
import { useAssistantGeneration } from "./use-assistant-generation";

function StrictWrapper({ children }: { children: ReactNode }) {
  return <StrictMode>{children}</StrictMode>;
}

describe("useAssistantGeneration submission", () => {
  it("submits a normalized prompt once and starts its authoritative generation", async () => {
    const fetchImpl = vi.fn<FetchFn>(() => jsonResponse(generationResponse()));
    const generationChanged = vi.fn();
    const historyChanged = vi.fn();
    const runner = vi.fn<typeof runAssistantGeneration>(async (_api, options) => {
      if (!options.initialProjection) throw new Error("Missing initial projection");
      const projection = {
        ...options.initialProjection,
        status: "COMPLETED" as const,
        lastSequence: 1,
        draft: "Đã xử lý",
      };
      await options.onPhase?.("LIVE");
      await options.onProjection?.(projection);
      return { kind: "terminal", projection };
    });
    const api = controllerApi(fetchImpl);
    const { result } = renderHook(() => useAssistantGeneration({
      api,
      conversationId: TEST_CONVERSATION_ID,
      createIdempotencyKey: () => TEST_IDEMPOTENCY_KEY,
      runner,
      onGenerationChanged: generationChanged,
      onHistoryChanged: historyChanged,
    }), { wrapper: StrictWrapper });

    let accepted = false;
    await act(async () => {
      accepted = await result.current.send("  Tóm tắt\n công việc  ");
    });

    expect(accepted).toBe(true);
    await waitFor(() => expect(result.current.phase).toBe("TERMINAL"));
    const [input, init] = fetchImpl.mock.calls[0] ?? [];
    expect(input).toBe(`/api/v1/assistant/conversations/${TEST_CONVERSATION_ID}/generations`);
    expect(init?.body).toBe(JSON.stringify({ prompt: "Tóm tắt\n công việc" }));
    expect(new Headers(init?.headers).get("Idempotency-Key")).toBe(TEST_IDEMPOTENCY_KEY);
    expect(runner).toHaveBeenCalledOnce();
    expect(generationChanged.mock.calls).toEqual([[TEST_GENERATION_ID], [null]]);
    expect(historyChanged).toHaveBeenCalledTimes(2);
    expect(result.current).toMatchObject({
      pendingPrompt: null,
      isSubmitting: false,
      projection: { status: "COMPLETED", draft: "Đã xử lý" },
    });
  });

  it("retries an uncertain submission with the exact same idempotency key", async () => {
    let requestCount = 0;
    const fetchImpl = vi.fn<FetchFn>(() => {
      requestCount += 1;
      return requestCount === 1
        ? Promise.reject(new TypeError("network interrupted"))
        : jsonResponse(generationResponse());
    });
    const keyFactory = vi.fn(() => TEST_IDEMPOTENCY_KEY);
    const runner = vi.fn<typeof runAssistantGeneration>((_api, options) => {
      if (!options.initialProjection) throw new Error("Missing initial projection");
      return Promise.resolve({ kind: "detached", projection: options.initialProjection });
    });
    const api = controllerApi(fetchImpl);
    const { result } = renderHook(() => useAssistantGeneration({
      api,
      conversationId: TEST_CONVERSATION_ID,
      createIdempotencyKey: keyFactory,
      runner,
    }));

    let firstAccepted = true;
    await act(async () => {
      firstAccepted = await result.current.send("Kiểm tra mùa vụ");
    });
    expect(firstAccepted).toBe(false);
    expect(result.current).toMatchObject({
      phase: "SUBMIT_FAILED",
      pendingPrompt: "Kiểm tra mùa vụ",
    });

    let retryAccepted = false;
    await act(async () => {
      retryAccepted = await result.current.retrySubmission();
    });
    expect(retryAccepted).toBe(true);
    const keys = fetchImpl.mock.calls.map(([, init]) =>
      new Headers(init?.headers).get("Idempotency-Key"));
    expect(keys).toEqual([TEST_IDEMPOTENCY_KEY, TEST_IDEMPOTENCY_KEY]);
    expect(keyFactory).toHaveBeenCalledOnce();
    expect(runner).toHaveBeenCalledOnce();
  });
});
