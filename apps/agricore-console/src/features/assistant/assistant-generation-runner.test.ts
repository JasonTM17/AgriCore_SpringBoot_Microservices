import { describe, expect, it } from "vitest";

import type { FetchFn } from "../../lib/api/event-stream-test-fixtures";
import { requestUrl } from "../../lib/api/event-stream-test-fixtures";
import {
  eventFrame as frame,
  generationEvent as event,
  runnerClient as client,
  runnerResponse as response,
  TEST_CONVERSATION_ID as CONVERSATION_ID,
  TEST_GENERATION_ID as GENERATION_ID,
} from "./assistant-generation-runner-test-fixtures";
import { runAssistantGeneration } from "./assistant-generation-runner";

describe("assistant generation runner", () => {
  it("replays the durable cursor then streams to terminal state", async () => {
    const queued = event(0, "STATUS", { status: "QUEUED" });
    const streamed = [
      event(1, "STATUS", { status: "RUNNING" }),
      event(2, "DELTA", { delta: "Cây khỏe" }),
      event(3, "COMPLETED", {
        status: "COMPLETED",
        assistantMessageId: "40000000-0000-0000-0000-000000000001",
        finishReason: "stop",
      }),
    ].map(frame).join("");
    const fetchImpl: FetchFn = (input, init) => {
      const accept = new Headers(init?.headers).get("Accept");
      return accept === "text/event-stream" ? response(streamed, true) : response([queued]);
    };
    const projections: number[] = [];
    const phases: string[] = [];

    const result = await runAssistantGeneration(client(fetchImpl), {
      conversationId: CONVERSATION_ID,
      generationId: GENERATION_ID,
      signal: new AbortController().signal,
      onProjection: (projection) => {
        projections.push(projection.lastSequence);
      },
      onPhase: (phase) => {
        phases.push(phase);
      },
    });

    expect(result).toMatchObject({
      kind: "terminal",
      projection: { status: "COMPLETED", lastSequence: 3, draft: "Cây khỏe" },
    });
    expect(projections).toEqual([0, 1, 2, 3]);
    expect(phases).toContain("LIVE");
    expect(phases.at(-1)).toBe("TERMINAL");
  });

  it("backs off after a closed stream and recovers terminal events through JSON", async () => {
    const events = [
      event(0, "STATUS", { status: "QUEUED" }),
      event(1, "STATUS", { status: "RUNNING" }),
      event(2, "DELTA", { delta: "recovered" }),
      event(3, "COMPLETED", {
        status: "COMPLETED",
        assistantMessageId: "40000000-0000-0000-0000-000000000001",
        finishReason: "stop",
      }),
    ];
    let replayCalls = 0;
    const fetchImpl: FetchFn = (_input, init) => {
      const accept = new Headers(init?.headers).get("Accept");
      if (accept === "text/event-stream") return response("", true);
      replayCalls += 1;
      return response(replayCalls === 1 ? [events[0]] : events.slice(1));
    };
    const waits: number[] = [];
    const recoveries: string[] = [];

    const result = await runAssistantGeneration(client(fetchImpl), {
      conversationId: CONVERSATION_ID,
      generationId: GENERATION_ID,
      signal: new AbortController().signal,
      wait: (delay) => {
        waits.push(delay);
        return Promise.resolve();
      },
      onRecovery: (notice) => {
        recoveries.push(notice.code);
      },
    });

    expect(result).toMatchObject({ kind: "terminal", projection: { draft: "recovered" } });
    expect(waits).toEqual([250]);
    expect(recoveries).toEqual(["ASSISTANT_STREAM_ENDED"]);
  });

  it("detaches on caller abort without invoking generation cancellation", async () => {
    const caller = new AbortController();
    let markStreamOpened: (() => void) | undefined;
    const streamOpened = new Promise<void>((resolve) => {
      markStreamOpened = resolve;
    });
    const urls: string[] = [];
    const fetchImpl: FetchFn = (input, init) => {
      urls.push(requestUrl(input));
      const accept = new Headers(init?.headers).get("Accept");
      if (accept !== "text/event-stream") {
        return response([event(0, "STATUS", { status: "QUEUED" })]);
      }
      const signal = init?.signal;
      const body = new ReadableStream<Uint8Array>({
        start(controller) {
          markStreamOpened?.();
          signal?.addEventListener("abort", () => {
            controller.error(new DOMException("Aborted", "AbortError"));
          }, { once: true });
        },
      });
      return Promise.resolve(new Response(body, {
        headers: { "Content-Type": "text/event-stream" },
      }));
    };
    const phases: string[] = [];
    const running = runAssistantGeneration(client(fetchImpl), {
      conversationId: CONVERSATION_ID,
      generationId: GENERATION_ID,
      signal: caller.signal,
      onPhase: (phase) => {
        phases.push(phase);
      },
    });

    await streamOpened;
    caller.abort();
    const result = await running;

    expect(result.kind).toBe("detached");
    expect(phases.at(-1)).toBe("DETACHED");
    expect(urls.every((url) => !url.endsWith("/cancel"))).toBe(true);
  });

  it("caps retry delays and hides unknown error details from recovery notices", async () => {
    const caller = new AbortController();
    const waits: number[] = [];
    const notices: string[] = [];
    const fetchImpl: FetchFn = () => Promise.reject(new Error("sensitive upstream detail"));

    const result = await runAssistantGeneration(client(fetchImpl), {
      conversationId: CONVERSATION_ID,
      generationId: GENERATION_ID,
      signal: caller.signal,
      wait: (delay) => {
        waits.push(delay);
        if (waits.length === 6) caller.abort();
        return Promise.resolve();
      },
      onRecovery: (notice) => {
        notices.push(notice.code);
      },
    });

    expect(result.kind).toBe("detached");
    expect(waits).toEqual([250, 500, 1_000, 2_000, 5_000, 5_000]);
    expect(new Set(notices)).toEqual(new Set(["ASSISTANT_STREAM_INTERRUPTED"]));
  });

  it("stops on persistent replay contract failures instead of retrying forever", async () => {
    const waits: number[] = [];
    const phases: string[] = [];
    const fetchImpl: FetchFn = () => response({ content: [] });

    const result = await runAssistantGeneration(client(fetchImpl), {
      conversationId: CONVERSATION_ID,
      generationId: GENERATION_ID,
      signal: new AbortController().signal,
      wait: (delay) => {
        waits.push(delay);
        return Promise.resolve();
      },
      onPhase: (phase) => {
        phases.push(phase);
      },
    });

    expect(result).toMatchObject({
      kind: "failed",
      errorCode: "INVALID_EVENT_REPLAY_BATCH",
    });
    expect(waits).toEqual([]);
    expect(phases.at(-1)).toBe("FAILED");
  });
});
