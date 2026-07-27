import { renderHook, waitFor } from "@testing-library/react";
import { StrictMode, type ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import type { FetchFn } from "../../lib/api/event-stream-test-fixtures";
import type { runAssistantGeneration } from "./assistant-generation-runner";
import {
  controllerApi,
  TEST_CONVERSATION_ID,
  TEST_GENERATION_ID,
} from "./assistant-generation-controller-test-fixtures";
import { useAssistantGeneration } from "./use-assistant-generation";

function StrictWrapper({ children }: { children: ReactNode }) {
  return <StrictMode>{children}</StrictMode>;
}

describe("useAssistantGeneration resume", () => {
  it("starts one resumable runner under React StrictMode", async () => {
    const runner = vi.fn<typeof runAssistantGeneration>((_api, options) => {
      if (!options.initialProjection) throw new Error("Missing initial projection");
      const projection = options.initialProjection;
      return new Promise((resolve) => {
        options.signal.addEventListener("abort", () => {
          resolve({ kind: "detached", projection });
        }, { once: true });
      });
    });
    const api = controllerApi(vi.fn<FetchFn>());
    const { result, unmount } = renderHook(() => useAssistantGeneration({
      api,
      conversationId: TEST_CONVERSATION_ID,
      initialGenerationId: TEST_GENERATION_ID,
      runner,
    }), { wrapper: StrictWrapper });

    await waitFor(() => expect(runner).toHaveBeenCalledOnce());
    expect(runner.mock.calls[0]?.[1]).toMatchObject({
      conversationId: TEST_CONVERSATION_ID,
      generationId: TEST_GENERATION_ID,
      initialProjection: { generationId: TEST_GENERATION_ID },
    });
    expect(result.current.phase).toBe("RECONCILING");
    unmount();
  });

  it("clears the resumable descriptor after a terminal result", async () => {
    const generationChanged = vi.fn();
    const runner = vi.fn<typeof runAssistantGeneration>((_api, options) => {
      if (!options.initialProjection) throw new Error("Missing initial projection");
      return Promise.resolve({
        kind: "terminal",
        projection: { ...options.initialProjection, status: "COMPLETED" },
      });
    });
    const api = controllerApi(vi.fn<FetchFn>());
    const { result } = renderHook(() => useAssistantGeneration({
      api,
      conversationId: TEST_CONVERSATION_ID,
      initialGenerationId: TEST_GENERATION_ID,
      onGenerationChanged: generationChanged,
      runner,
    }));

    await waitFor(() => expect(result.current.phase).toBe("TERMINAL"));
    expect(generationChanged).toHaveBeenCalledOnce();
    expect(generationChanged).toHaveBeenCalledWith(null);
  });

  it("does not construct a stream path from a malformed descriptor", async () => {
    const runner = vi.fn<typeof runAssistantGeneration>();
    const api = controllerApi(vi.fn<FetchFn>());
    renderHook(() => useAssistantGeneration({
      api,
      conversationId: TEST_CONVERSATION_ID,
      initialGenerationId: "../../other-generation",
      runner,
    }));

    await Promise.resolve();
    expect(runner).not.toHaveBeenCalled();
  });
});
