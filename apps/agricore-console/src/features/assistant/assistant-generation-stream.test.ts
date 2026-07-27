import { describe, expect, it, vi } from "vitest";

import { ApiClient } from "../../lib/api/client";
import type { AssistantGenerationEventType } from "../../lib/api/types";
import type { FetchFn } from "../../lib/api/event-stream-test-fixtures";
import type { DecodedAssistantGenerationEvent } from "./assistant-event-codec";
import { streamAssistantGeneration } from "./assistant-generation-stream";

const CONVERSATION_ID = "10000000-0000-0000-0000-000000000001";
const GENERATION_ID = "20000000-0000-0000-0000-000000000001";

function eventFrame(
  sequenceNo: number,
  eventType: AssistantGenerationEventType,
  payload: unknown,
): string {
  const envelope = {
    id: `30000000-0000-0000-0000-${String(sequenceNo).padStart(12, "0")}`,
    generationId: GENERATION_ID,
    sequenceNo,
    eventType,
    payload: JSON.stringify(payload),
    createdAt: "2026-07-20T12:00:00Z",
  };
  return `id:${sequenceNo}\nevent:${eventType.toLowerCase()}\ndata:${JSON.stringify(envelope)}\n\n`;
}

function streamResponse(body: string): Response {
  return new Response(body, { headers: { "Content-Type": "text/event-stream" } });
}

function client(fetchImpl: FetchFn): ApiClient {
  return new ApiClient({
    getAccessToken: () => "access-token",
    setAccessToken: () => undefined,
    fetchImpl,
  });
}

describe("assistant generation stream", () => {
  it("resumes after a durable cursor and reports heartbeat and safe stream errors", async () => {
    const body = [
      eventFrame(4, "DELTA", { delta: "xin chào" }),
      ":heartbeat\n\n",
      "event:stream-error\ndata:{\"code\":\"GENERATION_STREAM_UNAVAILABLE\"}\n\n",
    ].join("");
    const fetchImpl = vi.fn<FetchFn>(() => Promise.resolve(streamResponse(body)));
    const onEvent = vi.fn<(event: DecodedAssistantGenerationEvent) => void>();
    const onHeartbeat = vi.fn();
    const onStreamError = vi.fn();

    const result = await streamAssistantGeneration(client(fetchImpl), {
      conversationId: CONVERSATION_ID,
      generationId: GENERATION_ID,
      afterSequence: 3,
      onEvent,
      onHeartbeat,
      onStreamError,
    });

    expect(result).toEqual({
      lastSequence: 4,
      streamErrorCode: "GENERATION_STREAM_UNAVAILABLE",
    });
    expect(onEvent).toHaveBeenCalledOnce();
    expect(onEvent.mock.calls[0]?.[0]).toMatchObject({
      event: { sequenceNo: 4, eventType: "DELTA" },
    });
    expect(onHeartbeat).toHaveBeenCalledOnce();
    expect(onStreamError).toHaveBeenCalledWith("GENERATION_STREAM_UNAVAILABLE");
    const [input, init] = fetchImpl.mock.calls[0] ?? [];
    expect(input).toBe(
      `/api/v1/assistant/conversations/${CONVERSATION_ID}/generations/${GENERATION_ID}/events?after=3`,
    );
    expect(new Headers(init?.headers).get("Last-Event-ID")).toBe("3");
  });

  it("omits Last-Event-ID when replay starts before sequence zero", async () => {
    const fetchImpl = vi.fn<FetchFn>(() => Promise.resolve(streamResponse(
      eventFrame(0, "STATUS", { status: "QUEUED" }),
    )));

    const result = await streamAssistantGeneration(client(fetchImpl), {
      conversationId: "conversation/id",
      generationId: GENERATION_ID,
      afterSequence: -1,
      onEvent: () => undefined,
    });

    expect(result.lastSequence).toBe(0);
    const [input, init] = fetchImpl.mock.calls[0] ?? [];
    expect(input).toContain("/conversations/conversation%2Fid/");
    expect(new Headers(init?.headers).has("Last-Event-ID")).toBe(false);
  });

  it("rejects gaps before handing an event to application state", async () => {
    const onEvent = vi.fn();
    const fetchImpl: FetchFn = () => Promise.resolve(streamResponse(
      eventFrame(5, "DELTA", { delta: "gap" }),
    ));

    await expect(streamAssistantGeneration(client(fetchImpl), {
      conversationId: CONVERSATION_ID,
      generationId: GENERATION_ID,
      afterSequence: 3,
      onEvent,
    })).rejects.toMatchObject({
      code: "NONCONTIGUOUS_EVENT_STREAM",
      expectedSequence: 4,
      actualSequence: 5,
    });
    expect(onEvent).not.toHaveBeenCalled();
  });

  it("rejects frames received after a terminal stream-error", async () => {
    const body = [
      "event:stream-error\ndata:{\"code\":\"GENERATION_STREAM_UNAVAILABLE\"}\n\n",
      eventFrame(0, "STATUS", { status: "QUEUED" }),
    ].join("");
    const fetchImpl: FetchFn = () => Promise.resolve(streamResponse(body));
    const onEvent = vi.fn();

    await expect(streamAssistantGeneration(client(fetchImpl), {
      conversationId: CONVERSATION_ID,
      generationId: GENERATION_ID,
      afterSequence: -1,
      onEvent,
    })).rejects.toMatchObject({ code: "EVENT_AFTER_STREAM_ERROR" });
    expect(onEvent).not.toHaveBeenCalled();
  });

  it.each([-2, 1.5, Number.MAX_SAFE_INTEGER + 1])(
    "rejects invalid cursor %s before issuing a request",
    async (afterSequence) => {
      const fetchImpl = vi.fn<FetchFn>();

      await expect(streamAssistantGeneration(client(fetchImpl), {
        conversationId: CONVERSATION_ID,
        generationId: GENERATION_ID,
        afterSequence,
        onEvent: () => undefined,
      })).rejects.toThrow(RangeError);
      expect(fetchImpl).not.toHaveBeenCalled();
    },
  );
});
