import { describe, expect, it, vi } from "vitest";

import { ApiClient } from "../../lib/api/client";
import type { AssistantGenerationEventResponse, AssistantGenerationEventType } from "../../lib/api/types";
import {
  type FetchFn,
  requestUrl,
} from "../../lib/api/event-stream-test-fixtures";
import {
  replayAssistantGenerationEvents,
} from "./assistant-generation-replay";
import { createAssistantGenerationProjection } from "./assistant-generation-projection";

const CONVERSATION_ID = "10000000-0000-0000-0000-000000000001";
const GENERATION_ID = "20000000-0000-0000-0000-000000000001";

function event(
  sequenceNo: number,
  eventType: AssistantGenerationEventType,
  payload: unknown,
): AssistantGenerationEventResponse {
  return {
    id: `30000000-0000-0000-0000-${String(sequenceNo).padStart(12, "0")}`,
    generationId: GENERATION_ID,
    sequenceNo,
    eventType,
    payload: JSON.stringify(payload),
    createdAt: "2026-07-20T12:00:00Z",
  };
}

function jsonResponse(body: unknown): Promise<Response> {
  return Promise.resolve(new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
  }));
}

function client(fetchImpl: FetchFn): ApiClient {
  return new ApiClient({
    getAccessToken: () => "access-token",
    setAccessToken: () => undefined,
    fetchImpl,
  });
}

describe("assistant generation replay", () => {
  it("pages durable events until the terminal projection is reconstructed", async () => {
    const events = [
      event(0, "STATUS", { status: "QUEUED" }),
      event(1, "STATUS", { status: "RUNNING" }),
      event(2, "DELTA", { delta: "healthy" }),
      event(3, "COMPLETED", {
        status: "COMPLETED",
        assistantMessageId: "40000000-0000-0000-0000-000000000001",
        finishReason: "stop",
      }),
    ];
    const fetchImpl = vi.fn<FetchFn>((input) => {
      const url = new URL(requestUrl(input), "https://gateway.agricore.test");
      const after = Number(url.searchParams.get("after"));
      const limit = Number(url.searchParams.get("limit"));
      return jsonResponse(events.filter((item) => item.sequenceNo > after).slice(0, limit));
    });
    const projections: number[] = [];

    const projection = await replayAssistantGenerationEvents(
      client(fetchImpl),
      CONVERSATION_ID,
      createAssistantGenerationProjection(GENERATION_ID),
      {
        batchSize: 2,
        onProjection: (value) => {
          projections.push(value.lastSequence);
        },
      },
    );

    expect(projection).toMatchObject({
      status: "COMPLETED",
      lastSequence: 3,
      draft: "healthy",
    });
    expect(projections).toEqual([1, 3]);
    expect(fetchImpl).toHaveBeenCalledTimes(2);
  });

  it("rejects gaps without applying later events", async () => {
    const fetchImpl: FetchFn = () => jsonResponse([
      event(0, "STATUS", { status: "QUEUED" }),
      event(2, "DELTA", { delta: "gap" }),
    ]);

    await expect(replayAssistantGenerationEvents(
      client(fetchImpl),
      CONVERSATION_ID,
      createAssistantGenerationProjection(GENERATION_ID),
    )).rejects.toMatchObject({
      code: "NONCONTIGUOUS_EVENT_REPLAY",
      expectedSequence: 1,
      actualSequence: 2,
    });
  });

  it("rejects full duplicate batches that cannot advance the cursor", async () => {
    const duplicate = event(0, "STATUS", { status: "QUEUED" });
    const fetchImpl: FetchFn = () => jsonResponse([duplicate, duplicate]);
    const initial = createAssistantGenerationProjection(GENERATION_ID);
    const first = await replayAssistantGenerationEvents(
      client(() => jsonResponse([duplicate])),
      CONVERSATION_ID,
      initial,
    );

    await expect(replayAssistantGenerationEvents(client(fetchImpl), CONVERSATION_ID, first, {
      batchSize: 2,
    })).rejects.toMatchObject({ code: "EVENT_REPLAY_DID_NOT_ADVANCE" });
  });

  it("enforces the configured replay batch bound", async () => {
    const fetchImpl: FetchFn = (input) => {
      const url = new URL(requestUrl(input), "https://gateway.agricore.test");
      const sequence = Number(url.searchParams.get("after")) + 1;
      return jsonResponse([event(
        sequence,
        sequence === 0 ? "STATUS" : "DELTA",
        sequence === 0 ? { status: "QUEUED" } : { delta: "x" },
      )]);
    };

    await expect(replayAssistantGenerationEvents(
      client(fetchImpl),
      CONVERSATION_ID,
      createAssistantGenerationProjection(GENERATION_ID),
      { batchSize: 1, maxBatches: 2 },
    )).rejects.toMatchObject({ code: "EVENT_REPLAY_BATCH_LIMIT_EXCEEDED" });
  });

  it("rejects JSON responses that violate the requested batch contract", async () => {
    const fetchImpl: FetchFn = () => jsonResponse({ content: [] });

    await expect(replayAssistantGenerationEvents(
      client(fetchImpl),
      CONVERSATION_ID,
      createAssistantGenerationProjection(GENERATION_ID),
    )).rejects.toMatchObject({ code: "INVALID_EVENT_REPLAY_BATCH" });
  });

  it("does not query again after the projection is already terminal", async () => {
    const fetchImpl = vi.fn<FetchFn>();
    const terminal = {
      ...createAssistantGenerationProjection(GENERATION_ID),
      status: "CANCELLED" as const,
      lastSequence: 1,
    };

    await expect(replayAssistantGenerationEvents(
      client(fetchImpl),
      CONVERSATION_ID,
      terminal,
    )).resolves.toBe(terminal);
    expect(fetchImpl).not.toHaveBeenCalled();
  });
});
