import { act, renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { FetchFn } from "../../lib/api/event-stream-test-fixtures";
import { requestUrl } from "../../lib/api/event-stream-test-fixtures";
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

describe("useAssistantGeneration lifecycle", () => {
  it("calls cancellation only on explicit action and detaches the runner on unmount", async () => {
    const urls: string[] = [];
    const fetchImpl = vi.fn<FetchFn>((input) => {
      const url = requestUrl(input);
      urls.push(url);
      return jsonResponse(url.endsWith("/cancel")
        ? generationResponse({ status: "CANCEL_REQUESTED" })
        : generationResponse());
    });
    const captured: { signal?: AbortSignal } = {};
    const runner = vi.fn<typeof runAssistantGeneration>((_api, options) => {
      captured.signal = options.signal;
      if (!options.initialProjection) {
        return Promise.reject(new Error("Missing initial projection"));
      }
      const initialProjection = options.initialProjection;
      return new Promise((resolve) => {
        options.signal.addEventListener("abort", () => {
          resolve({ kind: "detached", projection: initialProjection });
        }, { once: true });
      });
    });
    const historyChanged = vi.fn();
    const api = controllerApi(fetchImpl);
    const { result, unmount } = renderHook(() => useAssistantGeneration({
      api,
      conversationId: TEST_CONVERSATION_ID,
      createIdempotencyKey: () => TEST_IDEMPOTENCY_KEY,
      runner,
      onHistoryChanged: historyChanged,
    }));

    await act(async () => {
      expect(await result.current.send("Hủy yêu cầu này")).toBe(true);
    });
    await waitFor(() => expect(runner).toHaveBeenCalledOnce());
    expect(captured.signal?.aborted).toBe(false);

    await act(async () => {
      expect(await result.current.cancel()).toBe(true);
    });
    expect(urls.filter((url) => url.endsWith("/cancel"))).toHaveLength(1);
    expect(captured.signal?.aborted).toBe(false);
    expect(historyChanged).toHaveBeenCalledTimes(2);

    unmount();
    expect(captured.signal?.aborted).toBe(true);
    expect(urls.filter((url) => url.endsWith("/cancel"))).toHaveLength(1);
  });

  it("rejects a second send while a generation remains active", async () => {
    const fetchImpl = vi.fn<FetchFn>(() => jsonResponse(generationResponse()));
    const runner = vi.fn<typeof runAssistantGeneration>(() => new Promise(() => undefined));
    const api = controllerApi(fetchImpl);
    const { result, unmount } = renderHook(() => useAssistantGeneration({
      api,
      conversationId: TEST_CONVERSATION_ID,
      createIdempotencyKey: () => TEST_IDEMPOTENCY_KEY,
      runner,
    }));

    await act(async () => {
      expect(await result.current.send("Yêu cầu đầu tiên")).toBe(true);
    });
    await act(async () => {
      expect(await result.current.send("Không được gửi chồng")).toBe(false);
    });

    expect(result.current.submissionError).toMatchObject({
      code: "GENERATION_ALREADY_ACTIVE",
    });
    expect(result.current.phase).toBe("RECONCILING");
    expect(fetchImpl).toHaveBeenCalledOnce();
    unmount();
  });

  it("aborts an in-flight command and ignores its stale result after conversation change", async () => {
    let commandAborted = false;
    const fetchImpl = vi.fn<FetchFn>((_input, init) => new Promise((_resolve, reject) => {
      init?.signal?.addEventListener("abort", () => {
        commandAborted = true;
        reject(new DOMException("Aborted", "AbortError"));
      }, { once: true });
    }));
    const api = controllerApi(fetchImpl);
    const { result, rerender } = renderHook(
      ({ conversationId }) => useAssistantGeneration({
        api,
        conversationId,
        createIdempotencyKey: () => TEST_IDEMPOTENCY_KEY,
      }),
      { initialProps: { conversationId: TEST_CONVERSATION_ID } },
    );

    let submission: Promise<boolean> | undefined;
    act(() => {
      submission = result.current.send("Yêu cầu đang gửi");
    });
    await waitFor(() => expect(fetchImpl).toHaveBeenCalledOnce());
    rerender({ conversationId: `${TEST_CONVERSATION_ID.slice(0, -1)}2` });
    await act(async () => {
      expect(await submission).toBe(false);
    });

    expect(commandAborted).toBe(true);
    expect(result.current).toMatchObject({
      phase: "IDLE",
      projection: null,
      submissionError: null,
    });
    expect(fetchImpl.mock.calls[0]?.[0]).toContain(TEST_CONVERSATION_ID);
    expect(TEST_GENERATION_ID).not.toBe(TEST_CONVERSATION_ID);
  });
});
